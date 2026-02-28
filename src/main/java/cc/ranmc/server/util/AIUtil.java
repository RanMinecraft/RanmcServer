package cc.ranmc.server.util;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.sql.SQLFilter;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import kong.unirest.core.Unirest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import static cc.ranmc.server.constant.Data.AI_API_KEY;
import static cc.ranmc.server.constant.Data.AI_BASE_URL;
import static cc.ranmc.server.constant.Data.AI_MODEL;
import static cc.ranmc.server.constant.Data.LOG_SQL;
import static cc.ranmc.server.util.MarkdownUtil.mdToHtml;

public class AIUtil {

    private static final int TIMEOUT = 120 * 1000;

    public static CompletableFuture<String> chat(String systemContext, String messageContext) {

        JSONObject json = new JSONObject();
        json.put("model", AI_MODEL);
        json.put("stream", false);
        json.put("temperature", 1);

        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", systemContext);
        messages.add(system);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", messageContext);
        messages.add(user);

        json.put("messages", messages);

        return Unirest.post(AI_BASE_URL + "/chat/completions")
                .requestTimeout(TIMEOUT)
                .header("Authorization", "Bearer " + AI_API_KEY)
                .header("Content-Type", "application/json")
                .body(json.toString())
                .asStringAsync()
                .thenApply(response -> {
                    if (response.getStatus() >= 200 && response.getStatus() < 300) {
                        return response.getBody();
                    } else {
                        throw new RuntimeException(
                                "HTTP Error: " + response.getStatus() + " Body: " + response.getBody()
                        );
                    }
                })
                .exceptionally(ex -> {
                    JSONObject error = new JSONObject();
                    error.put("error", ex.getMessage());
                    return error.toJSONString();
                });
    }

    public static void chat() {
        Main.getLogger().info("开始请求 AI 总结");
        StringBuilder builder = new StringBuilder();
        LOG_SQL.selectList(SQLKey.MESSAGE, new SQLFilter()
                .where(SQLKey.DATE, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))).forEach(row -> {
            /*//模型长度限制
            if (builder.length() > 60000) {
                chat(builder.toString());
                builder.setLength(0);
                Main.getLogger().warn("分段请求 AI 总结");
            }*/
            builder.append(row.getString(SQLKey.TIME))
                    .append(" ")
                    .append(row.getString(SQLKey.SENDER))
                    .append(row.getString(SQLKey.RECEIVER).equals("#") ? "" : ("悄悄对" + row.getString(SQLKey.RECEIVER)))
                    .append("说:").append(row.getString(SQLKey.MESSAGE))
                    .append("\n");
        });
        chat(builder.toString());
    }
    public static void chat(String context) {
        AIUtil.chat("请帮我详细总结我的世界服务器内聊天信息，" +
                        "如果玩家存在辱骂或刷屏等不当言语、" +
                        "对服务器漏洞或建议或不满的地方。" +
                        "以及对管理员阿然(Ranica)的讨论请告诉我。", context)
                .thenAccept(result -> {
                    if (result == null || result.isEmpty()) {
                        Main.getLogger().warn("请求 AI 总结失败: null");
                    }
                    try {
                        JSONObject root = JSONObject.parseObject(result);
                        if (root == null || root.containsKey("error")) {
                            Main.getLogger().warn("请求 AI 总结失败: {}", root == null ? "null" : root.getString("error"));
                            return;
                        }
                        JSONArray choices = root.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JSONObject first = choices.getJSONObject(0);
                            if (first != null) {
                                JSONObject message = first.getJSONObject("message");
                                if (message != null) {
                                    String content = message.getString("content");
                                    if (content != null) {
                                        Main.getLogger().info("请求 AI 总结成功\n{}", content);
                                        try {
                                            OhMyEmail.subject("服务器聊天 AI 总结")
                                                    .from("【桃花源】")
                                                    .to("xyfwdy@gmail.com")
                                                    .html(mdToHtml(content))
                                                    .send();
                                            Main.getLogger().info("发送 AI 总结邮件成功");
                                        } catch (SendMailException e) {
                                            Main.getLogger().error(e.getMessage());
                                            Main.getLogger().error("发送 AI 总结邮件失败");
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                        Main.getLogger().warn("请求 AI 总结未知结果: {}", result);
                    } catch (Exception e) {
                        Main.getLogger().error("请求 AI 总结错误: {}", e.getMessage());
                    }
                });
    }
}