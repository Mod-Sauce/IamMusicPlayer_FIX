package dev.felnull.imp;

import dev.felnull.imp.client.gui.config.button.Button;
import dev.felnull.imp.client.gui.config.proxy.UserProxy;
import dev.felnull.imp.util.ProxyUtil;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;


@Config(name = IamMusicPlayer.MODID)
@Config.Gui.Background("cloth-config2:transparent")
public class IMPConfig implements ConfigData {

  @ConfigEntry.Category("client")
  public double volume = 1f;

  @ConfigEntry.Category("client")
  public int maxPlayCont = 8;

  @ConfigEntry.Category("client")
  public boolean spatial = true;

  @ConfigEntry.Category("client")
  public int sampleRate = 44100;

  @ConfigEntry.Category("client")
  public boolean enableCache = false;

  @ConfigEntry.Category("client")
  public boolean globalCache = false;

  @ConfigEntry.Category("client")
  public boolean autoProxy = true;

  @ConfigEntry.Category("client")
  public UserProxy proxy = UserProxy.of(ProxyUtil.getSystemProxy());

  @ConfigEntry.Category("client")
  public boolean useYoutubeDownloader = true;

  @ConfigEntry.Category("client")
  public String imgurClientID = "9a0189f3c8b74b9";

  @ConfigEntry.Category("client")
  public String relayServerURL =
    "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";

  @ConfigEntry.Category("client")
  public String IMPRFolder = "iammusicplayerrenewed";

  @ConfigEntry.Category("client")
  public String lavaNativesFolder = "lavaplayer_natives";

  @ConfigEntry.Category("client")
  public boolean hideDisplaySprite = false;

  @ConfigEntry.Category("client")
  public boolean hideDecorativeAntenna = false;

  @ConfigEntry.Category("server")
  public long maxWaitTime = 1000 * 10;

  @ConfigEntry.Category("server")
  public long retryTime = 1000 * 3;

  @ConfigEntry.Category("server")
  public boolean dropItemRing = true;

  @ConfigEntry.Category("server")
  public boolean serverLyric = false;

  /** Delete music from **this playlist only**; off = the original global delete (same for {@code deletePlayList}). */
  @ConfigEntry.Category("server")
  public boolean deleteMusicPlayListOnly = true;

  @ConfigEntry.Category("integration")
  public boolean soundPhysicsRemasteredIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean touhouLittleMaidIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean patchouliIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean cctIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean jadeIntegration = true;

  @ConfigEntry.Category("debug")
  public boolean showMusicLines = false;

  @ConfigEntry.Category("debug")
  public boolean showSpeakerRange = false;

  /**
   * Float the current lyrics **above the position that is playing sound** (original + translation, two lines, facing the camera).
   */
  @ConfigEntry.Category("client")
  public boolean showBlockLyrics = true;

  /**
   * Hide production-crew / copyright lines in lyrics (they are shown as lyrics by default).
   */
  @ConfigEntry.Category("client")
  public boolean filterCreditLines = true;

  /** Positive shows lyrics later, 0 = off; set per device — a Bluetooth headset lags ~150-300 ms behind playback. */
  @ConfigEntry.Category("client")
  @ConfigEntry.BoundedDiscrete(min = -5000, max = 5000)
  public int lyricOffsetMs = 0;

  /** Floating lyric max row width in blocks; a 230-character KuGou krc "line" is ~34 blocks wide. */
  @ConfigEntry.Category("client")
  @ConfigEntry.BoundedDiscrete(min = 2, max = 24)
  public int blockLyricMaxWidth = 6;

  /** Max rows per string (original and translation counted separately); anything beyond is dropped with "…". */
  @ConfigEntry.Category("client")
  @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
  public int blockLyricMaxLines = 4;

  /** Split over-long lyric rows at sentence marks (。！？!?;；….) instead of mid-word, so the phrasing survives. */
  @ConfigEntry.Category("client")
  public boolean blockLyricWrapSentences = true;

