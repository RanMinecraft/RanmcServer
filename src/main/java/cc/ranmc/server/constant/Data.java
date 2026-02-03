package cc.ranmc.server.constant;

import static cc.ranmc.server.util.ConfigUtil.CONFIG;

public class Data {
    public static final String AUTHOR = "Ranica";
    public static final String VERSION = "Release 7.4";
    public static final String WEB_SITE = "https://www.ranmc.cc/";
    public static final String VERIFY_WEB_SITE = "https://www.ranmc.cc/verify.html?key=";
    public static final int PORT = 2263;
    public static final String BASE_PATH = "/";
    public static final String VERIFY_PATH = "/verify";
    public static final String BROADCAST_PATH = "/broadcast";
    public static final String CHART_PATH = "/chart";
    public static final String BANLIST_PATH = "/banlist";
    public static final String ADDRESS_PATH = "/address";
    public static final String TOKEN = CONFIG.getString("token");
    public static final String EMAIL_PWD = CONFIG.getString("email");
}
