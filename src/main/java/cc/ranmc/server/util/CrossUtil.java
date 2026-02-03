package cc.ranmc.server.util;

import io.javalin.http.Context;

public class CrossUtil {

    public static void allow(Context context) {
        // 允许跨域
        context.header("Access-Control-Allow-Origin", "*");
        context.header("Access-Control-Allow-Methods", "*");
        context.header("Access-Control-Allow-Headers", "*");
        context.header("Access-Control-Max-Age", "0");
        context.header("Access-Control-Allow-Credentials", "true");
    }

}
