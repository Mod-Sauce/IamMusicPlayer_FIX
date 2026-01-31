package dev.felnull.imp.explatform.client.forge;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IMPSoundPhysicsRemasteredExpectPlatformImpl {

  private static final Logger LOGGER = LogManager.getLogger(
    IMPSoundPhysicsRemasteredExpectPlatformImpl.class
  );

  public static void setDefaultEnvironment(int sourceID) {
    SoundPhysics.setDefaultEnvironment(sourceID);
  }

  public static void setLastSoundCategoryAndName(
    SoundSource sc,
    ResourceLocation name
  ) {
    try {
      SoundPhysics.setLastSoundCategoryAndName(sc, name);
    } catch (NoSuchMethodError e) {
      LOGGER.error(
        "Failed to load soundphysics intigration, due to: " + e
      );
    }
  }

  public static void onPlaySound(
    double posX,
    double posY,
    double posZ,
    int sourceID
  ) {
    SoundPhysics.onPlaySound(posX, posY, posZ, sourceID);
  }
}
