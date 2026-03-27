package dev.felnull.imp.client.music.loader;

import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

public class BiliBiliMusicLoader extends LavaMusicLoader{
    @Override
    protected boolean isSupportMedia(MusicSource source) {
        return IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType());
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        var url = BiliBiliUtil.getURLFromBV(source.getIdentifier());
        if(url == null)throw new RuntimeException("Loading failed.");
        var newSource = new MusicSource(source.getLoaderType(), url, source.getDuration());
        super.tryLoad(newSource);
    }
}
