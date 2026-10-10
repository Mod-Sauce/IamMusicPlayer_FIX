package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.gui.components.BiliBiliPartsListWidget;
import dev.felnull.imp.client.gui.components.ClickArea;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The parts / video-info page of one Bilibili video: cover, title, the facts from the same {@code view} response, the
 * description, and one row per part (a click selects, the Play key previews). Nothing here writes to the playlist — a
 * multi-part video stays one entry.
 */
public class BiliBiliPartsMMMonitor extends MusicManagerMonitor {
    private static final Component RETRY_TEXT = Component.translatable("imp.button.retry");
    private static final Component LIST_TITLE = Component.translatable("imp.fixedList.bilibiliParts");
    private static final Component PLAYBACK_CONTROL_TEXT = Component.translatable("imp.button.playbackControl");
    private static final Component NOT_BILIBILI_TEXT = Component.translatable("imp.text.bilibiliPartsNotBili");
    private static final Component LOADING_TEXT = Component.translatable("imp.text.bilibiliPartsLoading");
    private static final Component FAILED_TEXT = Component.translatable("imp.text.bilibiliPartsFailed");
    /** Bilibili itself says the submission is gone (`-404`) — retrying can never help, so the line says what happened. */
    private static final Component GONE_TEXT = Component.translatable("imp.text.bilibiliPartsGone");
    /** Risk control (`-352` / HTTP 412) refused the lookup — that one **is** worth retrying a bit later. */
    private static final Component RISK_TEXT = Component.translatable("imp.text.bilibiliPartsRisk");
    /** Prefix of the description's first line. */
    private static final Component DESC_PREFIX = Component.translatable("imp.text.biliDesc", "");
    /** A BV/AV id inside the description: drawn as a link and clickable. */
    private static final java.util.regex.Pattern VIDEO_ID =
            java.util.regex.Pattern.compile("(?i)(BV[0-9A-Za-z]{10}|av\\d{1,12})");
    private static final int LINK_COLOR = 0xFF2F6FE0;
    private static final int LINK_HOVER_COLOR = 0xFF17458F;
    /** Reusable click areas, one per visible link (positioned every tick — no widget is created per video). */
    private static final int LINK_SLOTS = 24;
    private static final Component COPY_LINK_TEXT = Component.translatable("imp.button.copyLink");
    private static final Component COPIED_TEXT = Component.translatable("imp.text.biliLinkCopied");

    // ---- layout (a page is 370×199 wide/tall; the header strip takes the first 10 px) ----
    private static final int COVER_X = 6;
    private static final int COVER_Y = 14;
    private static final int COVER_SIZE = 48;
    private static final int TEXT_X = COVER_X + COVER_SIZE + 6;
    private static final int TITLE_Y = 14;
    private static final int STATS_Y = 27;
    private static final int META_Y = 39;
    /** The description line, the last of the fact lines (it starts right of the cover, which ends at y = 62). */
    private static final int DESC_Y = 51;
    /** How many rows the description may use (2·9 px still ends above the part list at y = 72). */
    private static final int DESC_LINES = 2;
    private static final int DESC_LINE_H = 9;
    private static final int LIST_X = 6;
    private static final int LIST_Y = 72;
    private static final int LIST_W = 358;
    private static final int LIST_H = 100;
    private static final int LIST_ROWS = 5;
    private static final int PLAY_X = 6;
    private static final int PLAY_Y = 176;
    private static final int PLAY_SIZE = 20;
    private static final int SELECTED_X = 30;
    private static final int SELECTED_Y = 182;
    private static final int COPY_X = 232;
    private static final int COPY_Y = 182;
    private static final int COPY_W = 64;
    private static final int COPY_H = 9;
    private static final int RETRY_X = 300;
    private static final int RETRY_Y = 182;
    private static final int RETRY_W = 64;
    private static final int RETRY_H = 9;
    /** How long the "Copy link" key says "Copied" after a click. */
    private static final long COPIED_SHOW_MS = 1500L;
    /** Grey for the fact lines: readable on the light page, quieter than the title. */
    private static final int FACT_COLOR = 0xFF404040;

    private BiliBiliPartsListWidget partsList;
    private SmartButton playButton;
    private SmartButton retryButton;
    private SmartButton copyLinkButton;

    /** The video this page is about. ⚠️ Empty does **not** mean "another song" — see {@link #tick()}. */
    private String bvid = "";
    /** Title to fall back on before the API facts are known (the playlist entry's own name). */
    private String entryTitle = "";
    /** The playlist entry's cover, used while no per-part first frame / video cover is known. */
    private ImageInfo entryImage = ImageInfo.EMPTY;
    /** The playlist entry's own length — the last resort when a single-part video's length is not known. */
    private long entryDuration;
    /** The part list; {@code null} = not looked up yet, empty = "looked up, single part". */
    private volatile List<BiliBiliArcTags.Part> parts;
    /**
     * The video-level facts. ⚠️ **volatile on purpose**: written by the lookup thread and read every frame, so without it the JIT may keep the stale empty value.
     */
    private volatile BiliBiliArcTags.Info info = BiliBiliArcTags.Info.EMPTY;
    /** Which part's details are shown (1-based). */
    private int selectedPage = 1;
    private volatile boolean loading;
    private volatile boolean failed;
    /** The bvid the running (or already finished) lookup belongs to — one video is looked up **once**. */
    private String lookedUpFor = "";
    /** Signature of what the rows were built from, so a rebuild happens only when something really changed. */
    private String rowSignature = "";
    /** When "Copy link" was last used (0 = never); drives the short "Copied" confirmation on the key. */
    private long copiedAt;

    /** Reusable clickable widgets for the description links (see {@link #layoutLinks()}). */
    private final List<ClickArea> linkSlots = new ArrayList<>();

