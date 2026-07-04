package cc.ranmc.server.network;

import cc.ranmc.server.Main;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Data;
import cc.ranmc.server.constant.Prams;
import cc.ranmc.server.util.CrossUtil;
import com.alibaba.fastjson2.JSONObject;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import kong.unirest.core.Unirest;

public class BroadcastHandler {

    public static void handle(Context context) {
        CrossUtil.allow(context);
        context.contentType(ContentType.APPLICATION_JSON);
        JSONObject json = new JSONObject();
        if (!context.queryParamMap().containsKey(Prams.TOKEN) ||
                !Data.TOKEN.equals(context.queryParam(Prams.TOKEN))) {
            json.put(Prams.CODE, Code.NO_PERMISSION);
            context.result(json.toString());
            return;
        }
        if (context.queryParamMap().containsKey(Prams.MSG)) {
            //String msg = URLDecoder.decode(map.get(Prams.MSG), StandardCharsets.UTF_8);
            String msg = context.queryParam(Prams.MSG);
            if (context.queryParamMap().containsKey("type") && 
                    context.queryParam("type").equals("feishu")) {
                sendFeishu(msg);
                Main.getLogger().info("发出飞书广播{}", msg);
            } else {
                sendEmail(msg);
                Main.getLogger().info("发出邮件广播{}", msg);
            }
        } else {
            json.put(Prams.CODE, Code.UNKNOWN_REQUEST);
        }
        context.result(json.toString());
    }

    public static void sendFeishu(String msg) {
        try {
            JSONObject body = new JSONObject();
            body.put("msg_type", "text");
            JSONObject content = new JSONObject();
            content.put("text", msg);
            body.put("content", content);

            Unirest.post(Data.FEISHU_WEBHOOK)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .requestTimeout(8000)
                    .asStringAsync()
                    .thenAccept(response -> {
                        if (response.getStatus() >= 200 && response.getStatus() < 300) {
                            Main.getLogger().info("飞书提醒发送成功");
                        } else {
                            Main.getLogger().warn("飞书提醒发送失败: {}", response.getBody());
                        }
                    });
        } catch (Exception e) {
            Main.getLogger().error("飞书提醒发送失败: {}", e.getMessage());
        }
    }

    public static void sendEmail(String msg) {
        try {
            OhMyEmail.subject("服务器消息")
                    .from("【桃花源】")
                    .to("xyfwdy@gmail.com")
                    .text(msg)
                    .send();
        } catch (SendMailException e) {
            Main.getLogger().info("发送邮件失败{}", e.getMessage());
        }
    }
}

