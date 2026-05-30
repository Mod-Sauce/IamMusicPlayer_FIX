package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.gui.screen.monitor.music_manager.ImportPlayListBaseMMMonitor;
import dev.felnull.imp.music.resource.Music;

import java.util.List;
import java.util.Optional;

public interface IPlaylistLoader {
    String getID();
    IPlaylistResolver getResolver(String id);
    default Optional<String> autoPasteFromClipboard(String input){return Optional.empty();}

    static List<ImportPlayListBaseMMMonitor.PlayListEntry> createEntriesFromMusics(List<Music> musics){
        return musics.stream().map(m -> new ImportPlayListBaseMMMonitor.PlayListEntry(m.getName(), m.getAuthor(), m.getSource(), m.getImage())).toList();
    }
}
