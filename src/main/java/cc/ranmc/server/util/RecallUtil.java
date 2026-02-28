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
            String context = FileUtils.fileRead(file, "utf8");
            if (context == null) context = "";
            RECALL_LIST.addAll(Arrays.asList(context.split("\n")));
            Main.getLogger().error("载入屏蔽词 {} 条", RECALL_LIST.size());
        } catch (IOException e) {
            Main.getLogger().error("载入屏蔽词失败 {}", e.getMessage());
        }
    }

    public static String replace(String context) {
        if (context == null || context.isEmpty() || RECALL_LIST.isEmpty()) {
            return context;
        }
        String result = context;
        for (String word : RECALL_LIST) {
            if (word == null || word.isEmpty()) continue;
            String stars = "*".repeat(word.length());
            result = result.replace(word, stars);
        }
        return result;
    }
}
