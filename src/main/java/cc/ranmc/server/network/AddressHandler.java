package cc.ranmc.server.network;

import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Prams;
import com.alibaba.fastjson2.JSONObject;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

public class AddressHandler {

    public static void handle(Context context) {
        context.contentType(ContentType.APPLICATION_JSON);
        JSONObject json = new JSONObject();

        if (context.queryParamMap().containsKey(Prams.MSG)) {
            //String msg = URLDecoder.decode(map.get(Prams.MSG), StandardCharsets.UTF_8);
            String msg = context.queryParam(Prams.MSG);
            broadcast(msg);
            Main.getLogger().info("收到信件地址{}", msg);
        } else {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
        }
        context.result(json.toString());
    }

    public static void broadcast(String msg) {
        try {
            OhMyEmail.subject("收到信件地址")
                    .from("【桃花源】")
                    .to("xyfwdy@qq.com")
                    .text(msg)
                    .send();
        } catch (SendMailException e) {
            Main.getLogger().info("发送邮件失败{}", e.getMessage());
        }
    }
}

