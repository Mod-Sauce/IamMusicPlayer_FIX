package dev.felnull.imp.client.music.link;

import org.jetbrains.annotations.NotNull;

/** {@code platform} = music media name registered in {@code IMPMusicMedias} (netease / bilibili / qq_music), usable as
 * {@code getMedia(platform)}; one platform may have several loaders, so a more precise {@code loaderId} is also given —
 * {@code identifier} = what the loader needs; {@link dev.felnull.imp.client.music.playlist.IPlaylistLoader#getID()}. */
public record MusicLink(@NotNull String platform, @NotNull String loaderId, @NotNull Kind kind,
                        @NotNull String identifier) {

    /**
     * Kind of the parsed link; music and playlist/album/radio go through two different channels in IMP.
     */
    public enum Kind {
        /** Single music (goes through the "add music" single channel) */
        SONG,
        /** Video (Bilibili: essentially also a music) */
        VIDEO,
        /** Playlist */
        PLAYLIST,
        /** Album (Netease albums and playlists share the same fetch API, so they go through the playlist channel) */
        ALBUM,
        /** Radio / podcast (the "radio itself": fetched via byradio to get the whole program list) */
        RADIO,
        /** Single program (a different resource from radio: program id ≠ radio id, fetched via program/detail) */
        PROGRAM
    }

    /**
     * Whether this kind belongs to the "playlist import" channel (IMP playlist loaders only accept playlist/album/radio/program).
     */
    public boolean isPlaylistLike() {
        return kind == Kind.PLAYLIST || kind == Kind.ALBUM || kind == Kind.RADIO || kind == Kind.PROGRAM;
    }

    @Override
    public String toString() {
        return platform + "/" + kind.name().toLowerCase() + "/" + loaderId + ":" + identifier;
    }
}
