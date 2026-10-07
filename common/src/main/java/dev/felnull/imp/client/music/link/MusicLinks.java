package dev.felnull.imp.client.music.link;

import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-agnostic "music link → platform + identifier" resolver: pure string parsing only — no network
 * and no client type dependency, callable from any load channel; the caller hands the match to the loader
 * behind {@code IMPMusicMedias.getMedia(link.platform())}. */
public final class MusicLinks {
    private static final List<Platform> PLATFORMS = new ArrayList<>();

    /** Bilibili user space ("UP 主页"): {@code space.bilibili.com/<mid>}, only the video submissions are imported;
     * last among the Bilibili rules, so {@code /favlist?fid=<n>} (a favourites folder) hits the fid rule first.
     * Declared above the initialiser on purpose: a simple-name use inside it is an illegal forward reference. */
    private static final Pattern SPACE_URL = Pattern.compile("https?://[^\\s]*?space\\.bilibili\\.com/(\\d+)(?![0-9])");

    static {
        // ---- Netease Cloud Music ----
        // Two domain forms: the main domain music.163.com and the mobile one y.music.163.com/m/ (one extra /m/ layer).
        // The old hash form (/ followed by #/) is covered by (?:#/)?, and whatever follows # does not affect id extraction.
        PLATFORMS.add(new Platform("netease",
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?song[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.SONG,
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?playlist[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        PLATFORMS.add(new Platform("netease",
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?album[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.ALBUM,
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?album[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.ALBUM));
        // Single program: program?id= / dj?id= / old hash #/program/?id= / mobile /m/program
        // Note: **program and radio are two different resources** (program id ≠ radio id), so it has its own kind and loaderId.
        PLATFORMS.add(new Platform("netease", "netease_program",
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?(?:program|dj(?![a-zA-Z]))[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.PROGRAM,
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?(?:program|dj(?![a-zA-Z]))[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.PROGRAM));
        // Netease radio (host radio / podcast): accepts radio?id=, djradio?id=, radio/<id> and the old hash
        // #/radio/?id=. loaderId is netease_radio: "netease" is taken by Netease's playlist loader.
        PLATFORMS.add(new Platform("netease", "netease_radio",
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?(?:radio|djradio)"
                        + "(?:/\\d+(?![0-9])|[^\\s]*?[?&]id=|[^\\s]*?/)"
                        + "(\\d+)"),
                MusicLink.Kind.RADIO,
                Pattern.compile("https?://[^\\s]*?music\\.163\\.com/(?:m/)?(?:#/)?(?:radio|djradio)[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.RADIO));

        // ---- QQ Music ----
        // loaderId is "QQMusic": the ID returned by QQ Music's playlist loader getID() is exactly this spelling (upper case, no underscore),
        // unlike the media name "qq_music", so it must be given explicitly here, otherwise playlist links would not match the loader.
        PLATFORMS.add(new Platform("qq_music", "QQMusic",
                Pattern.compile("https?://[^\\s]*?y\\.qq\\.com/n(?:/m)?/ryqq/songDetail/([0-9A-Za-z]+)"),
                MusicLink.Kind.SONG,
                Pattern.compile("https?://[^\\s]*?y\\.qq\\.com/n(?:/m)?/ryqq/playlist/(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        PLATFORMS.add(new Platform("qq_music", "QQMusic",
                Pattern.compile("https?://[^\\s]*?y\\.qq\\.com[^\\s]*?[?&]songmid=([0-9A-Za-z]+)"),
                MusicLink.Kind.SONG,
                Pattern.compile("https?://[^\\s]*?y\\.qq\\.com[^\\s]*?[?&]id=(\\d+)"),
                MusicLink.Kind.PLAYLIST));

        // ---- Bilibili ----
        // ① URL form: pull the BV number out of the link
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("https?://[^\\s]*?bilibili\\.com/video/((?i:BV)[0-9A-Za-z]{10})(?![0-9A-Za-z])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        // ② Bare BV number: only the leading 12-character BV number itself is taken here.
        // ③ Case: lower-case bv is accepted too, and upper-cased after extraction.
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("(?<![0-9A-Za-z])((?i:BV)[0-9A-Za-z]{10})(?![0-9A-Za-z])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST, true));
        // ③b AV form with domain: bilibili.com/video/av<number>. ⚠️ Kept as a separate entry from the bare AV number in ⑤ below:
        // the bare entry is marked bareId (only participates on the Bilibili channel), so merging them would make
        // `/video/av80433022` unrecognizable under auto.
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("https?://[^\\s]*?bilibili\\.com/video/((?i:av)(\\d+))(?![0-9])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        // Bilibili short links: {@code b23.tv/BV…} is taken straight from the link (no network expansion),
        // {@code b23.tv/av<number>} is resolved to BV via the Bilibili API, while a {@code b23.tv/<7-char random>}
        // temporary link is not expanded or recognized (returns null, falls through the original failure path).
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("https?://[^\\s]*?b23\\.tv/((?i:BV)[0-9A-Za-z]{10})(?![0-9A-Za-z])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("https?://[^\\s]*?b23\\.tv/((?i:av)(\\d+))(?![0-9])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST));
        // ⑤ Bare AV number (with extra parameters, handled the same way as the bare BV number) — also **bareId** (loose match, see the note on resolveFor)
        PLATFORMS.add(new Platform("bilibili",
                Pattern.compile("(?<![0-9A-Za-z])((?i:av)(\\d+))(?![0-9])"),
                MusicLink.Kind.VIDEO,
                Pattern.compile("https?://[^\\s]*?bilibili\\.com[^\\s]*?[?&]fid=(\\d+)"),
                MusicLink.Kind.PLAYLIST, true));
        // The user's space ("UP 主页"), imported as the user's video submissions. Placed after every fid rule:
        // {@code space.bilibili.com/<mid>/favlist?fid=<n>} needs the folder id, not the mid, and the identifier is
        // prefixed ({@code space:<mid>}) because both forms are plain numbers otherwise.
        PLATFORMS.add(new Platform("bilibili",
                null, null,
                SPACE_URL, MusicLink.Kind.PLAYLIST));

        // ---- Kugou Music ----
        // Identifier = 32-hex hash (case-insensitive, lower-cased): /song/<hash>.html, /mixsong/<hash>.html,
        // {@code /song/#hash=<hash>&album_id=<id>} (after #), a bare hash. {@code album_id} is not part of it —
        // the fetch API only needs the hash.
        PLATFORMS.add(new Platform("kugou",
                Pattern.compile("https?://[^\\s]*?kugou\\.com/(?:song|mixsong)/([0-9A-Fa-f]{32})(?![0-9A-Za-z])"),
                MusicLink.Kind.SONG, null, null));
        // The hash of a share link is after # (`#hash=`) or in an ordinary query (`?hash=`/`&hash=`):
        // all of [?&#] are treated as separators here, so all three spellings are recognized.
        PLATFORMS.add(new Platform("kugou",
                Pattern.compile("https?://[^\\s]*?kugou\\.com[^\\s]*?[?&#]hash=([0-9A-Fa-f]{32})(?![0-9A-Za-z])"),
                MusicLink.Kind.SONG, null, null));
        // Bare hash: the whole string (trailing ?/&/# parameters and whitespace allowed) must be exactly it — no loose match like
        // "32 hex digits appear somewhere in the text": a **direct link** with a 32-hex-digit file name or a cover image URL
        // would otherwise be misidentified as Kugou.
        PLATFORMS.add(new Platform("kugou",
                Pattern.compile("^\\s*([0-9A-Fa-f]{32})(?:[&#?][^\\s]*)?\\s*$"),
                MusicLink.Kind.SONG, null, null));
        // Playlist / collection: www.kugou.com/yy/special/single/<id>.html. Registered on the list slot only (the song
        // slot stays null): match() tries the song slot first, so the same pattern in both would always return the song
        // slot's Kind — making isPlaylistLike() false and autoPasteFromMusicLink unable to recognize the link.
        PLATFORMS.add(new Platform("kugou",
                null,
                MusicLink.Kind.PLAYLIST,
                Pattern.compile("https?://[^\\s]*?kugou\\.com/(?:yy/)?special/single/(\\d+)"),
                MusicLink.Kind.PLAYLIST));
    }

    /** The BV number: 2-character prefix + 10 characters, 12 in total; either prefix case is accepted, but the
     * **body case must be kept as-is** — BV numbers are case-sensitive. */
    private static final Pattern BV_ID = Pattern.compile("((?i:BV))([0-9A-Za-z]{10})(?![0-9A-Za-z])");
    /**
     * Bilibili AV number: {@code av} + digits (the av number itself is case-insensitive in the protocol).
     */
    private static final Pattern AV_ID = Pattern.compile("((?i:av))(\\d+)(?![0-9])");

    /** Bilibili appends parameters to the video number, so an internal identifier looks like
     * {@code BV1Mx4y1Y7pJ-120-p1} or {@code av80433022-120-p1} ({@code -120} part index, {@code -p1} first part, the
     * link form being {@code ?p=number}); normalization cuts them all off, keeping only the number. */
    private static final Pattern URL_PAGE_PARAM = Pattern.compile("[?&]p=(\\d+)");

    /** Part suffix -p<N> (N≥1) at the end of the identifier string: it must be **kept** on normalization, all other suffixes are dropped. */
    private static final Pattern ID_PAGE_SUFFIX = Pattern.compile("-p\\d+$");

    private static final Pattern ID_TRAILING_PARAMS = Pattern.compile("((?i:BV)[0-9A-Za-z]{10}|(?i:av)\\d+)((?:-[0-9A-Za-z]+)+)");

    private MusicLinks() {
    }

    /** "Identifier conversion" hook: only QQ Music uses it, because a share link gives the songmid while the
     * input box and the playback/lyrics channels need the numeric songid. The conversion needs the network
     * ({@code song_mid} query), so it is injectable: {@link MusicLinks} uses no network and no client class. */
    public interface IdentifierConverter {
        @Nullable
        String convert(@NotNull String platform, @NotNull String identifier);
    }

    @Nullable
    private static volatile IdentifierConverter converter;

    /**
     * Register the identifier conversion hook (called by each platform at its own initialization point; pass null to unregister).
     */
    public static void setIdentifierConverter(@Nullable IdentifierConverter c) {
        converter = c;
    }

    /**
     * Resolution with the "current channel" (performs identifier conversion).
     */
    @Nullable
    public static MusicLink resolveFor(@Nullable String input, @Nullable String currentLoaderId) {
        return resolveFor(input, currentLoaderId, true);
    }

    /** Resolution with the "current channel": bare numbers ({@code BV…} / {@code av123}) take part only when
     * the current channel is their own platform — under {@code auto} (or null) they are loose text matches with no
     * domain anchor and steal an {@code av…}; {@code bilibili.com/video/…} and {@code b23.tv/…} are unaffected. */
    @Nullable
    public static MusicLink resolveFor(@Nullable String input, @Nullable String currentLoaderId, boolean convert) {
        if (input == null)
            return null;

        var text = input.trim();
        if (text.isEmpty())
            return null;

        for (Platform platform : PLATFORMS) {
            if (platform.bareId() && !platform.loaderId().equals(currentLoaderId))
                continue;
            var link = platform.match(text, convert);
            if (link != null)
                return link;
        }
        return null;
    }

    /** Platform link form entry: one pattern + the identifier kind of its first capture group. {@code loaderId}
     * says which loader the playlist import goes to and differs from {@code name} only with several loaders;
     * see {@link dev.felnull.imp.client.music.playlist.IPlaylistLoader#getID()}; Netease radio is that case. */
    private record Platform(String name, String loaderId, Pattern song, MusicLink.Kind songKind,
                            Pattern list, MusicLink.Kind listKind, boolean bareId) {

        /** Most platforms have a single loader: loaderId defaults to the platform name. */
        Platform(String name, Pattern song, MusicLink.Kind songKind, Pattern list, MusicLink.Kind listKind) {
            this(name, name, song, songKind, list, listKind, false);
        }

        Platform(String name, String loaderId, Pattern song, MusicLink.Kind songKind,
                 Pattern list, MusicLink.Kind listKind) {
            this(name, loaderId, song, songKind, list, listKind, false);
        }

        /** Bare-number form (no domain anchor, only participates on the same channel): loaderId defaults to the platform name. */
        Platform(String name, Pattern song, MusicLink.Kind songKind, Pattern list, MusicLink.Kind listKind,
                 boolean bareId) {
            this(name, name, song, songKind, list, listKind, bareId);
        }

        @Nullable
        MusicLink match(String text, boolean convert) {
            var songLink = match(text, song, songKind, convert);
            if (songLink != null)
                return songLink;
            return match(text, list, listKind, convert);
        }

        @Nullable
        private MusicLink match(String text, Pattern pattern, MusicLink.Kind kind, boolean convert) {
            if (pattern == null)
                return null;

            var matcher = pattern.matcher(text);
            if (!matcher.find())
                return null;

            // A form with no capture group (e.g. the Bilibili short link) means "the identifier needs the network", so the URL is passed through as-is
            var identifier = matcher.groupCount() >= 1 ? matcher.group(1) : text;
            // Part: take ?p=N from the original text and append it to the identifier as the suffix -p<N> (non-Bilibili platform text never has it, so they are unaffected)
            if ("bilibili".equals(loaderId)) {
                // The space form resolves to a prefixed identifier (a bare mid would be ambiguous with a
                // favourites-folder id) and never carries a part suffix, so it returns before the page handling
                var space = SPACE_URL.matcher(text);
                if (space.find())
                    return new MusicLink(name, loaderId, kind, BiliBiliSpaceUtil.ID_PREFIX + space.group(1));

                var urlPage = URL_PAGE_PARAM.matcher(text);
                if (urlPage.find()) {
                    identifier = identifier + "-p" + urlPage.group(1);
                } else {
            // A bare-number entry never captures a part, so the suffix would be lost at capture time; add it back here when the input is
            // already in internal form (BV…-p2) — automatic routing feeds the parse result into the resolver once more.
                    var innerPage = ID_PAGE_SUFFIX.matcher(text.strip());
                    if (innerPage.find())
                        identifier = identifier + innerPage.group();
                }
            }
            identifier = normalizeIdentifier(name, identifier, convert);
            if (identifier == null || identifier.isEmpty())
                return null;

            return new MusicLink(name, loaderId, kind, identifier);
        }
    }

    /** Identifier cleanup. BV: plugins/apps copy extra parameters along ({@code BV1Mx4y1Y7pJ-120-p1}), so only the
     * BV number is kept; the prefix is normalized to {@code BV}, the body case is kept as-is (BV numbers are
     * case-sensitive). AV: the lower-case prefix {@code av} (AV80433022 → av80433022); others are plain numbers. */
    @Nullable
    private static String normalizeIdentifier(String platform, String identifier, boolean convert) {
        if (identifier == null || identifier.isEmpty())
            return identifier;

        // Extra parameters: keep only the part (-p<N>), drop the rest (tracking parameters like -120-p1), leaving just the number itself
        var pageSuffix = ID_PAGE_SUFFIX.matcher(identifier);
        var keep = pageSuffix.find() ? pageSuffix.group() : "";
        var trailing = ID_TRAILING_PARAMS.matcher(identifier);
        if (trailing.find())
            return trailing.group(1) + keep;

        var bv = BV_ID.matcher(identifier);
        if (bv.find())
            return "BV" + bv.group(2) + keep;

        var av = AV_ID.matcher(identifier);
        if (av.find())
            return "av" + av.group(2) + keep;

        // Kugou hash: normalized to lower case, so that the same hash in two spellings is not treated as two music entries in the UI/dedup
        if ("kugou".equals(platform) && identifier.matches("[0-9A-Fa-f]{32}"))
            return identifier.toLowerCase();

        // Platform-specific conversion (e.g. QQ Music's songmid → numeric songid); if it fails, keep the original identifier
        // Note: this step **uses the network**, so it only runs when the caller allows conversion (resolveFor's convert parameter)
        var c = convert ? converter : null;
        if (c != null) {
            var converted = c.convert(platform, identifier);
            if (converted != null && !converted.isEmpty())
                return converted;
        }

        return identifier;
    }
}