  @ConfigEntry.Category("client")
  @ConfigEntry.Gui.Excluded
  public String configVersion = "1";

  @ConfigEntry.Category("hud")
  public boolean enableMusicInfoHUD = true;

  /** Lyric line in the corner music info box; the bottom HUD has its own switch ({@link #hudBottomLyricLine}). */
  @ConfigEntry.Category("hud")
  public boolean enableHudLyric = false;

  /** Handheld boombox ("手提音响"): bottom-centre song + lyric bar instead of the corner detail box. */
  @ConfigEntry.Category("hud")
  public boolean hudHandheldBottom = true;

  /**
   * Bottom HUD, song line ({@code name — author}). Each of the two lines has its own switch.
   */
  @ConfigEntry.Category("hud")
  public boolean hudBottomSongLine = true;

  /** Bottom HUD lyric line: decides whether lyrics are fetched for the bar; the corner box uses {@code enableHudLyric}. */
  @ConfigEntry.Category("hud")
  public boolean hudBottomLyricLine = true;

  /** Bottom HUD karaoke: needs per-character timings (QQ QRC, KuGou krc); the rest keep the single-colour line. */
  @ConfigEntry.Category("hud")
  public boolean hudBottomKaraoke = false;

  /** Bottom HUD background: {@code PLAIN} = none (vanilla shadow), {@code STRIP} = strip, {@code BOX} = dark box. */
  public enum HudBottomStyle {
    PLAIN, STRIP, BOX
  }

  /** How a B站 subtitle track becomes lyrics; top-level because AutoConfig's GUI registry only labels top-level enums. */
  public enum SubtitleTrans {
    /** Default: a Chinese main line gets **no** translation line; a non-Chinese main line still gets its Chinese translation. */
    SMART,
    /** Always add the **English** track as the translation line (a Chinese video then shows English under it). */
    EN,
    /** Always add the **Japanese** track as the translation line. */
    JA,
    /** What the mod did before this option: the first track in any other language. */
    FIRST
  }

  /** Which B站 subtitle tracks may become lyrics; {@code ALL} is the default — an AI-only video has nothing else. */
  public enum SubtitleTrack {
    /** Human and machine tracks; the default, and the only setting that finds lyrics on an AI-only video. */
    ALL,
    /** Human tracks only — machine subtitles are dropped even when a video has nothing else. */
    HUMAN_ONLY,
    /** Machine tracks only. */
    AI_ONLY
  }

  /** Preferred language of the main lyric line; {@code BUILTIN} = original language first, Chinese when there is none. */
  public enum SubtitleLang {
    BUILTIN,
    ZH,
    EN,
    JA,
    KO
  }

  /** What the 「刷新」 key is allowed to pull in; the constant names are the values the walk already understands. */
  public enum RefreshScope {
    AUTO,
    SEASON,
    ZONE,
    ALL
  }

  @ConfigEntry.Category("hud")
  public HudBottomStyle hudBottomStyle = HudBottomStyle.STRIP;

  /** Song-change popup (same {@code name — author} line); only useful while {@link #hudBottomSongLine} is off. */
  @ConfigEntry.Category("hud")
  public boolean hudSongChangePopup = true;

  /** How long the song-change popup stays (seconds); it fades out over the last ~0.6 s. */
  @ConfigEntry.Category("hud")
  public int hudSongChangeSeconds = 4;

  /** Max width in pixels of one bottom-HUD line; longer text is cut with an ellipsis. */
  @ConfigEntry.Category("hud")
  public int hudBottomMaxWidth = 220;

  /** Y (px) of the lyric line above the bottom edge; floored at {@code 60 + lineHeight + 1} to clear the item-name band. */
  @ConfigEntry.Category("hud")
  public int hudBottomMargin = 72;

