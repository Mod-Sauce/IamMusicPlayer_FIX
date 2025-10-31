package org.modsauce.impr.util;

import org.modsauce.impr.IamMusicPlayer;

import java.nio.file.Path;
import java.nio.file.Paths;

public class IMPPaths {
    public static Path getNaiveLibraryFolder(String lavaVersion) {
        return Paths.get(IamMusicPlayer.MODID).resolve(lavaVersion);
    }

    public static Path getTmpFolder() {
        return Paths.get(IamMusicPlayer.MODID).resolve("tmp");
    }
}
