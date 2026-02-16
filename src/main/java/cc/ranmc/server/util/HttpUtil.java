package cc.ranmc.server.util;

import com.alibaba.fastjson2.JSONObject;
import kong.unirest.core.Unirest;

import java.util.function.Consumer;

import static cc.ranmc.utils.BasicUtil.THY_PREFIX;
import static cc.ranmc.utils.BasicUtil.print;

public class HttpUtil {

    private static final int TIMEOUT = 8000;

    public static void get(String url, Consumer<String> callback) {
        Unirest.get(url).requestTimeout(TIMEOUT).asStringAsync()
                .handleAsync((result, e) -> {
                    if (e != null) {
                        print(THY_PREFIX + "&c网路请求失败 " + e.getMessage());
                        return "";
                    }
                    return result.getBody();
                }).thenAccept(callback);
    }

    public static void post(String url, String body, Consumer<String> callback) {
        Unirest.post(url)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .body(body)
                .requestTimeout(TIMEOUT)
                .asStringAsync()
                .handleAsync((result, e) -> {
                    if (e != null) {
                        print(THY_PREFIX + "&c网路请求失败 " + e.getMessage());
                        return "";
                    }
                    return result.getBody();
                }).thenAccept(callback);
    }

    public static void post(String url, JSONObject body, Consumer<String> callback) {
        Unirest.post(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(body.toString())
                .requestTimeout(TIMEOUT)
                .asStringAsync()
                .handleAsync((result, e) -> {
                    if (e != null) {
                        print(THY_PREFIX + "&c网路请求失败 " + e.getMessage());
                        return "";
                    }
                    return result.getBody();
                }).thenAccept(callback);
    }

}
