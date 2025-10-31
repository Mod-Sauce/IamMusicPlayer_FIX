package org.modsauce.impr.client.gui.screen.monitor.music_manager;

import org.modsauce.impr.blockentity.MusicManagerBlockEntity;
import org.modsauce.impr.client.gui.screen.MusicManagerScreen;

public class ImportYoutubePlayListMMMonitor extends ImportYoutubePlayListBaseMMMonitor {

    public ImportYoutubePlayListMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    protected void onImport() {
        getScreen().insCreateName(getImportPlayListName());
        insMonitor(MusicManagerBlockEntity.MonitorType.CREATE_PLAY_LIST);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.IMPORT_PLAY_LIST_SELECT;
    }
}
