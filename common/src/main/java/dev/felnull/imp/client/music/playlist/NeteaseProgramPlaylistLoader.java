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
 * Netease "single program" loader ({@code program?id=} / {@code dj?id=}, via {@code api/dj/program/detail?id=}) — unlike {@link NeteaseRadioPlaylistLoader} ({@code api/dj/program/byradio?radioId=}); program ids and radio ids are different resources and must not be mixed.
 */
public class NeteaseProgramPlaylistLoader implements IPlaylistLoader {
    public static final String ID = "netease_program";

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
                long programId;
                try {
                    programId = Long.parseLong(getInputId().trim());
                } catch (NumberFormatException e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                Music music;
                try {
                    music = NetMusicUtil.getProgram(programId);
                } catch (Exception e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;

                if (music == null) {
                    setFailure("Empty Program");
                    return;
                }

                List<Music> musics = new ArrayList<>();
                musics.add(music);

                var icon = music.getImage() == null ? ImageInfo.EMPTY : music.getImage();
                var author = music.getAuthor() == null ? "" : music.getAuthor();

                setResult(new PlaylistResult(String.valueOf(programId), musics.size(), music.getName(), author,
                        icon, IPlaylistLoader.createEntriesFromMusics(musics), musics));

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

    /**
     * Not listed on the "import playlist" screen — as {@link NeteaseRadioPlaylistLoader}: Netease keeps a single entry there, and a pasted program link still lands here precisely.
     */
    @Override
    public boolean showInSelectScreen() {
        return false;
    }
}
