package org.modsauce.impr.client.gui.screen.monitor.music_manager;

import org.modsauce.impr.blockentity.MusicManagerBlockEntity;
import org.modsauce.impr.client.gui.screen.MusicManagerScreen;
import org.modsauce.impr.music.resource.MusicSource;

public abstract class SavedMusicBaseMMMonitor extends MusicBaseMMMonitor {

    public SavedMusicBaseMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    protected void setMusicSource(MusicSource source, String author) {
        getScreen().insMusicSource(source);
        setMusicAuthor(author);
    }

    @Override
    protected void setMusicAuthor(String author) {
        super.setMusicAuthor(author);
        getScreen().insMusicAuthor(author);
    }
}
