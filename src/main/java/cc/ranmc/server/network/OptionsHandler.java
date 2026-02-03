package cc.ranmc.server.network;

import cc.ranmc.server.util.CrossUtil;
import io.javalin.http.Context;

public class OptionsHandler {

    public static void handle(Context context) {
        CrossUtil.allow(context);
        context.status(200);
    }
}

