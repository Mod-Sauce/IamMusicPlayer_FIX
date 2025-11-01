package org.modsauce.impr.explatform.client.forge;


import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public class IMPSoundPhysicsRemasteredExpectPlatformImpl {
    public static void setDefaultEnvironment(int sourceID) {
        SoundPhysics.setDefaultEnvironment(sourceID);
    }

    public static void setLastSoundCategoryAndName(SoundSource sc, String name) {
        SoundPhysics.setLastSoundCategoryAndName(sc, ResourceLocation.parse(name));
    }

    public static void onPlaySound(double posX, double posY, double posZ, int sourceID) {
        SoundPhysics.onPlaySound(posX, posY, posZ, sourceID);
    }
}

