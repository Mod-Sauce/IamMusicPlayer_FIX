package org.modsauce.impr.client.gui.screen.monitor.music_manager;

import dev.architectury.networking.NetworkManager;
import org.modsauce.impr.blockentity.MusicManagerBlockEntity;
import org.modsauce.impr.client.gui.screen.MusicManagerScreen;
import org.modsauce.impr.music.resource.Music;
import org.modsauce.impr.networking.IMPPackets;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DeleteMusicMMMonitor extends DeleteBaseMMMonitor {
    public DeleteMusicMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void onDelete() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlock)
            NetworkManager.sendToServer(IMPPackets.MUSIC_OR_PLAYLIST_DELETE, new IMPPackets.MusicOrPlayListDeleteMessage(getSelectedPlayList(musicManagerBlock), getSelectedMusicRaw(musicManagerBlock), BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity()), true).toRFBB());
    }

    @Override
    public @NotNull String getWaringName(MusicManagerBlockEntity musicManagerBlockEntity) {
        var sm = getSelectedMusic(musicManagerBlockEntity);
        if (sm != null)
            return sm.getName();
        return "";
    }

    @Override
    protected @Nullable MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.DETAIL_MUSIC;
    }

    @Nullable
    private Music getSelectedMusic(MusicManagerBlockEntity musicManagerBlockEntity) {
        var id = getSelectedMusicRaw(musicManagerBlockEntity);
        var pl = getSelectedPlayList(musicManagerBlockEntity);
        if (id != null && pl != null) {
            var sms = getSyncManager().getMusics(pl);
            if (sms != null)
                return sms.stream().filter(n -> id.equals(n.getUuid())).findFirst().orElse(null);
        }
        return null;
    }

    @Nullable
    private UUID getSelectedPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedPlayList(mc.player);
    }

    @Nullable
    private UUID getSelectedMusicRaw(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedMusic(mc.player);
    }
}