  /**
   * On-screen notice for "someone invited you to a playlist" (enabled by default).
   */
  @ConfigEntry.Category("hud")
  public boolean enablePlayListInviteNotice = true;

  @ConfigEntry.Category("debug")
  @Button("reloadLava")
  public Void reloadLavaLib = null;

  @ConfigEntry.Category("hud")
  @Button("settingHud")
  public Void settingHud = null;

  @ConfigEntry.Gui.Excluded
  @ConfigEntry.Category("hud")
  public int hudX = 10;
  @ConfigEntry.Gui.Excluded
  @ConfigEntry.Category("hud")
  public int hudY = 10;

  @ConfigEntry.Category("integration")
  @ConfigEntry.Gui.RequiresRestart
  public boolean createIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean sableIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean netMusicListIntegration = true;

  /**
   * Order of the "music load type" list: comma-joined loader ids ({@code auto} / {@code upload} always first,
   * unlisted last, empty = the original order); a segment that is not recognized is ignored.
   */
  @ConfigEntry.Category("sources")
  public String musicLoaderOrder = "";

  /**
   * Optional widgets of the music manager screen.
   */
  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("client")
  public UiConfig uiConfig = new UiConfig();

  /**
   * The **boombox** (portable speaker)'s own two settings.
   */
  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("client")
  public BoomboxConfig boomboxConfig = new BoomboxConfig();

  /** Sort order of the song list on the boombox "remote song picker page". */
  public enum BoomboxSongSort {
        /** Order they were added to the playlist (default). */
        ADD_ORDER,
        /** By music name. */
        NAME,
        /** By artist/author. */
        PLAYER,
        /** 加入时间＝曲子进歌单的时刻。**配置界面不列这一档**：只有「用监视器的排序」把它同步过来时才会出现。 */
        CREATE_DATE,
        /** B站 投稿时间（客户端上报给服务端；没报过的曲子按 0 排）。 */
        PUBLISH_DATE
  }

  public static class BoomboxConfig {
    /** Sort order on the boombox "remote song picker page"; default {@code ADD_ORDER} (the order they were added). */
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public BoomboxSongSort remoteSongSort = BoomboxSongSort.ADD_ORDER;

    /** Whether the sort above is descending. Off by default (= ascending/original order direction). */
    public boolean remoteSongSortDescending = false;

    /** Do not stop the music when switching between the remote play page and the song picker with the antenna key. */
    public boolean keepPlayingOnRemoteSwitch = true;

    /** World playback drops a track that only comes back as a trial clip (e.g. VIP); the playlist page still plays it. */
    public boolean skipTrialFragment = true;
  }

  public static class UiConfig {
    /**
     * Show a box at the top of the "playlists" page to **filter the songs in the currently selected playlist** (by name or artist).
     */
    public boolean enablePlayListMusicFilter = true;

    /**
     * [remembered] Playlist sort buttons, stored as the enum's {@code getName()} ({@code name}/{@code player}/
     * {@code create_date}, {@code ascending}/{@code descending}).
     */
    @ConfigEntry.Gui.Excluded
    public String playListSortType = "name";
    @ConfigEntry.Gui.Excluded
    public String playListOrderType = "descending";
    @ConfigEntry.Gui.Excluded
    public String musicSortType = "name";
    @ConfigEntry.Gui.Excluded
    public String musicOrderType = "descending";

    /**
     * [remembered] The 「分类」 picker of the playlists page: {@code all} / {@code ungrouped} / {@code multi} /
     * {@code single} / {@code season:<合集名>} / {@code zone:<分区名>}; unreadable values fall back to {@code all}.
     */
    @ConfigEntry.Gui.Excluded
    public String playListCategoryFilter = "all";
  }

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public NetMusicConfig netMusicConfig = new NetMusicConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public BilibiliConfig bilibiliConfig = new BilibiliConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public QQMusicConfig QQMusicConfig = new QQMusicConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public YoutubeConfig youtubeConfig = new YoutubeConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public SoundCloudConfig soundCloudConfig = new SoundCloudConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public OtherSourceConfig otherSourceConfig = new OtherSourceConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public KugouConfig kugouConfig = new KugouConfig();