    public BiliBiliPartsMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.partsList = addRenderWidget(new BiliBiliPartsListWidget(getStartX() + LIST_X, getStartY() + LIST_Y,
                LIST_W, LIST_H, LIST_TITLE, LIST_ROWS, List.of(), this::selectPart, null));

        // Play key: the same control and the same two icons the music pages use, so a preview behaves and looks alike
        this.playButton = addRenderWidget(new SmartButton(getStartX() + PLAY_X, getStartY() + PLAY_Y, PLAY_SIZE, PLAY_SIZE,
                PLAYBACK_CONTROL_TEXT, n -> {
            if (getScreen().isMusicPlaying() || getScreen().isMusicLoading()) {
                getScreen().stopMusic();
            } else {
                previewSelected();
            }
        }));
        this.playButton.setHideText(true);
        this.playButton.setIcon(WIDGETS_TEXTURE, 0, 123, 11, 11);

        this.retryButton = addRenderWidget(new SmartButton(getStartX() + RETRY_X, getStartY() + RETRY_Y, RETRY_W, RETRY_H,
                RETRY_TEXT, n -> {
            parts = null;
            failed = false;
            lookedUpFor = "";
            rowSignature = "";
        }));
        this.retryButton.visible = false;

        // What the page can *do* with a video: hand the address over (the point of a video-info page — the user asked for
        // this page to be worth opening for a single-part video too, and "where is that video" is its most common use).
        this.copyLinkButton = addRenderWidget(new SmartButton(getStartX() + COPY_X, getStartY() + COPY_Y, COPY_W, COPY_H,
                COPY_LINK_TEXT, n -> {
            if (!bvid.isEmpty()) {
                mc.keyboardHandler.setClipboard(BiliBiliUtil.videoUrl(bvid, selectedPage));
                copiedAt = System.currentTimeMillis();
            }
        }));
        this.copyLinkButton.visible = false;

