package cc.ranmc.server.network;

import cc.ranmc.server.Main;
import cc.ranmc.server.util.CrossUtil;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

public class BaseHandler {
    public static void handle(Context context) {
        CrossUtil.allow(context);
        context.contentType(ContentType.TEXT_HTML);
        context.result("<html><head><meta http-equiv=\"refresh\" content=\"0;url=https://www.ranmc.cc\"></head><body></body></html>");
        Main.getLogger().info("{}跳转官网", context.header("X-Forwarded-For"));
    }
}
