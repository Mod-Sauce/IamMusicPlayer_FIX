package dev.felnull.imp.client.music.playlist;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.util.YoutubeUtil;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YoutubePlayListLoader implements IPlaylistLoader{
    @Override
    public String getID() {
        return "youtube";
    }

    @Override
    public IPlaylistResolver getResolver(String id) {
        return new Resolver(id);
    }

    private static class Resolver extends IPlaylistResolver{
        public Resolver(String id) {
            super(id);
        }

        @Override
        public void run() {
            try {
                var pl = LavaPlayerManager.getInstance().loadTracks(getInputId());

                if (pl.getLeft() == null)
                    throw new IllegalStateException("Not PlayList");

                String name = pl.getLeft().getName();
                List<Music> musics = new ArrayList<>();

                for (AudioTrack track : pl.getRight()) {
                    if (track.getInfo().isStream) continue;

                    var ret = IMPMusicMedias.YOUTUBE.createResult(track);

                    var music = new Music(
                            UUID.randomUUID(),
                            ret.name(),
                            ret.author(),
                            ret.source(),
                            ret.imageInfo(),
                            Minecraft.getInstance().player.getGameProfile().getId(),
                            System.currentTimeMillis()
                    );

                    musics.add(music);
                }

                String author = "";
                var pid = YoutubeUtil.getPlayListID(getInputId());
                if (pid != null) {
                    var ypl = YoutubeUtil.getYoutubePlayList(pid);
                    author = ypl.details().author();
                }

                setResult(new PlaylistResult(getInputId(), musics.size(), name, author, ImageInfo.EMPTY, IPlaylistLoader.createEntriesFromMusics(musics), musics));

            } catch (Exception e) {
                setFailure("Failed to load playlist: " + getInputId());
            }
        }
    }
}
