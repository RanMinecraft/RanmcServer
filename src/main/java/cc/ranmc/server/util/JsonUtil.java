package cc.ranmc.server.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtil {

    private static final Gson GSON = new Gson();

    public static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public static JsonElement toTree(Object src) {
        return GSON.toJsonTree(src);
    }

    public static String getString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    public static int getInt(JsonObject obj, String key, int def) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsInt();
    }

    public static long getLong(JsonObject obj, String key, long def) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsLong();
    }

    public static long getLong(JsonObject obj, String key) {
        return getLong(obj, key, 0L);
    }

    public static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsDouble();
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || !e.isJsonObject() ? null : e.getAsJsonObject();
    }

    public static JsonArray getArray(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || !e.isJsonArray() ? null : e.getAsJsonArray();
    }
}
