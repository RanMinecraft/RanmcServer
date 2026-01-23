package cc.ranmc.server.network;

import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Prams;
import com.alibaba.fastjson2.JSONObject;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class AddressHandler {

    private static final Map<String,Long> postMap = new HashMap<>();

    public static void handle(Context context) {
        context.contentType(ContentType.APPLICATION_JSON);
        JSONObject json = new JSONObject();

        if (context.queryParamMap().containsKey(Prams.MSG)) {
            //String msg = URLDecoder.decode(map.get(Prams.MSG), StandardCharsets.UTF_8);
            String ip = context.header("X-Real-IP");
            long now = System.currentTimeMillis();
            if (postMap.getOrDefault(ip, 0L) + (10L * 60 * 1000) >= now) {
                json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
                json.put(Prams.MSG, "你已提交过收信地址，如需再次提交，请10分钟后再试。");
                context.result(json.toString());
                return;
            }
            postMap.put(ip, now);
            String msg = context.queryParam(Prams.MSG);
            broadcast(msg);
            Main.getLogger().info("收到信件地址{}", msg);
            json.put(Prams.MSG, "提交成功！我们将会尽快发出您的贺卡。");
        } else {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
        }
        context.result(json.toString());
    }

    public static void broadcast(String msg) {
        try {
            OhMyEmail.subject("收到信件地址")
                    .from("【桃花源】")
                    .to("xyfwdy@gmail.com")
                    .text(msg)
                    .send();
        } catch (SendMailException e) {
            Main.getLogger().info("发送邮件失败{}", e.getMessage());
        }
    }
}

