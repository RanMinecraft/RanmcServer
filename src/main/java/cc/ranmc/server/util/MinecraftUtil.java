package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import cc.ranmc.server.minecraft.MinecraftPing;
import cc.ranmc.server.minecraft.MinecraftPingOptions;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static cc.ranmc.server.network.BroadcastHandler.broadcast;

public class MinecraftUtil {

    @Getter
    private static Map<String,Boolean> serverStatusMap = new TreeMap<>();
    @Getter
    private static Map<String,Long> serverDelayMap = new TreeMap<>();
    private static final Map<String,String> serverSrvMap = new TreeMap<>();
    private static long recordId = 0;
    @Getter
    private static long lastCheckTime = 0;
    private static boolean lastCheckStatus = true;
    @Getter
    private static JSONObject onlineData = new JSONObject();

    public static void updateServerStatus() {
        HttpUtil.post("https://dnsapi.cn/Record.List",
                "login_token=" + ConfigUtil.CONFIG.getString("dnspod") + "&domain=ranmc.cc&format=json&length=3000",
                body -> {
                    if (!body.startsWith("{")) {
                        Main.getLogger().warn("获取记录列表失败");
                        return;
                    }
                    serverSrvMap.clear();
                    final JSONObject[] severData = new JSONObject[1];
                    Map<String,Boolean> newServerStatusMap = new TreeMap<>();
                    Map<String,Long> newServerDelayMap = new TreeMap<>();
                    JSONObject.parseObject(body).getJSONArray("records").forEach(record -> {
                        JSONObject json = JSONObject.parseObject(record.toString());
                        String name = json.getString("name");
                        String srv = json.getString("value");
                        if (name.startsWith("_minecraft._tcp.")
                                && !name.contains("test")
                                && !name.contains("city")) {
                            String serverName = name.replace("_minecraft._tcp.", "") + ".ranmc.cc";
                            JSONObject obj = getServerData(srv);
                            if (obj != null) severData[0] = obj;
                            newServerDelayMap.put(serverName, obj == null ? 0 : obj.getLongValue("delay", 0L));
                            newServerStatusMap.put(serverName, severData[0] != null);
                            serverSrvMap.put(serverName, srv);
                        } else if (name.equals("_minecraft._tcp")) {
                            serverSrvMap.put("ranmc.cc", srv);
                            recordId = json.getLong("id");
                        }
                    });

                    String mainSrv = ConfigUtil.CONFIG.getString("srv");
                    JSONObject obj = getServerData(mainSrv);
                    newServerDelayMap.put("ranmc.cc", obj == null ? 0 : obj.getLongValue("delay", 0L));
                    boolean mainServerOnline = obj != null;
                    if (obj != null) severData[0] = obj;
                    // 更新服务器在线信息
                    onlineData = new JSONObject();
                    if (severData[0] != null) {
                        String[] version = severData[0].getJSONObject("version")
                                .getString("name").split(" ");
                        onlineData.put("version", version[version.length - 1]);
                        onlineData.put("online", severData[0].getJSONObject("players")
                                .getIntValue("online", 0));
                        onlineData.put("max", severData[0].getJSONObject("players")
                                .getIntValue("max", 0));
                    }

                    newServerStatusMap.put("ranmc.cc", mainServerOnline);
                    mainSrv += ".";
                    if (mainServerOnline && !serverSrvMap.get("ranmc.cc").equals(mainSrv)) {
                        modifyRecord(mainSrv);
                        broadcast("主线已恢复,更新解析记录 " + mainSrv);
                    }

                    if (!mainServerOnline && serverSrvMap.get("ranmc.cc").equals(mainSrv) && !lastCheckStatus) {
                        String backupSrv = "";
                        for (String key : newServerStatusMap.keySet()) {
                            if (newServerStatusMap.get(key)) {
                                backupSrv = serverSrvMap.get(key);
                            }
                        }
                        String backupServerInfo = "无备用线路可用";
                        if (!backupSrv.isEmpty()) {
                            backupServerInfo = "切换到备用线路 " + backupSrv;
                            modifyRecord(backupSrv);
                        }
                        broadcast("检测到主线路离线," + backupServerInfo);
                    }

                    lastCheckStatus = mainServerOnline;
                    lastCheckTime = System.currentTimeMillis();
                    serverStatusMap = newServerStatusMap;
                    serverDelayMap = newServerDelayMap;
                });
    }

    private static void modifyRecord(String value) {
        HttpUtil.post("https://dnsapi.cn/Record.Modify",
                "login_token=" + ConfigUtil.CONFIG.getString("dnspod") +
                        "&domain=ranmc.cc&sub_domain=_minecraft._tcp&record_type=SRV&record_line_id=0&value=" + value + "&record_id=" + recordId,
                body -> {
                    if (!body.startsWith("{")) {
                        Main.getLogger().warn("修改记录列表失败");
                        return;
                    }
                    Main.getLogger().warn("修改主线记录 {} 结果{}", value,
                            unicode(JSONObject.parseObject(body).getJSONObject("status").getString("message")));
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
        JSONObject json = null;
        long startTime = 0, endTime = 0;
        try {
            MinecraftPingOptions options = new MinecraftPingOptions()
                    .setHostname(address)
                    .setPort(port);
            startTime = System.currentTimeMillis();
            String result = MinecraftPing.getPing(options);
            endTime = System.currentTimeMillis();
            json = JSONObject.parseObject(result);
        } catch (Exception ignored) {}
        if (json != null) json.put("delay", (endTime - startTime));
        return json;
    }
}
