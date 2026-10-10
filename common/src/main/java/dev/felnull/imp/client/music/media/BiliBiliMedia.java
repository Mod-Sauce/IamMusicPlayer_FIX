package dev.felnull.imp.client.music.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.sourceManager.BilibiliHttpAudioSourceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Pattern;

public class BiliBiliMedia extends LavaPlayerBaseMusicMedia {
    // Prefix case is unrestricted; the 10-character body is case-sensitive and must be fed to the API as-is
    private static final Pattern PATTERN = Pattern.compile("((?i:BV))([0-9A-Za-z]{10})(?![0-9A-Za-z])");
    // AV number (the old-style numbering): av + digits
    private static final Pattern AV_PATTERN = Pattern.compile("((?i:av))(\\d+)(?![0-9])");
    protected BiliBiliMedia(String name) {
        super(name);
    }

    @Override
    public void registerSourceManager(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new BilibiliHttpAudioSourceManager());
    }

    @Override
    public Component getMediaName() {
        return Component.translatable("imp.loaderType.bilibili");
    }

    @Override
    public Component getEnterText() {
        return Component.translatable("imp.text.enterText.bilibili");
    }

    @Override
    public ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID,
                "textures/gui/container/music_manager/loader_types/bilibili.png");
    }

    @Override
    public boolean isSearchable() {
        return false;
    }

    @Override
    public MusicMediaResult load(String sourceName) throws Exception {
        if(!IamMusicPlayer.getConfig().bilibiliConfig.enableBilibili)return null;
        // Take only the BV number itself: separators such as "-" cut off the copied tail, and a following alphanumeric character means a longer
        // number (then no match). ⚠️ The body case must be kept as-is (BV numbers are case-sensitive); only the prefix is normalized to BV.
        // The part (internal form BV…-p2) is carried along with the number as the suffix -p<N>: the player picks the part from it.
        var page = BiliBiliUtil.pageOf(sourceName);
        var m = PATTERN.matcher(sourceName);
        if(m.find()){
            sourceName = "BV" + m.group(2);
        } else {
            // AV number: convert it to a BV number via the API first.
            // ⚠️ This must be an `else`: a BV number's body may **happen to** contain "av"+digits (e.g. BV1av4y1Y7pJ),
            // and matching the bare AV after a BV hit would play the wrong video.
            var av = AV_PATTERN.matcher(sourceName);
            if(av.find()){
                long aid;
                try {
                    aid = Long.parseLong(av.group(2));
                } catch (NumberFormatException e) {
                    return null;   // a number long enough to overflow is not a valid aid
                }
                var bvid = BiliBiliUtil.fetchBvidByAid(aid);
                if(bvid == null)return null;
                sourceName = bvid;
            }
        }
        if (page > 1)
            sourceName = sourceName + "-p" + page;
        return BiliBiliUtil.getMusicMediaResultFromBV(sourceName);
    }

    @Override
    public boolean match(AudioTrack track) {
        return track.getSourceManager() instanceof BilibiliHttpAudioSourceManager;
    }

    @Override
    public int priority() {
        return 2;
    }
}
