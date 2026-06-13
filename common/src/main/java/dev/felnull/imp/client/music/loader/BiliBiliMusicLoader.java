package dev.felnull.imp.client.music.loader;

import dev.felnull.imp.IamMusicPlayer;
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
        if(!IamMusicPlayer.getConfig().bilibiliConfig.enableBilibili)return;
        super.tryLoad(source);
    }

    @Override
    protected String wrappedIdentifier(MusicSource source) {
        return "bilibili:" + source.getIdentifier();
    }
}
