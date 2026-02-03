package cc.ranmc.server;

import cc.ranmc.server.network.AddressHandler;
import cc.ranmc.server.network.BanlistHandler;
import cc.ranmc.server.network.BaseHandler;
import cc.ranmc.server.network.BroadcastHandler;
import cc.ranmc.server.network.ChartHandler;
import cc.ranmc.server.network.OptionsHandler;
import cc.ranmc.server.network.VerifyHandler;
import cc.ranmc.server.util.ConfigUtil;
import cc.ranmc.server.util.MinecraftUtil;
import io.github.biezhi.ome.OhMyEmail;
import io.javalin.Javalin;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import static cc.ranmc.server.constant.Data.ADDRESS_PATH;
import static cc.ranmc.server.constant.Data.ANY_PATH;
import static cc.ranmc.server.constant.Data.AUTHOR;
import static cc.ranmc.server.constant.Data.BANLIST_PATH;
import static cc.ranmc.server.constant.Data.BASE_PATH;
import static cc.ranmc.server.constant.Data.BROADCAST_PATH;
import static cc.ranmc.server.constant.Data.CHART_PATH;
import static cc.ranmc.server.constant.Data.EMAIL_PWD;
import static cc.ranmc.server.constant.Data.PORT;
import static cc.ranmc.server.constant.Data.VERIFY_PATH;
import static cc.ranmc.server.constant.Data.VERSION;
import static cc.ranmc.server.constant.Data.WEB_SITE;
import static io.github.biezhi.ome.OhMyEmail.defaultConfig;

@Getter
public final class Main {

    @Getter
    public static final Logger logger = LoggerFactory.getLogger(Main.class);

    static void main() {

        System.out.println("-----------------------");
        System.out.println("RanmcServer By " + AUTHOR);
        System.out.println("Version: " + VERSION);
        System.out.println(WEB_SITE);
        System.out.println("-----------------------");

        // 初始化配置文件
        ConfigUtil.load();

        // 初始化邮件
        Properties props = defaultConfig(false);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "gz-smtp.qcloudmail.com");
        props.put("mail.smtp.port", "465");
        OhMyEmail.config(props, "bot@ranmc.cc", EMAIL_PWD);

        Javalin.create()
                .get(BASE_PATH, BaseHandler::handle)
                .get(BANLIST_PATH, BanlistHandler::handle)
                .get(BROADCAST_PATH, BroadcastHandler::handle)
                .get(CHART_PATH, ChartHandler::handle)
                .get(VERIFY_PATH, VerifyHandler::handle)
                .post(ADDRESS_PATH, AddressHandler::handle)
                .options(ANY_PATH, OptionsHandler::handle)
                .start(PORT);

        getLogger().info("已成功运行在端口" + PORT);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                MinecraftUtil.updateServerStatus();
            }
        }, 0, 1000 * 60);
    }

}