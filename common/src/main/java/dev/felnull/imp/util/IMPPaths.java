package dev.felnull.imp.util;

import dev.felnull.imp.IamMusicPlayer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IMPPaths {

  public static Path getNaiveLibraryFolder(String lavaVersion) {
    return Paths.get(IamMusicPlayer.getConfig().IMPRFolder).resolve(
      IamMusicPlayer.getConfig().lavaNativesFolder
    );
  }

  public static Path getTmpFolder() {
    return Paths.get(IamMusicPlayer.getConfig().IMPRFolder).resolve("tmp");
  }

  public static Path getUserFolder(){
    return Paths.get(System.getProperty("user.home"));
  }

  public static Path getUserCacheDir() {
    String os = System.getProperty("os.name").toLowerCase();
    String home = System.getProperty("user.home");

    if (os.contains("linux")) {
      String xdgCache = System.getenv("XDG_CACHE_HOME");
      if (xdgCache != null && !xdgCache.isBlank() && Path.of(xdgCache).isAbsolute()) {
        return Path.of(xdgCache).resolve("iamusicplayer");
      }
      return Path.of(home, ".cache", "iamusicplayer");

    } else if (os.contains("mac")) {
      return Path.of(home, "Library", "Caches", "iamusicplayer");

    } else {
      // Windows
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        return Path.of(localAppData, "iamusicplayer", "Cache");
      }
      return Path.of(home, "AppData", "Local", "iamusicplayer", "Cache");
    }
  }
}
