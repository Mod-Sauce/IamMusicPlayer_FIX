package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分P (multi-part) support for B站 videos: read the part list and turn parts 2..n into playable musics. The list only
 * exists in {@code x/web-interface/view} (one request per video), so it is looked up on demand and cached in
 * {@link BiliBiliArcTags} — never during an import; {@code BV…-p2} identifiers already play without this.
 */
public final class BiliBiliParts {
    /** Gap between two lookups of the "展开分P"/sweep walks; the walk's workers overlap their round trips behind a pacer. */
    public static final long REQUEST_GAP_MS = 150L;

    private BiliBiliParts() {
    }

    /**
     * Hands one video's part list to the server, so a boombox walks parts while the video stays one playlist entry. Sent once per video per session; callers may be worker threads.
     */
    public static void report(String identifier, List<BiliBiliArcTags.Part> parts) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        if (bvid == null || bvid.isBlank() || parts == null || parts.isEmpty())
            return;
        if (!REPORTED.add(bvid))
            return;
        var names = new ArrayList<String>(parts.size());
        var durations = new ArrayList<Long>(parts.size());
        for (var p : parts) {
            names.add(p.name() == null ? "" : p.name());
            durations.add(p.durationMs());
        }
        try {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    dev.architectury.networking.NetworkManager.sendToServer(
                            dev.felnull.imp.networking.IMPPackets.BILIBILI_PARTS,
                            new dev.felnull.imp.networking.IMPPackets.BiliBiliPartsMessage(bvid, names, durations).toRFBB());
                } catch (Throwable t) {
                    REPORTED.remove(bvid);   // not delivered: let the next play/lookup try again
                }
            });
        } catch (Throwable t) {
            REPORTED.remove(bvid);
        }
    }

    /** Videos whose part list was already handed to the server this session. */
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Why the last lookup failed — a page can then say "the video is gone" instead of "network error". */
    public enum Failure {
        /** No lookup failed for this video (or a later one succeeded). */
        NONE,
        /** {@code code=-404}: the submission is gone — deleted, private, or withdrawn by Bilibili. */
        GONE,
        /** {@code code=-352} / {@code -412} / HTTP 412: risk control refused it **for now**; retrying later usually works. */
        RISK,
        /** Timeout, TLS, other HTTP status or an unparsable body: a network problem. */
        NETWORK
    }

    /** The last failure per video ({@link #failureOf}); an entry is removed as soon as a lookup succeeds again. */
    private static final java.util.Map<String, Failure> FAILURES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Why the **last** lookup of this video failed; {@link Failure#NONE} when there was none (or it succeeded since). */
    public static Failure failureOf(@Nullable String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid == null || bvid.isBlank() ? Failure.NONE : FAILURES.getOrDefault(bvid, Failure.NONE);
    }

    /** Whether Bilibili already told us (any session) that this submission is gone — no request needed. */
    public static boolean knownGone(@Nullable String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid != null && !bvid.isBlank() && BiliBiliArcTags.getInstance().isGone(bvid);
    }

    /** When this video's {@code view} was last answered by Bilibili, epoch ms ({@code 0} = never). */
    public static long checkedAt(@Nullable String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid == null || bvid.isBlank() ? 0L : BiliBiliArcTags.getInstance().checkedAt(bvid);
    }

    /**
     * The canonical bvid of an identifier that may be an {@code av} number (one request for those); keys use bvids.
     *
     * @return the id itself when it is already a bvid, its bvid when the request succeeded, else {@code null}
     */
    @Nullable
    public static String bvidOf(String identifier) {
        var id = BiliBiliUtil.stripPage(identifier);
        if (id == null || id.isBlank())
            return null;
        if (!id.regionMatches(true, 0, "av", 0, 2))
            return id;
        try {
            var url = "https://api.bilibili.com/x/web-interface/view?aid=" + id.substring(2);
            var response = BiliBiliUtil.client().send(
                    BiliBiliUtil.apiRequest(url, BiliBiliUtil.spaceCookie()).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("code") || json.get("code").getAsInt() != 0 || !json.has("data"))
                return null;
            var bvid = str(json.getAsJsonObject("data"), "bvid");
            return bvid.isBlank() ? null : bvid;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Records a failure reason, and drops the record on success (see {@link #failureOf}). */
    private static void remember(String bvid, @Nullable Failure failure) {
        if (failure == null || failure == Failure.NONE)
            FAILURES.remove(bvid);
        else
            FAILURES.put(bvid, failure);
    }

    /** The {@link Failure} that matches one Bilibili API {@code code} (see the enum for what each one means). */
    private static Failure failureOfCode(int code) {
        return switch (code) {
            case -404 -> Failure.GONE;
            case -352, -412 -> Failure.RISK;
            default -> Failure.NETWORK;
        };
    }

    /**
     * The part list already known for this video. Empty = never looked (or the lookup failed); a one-element list means "looked up, single part".
     */
    public static List<BiliBiliArcTags.Part> known(String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid == null ? List.of() : BiliBiliArcTags.getInstance().partsOf(bvid);
    }

    /**
     * The video-level facts already known (title / uploader / cover / counts / publish time), or
     * {@link BiliBiliArcTags.Info#EMPTY} when never looked at; read-only, no request.
     */
    public static BiliBiliArcTags.Info knownInfo(String identifier) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        return bvid == null ? BiliBiliArcTags.Info.EMPTY : BiliBiliArcTags.getInstance().infoOf(bvid);
    }

    /**
     * The video-level facts of one {@code view} response body. They come from the same response the part list does, so remembering them costs no request; anything missing reads as "not known" instead of throwing.
     */
    public static BiliBiliArcTags.Info infoOf(@Nullable JsonObject data) {
        if (data == null)
            return BiliBiliArcTags.Info.EMPTY;
        var owner = data.has("owner") && data.get("owner").isJsonObject() ? data.getAsJsonObject("owner") : null;
        var stat = data.has("stat") && data.get("stat").isJsonObject() ? data.getAsJsonObject("stat") : null;
        var cover = str(data, "pic");
        if (cover.startsWith("http://"))
            cover = cover.replaceFirst("^http://", "https://");
        return new BiliBiliArcTags.Info(
                str(data, "title"),
                owner == null ? "" : str(owner, "name"),
                cover,
                str(data, "tname"),
                num(stat, "view"),
                num(stat, "danmaku"),
                num(stat, "like"),
                num(data, "pubdate") * 1000L,
                str(data, "desc"),
                num(data, "duration") * 1000L,
                num(stat, "reply"),
                num(stat, "coin"),
                num(stat, "favorite"),
                num(stat, "share"));
    }

    /**
     * A string field of a JSON object; empty for missing/null/wrong type — numbers and booleans count as wrong, since a
     * numeric {@code pic} would otherwise be handed to the image loader as a URL.
     */
    private static String str(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key))
            return "";
        var el = o.get(key);
        return el == null || el.isJsonNull() || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()
                ? "" : el.getAsString();
    }

    /** A numeric field of a JSON object; 0 for missing/null/wrong type. */
    private static long num(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key))
            return 0L;
        var el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive())
            return 0L;
        try {
            return el.getAsLong();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * One {@code x/web-interface/view} request for this video's part list.
     *
     * @return the parts (more than one entry), an **empty** list when the video has a single part or the response
     * carries no {@code pages}, or {@code null} when the lookup failed (network / risk control / API code ≠ 0) —
     * the caller decides whether that is worth reporting.
     */
    @Nullable
    public static List<BiliBiliArcTags.Part> fetch(String identifier) {
        return fetch(identifier, false);
    }

    /** Same lookup with the success line silenced — the dead-link sweep calls this once per song. */
    @Nullable
    public static List<BiliBiliArcTags.Part> fetch(String identifier, boolean quiet) {
        var bvid = BiliBiliUtil.stripPage(identifier);
        if (bvid == null || bvid.isBlank())
            return List.of();
        try {
            var url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            // spaceCookie() adds the anonymous fingerprint trio (buvid3/buvid4/b_nut); without it Bilibili answers
            // -352 or an HTTP 412 risk page (see BiliBiliUtil#anonCookie).
            var response = BiliBiliUtil.client().send(
                    BiliBiliUtil.apiRequest(url, BiliBiliUtil.spaceCookie()).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                // 412 is the risk-control page (an HTML body) — retryable, unlike a plain network error.
                var kind = response.statusCode() == 412 ? Failure.RISK : Failure.NETWORK;
                remember(bvid, kind);
                LinkDebug.log("BiliBili parts HTTP", "bv", bvid, "httpStatus", response.statusCode(),
                        "bodyLen", response.body().length(), "kind", kind);
                return null;
            }
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("code") || json.get("code").getAsInt() != 0 || !json.has("data")) {
                var code = json.has("code") ? json.get("code").getAsInt() : Integer.MIN_VALUE;
                var kind = failureOfCode(code);
                remember(bvid, kind);
                // Persist a definite "gone" so the sweep can tick that row again without asking.
                if (kind == Failure.GONE)
                    BiliBiliArcTags.getInstance().markGone(bvid, true);
                LinkDebug.log("BiliBili parts code", "bv", bvid, "code", code, "kind", kind,
                        "message", json.has("message") ? json.get("message").getAsString() : "");
                return null;
            }
            var data = json.getAsJsonObject("data");
            var parts = partsOf(data);
            remember(bvid, null);
            // Mark "answered at …" (the sweep skips recently answered videos) and clear a stale "gone".
            var tags = BiliBiliArcTags.getInstance();
            tags.markChecked(bvid);
            if (tags.isGone(bvid))
                tags.markGone(bvid, false);
            // One line per lookup: without it a failure was indistinguishable from a hang. `pages=1` is the normal answer for a single-part video.
            if (!quiet)
                LinkDebug.log("BiliBili parts ok", "bv", bvid, "pages", parts.size());
            // The same response carries the video-level facts, so remember them too and save a second request when the part page needs them.
            BiliBiliArcTags.getInstance().rememberInfo(bvid, infoOf(data));
            // In memory only — not flushed here: the caller decides when to write. Writing per lookup made a 950-video walk rewrite the ~65 KB file ~950 times.
            if (!parts.isEmpty()) {
                BiliBiliArcTags.getInstance().rememberParts(bvid, parts);
                // …and handed to the server, which needs it to continue with the next part while the video stays a
                // single playlist entry (see report()).
                report(bvid, parts);
            }
            return parts.size() > 1 ? parts : List.of();
        } catch (Throwable t) {
            remember(bvid, Failure.NETWORK);
            LinkDebug.log("BiliBili parts EX", "bv", bvid, "ex", t.getClass().getSimpleName(), "msg", t.getMessage());
            return null;
        }
    }

    /**
     * The part list of one {@code view} body ({@code data.pages[]}); a single-part video still yields a one-element list
     * (name/frame dropped, length kept) = "looked up, nothing to expand"; no {@code pages} gives the empty list.
     */
    public static List<BiliBiliArcTags.Part> partsOf(JsonObject data) {
        if (data == null || !data.has("pages") || !data.get("pages").isJsonArray())
            return List.of();
        JsonArray pages = data.getAsJsonArray("pages");
        if (pages.isEmpty())
            return List.of();
        var out = new ArrayList<BiliBiliArcTags.Part>(pages.size());
        for (var e : pages) {
            if (!e.isJsonObject())
                continue;
            var page = e.getAsJsonObject();
            // Guard against JsonNull: has() is true for `"part": null` and getAsString() would throw
            var name = page.has("part") && !page.get("part").isJsonNull() ? page.get("part").getAsString() : "";
            var duration = page.has("duration") && !page.get("duration").isJsonNull()
                    ? page.get("duration").getAsLong() * 1000L : 0L;
            var frame = page.has("first_frame") && !page.get("first_frame").isJsonNull()
                    ? page.get("first_frame").getAsString() : "";
            out.add(new BiliBiliArcTags.Part(name, duration, frame));
        }
        if (out.isEmpty())
            return List.of();
        // Single-part marker: name/first frame of the only part are never used, so they stay out of the cache file.
        return out.size() == 1 ? List.of(new BiliBiliArcTags.Part("", out.get(0).durationMs(), "")) : List.copyOf(out);
    }

    /**
     * The playlist name of one part: {@code 主标题 · 分P名}, or {@code 主标题 (P2/2)} when the part has no name of its
     * own or repeats the video title (Bilibili does both).
     */
    public static String partName(String videoTitle, @Nullable BiliBiliArcTags.Part part, int page, int total) {
        var title = videoTitle == null ? "" : videoTitle.strip();
        var name = part == null || part.name() == null ? "" : part.name().strip();
        if (name.isEmpty() || name.equals(title))
            return title + " (P" + page + "/" + total + ")";
        return title.isEmpty() ? name : title + " · " + name;
    }

    /**
     * The musics for parts 2..n of an already imported video. P1 is not produced: the video's own entry plays part 1. Owner and creation time are copied from the original entry.
     */
    public static List<Music> buildParts(Music origin, List<BiliBiliArcTags.Part> parts) {
        var out = new ArrayList<Music>();
        if (origin == null || origin.getSource() == null || parts == null || parts.size() <= 1)
            return out;
        var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
        var bvid = BiliBiliUtil.stripPage(origin.getSource().getIdentifier());
        if (bvid == null || bvid.isBlank())
            return out;
        for (int i = 1; i < parts.size(); i++) {
            var page = i + 1;
            var part = parts.get(i);
            var image = origin.getImage();
            // Per-part first frame as the cover when the config asks for it (same as a played part does).
            if (cfg.usePageFirstFrame && cfg.loadVideoImage && part.firstFrame() != null && !part.firstFrame().isBlank())
                image = new ImageInfo(ImageInfo.ImageType.URL, part.firstFrame().replaceFirst("^http://", "https://"));
            out.add(new Music(
                    UUID.randomUUID(),
                    partName(origin.getName(), part, page, parts.size()),
                    origin.getAuthor(),
                    new MusicSource(IMPMusicMedias.BILIBILI.getName(), bvid + "-p" + page,
                            Math.max(0L, part.durationMs())),
                    image == null ? ImageInfo.EMPTY : image,
                    origin.getOwner(),
                    origin.getCreateDate()
            ));
        }
        return out;
    }

    /** {@code P2/3} for a multi-part video's identifier, or {@code ""} when there is nothing to say (the HUD's suffix). */
    public static String pageLabel(String identifier, @Nullable List<BiliBiliArcTags.Part> parts) {
        if (parts == null || parts.size() <= 1)
            return "";
        return "P" + BiliBiliUtil.pageOf(identifier) + "/" + parts.size();
    }
}
