package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.client.music.netmusic.URLType;
import dev.felnull.imp.client.music.sourceManager.NeteaseSourceManager;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class NeteaseMusicMedia extends LavaPlayerBaseMusicMedia {
    protected NeteaseMusicMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new NeteaseSourceManager());
    }

    @Override
    public Component getMediaName() {
        return Component.translatable("imp.loaderType.neteasecloudmusic");
    }

    @Override
    public Component getEnterText() {
        return Component.translatable("imp.text.enterText.neteasecloudmusic");
    }

    @Override
    public ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID,
                "textures/gui/container/music_manager/loader_types/neteasecloudmusic.png");
    }

    @Override
    public boolean isSearchable() {
        return true;
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if(!IamMusicPlayer.getConfig().netMusicConfig.enableNetease)return null;
        long id;
        try {
            id = Long.parseLong(sourceName);
        } catch (NumberFormatException e) {
            if(URLType.SONG.isMatch(sourceName))
                try {
                    id = Long.parseLong(sourceName);
                } catch (NumberFormatException ignore) {return null;}
            else return null;
        }

        var data = NetMusicUtil.getNetMusicJson(id);
        var infoData = NetMusicUtil.getNetMusicSongData(data);
        if(infoData == null)return null;
        var name = IamMusicPlayer.getConfig().netMusicConfig.withTransName && !infoData.getTransName().isEmpty() ?
                String.format("%s(%s)", infoData.getName(), infoData.getTransName()) :
                infoData.getName();
        var author = String.join("、", infoData.getArtists());
        var source = new MusicSource(IMPMusicMedias.NETEASE_MUSIC.getName(), sourceName, infoData.getDuration());
        var image = new ImageInfo(ImageInfo.ImageType.URL, NetMusicUtil.getIconUrlFromData(data)
                .toString());

        return new MusicMediaResult(source, image, name, author);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof NeteaseSourceManager;
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public List<MusicMediaResult> search(String searchText) {
        return NetMusicUtil.search(searchText);
    }
}