  /**
   * Kugou (single-track channel): the public API needs no signature, device or login — free tracks play, paid/VIP
   * ones answer "login required". Disabled = absent from the "music load type" list and unable to load.
   */
  public static class KugouConfig {
    public boolean enableKugou = true;

    /**
     * [lyric translation] Fetch words with krc (with per-line translation) or use lrc only (original text only).
     */
    public boolean lyricTranslation = true;

    /** [lyric format] krc or lrc: Kugou's translations exist only in krc; turning it off falls back to lrc (original text only). On by default. */
    public boolean lyricUseKrc = true;

    /**
     * Login cookie (**can be filled in by hand, or written automatically by scanning the code in the config screen's "Kugou login"**).
     */
    public String cookie = "";

    /**
     * Device identifier dfid (**registered and written automatically on first use**; the registration endpoint is rate-limited, hence caching it).
     */
    public String dfid = "";

    /** Device mid (paired with dfid, generated automatically). */
    @ConfigEntry.Gui.Excluded
    public String mid = "";

    /** Device guid (generated automatically, used to recompute mid). */
    @ConfigEntry.Gui.Excluded
    public String guid = "";

    /** Login token (written automatically after a successful QR-code login). */
    @ConfigEntry.Gui.Excluded
    public String token = "";

    /** Login user id (written automatically after a successful QR-code login). */
    @ConfigEntry.Gui.Excluded
    public String userid = "";

    /** VIP token (kept when the server returns it during QR-code login). */
    @ConfigEntry.Gui.Excluded
    public String vipToken = "";

    /**
     * Audio quality: 128 / 320 / flac / high (Hi-Res, VIP required).
     */
    public String quality = "128";

    /**
     * The "Kugou login" button on the config screen (opens the QR-code screen).
     */
    @ConfigEntry.Category("sources")
    @Button("loginKugou")
    public Void loginKugou = null;
  }

  /** YouTube: disabled = absent from the "music load type" list and cannot load; mainland China needs a proxy. */
  public static class YoutubeConfig {
    public boolean enableYoutube = true;
  }

  /** SoundCloud: disabled = absent from the "music load type" list and cannot load. */
  public static class SoundCloudConfig {
    public boolean enableSoundCloud = true;
  }

  /**
   * The two "non-platform" sources: upload (a local file sent to the **relay server**, so it lives there, not in the
   * save) and direct URL (no platform metadata). Disabled = the entry disappears from the "music load type" list.
   */
  public static class OtherSourceConfig {
    public boolean enableUpload = true;

    public boolean enableDirectUrl = true;
  }

  public static class NetMusicConfig{
    public boolean enableNetease = true;

    public boolean withTransName = true;

    @Button("loginNetease")
    public Void loginNetease;

    /** NetEase login cookie (written automatically after QR-code login, can also be filled in by hand). **Never sent out in plain text**, only to music.163.com. */
    public String neteaseCookie = "";

    @ConfigEntry.Gui.PrefixText
    @Button("openNetMusic")
    public Void openNetMusic = null;
  }

  public static class BilibiliConfig{

    public boolean enableBilibili = true;

    /**
     * 跳过 B站 的赞助/广告片段（SponsorBlock / 哔哩屏蔽团 {@code bsbsb.top}，只取「赞助/恰饭」与「无偿推广」两类）：
     * 在读取端把这几段音频整块丢掉，不重开流也不静音；查不到片段就当没有。
     */
    public boolean skipSponsorSegments = false;

    /**
     * Whether to load Bilibili video covers (the image of a Bilibili music entry).
     */
    public boolean loadVideoImage = true;

    /**
     * Whether to draw the first frame of a multi-part video as its cover (low quality, multi-P videos only).
     */
    public boolean usePageFirstFrame = false;

