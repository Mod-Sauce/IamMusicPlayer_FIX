package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.webdav.WebDAVClientProfileSync;
import dev.felnull.imp.client.webdav.WebDAVUtil;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class WebDAVMusicMedia implements MusicMedia {
    private static final Component ENTER_TEXT = Component.translatable("imp.text.enterText.webdav");
    private static final LocalAudioSourceManager LOCAL_MANAGER = new LocalAudioSourceManager();
    private final String name;
    private final ResourceLocation icon;

    public WebDAVMusicMedia(String name) {
        this.name = name;
        this.icon = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/music_manager/loader_types/" + name + ".png");
    }

    @Override
    public Component getMediaName() {
        return Component.translatable("imp.loaderType." + name);
    }

    @Override
    public Component getEnterText() {
        return ENTER_TEXT;
    }

    @Override
    public ResourceLocation getIcon() {
        return icon;
    }

    @Override
    public boolean isSearchable() {
        return true;
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if (!IamMusicPlayer.getConfig().webDAVConfig.enableWebDAV) return null;
        WebDAVClientProfileSync.sync();
        var normalized = WebDAVUtil.normalizeRelativePath(sourceName);
        var file = WebDAVUtil.downloadToTempFile(normalized);
        if (file == null) return null;
        var result = LOCAL_MANAGER.loadItem(LavaPlayerManager.getInstance().getAudioPlayerManager(), new AudioReference(file.toString(), ""));
        if (!(result instanceof AudioTrack track) || track.getInfo().isStream) return null;
        var source = new MusicSource(name, normalized, track.getDuration());
        return new MusicMediaResult(source, null, track.getInfo().title, track.getInfo().author);
    }

    @Override
    public List<MusicMediaResult> search(String searchText) {
        WebDAVClientProfileSync.sync();
        return WebDAVUtil.list(searchText);
    }
}
