package dev.felnull.imp.server.handler;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public class ServerMusicHandler {
    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(ServerMusicHandler::levelTick);
        LifecycleEvent.SERVER_STARTING.register(ServerMusicHandler::serverStarting);
        LifecycleEvent.SERVER_STOPPING.register(ServerMusicHandler::serverStopping);
    }

    private static void levelTick(ServerLevel serverLevel) {
        MusicRingManager.getInstance().tick(serverLevel);
        EarphoneSaveData.getInstance(serverLevel).tick(serverLevel);
    }

    private static void serverStarting(MinecraftServer server) {
        MusicRingManager.getInstance().clear();
    }

    private static void serverStopping(MinecraftServer server) {
        MusicRingManager.getInstance().clear();
        // The undo table is transient server state and must not leak into a world loaded later in this JVM.
        MusicManager.getInstance().clearUndoCache();
    }
}
