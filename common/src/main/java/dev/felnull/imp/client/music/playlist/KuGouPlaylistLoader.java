package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.kugou.KuGouUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * The KuGou "playlist / collection" importer ({@code ID = "kugou"}); tracks carry only a {@code "Artist - Title"}
 * {@code filename}, and each {@link Music} stores the **hash** because the audio address expires.
 */
public class KuGouPlaylistLoader implements IPlaylistLoader {
    public static final String ID = "kugou";

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
                long specialId;
                try {
                    specialId = Long.parseLong(getInputId().trim());
                } catch (NumberFormatException e) {
                    setFailure(e);
                    return;
                }

                if (isStopped()) return;
                var playlist = KuGouUtil.getPlaylist(specialId);
                if (playlist == null) {
                    setFailure("Empty Playlist");
                    return;
                }
                if (isStopped()) return;
                if (playlist.songs().isEmpty()) {
                    setFailure("Empty Playlist");
                    return;
                }

                var musics = new ArrayList<Music>();
                for (var s : playlist.songs()) {
                    // Music cover: the track API's `union_cover` (see KuGouUtil.getPlaylist) is the only source of a per-track cover here.
                    var cover = s.coverUrl() == null || s.coverUrl().isEmpty()
                            ? ImageInfo.EMPTY
                            : new ImageInfo(ImageInfo.ImageType.URL, s.coverUrl());
                    musics.add(new Music(
                            UUID.randomUUID(),
                            s.name() == null ? s.hash() : s.name(),
                            s.author() == null ? "" : s.author(),
                            new MusicSource(IMPMusicMedias.KUGOU.getName(), s.hash(), s.durationMs()),
                            cover,
                            Minecraft.getInstance().getGameProfile().getId(),
                            System.currentTimeMillis()
                    ));
                }

                var icon = playlist.coverUrl() == null || playlist.coverUrl().isEmpty()
                        ? ImageInfo.EMPTY
                        : new ImageInfo(ImageInfo.ImageType.URL, playlist.coverUrl());

                setResult(new PlaylistResult(String.valueOf(specialId), musics.size(),
                        playlist.name(), playlist.author(), icon,
                        IPlaylistLoader.createEntriesFromMusics(musics), musics));
            } catch (Exception e) {
                setFailure(e);
            }
        }
    }

    /** Empty on purpose: link shapes live in {@code MusicLinks}, which does the generic recognition instead. */
    @Override
    public Optional<String> autoPasteFromClipboard(String input) {
        return Optional.empty();
    }
}
