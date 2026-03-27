package dev.felnull.imp.explatform.neoforge;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import vazkii.patchouli.api.PatchouliAPI;

public class IMPPatchouliExpectPlatformImpl {
    public static void openBookGUI(ServerPlayer player, ResourceLocation location) {
        PatchouliAPI.get().openBookGUI(player, location);
    }
}
