package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import org.jetbrains.annotations.Nullable;

public class ImportPlayListSelectMMMonitor extends ImportSelectBaseMMMonitor {

    public ImportPlayListSelectMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType resolveMonitor() {
        return MusicManagerBlockEntity.MonitorType.IMPORT_PLAY_LIST;
    }


    @Override
    protected @Nullable MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.ADD_PLAY_LIST;
    }
}
