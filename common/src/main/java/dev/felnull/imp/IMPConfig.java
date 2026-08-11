package dev.felnull.imp;

import dev.felnull.imp.client.gui.config.button.Button;
import dev.felnull.imp.client.gui.config.proxy.UserProxy;
import dev.felnull.imp.util.ProxyUtil;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = IamMusicPlayer.MODID)
@Config.Gui.Background("textures/block/note_block.png")
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
  public boolean globalCache = true;

  @ConfigEntry.Category("client")
  public boolean autoProxy = true;

  @ConfigEntry.Category("client")
  public UserProxy proxy = UserProxy.of(ProxyUtil.getSystemProxy());

  @ConfigEntry.Category("client")
  public boolean useYoutubeDownloader = true;

  @ConfigEntry.Category("client")
  public boolean hideDisplaySprite = false;

  @ConfigEntry.Category("client")
  public boolean hideDecorativeAntenna = false;

  @ConfigEntry.Category("lavaplayer")
  public int ConnectionTimeout = 10000; // 10 seconds

  @ConfigEntry.Category("lavaplayer")
  public int ReadTimeout = 30000; // 30 seconds

  @ConfigEntry.Category("lavaplayer")
  public int DownloadRetryCount = 3;

  @ConfigEntry.Category("lavaplayer")
  public long DownloadRetryDelayMS = 1000;

  @ConfigEntry.Category("lavaplayer")
  public String relayServerURL =
    "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";

  @ConfigEntry.Category("lavaplayer")
  public String lavaPlayerNativesURL =
    "https://raw.githubusercontent.com/Mod-Sauce/lavanatives/refs/heads/main/lavaplayer/natives_link.json";

  @ConfigEntry.Category("lavaplayer")
  public String hashBaseUrl =
    "https://raw.githubusercontent.com/Mod-Sauce/lavanatives/refs/heads/main/lavaplayer";

  @ConfigEntry.Category("lavaplayer")
  public String IMPRFolder = "iammusicplayerrenewed";

  @ConfigEntry.Category("lavaplayer")
  public String lavaNativesFolder = "lavaplayer_natives";

  @ConfigEntry.Category("server")
  public long maxWaitTime = 1000 * 10;

  @ConfigEntry.Category("server")
  public long retryTime = 1000 * 3;

  @ConfigEntry.Category("server")
  public boolean dropItemRing = true;

  @ConfigEntry.Category("debug")
  public boolean showMusicLines = false;

  @ConfigEntry.Category("debug")
  public boolean showSpeakerRange = false;

  @ConfigEntry.Category("integration")
  public boolean soundPhysicsRemasteredIntegration = true;

  @ConfigEntry.Category("internal")
  @ConfigEntry.Gui.Excluded
  public String configVersion = "3";

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public NetMusicConfig netMusicConfig = new NetMusicConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public BilibiliConfig bilibiliConfig = new BilibiliConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public QQMusicConfig QQMusicConfig = new QQMusicConfig();

  public static class NetMusicConfig{
    public boolean enableNetease = true;

    public boolean withTransName = true;

    public String neteaseCookie = "";

    @ConfigEntry.Gui.PrefixText
    @Button("openNetMusic")
    public Void openNetMusic = null;
  }

  public static class BilibiliConfig{
    public boolean enableBilibili = true;

    public String bilibiliCookie = "";

    @ConfigEntry.Gui.PrefixText
    @Button("openMcedia")
    public Void openMcedia = null;
  }

  @ConfigEntry.Category("hud")
  public boolean enableMusicInfoHUD = true;

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

  public static class QQMusicConfig{
    public boolean enableQQMusic = true;

    public String QQMusicCookie = "";
  }
}
