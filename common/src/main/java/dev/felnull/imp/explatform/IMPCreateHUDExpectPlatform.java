package dev.felnull.imp.explatform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.felnull.imp.block.BoomboxData;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class IMPCreateHUDExpectPlatform {
    @ExpectPlatform
    @Nullable
    public static BoomboxData getDataFromPlayer(Player player){
        throw new AssertionError();
    }
}
