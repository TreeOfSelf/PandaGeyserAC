package me.TreeOfSelf.pandageyserac;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.session.GeyserSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PandaGeyserAC implements Extension {
    static {
        System.setProperty("bs.clientstop", "true");
    }

    private static ExtensionLogger logger;
    static boolean verbose;
    static volatile boolean enforce;
    private static PrintWriter moveLog;
    private final Map<GeyserConnection, AcPlayer> players = new ConcurrentHashMap<>();

    static ExtensionLogger log() {
        return logger;
    }

    static synchronized void moveLog(String line) {
        if (moveLog != null) moveLog.println(LocalTime.now() + " " + line);
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        logger = this.logger();
        Path folder = this.dataFolder();
        try {
            Files.createDirectories(folder);
            verbose = Files.exists(folder.resolve("verbose"));
            enforce = Files.exists(folder.resolve("enforce"));
            if (verbose) moveLog = new PrintWriter(new FileWriter(folder.resolve("moves.log").toFile(), true), true);
        } catch (IOException e) {
            logger.error("Could not open moves.log", e);
        }
        logger.info("PandaGeyserAC loaded (" + (enforce ? "setbacks on" : "log-only") + (verbose ? ", verbose moves.log" : "") + ")");
        Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                boolean now = Files.exists(folder.resolve("enforce"));
                if (now != enforce) {
                    enforce = now;
                    logger.info(now ? "Setbacks on" : "Log-only");
                }
            }
        }, "PandaGeyserAC toggles");
        watcher.setDaemon(true);
        watcher.start();
    }

    @Subscribe
    public void onLogin(SessionLoginEvent event) {
        if (!(event.connection() instanceof GeyserSession session)) return;
        AcPlayer player = new AcPlayer(session);
        PacketHook.install(session, player);
        players.put(event.connection(), player);
    }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) {
        players.remove(event.connection());
    }
}
