package dev.felnull.imp.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.renderer.BlockLyricRenderer;
import dev.felnull.imp.client.renderer.DebugSpeakerRangeRenderer;
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

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void renderLevel(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        var bs = this.renderBuffers.bufferSource();
        var camVec = camera.getPosition();
        if (IamMusicPlayer.getConfig().showSpeakerRange) {
            DebugSpeakerRangeRenderer.render(new PoseStack(), bs, camVec.x(), camVec.y(), camVec.z());
        }
        // Block lyrics: show the current lyric line above the "position that is sounding"
        // (the position is decided by the tracker -- whoever is playing gets it above them)
        if (IamMusicPlayer.getConfig().showBlockLyrics) {
            BlockLyricRenderer.render(new PoseStack(), bs, camera, deltaTracker.getGameTimeDeltaPartialTick(false));
        }
    }
}
