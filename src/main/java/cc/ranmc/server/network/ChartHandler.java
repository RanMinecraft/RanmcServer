package cc.ranmc.server.network;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Prams;
import cc.ranmc.server.util.CrossUtil;
import cc.ranmc.server.util.JsonUtil;
import cc.ranmc.server.util.MinecraftUtil;
import cc.ranmc.sql.SQLFilter;
import cc.ranmc.sql.SQLRow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cc.ranmc.server.constant.Data.DATA_SQL;
import static cc.ranmc.server.constant.Data.LOG_SQL;
import static cc.ranmc.server.util.ConfigUtil.getString;

public class ChartHandler {
    // TPS 图表固定为最近 24 小时，每 30 分钟一个点，共 48 个点
    private static final int TPS_SLOT_MINUTES = 30;
    private static final int TPS_POINT_COUNT = 48;
    private static final DateTimeFormatter TPS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TPS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static long seasonLastUpdate = 0;
    private static volatile Map<String, Integer> seasonRows = new LinkedHashMap<>();
    private static long tpsLastUpdate = 0;
    private static volatile JsonArray tpsRows = new JsonArray();
    private static long pvpLastUpdate = 0;
    private static volatile Map<String, Integer> pvpRows = new LinkedHashMap<>();

    public static void handle(Context context) {
        // 允许跨域
        CrossUtil.allow(context);
        context.contentType(ContentType.APPLICATION_JSON);

        JsonObject json = new JsonObject();
        // 检查请求
        if (HandlerType.GET != context.method() || !context.queryParamMap().containsKey(Prams.TYPE)) {
            json.addProperty(Prams.CODE, Code.UNKNOWN_REQUEST);
            context.status(Code.UNKNOWN_REQUEST);
            context.result(json.toString());
            return;
        }
        String type = context.queryParam(Prams.TYPE);
        if ("pvp".equalsIgnoreCase(type)) {
            updatePvpData();
            json.addProperty(Prams.CODE, Code.SUCCESS);
            json.add(Prams.DATA, JsonUtil.toTree(pvpRows));
        } else if ("tps".equalsIgnoreCase(type)) {
            updateTpsData();
            json.addProperty(Prams.CODE, Code.SUCCESS);
            json.add(Prams.DATA, tpsRows);
        } else if ("season".equalsIgnoreCase(type)) {
            updateSeasonData();
            json.addProperty(Prams.CODE, Code.SUCCESS);
            json.add(Prams.DATA, JsonUtil.toTree(seasonRows));
        } else if ("status".equalsIgnoreCase(type)) {
            updateSeasonData();
            json.addProperty(Prams.CODE, Code.SUCCESS);
            JsonArray data = new JsonArray();
            for (String key : MinecraftUtil.getServerStatusMap().keySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("host", key);
                obj.addProperty("status", MinecraftUtil.getServerStatusMap().get(key));
                obj.addProperty("latency", MinecraftUtil.getServerLatencyMap().get(key));
                data.add(obj);
            }
            json.add(Prams.DATA, data);
            json.addProperty(Prams.TIME, MinecraftUtil.getLastCheckTime());
        } else if ("online".equalsIgnoreCase(type)) {
            json.addProperty(Prams.CODE, Code.SUCCESS);
            json.add(Prams.DATA, MinecraftUtil.getOnlineData());
        } else {
            json.addProperty(Prams.CODE, Code.UNKNOWN_REQUEST);
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
        File file = new File(getString("season"));
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
        Map<String, Integer> tempRows = new LinkedHashMap<>();
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
            Integer count = (int) data.get(key);
            tempRows.put(name, tempRows.getOrDefault(name, 0) + count);
        }
        seasonRows = tempRows;
    }

