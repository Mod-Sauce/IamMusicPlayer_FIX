package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.music.link.MusicLink;
import dev.felnull.imp.client.music.link.MusicLinks;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Netease "radio / host radio (podcast)" loader: radio links ({@code radio?id=} / {@code djradio?id=}) use a different
 * fetch API ({@code api/dj/program/byradio}), so it is its own loader; each program's {@code mainSong} is the music.
 */
public class NeteaseRadioPlaylistLoader implements IPlaylistLoader {
    public static final String ID = "netease_radio";

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public IPlaylistResolver getResolver(String id) {
        return new Resolver(id);
    }

    private static class Resolver extends IPlaylistResolver {
        public Resolver(String id) {
            super(id);
        }

        @Override
        public void run() {
            if (isStopped()) return;
            try {
                long radioId;
                try {
                    radioId = Long.parseLong(getInputId().trim());
                } catch (NumberFormatException e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                List<Music> musics;
                try {
                    musics = new ArrayList<>(NetMusicUtil.getRadioPrograms(radioId));
                } catch (Exception e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                if (musics.isEmpty()) {
                    setFailure("Empty Radio");
                    return;
                }

                // Netease's radio detail API no longer works (code=404 / 400), and the program list carries no radio metadata,
                // so the title uses "radio <id>" and the author/cover fall back to the first program's host and cover —
                // this only affects the title shown in the UI, not the imported program list itself.
                var first = musics.get(0);
                var title = "电台 " + radioId;
                var author = first.getAuthor() == null ? "" : first.getAuthor();
                var icon = first.getImage() == null ? ImageInfo.EMPTY : first.getImage();

                setResult(new PlaylistResult(String.valueOf(radioId), musics.size(), title, author, icon,
                        IPlaylistLoader.createEntriesFromMusics(musics), musics));

            } catch (Exception ex) {
                setFailure(ex);
            }
        }
    }

    @Override
    public Optional<String> autoPasteFromClipboard(String input) {
        MusicLink link = MusicLinks.resolveFor(input, ID);
        if (link != null && link.isPlaylistLike() && ID.equals(link.loaderId()))
            return Optional.of(link.identifier());
        return Optional.empty();
    }

    /** Not listed on the import screen — the single Netease entry already covers radio; pasted radio links still land here. */
    @Override
    public boolean showInSelectScreen() {
        return false;
    }
}
