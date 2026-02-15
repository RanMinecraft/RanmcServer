package cc.ranmc.server.network;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Prams;
import cc.ranmc.server.util.CrossUtil;
import cc.ranmc.server.util.MinecraftUtil;
import cc.ranmc.sql.SQLBase;
import cc.ranmc.sql.SQLFilter;
import cc.ranmc.sql.SQLRow;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cc.ranmc.server.util.ConfigUtil.CONFIG;

public class ChartHandler {
    private static final SQLBase pvpData = new SQLBase(CONFIG.getString("sqlite"));
    private static final SQLBase tpsData = new SQLBase(CONFIG.getString("tps"));
    private static long seasonLastUpdate = 0;
    private static final Map<String, Integer> seasonRows = new LinkedHashMap<>();
    private static long tpsLastUpdate = 0;
    private static final JSONArray tpsRows = new JSONArray();
    private static long pvpLastUpdate = 0;
    private static final Map<String, Integer> pvpRows = new LinkedHashMap<>();

    public static void handle(Context context) {
        // 允许跨域
        CrossUtil.allow(context);
        context.contentType(ContentType.APPLICATION_JSON);

        JSONObject json = new JSONObject();
        // 检查请求
        if (HandlerType.GET != context.method() || !context.queryParamMap().containsKey(Prams.TYPE)) {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
            context.status(Code.UNKNOWN_REQUEST);
            context.result(json.toString());
            return;
        }
        String type = context.queryParam(Prams.TYPE);
        if ("pvp".equalsIgnoreCase(type)) {
            updatePvpData();
            json.put(Prams.CODE, Code.SUCCESS);
            json.put(Prams.DATA, pvpRows);
        } else if ("tps".equalsIgnoreCase(type)) {
            updateTpsData();
            json.put(Prams.CODE, Code.SUCCESS);
            json.put(Prams.DATA, tpsRows);
        } else if ("season".equalsIgnoreCase(type)) {
            updateSeasonData();
            json.put(Prams.CODE, Code.SUCCESS);
            json.put(Prams.DATA, seasonRows);
        } else if ("status".equalsIgnoreCase(type)) {
            updateSeasonData();
            json.put(Prams.CODE, Code.SUCCESS);
            JSONArray data = new JSONArray();
            for (String key : MinecraftUtil.getServerStatusMap().keySet()) {
                JSONObject obj = new JSONObject();
                obj.put("host", key);
                obj.put("status", MinecraftUtil.getServerStatusMap().get(key));
                obj.put("latency", MinecraftUtil.getServerLatencyMap().get(key));
                data.add(obj);
            }
            json.put(Prams.DATA, data);
            json.put(Prams.TIME, MinecraftUtil.getLastCheckTime());
        } else if ("online".equalsIgnoreCase(type)) {
            json.put(Prams.CODE, Code.SUCCESS);
            json.put(Prams.DATA, MinecraftUtil.getOnlineData());
        } else {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
        }

        if (context.header("X-Forwarded-For") != null) {
            Main.getLogger().info("{}请求{}数据",
                    context.header("X-Forwarded-For"), type);
        }
        context.result(json.toString());
    }

