package cc.ranmc.server.network;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Prams;
import cc.ranmc.server.util.CrossUtil;
import cc.ranmc.sql.SQLBase;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static cc.ranmc.server.util.ConfigUtil.CONFIG;

public class BanlistHandler {

    private static final SQLBase data = new SQLBase(CONFIG.getString("sqlite"));
    private static long lastUpdate = 0;
    private static List<JSONObject> banlist;

    public static void handle(Context context) {
        CrossUtil.allow(context);

        context.contentType(ContentType.APPLICATION_JSON);
        Main.getLogger().info("{}请求封禁列表",
                context.header("X-Forwarded-For"));

        JSONObject json = new JSONObject();

        // 检查请求
        if (!context.queryParamMap().containsKey(Prams.PAGE)) {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
            context.status(Code.UNKNOWN_REQUEST);
            context.result(json.toString());
            return;
        }
        // 获取页数
        int page = 1;
        try {
            page = Integer.parseInt(Objects.requireNonNull(context.queryParam(Prams.PAGE)));
            if (page < 1) page = 1;
        } catch (Exception ignore) {}
        updateBanlist();
        List<JSONObject> list = new ArrayList<>();
        // 过滤玩家名字
        if (context.queryParamMap().containsKey(Prams.PLAYER)) {
            banlist.forEach(obj -> {
                String searchName = context.queryParam(Prams.PLAYER);
                if (searchName != null &&
                        !searchName.isEmpty() &&
                        obj.getString(SQLKey.PLAYER).toLowerCase().
                                contains(searchName.toLowerCase())) {
                    list.add(obj);
                }
            });
        } else {
            list.addAll(banlist);
        }
        // 是否倒叙
        if (context.queryParamMap().containsKey(Prams.SORT_ORDER) &&
                "desc".equalsIgnoreCase(context.queryParam(Prams.SORT_ORDER))) {
            Collections.reverse(list);
        }
        // 获取单页显示数量
        int limit = 30;
        if (context.queryParamMap().containsKey(Prams.LIMIT)) {
            try {
                limit = Integer.parseInt(Objects.requireNonNull(context.queryParam(Prams.LIMIT)));
            } catch (Exception ignore) {}
        }
        if (limit > 50) limit = 50;
        // 返回结果
        context.status(200);
        json.put(Prams.CODE, Code.SUCCESS);
        json.put(Prams.TOTAL, list.size());
        json.put(Prams.TOTAL_NOT_FILTERED, banlist.size());
        JSONArray array = new JSONArray();
        page = (page - 1) * limit;
        for (int i = page; i < page + limit; i++) {
            if (i >= list.size()) break;
            array.add(list.get(i));
        }
        json.put(Prams.DATA, array);
        context.result(json.toString());
    }

    /**
     * 更新列表
     */
    private static void updateBanlist() {
        long now = System.currentTimeMillis();
        if (lastUpdate + (10 * 60 * 1000) > now) return;
        lastUpdate = now;
        banlist = new ArrayList<>();
        AtomicInteger id = new AtomicInteger();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        data.selectList(SQLKey.BANLIST).forEach(row -> {
            id.getAndIncrement();
            JSONObject json = new JSONObject();
            json.put("player", row.getString(SQLKey.PLAYER));
            json.put("reason", row.getString(SQLKey.REASON));
            json.put("banTime", row.getString(SQLKey.DATE));
            json.put("releaseTime", format.format(row.getLong(SQLKey.TIME)));
            json.put("operator", row.getString(SQLKey.ADMIN));
            json.put("id", id.get());
            banlist.add(json);
        });
    }

}
