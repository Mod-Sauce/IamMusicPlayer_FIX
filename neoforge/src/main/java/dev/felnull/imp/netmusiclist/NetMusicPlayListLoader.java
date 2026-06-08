package dev.felnull.imp.netmusiclist;

import com.gly091020.netMusicListNeoforge.NetMusicList;
import com.gly091020.netMusicListNeoforge.item.NetMusicListItem;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.client.music.netmusic.URLType;
import dev.felnull.imp.client.music.playlist.IPlaylistLoader;
import dev.felnull.imp.client.music.playlist.IPlaylistResolver;
import dev.felnull.imp.client.music.playlist.PlaylistResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

public class NetMusicPlayListLoader implements IPlaylistLoader {
    @Override
    public String getID() {
        return "netMusicPlaylist";
    }

    @Override
    public IPlaylistResolver getResolver(String id) {
        return new Resolver();
    }

    private static class Resolver extends IPlaylistResolver {
        public Resolver() {
            super("net_music_play_list");
        }

        @Override
        public void run() {
            var player = Minecraft.getInstance().player;
            if(player == null){
                setFailure("No player");
                return;
            }
            var itemStack = player.getMainHandItem();
            if(!itemStack.is(NetMusicList.MUSIC_LIST_ITEM)){
                setFailure("No playlist item");
                return;
            }
            var data = NetMusicListItem.getComponent(itemStack);
            if(data == null){
                setFailure("No data");
                return;
            }

            List<Music> musics;
            try {
                musics = NetMusicUtil.getMusicsData(data.songInfos().stream()
                        .filter(info -> URLType.SONG.isMatch(info.songUrl))
                        .map(info -> Long.parseLong(Objects.requireNonNull(URLType.SONG.getMatch(info.songUrl)))
                        ).toList()
                );
            } catch (Exception e) {
                setFailure(e);
                return;
            }

            setResult(new PlaylistResult(
                    "net_music_play_list",
                    musics.size(),
                    itemStack.getHoverName().getString(),
                    player.getDisplayName().getString(),
                    ImageInfo.EMPTY,
                    IPlaylistLoader.createEntriesFromMusics(musics),
                    musics
            ));
        }
    }
}
