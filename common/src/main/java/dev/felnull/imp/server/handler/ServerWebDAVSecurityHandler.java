package dev.felnull.imp.server.handler;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.felnull.imp.server.webdav.ServerWebDAVCrypto;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerWebDAVSecurityHandler {
    private static final Logger LOGGER = LogManager.getLogger(ServerWebDAVSecurityHandler.class);
    private static final int PLAYER_WARNING_DELAY_TICKS = 60;
    private static final Component DEFAULT_SECRET_WARNING = Component.translatable("imp.text.webdav.warning")
            .withStyle(ChatFormatting.YELLOW);

    private static boolean warnInConsole;
    private static boolean playerWarningSent;
    private static int ticksSinceFirstPlayerSeen = -1;

    private ServerWebDAVSecurityHandler() {
    }

    public static void init() {
        LifecycleEvent.SERVER_STARTING.register(ServerWebDAVSecurityHandler::onServerStarting);
        LifecycleEvent.SERVER_STOPPING.register(ServerWebDAVSecurityHandler::onServerStopping);
        TickEvent.SERVER_POST.register(ServerWebDAVSecurityHandler::onServerTick);
    }

    private static void onServerStarting(MinecraftServer server) {
        playerWarningSent = false;
        ticksSinceFirstPlayerSeen = -1;
        warnInConsole = ServerWebDAVCrypto.isUsingFallbackSecret();

        if (warnInConsole) {
            LOGGER.warn("[WebDAV] Using default fallback encryption secret. Set IMP_WEBDAV_SECRET in the server environment to protect stored WebDAV credentials.");
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        playerWarningSent = false;
        ticksSinceFirstPlayerSeen = -1;
        warnInConsole = false;
    }

    private static void onServerTick(MinecraftServer server) {
        if (!warnInConsole || playerWarningSent) {
            return;
        }

        if (server.getPlayerList().getPlayerCount() <= 0) {
            ticksSinceFirstPlayerSeen = -1;
            return;
        }

        if (ticksSinceFirstPlayerSeen < 0) {
            ticksSinceFirstPlayerSeen = 0;
            return;
        }

        ticksSinceFirstPlayerSeen++;
        if (ticksSinceFirstPlayerSeen < PLAYER_WARNING_DELAY_TICKS) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.sendSystemMessage(DEFAULT_SECRET_WARNING);
        playerWarningSent = true;
    }
}
