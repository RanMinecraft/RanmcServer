package cc.ranmc.server.util;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.sql.SQLFilter;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import kong.unirest.core.Unirest;

import java.util.concurrent.CompletableFuture;

import static cc.ranmc.server.constant.Data.AI_API_KEY;
import static cc.ranmc.server.constant.Data.AI_BASE_URL;
import static cc.ranmc.server.constant.Data.AI_MODEL;
import static cc.ranmc.server.constant.Data.FEISHU_WEBHOOK;
import static cc.ranmc.server.constant.Data.LOG_SQL;

public class AIUtil {

    private static final int TIMEOUT = 150 * 1000;

    public static CompletableFuture<String> chat(String systemContext, String messageContext) {

        JSONObject json = new JSONObject();
        json.put("model", AI_MODEL);
        json.put("stream", false);
        json.put("temperature", 0.5);

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

    public static void start(String date) {
        Main.getLogger().info("开始请求 AI 总结");
        StringBuilder builder = new StringBuilder();
        LOG_SQL.selectList(SQLKey.MESSAGE, new SQLFilter()
                .where(SQLKey.DATE, date)).forEach(row -> {
            builder.append(row.getString(SQLKey.TIME))
                    .append(" ")
                    .append(row.getString(SQLKey.SENDER))
                    .append(row.getString(SQLKey.RECEIVER).equals("#") ? "" : ("悄悄对" + row.getString(SQLKey.RECEIVER)))
                    .append("说:").append(row.getString(SQLKey.MESSAGE))
                    .append("\n");
        });
        chat(builder.toString());
    }

    private static void sendFeishuSummary(String markdownContent) {
        try {
            JSONObject body = new JSONObject();
            body.put("msg_type", "post");

            JSONObject zhCn = new JSONObject();
            zhCn.put("title", "服务器聊天 AI 总结");
            zhCn.put("content", buildFeishuParagraphs(markdownContent));

            JSONObject post = new JSONObject();
            post.put("zh_cn", zhCn);

            JSONObject content = new JSONObject();
            content.put("post", post);

            body.put("content", content);

            Unirest.post(FEISHU_WEBHOOK)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .requestTimeout(8000)
                    .asStringAsync()
                    .thenAccept(response -> {
                        if (response.getStatus() >= 200 && response.getStatus() < 300) {
                            Main.getLogger().info("发送 AI 总结飞书成功");
                        } else {
                            Main.getLogger().warn("发送 AI 总结飞书失败 {}", response.getBody());
                        }
                    });
        } catch (Exception e) {
            Main.getLogger().error("发送 AI 总结飞书失败 {}", e.getMessage());
        }
    }

    private static JSONArray buildFeishuParagraphs(String markdownContent) {
        JSONArray paragraphs = new JSONArray();
        for (String line : markdownContent.split("\\R")) {
            String normalized = normalizeMarkdownLine(line);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            JSONArray paragraph = new JSONArray();
            JSONObject text = new JSONObject();
            text.put("tag", "text");
            text.put("text", normalized);
            paragraph.add(text);
            paragraphs.add(paragraph);
        }
        return paragraphs;
    }

    private static String normalizeMarkdownLine(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("```".equals(text)) {
            return null;
        }
        if (text.matches("^\\|(?:\\s*-+:?\\s*\\|)+$")) {
            return null;
        }
        if (text.startsWith("### ")) {
            text = "【" + text.substring(4).trim() + "】";
        } else if (text.startsWith("## ")) {
            text = "【" + text.substring(3).trim() + "】";
        } else if (text.startsWith("# ")) {
            text = "【" + text.substring(2).trim() + "】";
        } else if (text.startsWith("- ") || text.startsWith("* ")) {
            text = "• " + text.substring(2).trim();
        } else if (text.matches("^-{3,}$")) {
            text = "──────────";
        }
        text = text.replace("**", "")
                .replace("__", "")
                .replace("`", "");
        return text;
    }

    public static void chat(String context) {
        AIUtil.chat("请帮我详细总结我的世界桃花源服务器内聊天信息都有谁发生了什么事，" +
                        "如果玩家存在辱骂或刷屏等不当言语请告诉我具体时间和聊天内容并分析原因，" +
                        "留意玩家对服务器建议、漏洞或不满的地方以及对管理员阿然(Ranica)的讨论。", context)
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
                                        sendFeishuSummary(content);
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
