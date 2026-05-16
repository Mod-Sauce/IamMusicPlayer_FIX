package dev.felnull.imp.forge.mixin.maid;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBipedHead;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.forge.maid.MaidBoomboxRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// I'm so sorry, 943
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBipedHead", remap = false)
@Pseudo
public class MaidGeoRendererBoomboxMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V", at = @At(value = "HEAD"), cancellable = true)
    public void renderBoombox(PoseStack poseStack, MultiBufferSource bufferIn, int packedLightIn, Mob mob, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci){
        if (MaidBoomboxRenderer.renderBoombox(poseStack, bufferIn, packedLightIn, mob, partialTicks,
                p -> RenderUtils.prepMatrixForLocator(p, ((GeckoLayerMaidBipedHead)(Object)this).getGeoEntity(mob).getGeoModel().headBones()),
                p -> p.scale(-0.8F, 0.8F, -0.8F)))
            ci.cancel();
    }
}
