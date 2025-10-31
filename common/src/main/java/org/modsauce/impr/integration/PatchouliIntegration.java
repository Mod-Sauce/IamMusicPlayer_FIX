package org.modsauce.impr.integration;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.explatform.IMPPatchouliExpectPlatform;
import dev.felnull.otyacraftengine.integration.BaseIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class PatchouliIntegration extends BaseIntegration {
    public static final PatchouliIntegration INSTANCE = new PatchouliIntegration();

    @Override
    public String getModId() {
        return "patchouli";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().patchouliIntegration;
    }

    public void openBookGUI(ServerPlayer player, ResourceLocation location) {
        IMPPatchouliExpectPlatform.openBookGUI(player, location);
    }

    public ResourceLocation getOpenBookGui() {
        return IMPPatchouliExpectPlatform.getOpenBookGui();
    }
}