        // Description links: a fixed pool of invisible click areas, repositioned by layoutLinks() (the page paints the text)
        // init() runs again on every re-entry and Monitor.init() drops the old widgets, so the pool must start empty —
        // otherwise the first slots point at widgets nobody registered and the links stop reacting (n=24→48→72 in the log).
        linkSlots.clear();
        for (int i = 0; i < LINK_SLOTS; i++) {
            var slot = addRenderWidget(new ClickArea(0, 0, 1, 1, Component.empty(),
                    area -> openLink(area.getMessage().getString())));
            slot.visible = false;
            slot.active = false;
            linkSlots.add(slot);
        }
    }

    @Override
    public void depose() {
        super.depose();
        partsList = null;
        playButton = null;
        retryButton = null;
        copyLinkButton = null;
        // Leaving the page stops nothing on purpose: the preview is the manager's own player and the user may well want
        // to walk back to the playlist while listening — the Play key here (and on the music pages) stops it.
    }

    @Override
    public void tick() {
        super.tick();
        var music = getSelectedMusic();
        var resolved = music != null && isBiliBili(music)
                ? BiliBiliUtil.stripPage(music.getSource().getIdentifier()) : null;
        if (resolved != null && !resolved.isBlank() && !resolved.equals(bvid)) {
            // **Really another video** (a page instance belongs to one selection, so start over)
            bvid = resolved;
            entryTitle = music.getName();
            entryImage = music.getImage() == null ? ImageInfo.EMPTY : music.getImage();
            entryDuration = music.getSource().getDuration();
            selectedPage = Math.max(1, BiliBiliUtil.pageOf(music.getSource().getIdentifier()));
            parts = null;
            info = BiliBiliArcTags.Info.EMPTY;
            failed = false;
            loading = false;
            lookedUpFor = "";
            rowSignature = "";
            coverKey = null;
        }
        // `resolved == null` means "the sync snapshot is momentarily empty" or "not a Bilibili music" — it must **not** clear
        // anything, or the page drops its list and re-requests the parts on every broadcast.

        if (bvid.isEmpty()) {
            if (partsList != null)
                partsList.setEntryList(List.of());
            hideButtons();
            return;
        }

        // Local knowledge shows up at once; the request goes out when the parts are unknown **or the facts are missing** —
        // a part list cached by an older version carries no `info`, and gating on `parts == null` alone left those videos empty.
        adoptFromCache();
        // "Has a title" is not "has been looked at": entries written by older versions carry only the fields that existed
        // then, so they need one lookup each. checkedAt == 0 says exactly that; afterwards the entry carries the mark.
        if ((parts == null || info.isEmpty() || BiliBiliParts.checkedAt(bvid) == 0L) && !loading && !failed
                && !bvid.equals(lookedUpFor))
            startLookup();

        if (retryButton != null)
            retryButton.visible = failed;
        updateCopyLinkButton();
        if (playButton != null) {
            var playing = getScreen().isMusicPlaying() || getScreen().isMusicLoading();
            // A single-part video is playable too (the whole video) — the list just has nothing to list
            playButton.active = parts != null;
            playButton.setIcon(WIDGETS_TEXTURE, 0, playing ? 134 : 123, 11, 11);
        }

        // Rows are rebuilt on the client thread only, and only when the shown content really changed
        var signature = rowsSignature(bvid, parts, selectedPage, playingPage(), thumbnailsOn());
        if (!signature.equals(rowSignature)) {
            rowSignature = signature;
            if (partsList != null)
                partsList.setEntryList(rows(parts, entryTitle, selectedPage, playingPage(), thumbnailsOn()));
        }
        // A single-part video draws **no** list frame: there is nothing to list, and the description uses that space instead.
        if (partsList != null)
            partsList.visible = isMultiPart(parts);
        layoutLinks();
    }

    /**
     * The rows to show; a one-part video gets an empty list on purpose — a lone 「P1」 row looked wrong, so this is a
     * video-info page first and becomes a part list only when there is something to list.
     */
    private static List<BiliBiliPartsListWidget.Row> rows(@Nullable List<BiliBiliArcTags.Part> list, String title,
                                                           int selected, int playing, boolean thumbs) {
        return list != null && list.size() > 1 ? buildRows(list, title, selected, playing, thumbs) : List.of();
    }

    /** Whether this video really has several parts (a 1-element list is the "looked up, single part" marker). */
    static boolean isMultiPart(@Nullable List<BiliBiliArcTags.Part> list) {
        return list != null && list.size() > 1;
    }

    private void hideButtons() {
        if (retryButton != null)
            retryButton.visible = false;
        if (playButton != null) {
            playButton.visible = false;
            playButton.active = false;
        }
        if (copyLinkButton != null)
            copyLinkButton.visible = false;
    }

    /** Shows the "Copy link" key for a Bilibili video, and lets it report the copy for a moment. */
    private void updateCopyLinkButton() {
        if (copyLinkButton == null)
            return;
        copyLinkButton.visible = !bvid.isEmpty();
        copyLinkButton.setMessage(copiedAt > 0L && System.currentTimeMillis() - copiedAt < COPIED_SHOW_MS
                ? COPIED_TEXT : COPY_LINK_TEXT);
    }

    /** Takes over anything the local caches already know (no request; the reads are cheap map lookups). */
    private void adoptFromCache() {
        if (parts == null) {
            var known = BiliBiliParts.known(bvid);
            if (!known.isEmpty())
                parts = known;
        }
        if (info.isEmpty()) {
            var knownInfo = BiliBiliParts.knownInfo(bvid);
            if (!knownInfo.isEmpty())
                info = knownInfo;
        }
    }

    /** Starts the one-shot part lookup (a cache hit means no request at all). Never blocks the client thread. */
    private void startLookup() {
        var id = bvid;
        if (id.isEmpty())
            return;
        lookedUpFor = id;
        loading = true;
        var thread = new Thread(() -> {
            List<BiliBiliArcTags.Part> got = null;
            var gotInfo = BiliBiliArcTags.Info.EMPTY;
            try {
                var known = BiliBiliParts.known(id);
                var knownInfo = BiliBiliParts.knownInfo(id);
                if (!known.isEmpty() && !knownInfo.isEmpty()) {
                    got = known;
                    // The server keeps its own copy for the "counts as one entry" rule: re-report it after a server restart
                    BiliBiliParts.report(id, known);
                    gotInfo = knownInfo;
                } else {
                    // Parts and/or the video facts are missing: one `view` request fills both (fetch() remembers the
                    // info too, see BiliBiliParts:198) — this is what back-fills a part list cached without info.
                    got = BiliBiliParts.fetch(id);
                    gotInfo = BiliBiliParts.knownInfo(id);
                    if ((got == null || got.isEmpty()) && !known.isEmpty()) {
                        // The request failed **or** it answered "single part" (fetch() reports that as an empty list), so the
                        // part list we have must not be thrown away: an empty list loses the "looked up" marker and reads as "still looking".
                        got = known;
                        BiliBiliParts.report(id, known);
                    }
                }
            } catch (Throwable ignored) {
                got = null;
            }
            if (got == null) {
                failed = true;
            } else {
                parts = got;
                if (!gotInfo.isEmpty())
                    info = gotInfo;
                // The write happens on the cache's own background writer (see BiliBiliArcTags#flush): neither the UI
                // nor this lookup thread waits for the disk
                BiliBiliArcTags.getInstance().flush();
            }
            loading = false;
        }, "IMP-BiliBili-Parts-Page");
        thread.setDaemon(true);
        thread.start();
    }

    /** A row was clicked: show that part's details (playing stays an explicit action — the Play key). */
    private void selectPart(BiliBiliPartsListWidget.Row row) {
        selectedPage = Math.max(1, row.page());
        rowSignature = "";
    }

    /** Plays the selected part (or the whole video when it has a single part) through the manager's preview path. */
    private void previewSelected() {
        if (bvid.isEmpty())
            return;
        var list = parts;
        var multi = isMultiPart(list);
        var index = selectedPage - 1;
        var part = multi && index >= 0 && index < list.size() ? list.get(index) : null;
        var duration = part != null ? part.durationMs() : singleDuration(list, info);
        var identifier = multi && selectedPage > 1 ? bvid + "-p" + selectedPage : bvid;
        getScreen().playMusic(new MusicSource(IMPMusicMedias.BILIBILI.getName(), identifier, Math.max(0L, duration)), 0L);
    }

    /**
     * The length to use when no part is selected: the part list's, else the video facts' (the in-world panel
     * never runs {@code tick()}, so its entry length stays 0), else the entry's — never 0, since the upstream player
     * reads "position &gt;= duration" as finished and destroys the entry on the first tick.
     */
    private long singleDuration(@Nullable List<BiliBiliArcTags.Part> list, BiliBiliArcTags.Info facts) {
        if (list != null && !list.isEmpty() && list.get(0).durationMs() > 0L)
            return list.get(0).durationMs();
        if (facts != null && facts.durationMs() > 0L)
            return facts.durationMs();
        return entryDuration;
    }

    /** Which part the manager is previewing right now (0 = none, or something else entirely). */
    private int playingPage() {
        if (!getScreen().isMusicPlaying() && !getScreen().isMusicLoading())
            return 0;
        var entry = getScreen().getMusicPlayer();
        return entry == null || entry.getSource() == null ? 0 : pageOf(bvid, entry.getSource().getIdentifier());
    }

    /** The part number of {@code identifier} when it belongs to {@code bvid}, else 0. */
    private static int pageOf(String bvid, String identifier) {
        if (bvid == null || bvid.isEmpty() || identifier == null || identifier.isEmpty())
            return 0;
        return bvid.equals(BiliBiliUtil.stripPage(identifier)) ? BiliBiliUtil.pageOf(identifier) : 0;
    }

    /** Whether per-part first frames are used as covers (the page follows this config). */
    private static boolean thumbnailsOn() {
        try {
            var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
            return cfg.usePageFirstFrame && cfg.loadVideoImage;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String rowsSignature(@Nullable List<BiliBiliArcTags.Part> list, int selected, int playing, boolean thumbs) {
        return (list == null ? "?" : String.valueOf(list.size())) + "/" + selected + "/" + playing + "/" + thumbs;
    }

    /** Same, but including the video: two different videos can have the same part count (the rows must still rebuild). */
    private static String rowsSignature(String bvid, @Nullable List<BiliBiliArcTags.Part> list, int selected, int playing,
                                        boolean thumbs) {
        return bvid + "|" + rowsSignature(list, selected, playing, thumbs);
    }

    /** The rows of the list ({@code null} = still unknown ⇒ an empty list). */
    private static List<BiliBiliPartsListWidget.Row> buildRows(@Nullable List<BiliBiliArcTags.Part> list, String title,
                                                               int selected, int playing, boolean thumbs) {
        if (list == null || list.isEmpty())
            return List.of();
        var rows = new ArrayList<BiliBiliPartsListWidget.Row>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var page = i + 1;
            var part = list.get(i);
            rows.add(BiliBiliPartsListWidget.Row.of(page, partLabel(title, part), part.durationMs(),
                    page == selected, page == playing, thumbs ? firstFrame(part) : null));
        }
        return rows;
    }

    /** The part's first frame as an image, or {@code null} when the API gave none. */
    @Nullable
    private static ImageInfo firstFrame(@Nullable BiliBiliArcTags.Part part) {
        var url = part == null || part.firstFrame() == null ? "" : part.firstFrame().trim();
        if (url.isEmpty())
            return null;
        if (url.startsWith("http://"))
            url = url.replaceFirst("^http://", "https://");
        return new ImageInfo(ImageInfo.ImageType.URL, url);
    }

    /** What a row shows next to its {@code P<n>} marker: the part's name, or the video title when the part has none / repeats it. */
    private static Component partLabel(String title, @Nullable BiliBiliArcTags.Part part) {
        var name = part == null || part.name() == null ? "" : part.name().strip();
        var videoTitle = title == null ? "" : title.strip();
        return Component.literal(name.isEmpty() || name.equals(videoTitle) ? videoTitle : name);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * The cover at the top: the selected part's first frame when the config wants page first frames, else the entry's,
     * else the API cover. A cold URL answers with an animated "loading" GIF, so this only picks what we want.
     */
    private ImageInfo coverImage(@Nullable List<BiliBiliArcTags.Part> list, int selected,
                                 BiliBiliArcTags.Info facts, ImageInfo fallback) {
        ImageInfo chosen = null;
        if (thumbnailsOn() && list != null && selected >= 1 && selected <= list.size()) {
            chosen = firstFrame(list.get(selected - 1));
        }
        if (chosen == null && fallback != null && !fallback.isEmpty())
            chosen = fallback;
        if (chosen == null && facts != null && !facts.cover().isBlank())
            chosen = new ImageInfo(ImageInfo.ImageType.URL, facts.cover());
        if (chosen == null)
            return ImageInfo.EMPTY;
        // One ImageInfo per distinct URL: the renderer keys its texture cache by the identifier, and re-allocating the
        // same ImageInfo every frame is pure garbage (the panel path draws every frame too)
        var key = chosen.getImageType() + "|" + chosen.getIdentifier();
        if (!key.equals(coverKey)) {
            coverKey = key;
            coverValue = chosen;
        }
        return coverValue;
    }

    /** The URL the current {@link #coverValue} was built from (see {@link #coverImage}). */
    private String coverKey;
    private ImageInfo coverValue = ImageInfo.EMPTY;

    /** The cover to actually draw: the wanted one once its texture is in, otherwise **the last one that was**, so a cover change never leaves a hole. */
    private ImageInfo coverDrawable(ImageInfo wanted) {
        if (wanted != null && !wanted.isEmpty() && PlayImageRenderer.getInstance().isLoaded(wanted, false)) {
            coverShown = wanted;
            return wanted;
        }
        return coverShown == null ? ImageInfo.EMPTY : coverShown;
    }

    /** The last cover whose texture was ready (see {@link #coverDrawable}). */
    private ImageInfo coverShown = ImageInfo.EMPTY;

    /** The title line: the API's own title once known, else the playlist entry's name. */
    private String titleText(BiliBiliArcTags.Info facts) {
        return facts != null && !facts.title().isBlank() ? facts.title() : entryTitle;
    }

    /** Fits a line to {@code maxWidth} px the way the rest of the manager does. */
    private static Component fit(Component text, int maxWidth) {
        if (maxWidth <= 0)
            return text;
        var s = text.getString();
        var cut = OEClientUtils.getWidthOmitText(s, maxWidth, "...");
        return cut.equals(s) ? text : Component.literal(cut);
    }

    /** One description line and where its video links sit (character ranges inside {@link #text}). */
    private record DescLine(String text, List<int[]> links, List<String> ids) {
    }

    /**
     * The description, cut to {@code textWidth} px by appearance; a BV/AV id is never split across two lines and is
     * reported in {@code links}/{@code ids}.
     */
    private static List<DescLine> descLines(BiliBiliArcTags.Info facts, int textWidth, int maxLines, int firstIndent) {
        var out = new ArrayList<DescLine>();
        var text = descText(facts);
        if (textWidth <= 0 || maxLines <= 0 || text.isEmpty())
            return out;
        var found = new ArrayList<int[]>();
        var matcher = VIDEO_ID.matcher(text);
        while (matcher.find())
            found.add(new int[]{matcher.start(), matcher.end()});
        int from = 0;
        while (from < text.length() && out.size() < maxLines) {
            var last = out.size() == maxLines - 1;
            var end = lineEnd(text, from, textWidth - (out.isEmpty() ? firstIndent : 0), found, last);
            if (end <= from)
                break;
            var shown = text.substring(from, end);
            if (last && end < text.length())
                shown = shown + "...";
            var links = new ArrayList<int[]>(2);
            var ids = new ArrayList<String>(2);
            for (var f : found) {
                if (f[0] >= from && f[1] <= end) {
                    links.add(new int[]{f[0] - from, f[1] - from});
                    ids.add(text.substring(f[0], f[1]));
                }
            }
            out.add(new DescLine(shown, List.copyOf(links), List.copyOf(ids)));
            from = end;
            while (from < text.length() && text.charAt(from) == ' ')
                from++;
        }
        return out;
    }

    /** How much of {@code text} fits from {@code from} at {@code width} px, never cutting a video id in half. */
    private static int lineEnd(String text, int from, int width, List<int[]> links, boolean reserveEllipsis) {
        var limit = reserveEllipsis ? width - mc.font.width("...") : width;
        int used = 0;
        int i = from;
        while (i < text.length()) {
            var next = i + 1;
            for (var l : links) {
                if (l[0] == i) {
                    next = l[1];
                    break;
                }
            }
            var w = mc.font.width(text.substring(i, next));
            if (used + w > limit)
                break;
            used += w;
            i = next;
        }
        return i;
    }

    /** The line as one component with its links in link blue (used by the in-world panel, which cannot be clicked). */
    private static Component coloredDescLine(DescLine line) {
        if (line.links().isEmpty())
            return Component.literal(line.text());
        var out = Component.empty();
        int at = 0;
        for (int i = 0; i < line.links().size(); i++) {
            var span = line.links().get(i);
            if (span[0] > at)
                out.append(Component.literal(line.text().substring(at, span[0])));
            out.append(Component.literal(line.text().substring(span[0], span[1]))
                    .withStyle(Style.EMPTY.withColor(LINK_COLOR)));
            at = span[1];
        }
        if (at < line.text().length())
            out.append(Component.literal(line.text().substring(at)));
        return out;
    }

    /** How many description lines may be drawn: {@link #DESC_LINES} while the part list is shown, otherwise as many as fit down to the list's bottom edge. */
    private static int descLineCapacity(boolean rowsShown) {
        if (rowsShown)
            return DESC_LINES;
        return Math.max(DESC_LINES, (LIST_Y + LIST_H - DESC_Y) / DESC_LINE_H - 1);
    }

    /** Places the reusable link widgets over the visible description links (called every tick). */
    private void layoutLinks() {
        var textWidth = width - TEXT_X - 6;
        var prefixWidth = mc.font.width(DESC_PREFIX);
        var lines = descLines(info, textWidth, descLineCapacity(isMultiPart(parts)), prefixWidth);
        int slot = 0;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var x = getStartX() + TEXT_X + (i == 0 ? prefixWidth : 0);
            var y = getStartY() + DESC_Y + i * DESC_LINE_H;
            int at = 0;
            for (int k = 0; k < line.links().size() && slot < linkSlots.size(); k++) {
                var span = line.links().get(k);
                if (span[0] > at)
                    x += mc.font.width(line.text().substring(at, span[0]));
                var id = line.ids().get(k);
                var widget = linkSlots.get(slot++);
                widget.setX(x);
                widget.setY(y);
                widget.setWidth(Math.max(1, mc.font.width(id)));
                widget.setHeight(DESC_LINE_H);
                widget.setMessage(Component.literal(id));
                widget.visible = true;
                widget.active = true;
                x += mc.font.width(id);
                at = span[1];
            }
        }
        for (; slot < linkSlots.size(); slot++) {
            linkSlots.get(slot).visible = false;
            linkSlots.get(slot).active = false;
        }
        // One line per content change: which links are laid out and where (a click that leaves no trace in the log means
        // it never reached a click area at all).
        var laid = new StringBuilder();
        for (int i = 0; i < Math.min(slot, linkSlots.size()); i++)
            laid.append(i > 0 ? ";" : "").append(linkSlots.get(i).getMessage().getString())
                    .append('@').append(linkSlots.get(i).getX()).append(',').append(linkSlots.get(i).getY());
        if (!laid.toString().equals(laidLinks)) {
            laidLinks = laid.toString();
            LinkDebug.log("BiliBili desc links", "pool", linkSlots.size(), "n", slot,
                    "at", laid.length() > 120 ? laid.substring(0, 120) + "…" : laid);
        }
    }

    /** Last laid-out link set (see {@link #layoutLinks()}). */
    private String laidLinks = "";

    /** Draws one description line — the whole line, links included, is painted here (the click areas draw nothing). */
    private void drawDescLine(GuiGraphics guiGraphics, DescLine line, int index, int mouseX, int mouseY) {
        var x = getStartX() + TEXT_X;
        var y = getStartY() + DESC_Y + index * DESC_LINE_H;
        if (index == 0) {
            drawSmartText(guiGraphics, DESC_PREFIX, x, y, FACT_COLOR);
            x += mc.font.width(DESC_PREFIX);
        }
        drawSmartText(guiGraphics, lineComponent(line, x, y, mouseX, mouseY), x, y, FACT_COLOR);
    }

    /** The line as one component: plain text in the fact colour, links in link blue (the hovered one darker + underlined). */
    private static Component lineComponent(DescLine line, int lineX, int lineY, int mouseX, int mouseY) {
        if (line.links().isEmpty())
            return Component.literal(line.text());
        var out = Component.empty();
        int at = 0;
        int cursor = lineX;
        for (int i = 0; i < line.links().size(); i++) {
            var span = line.links().get(i);
            if (span[0] > at) {
                var plain = line.text().substring(at, span[0]);
                out.append(Component.literal(plain));
                cursor += mc.font.width(plain);
            }
            var id = line.ids().get(i);
            var width = mc.font.width(id);
            var hovered = mouseX >= cursor && mouseX < cursor + width && mouseY >= lineY && mouseY < lineY + DESC_LINE_H;
            var link = Component.literal(line.text().substring(span[0], span[1]))
                    .withStyle(Style.EMPTY.withColor(hovered ? LINK_HOVER_COLOR : LINK_COLOR));
            out.append(hovered ? link.copy().withStyle(ChatFormatting.UNDERLINE) : link);
            cursor += width;
            at = span[1];
        }
        if (at < line.text().length())
            out.append(Component.literal(line.text().substring(at)));
        return out;
    }

    /** A description link was clicked: the mod's own detail page (default) or the system browser, per config. */
    private void openLink(String id) {
        if (id == null || id.isBlank())
            return;
        var browser = IamMusicPlayer.getConfig().bilibiliConfig.descLinksOpenInBrowser;
        LinkDebug.log("BiliBili desc link click", "id", id, "browser", browser);
        if (browser) {
            openInBrowser(id);
            return;
        }
        var thread = new Thread(() -> {
            var bvid = BiliBiliParts.bvidOf(id);
            LinkDebug.log("BiliBili desc link resolve", "id", id, "bvid", bvid);
            if (bvid == null) {
                openInBrowser(id);
                return;
            }
            // One lookup: it fills title/cover/duration, which the detail page needs
            BiliBiliParts.fetch(bvid);
            var facts = BiliBiliParts.knownInfo(bvid);
            LinkDebug.log("BiliBili desc link fetch", "bvid", bvid, "title", facts.title(), "empty", facts.isEmpty());
            if (facts.isEmpty()) {
                openInBrowser(bvid);
                return;
            }
            var source = new MusicSource(IMPMusicMedias.BILIBILI.getName(), bvid, Math.max(1000L, facts.durationMs()));
            var image = facts.cover().isBlank() ? ImageInfo.EMPTY
                    : new ImageInfo(ImageInfo.ImageType.URL, facts.cover());
            var guest = new Music(UUID.randomUUID(), facts.title().isBlank() ? bvid : facts.title(), facts.author(),
                    source, image, mc.player.getGameProfile().getId(), System.currentTimeMillis());
            mc.execute(() -> {
                var screen = getScreen();
                LinkDebug.log("BiliBili desc link open", "bvid", bvid, "screen", screen != null);
                if (screen != null)
                    screen.openGuestMusic(guest);
            });
        }, "IMP-BiliBili-DescLink");
        thread.setDaemon(true);
        thread.start();
    }

    /** Fallback (and the configured behaviour): hand the address to the system browser. */
    private static void openInBrowser(String id) {
        LinkDebug.log("BiliBili desc link browser", "id", id, "url", BiliBiliUtil.videoUrl(id, 1));
        try {
            Util.getPlatform().openUri(BiliBiliUtil.videoUrl(id, 1));
        } catch (Throwable t) {
            LinkDebug.log("BiliBili desc link browser FAIL", "id", id, "ex", t.getClass().getSimpleName());
        }
    }

    /** The flattened description (newlines collapse into spaces), {@code ""} when the API gave none. */
    private static String descText(BiliBiliArcTags.Info facts) {
        var desc = facts == null || facts.desc() == null ? "" : facts.desc().strip();
        return desc.isEmpty() ? "" : desc.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        var list = parts;
        var facts = info;
        int textWidth = width - TEXT_X - 6;
        drawSmartFixedWidthText(guiGraphics, fit(Component.literal(titleText(facts)), textWidth),
                getStartX() + TEXT_X, getStartY() + TITLE_Y, textWidth);
        var stats = statsLine(facts);
        if (stats != null)
            drawSmartFixedWidthText(guiGraphics, fit(stats, textWidth), getStartX() + TEXT_X, getStartY() + STATS_Y,
                    textWidth, FACT_COLOR);
        var meta = metaLine(facts, list == null ? 0 : list.size(), bvid.isEmpty(), bvid);
        if (meta != null)
            drawSmartFixedWidthText(guiGraphics, fit(meta, textWidth), getStartX() + TEXT_X, getStartY() + META_Y,
                    textWidth, FACT_COLOR);
        // A single-part video draws no part list, so the description uses that space (see descLineCapacity).
        var rowsShown = isMultiPart(list);
        var desc = descLines(facts, textWidth, descLineCapacity(rowsShown), mc.font.width(DESC_PREFIX));
        for (int line = 0; line < desc.size(); line++)
            drawDescLine(guiGraphics, desc.get(line), line, mouseX, mouseY);
        drawSmartFixedWidthText(guiGraphics, selectedText(list, facts, bvid),
                getStartX() + SELECTED_X, getStartY() + SELECTED_Y, COPY_X - SELECTED_X - 4);

        var cover = coverDrawable(coverImage(list, selectedPage, facts, entryImage));
        if (!cover.isEmpty())
            PlayImageRenderer.getInstance().drawIfLoaded(cover, guiGraphics.pose(), getStartX() + COVER_X,
                    getStartY() + COVER_Y, COVER_SIZE, false);
    }

    /**
     * The in-world panel runs on another instance (never {@code init}/{@code tick}, {@code screen == null}, no widgets),
     * so nothing here may touch a widget field, and it never requests anything — it draws what the caches know.
     */
    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource,
                                 int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;

        var music = getSelectedMusic(blockEntity);
        var identifier = music == null || music.getSource() == null ? "" : music.getSource().getIdentifier();
        var panelBvid = isBiliBili(music) ? BiliBiliUtil.stripPage(identifier) : null;
        if (panelBvid == null)
            panelBvid = "";
        var known = panelBvid.isEmpty() ? null : BiliBiliParts.known(panelBvid);
        var facts = panelBvid.isEmpty() ? BiliBiliArcTags.Info.EMPTY : BiliBiliParts.knownInfo(panelBvid);
        var musicName = music == null ? "" : music.getName();
        var title = facts.title().isBlank() ? musicName : facts.title();
        var selected = panelBvid.isEmpty() ? 1 : Math.max(1, BiliBiliUtil.pageOf(identifier));
        int textWidth = width - TEXT_X - 6;

        renderSmartTextSprite(poseStack, multiBufferSource, fit(Component.literal(title), textWidth),
                TEXT_X, TITLE_Y, OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, i);
        var stats = statsLine(facts);
        if (stats != null)
            renderSmartTextSpriteColorSprite(poseStack, multiBufferSource, fit(stats, textWidth), TEXT_X, STATS_Y,
                    OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, FACT_COLOR, i);
        var meta = metaLine(facts, known == null ? 0 : known.size(), panelBvid.isEmpty(), panelBvid);
        if (meta != null)
            renderSmartTextSpriteColorSprite(poseStack, multiBufferSource, fit(meta, textWidth), TEXT_X, META_Y,
                    OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, FACT_COLOR, i);
        var rowsShown = isMultiPart(known);
        var prefixWidth = mc.font.width(DESC_PREFIX);
        var desc = descLines(facts, textWidth, descLineCapacity(rowsShown), prefixWidth);
        for (int line = 0; line < desc.size(); line++) {
            var text = coloredDescLine(desc.get(line));
            if (line == 0)
                text = DESC_PREFIX.copy().append(text);
            renderSmartTextSpriteColorSprite(poseStack, multiBufferSource, text, TEXT_X,
                    DESC_Y + line * DESC_LINE_H, OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight,
                    FACT_COLOR, i);
        }
        renderSmartTextSprite(poseStack, multiBufferSource, selectedText(known, facts, panelBvid),
                SELECTED_X, SELECTED_Y, OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, i);

        var cover = coverDrawable(coverImage(known, selected, facts, music == null ? ImageInfo.EMPTY : music.getImage()));
        if (cover != null && !cover.isEmpty())
            PlayImageRenderer.getInstance().renderSpriteIfLoaded(cover, poseStack, multiBufferSource,
                    COVER_X * onPxW, monitorHeight - (COVER_Y + COVER_SIZE) * onPxH, OERenderUtils.MIN_BREADTH * 4,
                    COVER_SIZE * onPxH, i, j);

        // A single-part video draws no list frame (the description takes that space) — same rule as partsList.visible in tick().
        var rows = rows(known, title, selected, 0, thumbnailsOn());
        if (rowsShown)
            renderFixedListSprite(poseStack, multiBufferSource, LIST_X, LIST_Y, OERenderUtils.MIN_BREADTH * 3,
                    LIST_W, LIST_H, i, j, onPxW, onPxH, monitorHeight, rows, LIST_ROWS,
                    (poseStack1, multiBufferSource1, x, y, z, w, h, i1, j1, entry) -> {
                        renderSmartButtonBoxSprite(poseStack1, multiBufferSource1, x, y, z + OERenderUtils.MIN_BREADTH,
                                w, h, i1, j1, onPxW, onPxH, monitorHeight, entry.selected());
                        int sx = 2;
                        var thumb = entry.thumb();
                        if (thumb != null && !thumb.isEmpty()) {
                            int size = Math.min(BiliBiliPartsListWidget.THUMB_SIZE, (int) h - 4);
                            PlayImageRenderer.getInstance().renderSpriteIfLoaded(thumb, poseStack1, multiBufferSource1,
                                    x + 2, y + 2, z + OERenderUtils.MIN_BREADTH * 2, size * onPxH, i1, j1);
                            sx += size + 2;
                        }
                        var marker = Component.literal("P" + entry.page());
                        int markerWidth = mc.font.width(marker) + 4;
                        var duration = Component.literal(BiliBiliPartsListWidget.durationText(entry.durationMs()));
                        int durationWidth = mc.font.width(duration) + 3;
                        renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1, marker, x + sx, y + 4,
                                z + OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, 0xFF000000, i1);
                        renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1, entry.label(), x + sx + markerWidth, y + 4,
                                z + OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, 0xFF000000, i1);
                        renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1, duration, x + w - durationWidth, y + 4,
                                z + OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, 0xFF000000, i1);
                    });

        // Buttons: hand-drawn as well (widgets exist on the GUI path only). ⚠️ "Can play" means "the parts are known",
        // not "the list has rows" — a single-part video is playable with an empty list.
        renderSmartButtonSprite(poseStack, multiBufferSource, PLAY_X, PLAY_Y, OERenderUtils.MIN_BREADTH * 2,
                PLAY_SIZE, PLAY_SIZE, i, j, onPxW, onPxH, monitorHeight, WIDGETS_TEXTURE, 0, 123, 11, 11, 256, 256,
                panelBvid.isEmpty() || known == null);
        if (!panelBvid.isEmpty() && (known == null || known.isEmpty()))
            renderSmartButtonSprite(poseStack, multiBufferSource, RETRY_X, RETRY_Y, OERenderUtils.MIN_BREADTH * 2,
                    RETRY_W, RETRY_H, i, j, onPxW, onPxH, monitorHeight, RETRY_TEXT, true);
    }

    /**
     * The line next to the Play key: which part is shown, its name and length — or just the length for a single-part
     * video. Ask "is there something to show", never "is a request in flight" — the list may come from the cache.
     */
    private Component selectedText(@Nullable List<BiliBiliArcTags.Part> list, BiliBiliArcTags.Info facts, String videoId) {
        if (videoId == null || videoId.isEmpty())
            return NOT_BILIBILI_TEXT;
        if (isMultiPart(list)) {
            var index = selectedPage - 1;
            var part = index >= 0 && index < list.size() ? list.get(index) : null;
            var name = partLabel(titleText(facts), part).getString();
            var duration = part == null ? "--:--" : BiliBiliPartsListWidget.durationText(part.durationMs());
            return Component.translatable("imp.text.biliPartSelected", "P" + selectedPage, name, duration);
        }
        if (failed) {
            // A deleted submission is not a network problem — say what actually happened (see BiliBiliParts.Failure).
            return switch (BiliBiliParts.failureOf(videoId)) {
                case GONE -> GONE_TEXT;
                case RISK -> RISK_TEXT;
                default -> FAILED_TEXT;
            };
        }
        // An empty list is how a single-part video answers, so "still looking" may only mean a request in flight.
        if (loading)
            return LOADING_TEXT;
        return Component.translatable("imp.text.biliVideoDuration",
                BiliBiliPartsListWidget.durationText(singleDuration(list, facts)));
    }

    // ------------------------------------------------------------------ fact lines (shared by both render paths)

    /**
     * {@code play 12.3K · danmaku 456 · likes 1.2K · coins 345 · favourites 678}, or {@code null} when nothing is
     * known yet; all five
     * numbers come from the same {@code view} response as the part list, so the line costs no extra request.
     */
    @Nullable
    private static Component statsLine(BiliBiliArcTags.Info facts) {
        var pieces = new ArrayList<String>(5);
        if (facts.views() > 0L)
            pieces.add(Component.translatable("imp.text.biliPlayCount", count(facts.views())).getString());
        if (facts.danmaku() > 0L)
            pieces.add(Component.translatable("imp.text.biliDanmakuCount", count(facts.danmaku())).getString());
        if (facts.likes() > 0L)
            pieces.add(Component.translatable("imp.text.biliLikeCount", count(facts.likes())).getString());
        if (facts.coins() > 0L)
            pieces.add(Component.translatable("imp.text.biliCoinCount", count(facts.coins())).getString());
        if (facts.favorites() > 0L)
            pieces.add(Component.translatable("imp.text.biliFavoriteCount", count(facts.favorites())).getString());
        return pieces.isEmpty() ? null : Component.literal(String.join(" · ", pieces));
    }

    /** {@code UP name · zone · season xxx · published yyyy-MM-dd ·(7 parts)}, or {@code null} when there is nothing to say. */
    @Nullable
    private static Component metaLine(BiliBiliArcTags.Info facts, int partCount, boolean notBilibili, String bvid) {
        if (notBilibili)
            return null;
        var tag = bvid == null || bvid.isEmpty() ? null : BiliBiliArcTags.getInstance().get(bvid);
        var pieces = new ArrayList<String>(5);
        if (!facts.author().isBlank())
            pieces.add(Component.translatable("imp.text.biliAuthor", facts.author().strip()).getString());
        // Zone: view.tname comes back empty, so fall back to the zone the import recorded.
        var zone = !facts.zone().isBlank() ? facts.zone() : (tag == null || tag.zone() == null ? "" : tag.zone());
        if (!zone.isBlank())
            pieces.add(zone);
        // Season: from the local tags as well — the import knows it, view does not.
        if (tag != null && tag.seasons() != null && !tag.seasons().isEmpty()) {
            var season = tag.seasons().iterator().next();
            if (season != null && !season.isBlank())
                pieces.add(Component.translatable("imp.text.biliSeason", bareSeason(season)).getString());
        }
        if (facts.pubdateMs() > 0L)
            pieces.add(Component.translatable("imp.text.biliPublishDate", date(facts.pubdateMs())).getString());
        // The part count only while the line is short: the rows below already show P1..Pn and the date must stay visible.
        if (partCount > 1 && String.join(" · ", pieces).length() <= 22)
            pieces.add(Component.translatable("imp.text.biliPartTotal", partCount).getString());
        return pieces.isEmpty() ? null : Component.literal(String.join(" · ", pieces));
    }

    /** Stored season names carry the season marker the import writes; the page shows the bare name. */
    private static String bareSeason(String season) {
        var s = season.strip();
        return s.startsWith("合集·") ? s.substring("合集·".length()).strip() : s;
    }

    /** {@code 12345} → {@code 12.3K}: keeps the fact lines short. Chinese clients keep the platform's 万/亿 convention. */
    static String count(long n) {
        if (!isChineseClient()) {
            if (n >= 1_000_000L)
                return String.format("%.1fM", n / 1_000_000.0);
            if (n >= 1_000L)
                return String.format("%.1fK", n / 1_000.0);
            return String.valueOf(n);
        }
        if (n >= 100_000_000L)
            return String.format("%.1f亿", n / 100_000_000.0);
        if (n >= 10_000L)
            return String.format("%.1f万", n / 10_000.0);
        return String.valueOf(n);
    }

    /** Whether the client runs a Chinese language pack, where the platform's 万/亿 units are kept. */
    private static boolean isChineseClient() {
        var code = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
    }

    /** Publish time (ms) → {@code yyyy-MM-dd} in the local zone. */
    static String date(long ms) {
        try {
            return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isBiliBili(@Nullable Music music) {
        return music != null && music.getSource() != null
                && IMPMusicMedias.BILIBILI.getName().equals(music.getSource().getLoaderType());
    }

    @Override
    protected @Nullable MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.DETAIL_MUSIC;
    }

    @Nullable
    private Music getSelectedMusic() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedMusic(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    private Music getSelectedMusic(MusicManagerBlockEntity blockEntity) {
        // A description link opens a guest video: this page (and the detail page) then show that one instead of the playlist row
        var screen = getScreen();
        var guest = screen == null ? null : screen.getGuestMusic();
        if (guest != null)
            return guest;
        var id = getSelectedMusicRaw(blockEntity);
        var pl = getSelectedPlayList(blockEntity);
        if (id == null || pl == null)
            return null;
        var musics = getSyncManager().getMusics(pl);
        return musics == null ? null : musics.stream().filter(n -> id.equals(n.getUuid())).findFirst().orElse(null);
    }

    @Nullable
    private UUID getSelectedMusicRaw(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getSelectedMusic(mc.player);
    }

    @Nullable
    private UUID getSelectedPlayList(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getSelectedPlayList(mc.player);
    }
}
