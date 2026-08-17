package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import cc.ranmc.server.minecraft.MinecraftPing;
import cc.ranmc.server.minecraft.MinecraftPingOptions;
import com.google.gson.JsonObject;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static cc.ranmc.server.network.BroadcastHandler.broadcast;
public class MinecraftUtil {

    @Getter
    private static Map<String,Boolean> serverStatusMap = new TreeMap<>();
    @Getter
    private static Map<String,Long> serverLatencyMap = new TreeMap<>();
    private static final Map<String,String> serverSrvMap = new TreeMap<>();
    private static long recordId = 0;
    @Getter
    private static long lastCheckTime = 0;
    @Getter
    private static JsonObject onlineData = new JsonObject();
    private static int offset = 0;

    public static void updateServerStatus() {
        HttpUtil.post("https://dnsapi.cn/Record.List",
                "login_token=" + ConfigUtil.getString("dnspod") + "&domain=ranmc.cc&format=json&length=3000",
                body -> {
                    if (!body.startsWith("{")) {
                        Main.getLogger().warn("获取记录列表失败");
                        return;
                    }
                    final boolean[] updateOnlineData = {false};
                    serverSrvMap.clear();
                    final JsonObject[] severData = new JsonObject[1];
                    Map<String,Boolean> newServerStatusMap = new TreeMap<>();
                    Map<String,Long> newServerLatencyMap = new TreeMap<>();
                    JsonUtil.parse(body).getAsJsonArray("records").forEach(record -> {
                        JsonObject json = record.getAsJsonObject();
                        String name = JsonUtil.getString(json, "name");
                        String srv = JsonUtil.getString(json, "value");
                        String status = JsonUtil.getString(json, "status");
                        if (name.startsWith("_minecraft._tcp.b")
                                && !name.contains("test")
                                && !name.contains("city")) {
                            // 跳过被暂停的解析记录
                            if ("disable".equals(status)) return;
                            String serverName = name.replace("_minecraft._tcp.", "") + ".ranmc.cc";
                            JsonObject obj = getServerData(srv);
                            if (obj != null) severData[0] = obj;
                            boolean online = obj != null;
                            newServerLatencyMap.put(serverName, online ? JsonUtil.getLong(obj, "latency", 0L) : 0);
                            newServerStatusMap.put(serverName, online);
                            // 因为 樱花frp 无法被 mclist 解析
                            if (!serverName.equals("b6.ranmc.cc")) {
                                serverSrvMap.put(serverName, srv);
                            }
                            if (online && !updateOnlineData[0]) {
                                // 更新服务器在线信息
                                updateOnlineData[0] = true;
                                onlineData = new JsonObject();
                                String[] version = JsonUtil.getString(
                                        JsonUtil.getObject(severData[0], "version"), "name")
                                        .split(" ");
                                onlineData.addProperty("version", version[version.length - 1]);
                                JsonObject players = JsonUtil.getObject(severData[0], "players");
                                onlineData.addProperty("online", JsonUtil.getInt(players, "online", 0));
                                onlineData.addProperty("max", JsonUtil.getInt(players, "max", 0));
                            }
                        } else if (name.equals("_minecraft._tcp")) {
                            recordId = JsonUtil.getLong(json, "id");
                        }
                    });

                    // 切换线路
                    if (serverSrvMap.isEmpty()) {
                        broadcast("无可用线路");
                    } else {
                        offset++;
                        if (offset >= serverSrvMap.size()) offset = 0;
                        modifyRecord(new ArrayList<>(serverSrvMap.values()).get(offset));
                    }

                    lastCheckTime = System.currentTimeMillis();
                    serverStatusMap = newServerStatusMap;
                    serverLatencyMap = newServerLatencyMap;
                });
    }

    private static void modifyRecord(String value) {
        HttpUtil.post("https://dnsapi.cn/Record.Modify",
                "login_token=" + ConfigUtil.getString("dnspod") +
                        "&domain=ranmc.cc&sub_domain=_minecraft._tcp&record_type=SRV&record_line_id=0&value=" + value + "&record_id=" + recordId,
                body -> {
                    if (!body.startsWith("{") ||
                            !unicode(JsonUtil.getString(JsonUtil.getObject(JsonUtil.parse(body), "status"), "message")).contains("成功")) {
                        Main.getLogger().warn("修改记录列表失败 {}", value);
                    }
                });
    }

    private static String unicode(String unicode) {
        Properties p = new Properties();
        try {
            p.load(new java.io.StringReader("key=" + unicode));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return p.getProperty("key");
    }

    private static JsonObject getServerData(String srvValue) {
        String[] srvValueSplit = srvValue.split(" ");
        return getServerData(srvValueSplit[3], Integer.parseInt(srvValueSplit[2]));
    }

    private static JsonObject getServerData(String address, int port) {
         try {
            return MinecraftPing.getPing(new MinecraftPingOptions()
                    .setHostname(address)
                    .setPort(port));
        } catch (Exception ignored) {}
        return null;
    }
}
