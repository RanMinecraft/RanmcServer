package cc.ranmc.server.constant;

import cc.ranmc.sql.SQLBase;

import static cc.ranmc.server.util.ConfigUtil.getString;

public class Data {
    public static final String AUTHOR = "Ranica";
    public static final String WEB_SITE = "https://www.ranmc.cc/";
    public static final String VERIFY_WEB_SITE = "https://www.ranmc.cc/verify.html?key=";
    public static final int PORT = 2263;
    public static final String BASE_PATH = "/";
    public static final String ANY_PATH = "/*";
    public static final String VERIFY_PATH = "/verify";
    public static final String BROADCAST_PATH = "/broadcast";
    public static final String CHART_PATH = "/chart";
    public static final String BANLIST_PATH = "/banlist";
    public static final String PLUGIN_PATH = "/plugin";
    public static final String TOKEN = getString("token");
    public static final String EMAIL_PWD = getString("email");
    public static final String AI_BASE_URL = getString("ai_base_url");
    public static final String AI_API_KEY = getString("ai_api_key");
    public static final String AI_MODEL = getString("ai_model");
    public static final String FEISHU_WEBHOOK = getString("feishu_webhook");
    public static final String PLUGIN_KEY = getString("plugin_key");
    public static final SQLBase DATA_SQL = new SQLBase(getString("data_sql"));
    public static final SQLBase LOG_SQL = new SQLBase(getString("log_sql"));
}
