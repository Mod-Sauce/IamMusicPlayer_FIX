package dev.felnull.imp;

import dev.felnull.imp.client.gui.config.button.Button;
import dev.felnull.imp.client.gui.config.proxy.UserProxy;
import dev.felnull.imp.util.ProxyUtil;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.List;

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
  public boolean globalCache = true;

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
  public List<String> lavaPlayerURLs = List.of(
          "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer",
          "https://raw.giteeusercontent.com/gly091020/test_lavaplayer_IMP/raw/main/lavaplayer"
  );

  @ConfigEntry.Category("client")
  @ConfigEntry.Gui.Tooltip
  public boolean disableHash = false;

  @ConfigEntry.Category("client")
  public String IMPRFolder = "iammusicplayerrenewed";

  @ConfigEntry.Category("client")
  public String lavaNativesFolder = "lavaplayer_natives";

  @ConfigEntry.Category("client")
  public boolean hideDisplaySprite = false;

  @ConfigEntry.Category("client")
  public boolean hideDecorativeAntenna = false;

  @ConfigEntry.Category("client")
  public boolean tryUseGitee = true;

  @ConfigEntry.Category("server")
  public long maxWaitTime = 1000 * 10;

  @ConfigEntry.Category("server")
  public long retryTime = 1000 * 3;

  @ConfigEntry.Category("server")
  public boolean dropItemRing = true;

  @ConfigEntry.Category("server")
  public boolean serverLyric = false;

  @ConfigEntry.Category("integration")
  public boolean soundPhysicsRemasteredIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean touhouLittleMaidIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean patchouliIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean cctIntegration = true;

  @ConfigEntry.Category("debug")
  public boolean showMusicLines = false;

  @ConfigEntry.Category("debug")
  public boolean showSpeakerRange = false;

  @ConfigEntry.Category("client")
  @ConfigEntry.Gui.Excluded
  public String configVersion = "1";

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

  @ConfigEntry.Category("integration")
  @ConfigEntry.Gui.RequiresRestart
  public boolean createIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean sableIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean netMusicListIntegration = true;

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public NetMusicConfig netMusicConfig = new NetMusicConfig();

  @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
  @ConfigEntry.Category("sources")
  public BilibiliConfig bilibiliConfig = new BilibiliConfig();

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
}
