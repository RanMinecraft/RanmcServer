package cc.ranmc.server.qqbot;

import cc.ranmc.server.Main;
import cc.ranmc.server.util.MinecraftUtil;
import com.alibaba.fastjson2.JSONObject;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QQBotHandler {

    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final String WS_URL = "wss://api.sgroup.qq.com/websocket/";
    private static final String API_URL = "https://api.sgroup.qq.com";

    private static String appId;
    private static String clientSecret;
    private static String accessToken;
    private static long tokenExpireTime;
    private static WebSocket webSocket;
    private static ScheduledExecutorService scheduler;
    private static int lastSeq;
    private static String sessionId;
    private static long heartbeatInterval = 41250;
    private static final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public static void start(String appId, String clientSecret) {
        QQBotHandler.appId = appId;
        QQBotHandler.clientSecret = clientSecret;
        getAccessToken();
        connect();
    }

    private static void getAccessToken() {
        JSONObject body = new JSONObject();
        body.put("appId", appId);
        body.put("clientSecret", clientSecret);
        HttpResponse<String> response = Unirest.post(TOKEN_URL)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .requestTimeout(10000)
                .asString();
        if (response.isSuccess()) {
            JSONObject json = JSONObject.parseObject(response.getBody());
            accessToken = json.getString("access_token");
            tokenExpireTime = System.currentTimeMillis() + json.getLongValue("expires_in", 7200) * 1000L;
            Main.getLogger().info("QQ Bot AccessToken 已获取");
        } else {
            Main.getLogger().error("QQ Bot 获取AccessToken失败: {}", response.getBody());
        }
    }

    private static void refreshTokenIfNeeded() {
        if (System.currentTimeMillis() > tokenExpireTime - 60000) {
            getAccessToken();
        }
    }

    private static void connect() {
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new WebSocketListener())
                .thenAccept(ws -> {
                    webSocket = ws;
                    Main.getLogger().info("QQ Bot WebSocket 已连接");
                })
                .exceptionally(e -> {
                    Main.getLogger().error("QQ Bot WebSocket 连接失败: {}", e.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    private static void scheduleReconnect() {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            reconnecting.set(false);
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    private static class WebSocketListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            ws.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                try {
                    handleMessage(msg);
                } catch (Exception e) {
                    Main.getLogger().error("QQ Bot 处理消息错误: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            Main.getLogger().warn("QQ Bot WebSocket 断开: {} {}", code, reason);
            if (!reconnecting.getAndSet(true)) {
                reconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            Main.getLogger().error("QQ Bot WebSocket 错误: {}", error.getMessage());
        }
    }

    private static void handleMessage(String msg) {
        JSONObject payload = JSONObject.parseObject(msg);
        int op = payload.getIntValue("op", 0);
        String t = payload.getString("t");
        JSONObject d = payload.getJSONObject("d");
        int s = payload.getIntValue("s", 0);
        if (s > 0) lastSeq = s;

        switch (op) {
            case 10: // Hello
                heartbeatInterval = d.getLongValue("heartbeat_interval", 41250);
                identify();
                break;
            case 11: // Heartbeat ACK
                break;
            case 0: // Dispatch
                handleDispatch(t, d);
                break;
            case 7: // Reconnect
                reconnect();
                break;
            case 9: // Invalid Session
                sessionId = null;
                reconnect();
                break;
        }
    }

    private static void identify() {
        JSONObject d = new JSONObject();
        d.put("token", "QQBot " + accessToken);
        d.put("intents", 1 << 25); // GROUP_AT_MESSAGE_CREATE
        d.put("shard", new int[]{0, 1});
        send(2, d);
    }

    private static void resumeSession() {
        JSONObject d = new JSONObject();
        d.put("token", "QQBot " + accessToken);
        d.put("session_id", sessionId);
        d.put("seq", lastSeq);
        send(6, d);
    }

    private static void startHeartbeat() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            JSONObject d = new JSONObject();
            if (lastSeq > 0) d.put("seq", lastSeq);
            send(1, d);
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private static void handleDispatch(String type, JSONObject d) {
        if (d == null) return;
        switch (type) {
            case "READY":
                sessionId = d.getString("session_id");
                startHeartbeat();
                Main.getLogger().info("QQ Bot 已就绪: {}",
                        d.getJSONObject("user") != null
                                ? d.getJSONObject("user").getString("username")
                                : "未知");
                break;
            case "RESUMED":
                startHeartbeat();
                Main.getLogger().info("QQ Bot 会话已恢复");
                break;
            case "GROUP_AT_MESSAGE_CREATE":
                handleGroupAtMessage(d);
                break;
        }
    }

    private static void handleGroupAtMessage(JSONObject d) {
        String content = d.getString("content");
        if (content == null) return;
        content = content.trim();
        String groupOpenid = d.getString("group_openid");
        String msgId = d.getString("id");

        Main.getLogger().info("QQ群@消息 [{}]: {}", groupOpenid, content);

        if (content.contains("在线人数") || content.contains("在线玩家")) {
            sendOnlineCount(groupOpenid, msgId);
        } else if (content.contains("线路状态") || content.contains("服务器状态")) {
            sendServerStatus(groupOpenid, msgId);
        }
    }

    private static void sendOnlineCount(String groupOpenid, String msgId) {
        JSONObject onlineData = MinecraftUtil.getOnlineData();
        if (onlineData == null || onlineData.isEmpty()) {
            sendGroupMessage(groupOpenid, msgId, "暂无在线人数数据");
            return;
        }
        int online = onlineData.getIntValue("online", 0);
        int max = onlineData.getIntValue("max", 0);
        String response = String.format("当前在线人数: %d/%d", online, max);
        sendGroupMessage(groupOpenid, msgId, response);
    }

    private static void sendServerStatus(String groupOpenid, String msgId) {
        Map<String, Boolean> statusMap = MinecraftUtil.getServerStatusMap();
        Map<String, Long> latencyMap = MinecraftUtil.getServerLatencyMap();

        if (statusMap.isEmpty()) {
            sendGroupMessage(groupOpenid, msgId, "暂无服务器线路数据");
            return;
        }

        StringBuilder sb = new StringBuilder("服务器线路状态:\n");
        int onlineCount = 0;
        int totalCount = 0;
        for (String host : statusMap.keySet()) {
            totalCount++;
            Boolean online = statusMap.get(host);
            Long latency = latencyMap.get(host);
            String status = Boolean.TRUE.equals(online) ? "在线" : "离线";
            String latencyStr = Boolean.TRUE.equals(online) && latency != null ? latency + "ms" : "-";
            if (Boolean.TRUE.equals(online)) onlineCount++;
            sb.append(String.format("%s: %s (%s)\n", host.replace(".ranmc.cc", ""), status, latencyStr));
        }
        sb.append(String.format("总计: %d/%d 在线", onlineCount, totalCount));
        sendGroupMessage(groupOpenid, msgId, sb.toString());
    }

    private static void sendGroupMessage(String groupOpenid, String msgId, String content) {
        refreshTokenIfNeeded();
        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("msg_type", 0);
        body.put("msg_id", msgId); // 被动回复消息

        Unirest.post(API_URL + "/v2/groups/" + groupOpenid + "/messages")
                .header("Authorization", "QQBot " + accessToken)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .requestTimeout(10000)
                .asStringAsync()
                .thenAccept(response -> {
                    if (response.getStatus() < 200 || response.getStatus() >= 300) {
                        Main.getLogger().warn("QQ群消息发送失败: {}", response.getBody());
                    }
                });
    }

    private static void send(int op, JSONObject d) {
        if (webSocket != null) {
            JSONObject payload = new JSONObject();
            payload.put("op", op);
            payload.put("d", d);
            webSocket.sendText(payload.toString(), true);
        }
    }

    private static void reconnect() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        sessionId = null;
        if (webSocket != null) {
            try {
                webSocket.sendClose(1000, "reconnect");
            } catch (Exception ignored) {
            }
        }
        refreshTokenIfNeeded();
        scheduleReconnect();
    }
}