    private static void updateSeasonData() {
        long now = System.currentTimeMillis();
        if (seasonLastUpdate + (60 * 60 * 1000) > now) return;
        seasonLastUpdate = now;
        File file = new File(CONFIG.getString("season"));
        if (!file.exists()) {
            Main.getLogger().error("找不到 season.yml");
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.trim().isEmpty()
                        || line.startsWith("title")
                        || line.startsWith("#")
                        || line.startsWith(" ")) continue;
                if (line.contains(":")) {
                    String[] parts = line.split(":");
                    String name = parts[0].trim();
                    int value = Integer.parseInt(parts[1].trim());
                    data.put(name, value);
                }
            }
        } catch (IOException e) {
            Main.getLogger().error("无法读取 season.yml");
            return;
        }
        seasonRows.clear();
        List<String> order = new ArrayList<>();
        for (String key : data.keySet()) {
            String name = key.replace("三叉戟", "")
                    .replace("戟", "")
                    .replace("之盾", "")
                    .replace("盾", "")
                    .replace("头盔", "")
                    .replace("之甲", "")
                    .replace("护腿", "")
                    .replace("之靴", "")
                    .replace("之矛", "");
            if (!order.contains(name)) order.add(name);
            Integer count = (int) data.get(key);
            seasonRows.put(name, seasonRows.getOrDefault(name, 0) + count);
        }
    }

    public static void updateTpsData() {
        long now = System.currentTimeMillis();
        if (tpsLastUpdate + (30 * 60 * 1000) > now) return;
        tpsLastUpdate = now;
        List<SQLRow> tpsList = tpsData.selectList(SQLKey.TPS.toUpperCase(),
                new SQLFilter()
                        .order("CAST(ID AS INT) DESC")
                        .limit(68));
        tpsRows.clear();
        for (SQLRow row : tpsList) {
            JSONObject obj = new JSONObject();
            obj.put(SQLKey.DATE.toLowerCase(), row.getString(SQLKey.DATE));
            obj.put(SQLKey.TIME.toLowerCase(), row.getString(SQLKey.TIME));
            obj.put(SQLKey.PLAYER.toLowerCase(), row.getString(SQLKey.PLAYER));
            obj.put(SQLKey.TPS.toLowerCase(), row.getString(SQLKey.TPS));
            obj.put(SQLKey.MSPT.toLowerCase(), row.getString(SQLKey.MSPT));
            tpsRows.add(obj);
        }
    }

    public static void updatePvpData() {
        long now = System.currentTimeMillis();
        if (pvpLastUpdate + (6 * 60 * 60 * 1000) > now) return;
        pvpLastUpdate = now;
        List<SQLRow> backupList = pvpData.selectList(SQLKey.FIGHT, new SQLFilter());
        Map<String, Integer> countMap = new HashMap<>();
        for (SQLRow map : backupList) {
            int point = map.getInt(SQLKey.POINT, 0);
            String name = getLevel(
                    point,
                    map.getInt(SQLKey.SEASON_COUNT, 0));
            countMap.put(name, countMap.getOrDefault(name, 0) + 1);
        }
        int emerable = countMap.getOrDefault("翡翠", 0);
        int chapion = 0;
        if (emerable > 0) {
            emerable --;
            chapion = 1;
        }
        pvpRows.clear();
        pvpRows.put("巅峰", chapion);
        pvpRows.put("翡翠", emerable);
        pvpRows.put("钻石Ⅰ", countMap.getOrDefault("钻石Ⅰ", 0));
        pvpRows.put("钻石Ⅱ", countMap.getOrDefault("钻石Ⅱ", 0));
        pvpRows.put("钻石Ⅲ", countMap.getOrDefault("钻石Ⅲ", 0));
        pvpRows.put("黄金Ⅰ", countMap.getOrDefault("黄金Ⅰ", 0));
        pvpRows.put("黄金Ⅱ", countMap.getOrDefault("黄金Ⅱ", 0));
        pvpRows.put("黄金Ⅲ", countMap.getOrDefault("黄金Ⅲ", 0));
        pvpRows.put("铁锭Ⅰ", countMap.getOrDefault("铁锭Ⅰ", 0));
        pvpRows.put("铁锭Ⅱ", countMap.getOrDefault("铁锭Ⅱ", 0));
        pvpRows.put("铁锭Ⅲ", countMap.getOrDefault("铁锭Ⅲ", 0));
        pvpRows.put("粗铜Ⅰ", countMap.getOrDefault("粗铜Ⅰ", 0));
        pvpRows.put("粗铜Ⅱ", countMap.getOrDefault("粗铜Ⅱ", 0));
        pvpRows.put("粗铜Ⅲ", countMap.getOrDefault("粗铜Ⅲ", 0));
        pvpRows.put("粗铜", countMap.getOrDefault("粗铜", 0));
        //pvpRows.put("未定级", countMap.getOrDefault("未定级", 0));
    }

    private static String getLevel(int point, int count) {
        if (count < 3) return "未定级";
        String text = "粗铜";
        if (point >= 1000) text = "粗铜Ⅲ";
        if (point >= 1100) text = "粗铜Ⅱ";
        if (point >= 1200) text = "粗铜Ⅰ";
        if (point >= 1300) text = "铁锭Ⅲ";
        if (point >= 1400) text = "铁锭Ⅱ";
        if (point >= 1500) text = "铁锭Ⅰ";
        if (point >= 1600) text = "黄金Ⅲ";
        if (point >= 1700) text = "黄金Ⅱ";
        if (point >= 1800) text = "黄金Ⅰ";
        if (point >= 1900) text = "钻石Ⅲ";
        if (point >= 2000) text = "钻石Ⅱ";
        if (point >= 2100) text = "钻石Ⅰ";
        if (point >= 2200) text = "翡翠";
        return text;
    }
}

