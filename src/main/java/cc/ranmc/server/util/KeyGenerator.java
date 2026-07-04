package cc.ranmc.server.util;

import java.util.Random;

public class KeyGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz123456789";

    public static String get() {
        StringBuilder builder = new StringBuilder();
        int length = (int) ((Math.random() * 3) + 4);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            builder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return builder.toString();
    }
}
