package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.felnull.imp.client.bilibili.BiliBiliUtil.*;

public class BilibiliPlaylistLoader implements IPlaylistLoader{
    private static final Pattern FID_PATTERN =
            Pattern.compile("[?&]fid=(\\d+)");
    /** The user's space ("UP 主页"); see {@link BiliBiliSpaceUtil#ID_PREFIX} for why the identifier is prefixed. */
    private static final Pattern SPACE_PATTERN =
            Pattern.compile("space\\.bilibili\\.com/(\\d+)(?![0-9])");
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
                // ---- User space ("UP 主页"): every video submission of that user ----
                // ⚠️ Checked first: a space id is prefixed (`space:<mid>`) exactly so that it can never be mistaken
                // for the plain numeric favourites-folder id handled below.
                var spaceMid = BiliBiliSpaceUtil.midOf(getInputId());
                if (spaceMid >= 0) {
                    resolveSpace(spaceMid);
                    return;
                }

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
                // 收藏夹列表自带标题/作者/封面/时长/投稿时间 ⇒ 不逐首 `view`：108 首从 108 个请求降到 6 个（
                // 顺带把投稿时间写进本地标签，行右侧就不会是「未记录」）。
                var media = fetchFavMedia(fid);
                var musics = new ArrayList<Music>();
                for (var m : media) {
                    if (m.bvid() == null || m.bvid().isBlank())
                        continue;
                    if (m.pubtimeMs() > 0L)
                        dev.felnull.imp.client.bilibili.BiliBiliArcTags.getInstance()
                                .rememberPubdate(m.bvid(), m.pubtimeMs());
                    musics.add(new Music(
                            UUID.randomUUID(),
                            m.title(),
                            m.author(),
                            new MusicSource(
                                    IMPMusicMedias.BILIBILI.getName(),
                                    m.bvid(),
                                    m.durationSec() * 1000L
                            ),
                            m.cover().isBlank() ? ImageInfo.EMPTY : new ImageInfo(ImageInfo.ImageType.URL, m.cover()),
                            Minecraft.getInstance().getGameProfile().getId(),
                            System.currentTimeMillis()
                    ));
                }
                dev.felnull.imp.client.bilibili.BiliBiliArcTags.getInstance().flush();
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

        /** "UP 主页" import: all submission pages of that user into one playlist; the space API carries
         * title/author/cover/length, so no per-video {@code fetchVideo}; pages are paced ({@code spacePageDelayMs})
         * and capped ({@code spaceMaxPages}), else risk control answers 412; 充电专属/课堂/直播回放 skipped as unplayable. */
        private void resolveSpace(long mid) throws Exception {
            var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
            var delay = Math.max(0, cfg.spacePageDelayMs);
            var maxPages = cfg.spaceMaxPages <= 0 ? Integer.MAX_VALUE : cfg.spaceMaxPages;

            var musics = new ArrayList<Music>();
            var seen = new java.util.HashSet<String>();
            var upName = "";
            var cover = ImageInfo.EMPTY;
            var total = 0;

            var page = 1;
            while (page <= maxPages) {
                if (isStopped())
                    return;
                var result = BiliBiliSpaceUtil.fetchVideos(mid, page, BiliBiliSpaceUtil.MAX_PAGE_SIZE, "pubdate");
                total = result.total();
                if (result.videos().isEmpty())
                    break;

                for (var v : result.videos()) {
                    if (upName.isEmpty() && !v.author().isBlank())
                        upName = v.author();
                    if (cover == ImageInfo.EMPTY && cfg.loadVideoImage && !v.cover().isBlank())
                        cover = new ImageInfo(ImageInfo.ImageType.URL, v.cover());
                    if (skipFromSpace(v) || !seen.add(v.bvid()))
                        continue;
                    musics.add(new Music(
                            UUID.randomUUID(),
                            v.title(),
                            v.author(),
                            new MusicSource(IMPMusicMedias.BILIBILI.getName(), v.bvid(), v.durationMs()),
                            cfg.loadVideoImage && !v.cover().isBlank()
                                    ? new ImageInfo(ImageInfo.ImageType.URL, v.cover())
                                    : ImageInfo.EMPTY,
                            Minecraft.getInstance().getGameProfile().getId(),
                            System.currentTimeMillis()
                    ));
                }

                if (musics.size() >= total)
                    break;
                page++;
                if (page <= maxPages && delay > 0)
                    Thread.sleep(delay);
            }

            if (musics.isEmpty()) {
                setFailure(new IllegalStateException(
                        dev.felnull.imp.client.music.MusicLoadFailure.text("imp.reason.space.upEmpty")));
                return;
            }

            setResult(new PlaylistResult(BiliBiliSpaceUtil.ID_PREFIX + mid,
                    musics.size(),
                    upName.isBlank() ? ("UP " + mid + " 的投稿") : (upName + " 的投稿"),
                    upName,
                    cover,
                    IPlaylistLoader.createEntriesFromMusics(musics), musics));
        }

        /** Charging-only, paid course (lesson) and live-replay entries do not give playable audio. */
        private static boolean skipFromSpace(BiliBiliSpaceUtil.SpaceVideo v) {
            return v.chargingOnly() || v.lesson() || v.livePlayback();
        }
    }

    @Override
    public Optional<String> autoPasteFromClipboard(String input) {
        var matcher = FID_PATTERN.matcher(input);
        if(matcher.find())
            return Optional.ofNullable(matcher.group(1));
        // User space ("UP 主页"): tried **after** the folder form, so `…/favlist?fid=<n>` still imports that folder
        var space = SPACE_PATTERN.matcher(input);
        if(space.find())
            return Optional.of(BiliBiliSpaceUtil.ID_PREFIX + space.group(1));
        return Optional.empty();
    }
}
