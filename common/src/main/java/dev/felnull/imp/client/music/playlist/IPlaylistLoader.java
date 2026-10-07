package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.gui.screen.monitor.music_manager.ImportPlayListBaseMMMonitor;
import dev.felnull.imp.client.music.link.MusicLink;
import dev.felnull.imp.client.music.link.MusicLinks;
import dev.felnull.imp.music.resource.Music;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface IPlaylistLoader {
    String getID();
    IPlaylistResolver getResolver(String id);
    default Optional<String> autoPasteFromClipboard(String input){return Optional.empty();}

    /**
     * Whether to list a separate button on the "import playlist" selection screen.
     */
    default boolean showInSelectScreen() {
        return true;
    }

    /** Clipboard recognition: parses the pasted link with {@link MusicLinks}, matching only the links it owns; each
     * loader's own version overrides it with higher priority. */
    static Optional<String> autoPasteFromMusicLink(IPlaylistLoader loader, String input) {
        return autoPasteFromMusicLink(loader, input, true);
    }

    /** Same as above, but "identifier conversion" can be off (`convert=false` means **no network**): used where only
     * the "which loader gets this" check matters, like clipboard probing on the import screen. */
    static Optional<String> autoPasteFromMusicLink(IPlaylistLoader loader, String input, boolean convert) {
        if (loader == null)
            return Optional.empty();

        MusicLink link = MusicLinks.resolveFor(input, loader.getID(), convert);
        if (link != null && link.isPlaylistLike() && link.loaderId().equals(loader.getID()))
            return Optional.of(link.identifier());

        return Optional.empty();
    }

    /**
     * Only determine "which playlist loader this clipboard should go to", **no conversion, no network**; returns null when it is not a playlist-like link.
     */
    @Nullable
    static String playlistLoaderIdFromClipboard(@Nullable String input) {
        MusicLink link = MusicLinks.resolveFor(input, null, false);
        return link != null && link.isPlaylistLike() ? link.loaderId() : null;
    }

    static List<ImportPlayListBaseMMMonitor.PlayListEntry> createEntriesFromMusics(List<Music> musics){
        return musics.stream().map(m -> new ImportPlayListBaseMMMonitor.PlayListEntry(m.getName(), m.getAuthor(), m.getSource(), m.getImage())).toList();
    }
}
