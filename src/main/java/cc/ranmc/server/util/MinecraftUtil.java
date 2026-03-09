package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import cc.ranmc.server.minecraft.MinecraftPing;
import cc.ranmc.server.minecraft.MinecraftPingOptions;
import com.alibaba.fastjson2.JSONObject;
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
    private static JSONObject onlineData = new JSONObject();
    private static int offset = 0;

    public static void updateServerStatus() {
        HttpUtil.post("https://dnsapi.cn/Record.List",
                "login_token=" + ConfigUtil.CONFIG.getString("dnspod") + "&domain=ranmc.cc&format=json&length=3000",
                body -> {
                    if (!body.startsWith("{")) {
                        Main.getLogger().warn("获取记录列表失败");
                        return;
                    }
                    final boolean[] updateOnlineData = {false};
                    serverSrvMap.clear();
                    final JSONObject[] severData = new JSONObject[1];
                    Map<String,Boolean> newServerStatusMap = new TreeMap<>();
                    Map<String,Long> newServerLatencyMap = new TreeMap<>();
                    JSONObject.parseObject(body).getJSONArray("records").forEach(record -> {
                        JSONObject json = JSONObject.parseObject(record.toString());
                        String name = json.getString("name");
                        String srv = json.getString("value");
                        if (name.startsWith("_minecraft._tcp.b")
                                && !name.contains("test")
                                && !name.contains("city")) {
                            String serverName = name.replace("_minecraft._tcp.", "") + ".ranmc.cc";
                            JSONObject obj = getServerData(srv);
                            if (obj != null) severData[0] = obj;
                            boolean online = obj != null;
                            newServerLatencyMap.put(serverName, online ? obj.getLongValue("latency", 0L) : 0);
                            newServerStatusMap.put(serverName, online);
                            serverSrvMap.put(serverName, srv);
                            if (online && !updateOnlineData[0]) {
                                // 更新服务器在线信息
                                updateOnlineData[0] = true;
                                onlineData = new JSONObject();
                                String[] version = severData[0].getJSONObject("version")
                                        .getString("name").split(" ");
                                onlineData.put("version", version[version.length - 1]);
                                onlineData.put("online", severData[0].getJSONObject("players")
                                        .getIntValue("online", 0));
                                onlineData.put("max", severData[0].getJSONObject("players")
                                        .getIntValue("max", 0));
                            }
                        } else if (name.equals("_minecraft._tcp")) {
                            recordId = json.getLong("id");
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
                "login_token=" + ConfigUtil.CONFIG.getString("dnspod") +
                        "&domain=ranmc.cc&sub_domain=_minecraft._tcp&record_type=SRV&record_line_id=0&value=" + value + "&record_id=" + recordId,
                body -> {
                    if (!body.startsWith("{") ||
                            !unicode(JSONObject.parseObject(body).getJSONObject("status").getString("message")).contains("成功")) {
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

    private static JSONObject getServerData(String srvValue) {
        String[] srvValueSplit = srvValue.split(" ");
        return getServerData(srvValueSplit[3], Integer.parseInt(srvValueSplit[2]));
    }

    private static JSONObject getServerData(String address, int port) {
         try {
            return MinecraftPing.getPing(new MinecraftPingOptions()
                    .setHostname(address)
                    .setPort(port));
        } catch (Exception ignored) {}
        return null;
    }
}
