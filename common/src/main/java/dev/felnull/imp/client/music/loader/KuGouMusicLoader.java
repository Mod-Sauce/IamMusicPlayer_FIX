package dev.felnull.imp.client.music.loader;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.NotNull;

/**
 * The LavaPlayer loader for KuGou: wraps a {@code MusicSource} into {@code "kugou:<hash>"} and hands it to {@code KuGouAudioSourceManager}, which resolves the current audio address once **before playback** (KuGou addresses expire).
 */
public class KuGouMusicLoader extends LavaMusicLoader {
    @Override
    protected boolean isSupportMedia(MusicSource source) {
        return IMPMusicMedias.KUGOU.getName().equals(source.getLoaderType());
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void tryLoad(@NotNull MusicSource source) throws Exception {
        if (!IamMusicPlayer.getConfig().kugouConfig.enableKugou)
            return;
        super.tryLoad(source);
    }

    @Override
    protected String wrappedIdentifier(MusicSource source) {
        return "kugou:" + source.getIdentifier();
    }
}
