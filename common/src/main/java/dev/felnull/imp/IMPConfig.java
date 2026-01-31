package dev.felnull.imp;

import dev.felnull.imp.client.gui.config.Button;
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
  public boolean useYoutubeDownloader = true;

  @ConfigEntry.Category("client")
  public String relayServerURL =
    "https://raw.githubusercontent.com/TeamFelnull/IamMusicPlayer/master/relay_server.json";

  @ConfigEntry.Category("client")
  public String lavaPlayerNativesURL =
    "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer/natives_link.json";

  @ConfigEntry.Category("client")
  public String hashBaseUrl =
    "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer";

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

  @ConfigEntry.Category("integration")
  public boolean soundPhysicsRemasteredIntegration = true;

  @ConfigEntry.Category("integration")
  public boolean touhouLittleMaidIntegration = true;

  @ConfigEntry.Category("debug")
  public boolean showMusicLines = false;

  @ConfigEntry.Category("debug")
  public boolean showSpeakerRange = false;

  @ConfigEntry.Category("client")
  @ConfigEntry.Gui.Excluded
  public String configVersion = "1";

  @ConfigEntry.Category("netease")
  public boolean enableNetease = true;

  @ConfigEntry.Category("netease")
  public boolean withTransName = true;

  @ConfigEntry.Category("netease")
  public String neteaseCookie = "";

  @ConfigEntry.Category("hud")
  public boolean enableMusicInfoHUD = true;

  @ConfigEntry.Category("debug")
  @Button("reloadLava")
  public Void reloadLavaLib = null;
}
