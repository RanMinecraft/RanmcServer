package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import com.google.gson.JsonObject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class ConfigUtil {

    public static JsonObject CONFIG = new JsonObject();
    public static void load() {
        try {
            File file = new File(System.getProperty("user.dir") + "/config.json");
            CONFIG = JsonUtil.parse(FileUtils.fileRead(file, "utf8"));
        } catch (IOException e) {
            Main.getLogger().error(e.getMessage());
        }
    }

    public static String getString(String key) {
        return JsonUtil.getString(CONFIG, key);
    }
}
