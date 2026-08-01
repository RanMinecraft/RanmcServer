package cc.ranmc.server.network;

import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Data;
import cc.ranmc.server.constant.Prams;
import cc.ranmc.server.util.CrossUtil;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

public class PluginHandler {

    public static void handle(Context context) {
        CrossUtil.allow(context);
        context.contentType(ContentType.APPLICATION_JSON);

        JSONObject json = new JSONObject();
        if (!context.queryParamMap().containsKey(Prams.KEY) ||
                !Data.PLUGIN_KEY.equals(context.queryParam(Prams.KEY))) {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
            json.put(Prams.MSG, "未验证");
            context.status(Code.UNKNOWN_REQUEST);
            context.result(json.toString());
            return;
        }
        json.put(Prams.CODE, Code.SUCCESS);
        json.put(Prams.MSG, "成功");
        context.result(json.toString());
    }
}
