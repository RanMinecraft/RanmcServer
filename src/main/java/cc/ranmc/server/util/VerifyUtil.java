package cc.ranmc.server.util;

import cc.ranmc.server.Main;
import cc.ranmc.server.bean.VerifyBean;
import cc.ranmc.server.constant.Code;
import cc.ranmc.server.constant.Email;
import io.github.biezhi.ome.OhMyEmail;
import io.github.biezhi.ome.SendMailException;
import lombok.Getter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static cc.ranmc.server.constant.Data.VERIFY_WEB_SITE;

public class VerifyUtil {

    @Getter
    private static final Map<String, VerifyBean> verifyMap = new HashMap<>();
    
    public static int check(String player, String email, String mode) {
        String action = switch (mode) {
            case "0" -> "绑定游戏账号";
            case "1" -> "重置游戏密码";
            case "2" -> "迁移账号数据";
            default -> "错误操作";
        };

        if (verifyMap.containsKey(email)) {
            VerifyBean verifyBean = verifyMap.get(email);
            if (verifyBean.isPass()) {
                verifyMap.remove(email);
                Main.getLogger().info("成功{}：{}({})", action, player, email);
                return Code.SUCCESS;
            } else if (verifyBean.getTime() < new Date().getTime()) {
                verifyMap.remove(email);
                Main.getLogger().warn("等待确认超时:{}", email);
                return Code.TIME_OUT;
            }
        } else {
            String key = KeyGenerator.get();
            VerifyBean verifyBean = new VerifyBean();
            verifyBean.setMode(mode);
            verifyBean.setPlayer(player);
            verifyBean.setKey(key);
            verifyMap.put(email, verifyBean);
            String url = VERIFY_WEB_SITE + key;
            try {
                OhMyEmail.subject(action + "确认")
                        .from("【桃花源】")
                        .to(email)
                        .html(Email.text
                                .replace("%player%", player)
                                .replace("%url%", url)
                                .replace("%action%", action))
                        .send();
                Main.getLogger().info("发送邮件成功:{}", email);
            } catch (SendMailException e) {
                Main.getLogger().error(e.getMessage());
                Main.getLogger().error("发送邮件失败:{}", email);
                return Code.ERROR;
            }
        }
        return Code.WAITING;
    }
}