    /** Bilibili login cookie, may be empty; sent only to bilibili.com but **stored in the config in plain text**. */
    public String bilibiliCookie = "";

    /**
     * What 「刷新」 may pull in: {@code AUTO} = its 合集 when the playlist is mostly one, else its 分区; {@code SEASON} /
     * {@code ZONE} = only that; {@code ALL} = all submissions. No determinable scope = fail, never fall back to {@code ALL}.
     */
    public RefreshScope refreshScope = RefreshScope.AUTO;

    /** Not implemented — the B站 source plays videos via playurl — so the field stays out of the config screen. */
    @ConfigEntry.Gui.Excluded
    public boolean refreshIncludeAudio = false;

    /** BV/AV links in a description: {@code false} = the mod's detail page, {@code true} = the system browser. */
    public boolean descLinksOpenInBrowser = false;

    /**
     * CDN hosts to try first, comma separated, **first match wins** (a token matches by substring); default
     * {@code upos-,bilivideo,mcdn-}. B站's slow P2P edge ({@code mcdn.*:8082}) is always ranked last; empty = default.
     */
    public String cdnOrder = "upos-,bilivideo,mcdn-";

    /** 「UP 主页」import: max submission pages (30 videos each), {@code 0} = unlimited; asking too fast gets HTTP 412. */
    public int spaceMaxPages = 0;

    /**
     * 「UP 主页」import: delay in ms between two submission pages (default 400) — the only lever against Bilibili's
     * risk control; lowering it (or 0) makes an HTTP 412 far more likely.
     */
    public int spacePageDelayMs = 400;

    /**
     * How many B站 requests may be in flight at once (1..8, default 3); shared by the 「UP 主页」 import and the 分P
     * expansion. It does not raise the request rate — the pacer still starts one per gap; {@code 1} = strictly sequential.
     */
    public int spaceParallelism = 3;

    /** Master switch for B站 subtitle lyrics: {@code false} = the request is never sent, so nothing below matters. */
    public boolean subtitleAsLyrics = true;

    /**
     * Ask only for videos that came from a **UP page import** (they carry the local 分区/合集 tag the check reads);
     * off by default = every B站 track is asked. A miss ends as "no lyrics", never as an error.
     */
    public boolean subtitleUpOnly = false;

    /** See {@link SubtitleTrack}: dropping machine tracks leaves an AI-only video with no lyrics at all. */
    public SubtitleTrack subtitleTrack = SubtitleTrack.ALL;

    /** See {@link SubtitleLang}; {@code BUILTIN} is the shipped rule, the rest force one language. */
    public SubtitleLang subtitleLang = SubtitleLang.BUILTIN;

    /** See {@link SubtitleTrans}: which track is the main line and whether a translation line is drawn under it. */
    public SubtitleTrans subtitleTrans = SubtitleTrans.SMART;

    /** Result goes into {@link #bilibiliCookie}; optional, but anonymous requests are far more likely to hit HTTP 412. */
    @Button("loginBilibili")
    public Void loginBilibili = null;

    @ConfigEntry.Gui.PrefixText
    @Button("openMcedia")
    public Void openMcedia = null;
  }

  public static class QQMusicConfig{
    public boolean enableQQMusic = true;

    /** QQ Music cookie, not required, but it covers more tracks at higher quality; the login button writes it. */
    public String QQMusicCookie = "";

    /**
     * Playback quality: {@code auto} = start at lossless and step down until QQ hands out a file, {@code 320} = up to
     * 320k MP3, {@code 128} = the standard file. A locked tier comes back empty, so stepping down is safe.
     */
    public String QQMusicQuality = "auto";

    /** Result goes into {@link #QQMusicCookie}; optional — anonymous requests already play part of QQ's catalogue. */
    @Button("loginQQMusic")
    public Void loginQQMusic = null;
  }
}
