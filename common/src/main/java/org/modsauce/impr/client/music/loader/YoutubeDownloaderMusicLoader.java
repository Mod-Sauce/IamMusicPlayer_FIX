package org.modsauce.impr.client.music.loader;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.music.media.IMPMusicMedias;
import org.modsauce.impr.client.util.YoutubeUtil;
import org.modsauce.impr.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

public class YoutubeDownloaderMusicLoader extends LavaMusicLoader {
    @Override
    protected boolean isSupportMedia(MusicSource source) {
        return IMPMusicMedias.YOUTUBE.getName().equals(source.getLoaderType());
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        if (!IamMusicPlayer.getConfig().useYoutubeDownloader)
            throw new RuntimeException("YoutubeDownloader is disabled in config");
        super.tryLoad(source);
    }

    @Override
    protected String wrappedIdentifier(MusicSource source) throws Exception {
        if (source.isLive())
            return null;

        var url = YoutubeUtil.getYoutubeRawURL(source.getIdentifier());
        if (url == null)
            throw new RuntimeException("Failed to get Youtube URL");
        return url;
    }

    @Override
    public int priority() {
        return 1;
    }
}
