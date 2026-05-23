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
}
