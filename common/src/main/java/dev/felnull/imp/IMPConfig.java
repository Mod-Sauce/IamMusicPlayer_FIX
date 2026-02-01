package dev.felnull.imp;

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
    "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer/natives_link.json";

  @ConfigEntry.Category("lavaplayer")
  public String hashBaseUrl =
    "https://raw.githubusercontent.com/Mod-Sauce/test_lavaplayer_IMP/refs/heads/main/lavaplayer";

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
  public String configVersion = "1";
}
