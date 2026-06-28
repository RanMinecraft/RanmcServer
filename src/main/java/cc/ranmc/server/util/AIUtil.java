package cc.ranmc.server.util;

import cc.ranmc.constant.SQLKey;
import cc.ranmc.server.Main;
import cc.ranmc.sql.SQLFilter;
import cc.ranmc.sql.SQLRow;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import kong.unirest.core.Unirest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static cc.ranmc.server.constant.Data.AI_API_KEY;
import static cc.ranmc.server.constant.Data.AI_BASE_URL;
import static cc.ranmc.server.constant.Data.AI_MODEL;
import static cc.ranmc.server.constant.Data.LOG_SQL;
import static cc.ranmc.server.util.MarkdownUtil.mdToHtml;

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
                                        try {
                                            OhMyEmail.subject("服务器聊天 AI 总结")
                                                    .from("【桃花源】")
                                                    .to("xyfwdy@gmail.com")
                                                    .html(mdToHtml(content))
                                                    .send();
                                            Main.getLogger().info("发送 AI 总结邮件成功");
                                        } catch (SendMailException e) {
                                            Main.getLogger().error("发送 AI 总结邮件失败 {}", e.getMessage());
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

    // ==================== 违规检测（增量模式） ====================

    /** 上次检测到的最大消息 ID，用于增量检测避免重复 */
    private static volatile int lastMessageId = -1;

    /**
     * 检测最新聊天消息，发现辱骂/刷屏等违规行为<br>
     * 由定时器每1分钟调用一次<br>
     * - 首次运行：先查出最后200条中的最大 ID，再正序取这200条<br>
     * - 后续运行：只取 ID > lastMessageId 的新消息<br>
     * 每次检测后更新 lastMessageId 为本次检测到的最大 ID
     */
    public static void checkRecentChat() {
        // 首次运行：先查出当前最大 ID，锚定最后200条的范围
        if (lastMessageId == -1) {
            SQLRow lastRow = LOG_SQL.selectRow(SQLKey.MESSAGE, new SQLFilter().order("ID DESC").limit(1));
            int max = lastRow.getInt(SQLKey.ID, 0);
            if (max == 0) {
                lastMessageId = 0;
                return; // 表为空
            }
            lastMessageId = max;
        }

        StringBuilder builder = new StringBuilder();
        AtomicInteger newMaxId = new AtomicInteger(lastMessageId);

        LOG_SQL.selectList(SQLKey.MESSAGE, new SQLFilter()
                        .whereMoreThan(SQLKey.ID, lastMessageId)
                        .order("ID ASC")
                        .limit(200))
                .forEach(row -> {
                    int id = row.getInt(SQLKey.ID, 0);
                    if (id > newMaxId.get()) newMaxId.set(id);
                    builder.append(row.getString(SQLKey.TIME))
                            .append(" ")
                            .append(row.getString(SQLKey.SENDER))
                            .append(row.getString(SQLKey.RECEIVER).equals("#") ? "" : ("悄悄对" + row.getString(SQLKey.RECEIVER)))
                            .append("说:").append(row.getString(SQLKey.MESSAGE))
                            .append("\n");
                });

        // 更新断点（无论是否有消息都要更新，避免重复）
        if (newMaxId.get() > lastMessageId) {
            lastMessageId = newMaxId.get();
        }

        String chatLog = builder.toString();
        if (chatLog.isBlank()) {
            return; // 无新消息，跳过
        }

        //Main.getLogger().debug("开始 AI 违规检测（增量消息，ID {} 以上）", lastMessageId - chatLog.split("\n").length + 1);

        String systemPrompt = """
                你是一个我的世界服务器聊天监控助手。\
                我会给你最近的聊天记录，请你分析是否有玩家存在以下违规行为：\
                1. 辱骂、攻击性、歧视性言语（3次以上） 2. 恶意刷屏（重复发送相同或高度相似内容10次以上）\
                3. 发布宣传其他服务器（非桃花源 ranmc.cc）广告或违规链接。
                如果有违规行为，请用以下 JSON 格式输出（仅输出 JSON，不要多余文字）：
                {"violations":[{"player":"玩家名","reason":"违规原因描述"}]}
                如果没有违规行为，请输出：{"violations":[]}""";

        AIUtil.chat(systemPrompt, chatLog)
                .thenAccept(result -> {
                    if (result == null || result.isEmpty()) {
                        return;
                    }
                    try {
                        JSONObject root = JSONObject.parseObject(result);
                        if (root == null || root.containsKey("error")) {
                            return;
                        }
                        JSONArray choices = root.getJSONArray("choices");
                        if (choices == null || choices.isEmpty()) {
                            return;
                        }
                        JSONObject first = choices.getJSONObject(0);
                        if (first == null) {
                            return;
                        }
                        JSONObject message = first.getJSONObject("message");
                        if (message == null) {
                            return;
                        }
                        String content = message.getString("content");
                        if (content == null || content.isEmpty()) {
                            return;
                        }

                        // 尝试提取 JSON
                        JSONObject aiResponse;
                        try {
                            aiResponse = JSONObject.parseObject(content);
                        } catch (Exception e) {
                            // 如果 AI 返回了带 markdown 包裹的 JSON
                            int startIdx = content.indexOf('{');
                            int endIdx = content.lastIndexOf('}');
                            if (startIdx != -1 && endIdx > startIdx) {
                                aiResponse = JSONObject.parseObject(content.substring(startIdx, endIdx + 1));
                            } else {
                                return;
                            }
                        }

                        JSONArray violations = aiResponse.getJSONArray("violations");
                        if (violations == null || violations.isEmpty()) {
                            //Main.getLogger().debug("AI 违规检测: 未发现违规行为");
                            return;
                        }

                        for (int i = 0; i < violations.size(); i++) {
                            JSONObject v = violations.getJSONObject(i);
                            String player = v.getString("player");
                            String reason = v.getString("reason");
                            Main.getLogger().warn("AI 检测到违规 - 玩家: {}, 原因: {}", player, reason);

                            // 提取该玩家的发言记录，传给 mute 做上下文
                            String playerContext = extractPlayerContext(builder, player);
                            mute(player, reason, playerContext);
                        }
                    } catch (Exception e) {
                        Main.getLogger().error("AI 违规检测解析错误: {}", e.getMessage());
                    }
                });
    }

    /**
     * 从已构建的聊天日志中提取指定玩家的发言
     */
    private static String extractPlayerContext(StringBuilder fullLog, String playerName) {
        StringBuilder result = new StringBuilder();
        for (String line : fullLog.toString().split("\n")) {
            if (line.contains(" " + playerName + "说:")) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * 禁言指定玩家<br>
     *
     * @param playerName 玩家名
     * @param reason     AI 判断的违规原因
     * @param context    该玩家最近1分钟的全部聊天内容，供禁言时参考
     */
    public static void mute(String playerName, String reason, String context) {
        Main.getLogger().warn("需要禁言违规玩家: {} 原因: {}\n上下文:\n{}", playerName, reason, context);

        try {
            OhMyEmail.subject("服务器消息")
                    .from("【桃花源】")
                    .to("xyfwdy@qq.com")
                    .text("需要禁言违规玩家: " + playerName + "\n原因: " + reason + "\n上下文: \n" + context)
                    .send();
        } catch (SendMailException e) {
            Main.getLogger().info("发送邮件失败{}", e.getMessage());
        }
    }
}