    public static void updateTpsData() {
        long now = System.currentTimeMillis();
        if (tpsLastUpdate + (5 * 60 * 1000) > now) return;
        tpsLastUpdate = now;

        // 以当前时刻向下取整到 20 分钟，作为最后一个时间槽
        LocalDateTime nowTime = LocalDateTime.now();
        LocalDateTime lastSlot = nowTime
                .withMinute(nowTime.getMinute() / TPS_SLOT_MINUTES * TPS_SLOT_MINUTES)
                .withSecond(0)
                .withNano(0);
        LocalDateTime firstSlot = lastSlot.minusMinutes((long) TPS_SLOT_MINUTES * (TPS_POINT_COUNT - 1));
        LocalDateTime windowEnd = lastSlot.plusMinutes(TPS_SLOT_MINUTES);

        // 多发一些原始记录，避免停机导致的记录缺失
        List<SQLRow> tpsList = LOG_SQL.selectList(SQLKey.TPS.toUpperCase(),
                new SQLFilter()
                        .order("CAST(ID AS INT) DESC")
                        .limit(TPS_POINT_COUNT * 3));

        // 把落在同一时间槽内的记录累加，便于取平均
        Map<LocalDateTime, double[]> slotSum = new HashMap<>();
        Map<LocalDateTime, int[]> slotCount = new HashMap<>();
        for (SQLRow row : tpsList) {
            LocalDateTime recordTime = parseRecordTime(row);
            if (recordTime == null) continue;
            if (recordTime.isBefore(firstSlot) || !recordTime.isBefore(windowEnd)) continue;
            LocalDateTime slot = recordTime
                    .withMinute(recordTime.getMinute() / TPS_SLOT_MINUTES * TPS_SLOT_MINUTES)
                    .withSecond(0)
                    .withNano(0);
            double[] sum = slotSum.computeIfAbsent(slot, k -> new double[2]);
            sum[0] += row.getInt(SQLKey.PLAYER, 0);
            sum[1] += row.getDouble(SQLKey.TPS, 20d);
            slotCount.computeIfAbsent(slot, k -> new int[1])[0]++;
        }

        // 按固定时间刻度输出，保持与旧接口一致的时间顺序（新 -> 旧）
        JsonArray tempRows = new JsonArray();
        for (int i = 0; i < TPS_POINT_COUNT; i++) {
            LocalDateTime slot = lastSlot.minusMinutes((long) TPS_SLOT_MINUTES * i);
            double[] sum = slotSum.get(slot);
            int count = sum == null ? 0 : slotCount.get(slot)[0];
            // 当前时间所在的时间槽若还没有记录，则不返回最后一条
            if (i == 0 && count == 0) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty(SQLKey.DATE.toLowerCase(), slot.format(TPS_DATE_FORMAT));
            obj.addProperty(SQLKey.TIME.toLowerCase(), slot.format(TPS_TIME_FORMAT));
            if (count > 0) {
                obj.addProperty(SQLKey.PLAYER.toLowerCase(), (int) Math.round(sum[0] / count));
                obj.addProperty(SQLKey.TPS.toLowerCase(), sum[1] / count);
            } else {
                obj.addProperty(SQLKey.PLAYER.toLowerCase(), 0);
                obj.addProperty(SQLKey.TPS.toLowerCase(), 0);
            }
            tempRows.add(obj);
        }
        tpsRows = tempRows;
    }

    private static LocalDateTime parseRecordTime(SQLRow row) {
        try {
            LocalDate date = LocalDate.parse(row.getString(SQLKey.DATE));
            LocalTime time = LocalTime.parse(row.getString(SQLKey.TIME));
            return LocalDateTime.of(date, time);
        } catch (Exception e) {
            return null;
        }
    }

    public static void updatePvpData() {
        long now = System.currentTimeMillis();
        if (pvpLastUpdate + (6 * 60 * 60 * 1000) > now) return;
        pvpLastUpdate = now;
        List<SQLRow> backupList = DATA_SQL.selectList(SQLKey.FIGHT, new SQLFilter());
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
        Map<String, Integer> tempRows = new LinkedHashMap<>();
        tempRows.put("巅峰", chapion);
        tempRows.put("翡翠", emerable);
        tempRows.put("钻石Ⅰ", countMap.getOrDefault("钻石Ⅰ", 0));
        tempRows.put("钻石Ⅱ", countMap.getOrDefault("钻石Ⅱ", 0));
        tempRows.put("钻石Ⅲ", countMap.getOrDefault("钻石Ⅲ", 0));
        tempRows.put("黄金Ⅰ", countMap.getOrDefault("黄金Ⅰ", 0));
        tempRows.put("黄金Ⅱ", countMap.getOrDefault("黄金Ⅱ", 0));
        tempRows.put("黄金Ⅲ", countMap.getOrDefault("黄金Ⅲ", 0));
        tempRows.put("铁锭Ⅰ", countMap.getOrDefault("铁锭Ⅰ", 0));
        tempRows.put("铁锭Ⅱ", countMap.getOrDefault("铁锭Ⅱ", 0));
        tempRows.put("铁锭Ⅲ", countMap.getOrDefault("铁锭Ⅲ", 0));
        tempRows.put("粗铜Ⅰ", countMap.getOrDefault("粗铜Ⅰ", 0));
        tempRows.put("粗铜Ⅱ", countMap.getOrDefault("粗铜Ⅱ", 0));
        tempRows.put("粗铜Ⅲ", countMap.getOrDefault("粗铜Ⅲ", 0));
        tempRows.put("粗铜", countMap.getOrDefault("粗铜", 0));
        //tempRows.put("未定级", countMap.getOrDefault("未定级", 0));
        pvpRows = tempRows;
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
