package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecallUtil {

    private static final List<String> RECALL_LIST = new ArrayList<>();

    public static void load() {
        try {
            File file = new File(System.getProperty("user.dir") + "/recall.txt");
            String content = FileUtils.fileRead(file, "utf8");
            if (content == null) content = "";
            RECALL_LIST.addAll(Arrays.asList(content.split("\n")));
            Main.getLogger().error("载入屏蔽词 {} 条", RECALL_LIST.size());
        } catch (IOException e) {
            Main.getLogger().error("载入屏蔽词失败 {}", e.getMessage());
        }
    }

    public static String replace(String content) {
        if (content == null || content.isEmpty() || RECALL_LIST.isEmpty()) {
            return content;
        }
        String result = content;
        for (String word : RECALL_LIST) {
            if (word == null || word.isEmpty()) continue;
            String stars = "*".repeat(word.length());
            result = result.replace(word, stars);
        }
        return result;
    }
}
