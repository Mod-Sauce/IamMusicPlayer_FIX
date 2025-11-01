package org.modsauce.impr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.client.renderer.DebugSpeakerRangeRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderLevel", at = @At(value = "TAIL"))
    private void renderLevel(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        if (IamMusicPlayer.getConfig().showSpeakerRange) {
            var bs = this.renderBuffers.bufferSource();
            var camVec = camera.getPosition();
            // Create a new PoseStack for rendering
            PoseStack poseStack = new PoseStack();
            DebugSpeakerRangeRenderer.render(poseStack, bs, camVec.x(), camVec.y(), camVec.z());
        }
    }
}
