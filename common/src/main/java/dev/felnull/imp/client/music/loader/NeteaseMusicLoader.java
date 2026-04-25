package dev.felnull.imp.client.music.loader;

import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

public class NeteaseMusicLoader extends LavaMusicLoader{
    @Override
    protected boolean isSupportMedia(MusicSource source) {
        return IMPMusicMedias.NETEASE_MUSIC.getName().equals(source.getLoaderType());
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        long id;
        try{
            id = Long.parseLong(source.getIdentifier());
        } catch (NumberFormatException e) {
            throw new RuntimeException("数据错误", e);
        }
        var url = NetMusicUtil.getNetMusicUrl(id);
        if(url == null)throw new RuntimeException("加载失败");
        var newSource = new MusicSource(source.getLoaderType(), url.toString(), source.getDuration());
        super.tryLoad(newSource);
    }
}
