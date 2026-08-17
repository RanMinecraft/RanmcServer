package cc.ranmc.server.util;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.sql.SQLFilter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kong.unirest.core.Unirest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static cc.ranmc.server.constant.Data.AI_API_KEY;
import static cc.ranmc.server.constant.Data.AI_BASE_URL;
import static cc.ranmc.server.constant.Data.AI_MODEL;
import static cc.ranmc.server.constant.Data.FEISHU_WEBHOOK;
import static cc.ranmc.server.constant.Data.LOG_SQL;

public class AIUtil {

    private static final int TIMEOUT = 150 * 1000;
    private static final String SUMMARY_PROMPT = """
            请帮我详细总结我的世界桃花源服务器内聊天信息。
            输出必须严格使用以下 Markdown 标题结构，并使用简短列表，缺失内容写“无”：
            # 今日概览
            # 重点事件
            # 异常言论
            # 建议与反馈
            # 管理员相关
            # 结论与建议

            额外要求：
            1. 不要使用 Markdown 表格。
            2. 不要使用代码块。
            3. 不要输出 HTML。
            4. 每个要点尽量简短，优先保留时间、玩家名、事件结论。
            5. 如果玩家存在辱骂或刷屏等不当言语，请写明具体时间、聊天内容并分析原因。
            6. 留意玩家对服务器建议、漏洞或不满的地方，以及对管理员阿然(Ranica)的讨论。
            """;

    public static CompletableFuture<String> chat(String systemContext, String messageContext) {

        JsonObject json = new JsonObject();
        json.addProperty("model", AI_MODEL);
        json.addProperty("stream", false);
        json.addProperty("temperature", 0.5);

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemContext);
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", messageContext);
        messages.add(user);

        json.add("messages", messages);

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
                    JsonObject error = new JsonObject();
                    error.addProperty("error", ex.getMessage());
                    return error.toString();
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
        summarize(date, builder.toString());
    }

    private static void sendFeishuSummary(String date, String markdownContent) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("msg_type", "interactive");
            body.add("card", buildSummaryCard(date, markdownContent));

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

    private static JsonObject buildSummaryCard(String date, String markdownContent) {
        JsonObject card = new JsonObject();
        card.addProperty("schema", "2.0");

        JsonObject config = new JsonObject();
        config.addProperty("update_multi", true);
        card.add("config", config);

        JsonObject header = new JsonObject();
        header.addProperty("template", "blue");
        JsonObject title = new JsonObject();
        title.addProperty("tag", "plain_text");
        title.addProperty("content", "服务器聊天日报");
        header.add("title", title);
        JsonObject subtitle = new JsonObject();
        subtitle.addProperty("tag", "plain_text");
        subtitle.addProperty("content", date == null ? "" : date);
        header.add("subtitle", subtitle);
        card.add("header", header);

        JsonObject body = new JsonObject();
        body.addProperty("direction", "vertical");
        body.addProperty("padding", "12px 12px 12px 12px");
        body.add("elements", buildCardElements(markdownContent));
        card.add("body", body);
        return card;
    }

    private static JsonArray buildCardElements(String markdownContent) {
        JsonArray elements = new JsonArray();
        List<Section> sections = splitSections(markdownContent);
        if (sections.isEmpty()) {
            sections.add(new Section("今日概览", sanitizeMarkdown(markdownContent)));
        }

        for (Section section : sections) {
            if (section.content == null || section.content.isBlank()) {
                continue;
            }
            JsonObject element = new JsonObject();
            element.addProperty("tag", "markdown");
            element.addProperty("content", "## " + section.title + "\n" + section.content);
            element.addProperty("text_align", "left");
            element.addProperty("margin", "0px 0px 12px 0px");
            elements.add(element);
        }
        return elements;
    }

    private static List<Section> splitSections(String markdownContent) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : markdownContent.split("\\R")) {
            String line = normalizeMarkdownLine(rawLine);
            if (line == null) {
                continue;
            }
            if (isHeading(line)) {
                appendSection(sections, currentTitle, currentContent);
                currentTitle = extractHeading(line);
                currentContent = new StringBuilder();
                continue;
            }
            if (currentContent.length() > 0) {
                currentContent.append("\n");
            }
            currentContent.append(line);
        }
        appendSection(sections, currentTitle, currentContent);
        return sections;
    }

    private static void appendSection(List<Section> sections, String title, StringBuilder content) {
        String text = sanitizeMarkdown(content.toString());
        if (text.isBlank()) {
            return;
        }
        sections.add(new Section(title == null ? "补充信息" : title, text));
    }

    private static boolean isHeading(String line) {
        return line.startsWith("# ")
                || line.startsWith("## ")
                || line.startsWith("### ");
    }

    private static String extractHeading(String line) {
        return line.replaceFirst("^#+\\s*", "").trim();
    }

    private static String sanitizeMarkdown(String text) {
        StringBuilder builder = new StringBuilder();
        for (String rawLine : text.split("\\R")) {
            String line = normalizeMarkdownLine(rawLine);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private static String normalizeMarkdownLine(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty() || "```".equals(text)) {
            return null;
        }
        if (text.matches("^\\|(?:\\s*-+:?\\s*\\|)+$")) {
            return null;
        }
        if (text.startsWith("|") && text.endsWith("|")) {
            String[] cells = text.substring(1, text.length() - 1).split("\\|");
            StringBuilder builder = new StringBuilder("- ");
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    builder.append(" ｜ ");
                }
                builder.append(cells[i].trim());
            }
            text = builder.toString();
        } else if (text.startsWith("* ")) {
            text = "- " + text.substring(2).trim();
        } else if (text.matches("^-{3,}$")) {
            return null;
        }
        return text.replace("`", "").trim();
    }

    private static class Section {
        private final String title;
        private final String content;

        private Section(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    public static void summarize(String date, String context) {
        AIUtil.chat(SUMMARY_PROMPT, context)
                .thenAccept(result -> {
                    if (result == null || result.isEmpty()) {
                        Main.getLogger().warn("请求 AI 总结失败: null");
                    }
                    try {
                        JsonObject root = JsonUtil.parse(result);
                        if (root == null || root.has("error")) {
                            Main.getLogger().warn("请求 AI 总结失败: {}", root == null ? "null" : JsonUtil.getString(root, "error"));
                            return;
                        }
                        JsonArray choices = JsonUtil.getArray(root, "choices");
                        if (choices != null && !choices.isEmpty()) {
                            JsonObject first = choices.get(0).getAsJsonObject();
                            if (first != null) {
                                JsonObject message = JsonUtil.getObject(first, "message");
                                if (message != null) {
                                    String content = JsonUtil.getString(message, "content");
                                    if (content != null) {
                                        Main.getLogger().info("请求 AI 总结成功\n{}", content);
                                        sendFeishuSummary(date, content);
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
