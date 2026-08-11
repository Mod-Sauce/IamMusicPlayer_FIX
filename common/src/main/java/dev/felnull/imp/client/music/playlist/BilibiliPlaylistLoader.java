package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.felnull.imp.client.bilibili.BiliBiliUtil.*;

public class BilibiliPlaylistLoader implements IPlaylistLoader{
    private static final Pattern FID_PATTERN =
            Pattern.compile("[?&]fid=(\\d+)");
    @Override
    public String getID() {
        return "bilibili";
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
                long fid;
                try {
                    fid = Long.parseLong(getInputId());
                } catch (NumberFormatException e) {
                    var matcher = FID_PATTERN.matcher(getInputId());
                    if(matcher.find()){
                        try {
                            fid = Long.parseLong(Objects.requireNonNull(matcher.group(1)));
                        } catch (NumberFormatException | NullPointerException e1) {
                            setFailure(e1);
                            return;
                        }
                    }else {
                        setFailure(e);
                        return;
                    }
                }
                var info = fetchFavInfo(fid);
                var bvids = fetchFavBvids(fid);
                var musics = new ArrayList<Music>();
                for (String bvid : bvids) {
                    var v = fetchVideo(bvid);
                    if (v == null) continue;
                    musics.add(new Music(
                            UUID.randomUUID(),
                            v.title(),
                            v.author(),
                            new MusicSource(
                                    IMPMusicMedias.BILIBILI.getName(),
                                    bvid,
                                    v.duration() * 1000L
                            ),
                            new ImageInfo(ImageInfo.ImageType.URL, v.cover()),
                            Minecraft.getInstance().getUser().getGameProfile().getId(),
                            System.currentTimeMillis()
                    ));
                }
                setResult(new PlaylistResult(String.valueOf(fid),
                        musics.size(),
                        info.title(),
                        info.author(),
                        new ImageInfo(ImageInfo.ImageType.URL, info.cover()),
                        IPlaylistLoader.createEntriesFromMusics(musics), musics));
            } catch (Exception e) {
                setFailure(e);
            }
        }
    }

    @Override
    public Optional<String> autoPasteFromClipboard(String input) {
        var matcher = FID_PATTERN.matcher(input);
        if(matcher.find())
            return Optional.ofNullable(matcher.group(1));
        return Optional.empty();
    }
}
