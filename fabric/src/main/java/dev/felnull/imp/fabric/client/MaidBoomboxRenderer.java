package dev.felnull.imp.fabric.client;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.client.renderer.blockentity.BoomboxBlockEntityRenderer;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;

public class MaidBoomboxRenderer extends RenderLayer<Mob, BedrockModel<Mob>> {
    public MaidBoomboxRenderer(RenderLayerParent<Mob, BedrockModel<Mob>> renderLayerParent) {
        super(renderLayerParent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, Mob entity, float f, float g, float h, float j, float k, float l) {
        if(!(entity instanceof EntityMaid entityMaid))return;
        var stack = entityMaid.getBackpackShowItem();
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem())){
            poseStack.pushPose();
            this.getParentModel().getHead().translateAndRotate(poseStack);
            poseStack.scale(0.8F, 0.8F, 0.8F);
            poseStack.mulPose(new Quaternionf().rotateX((float) Math.PI));
            poseStack.translate(-0.5f, 0, -0.2f);
            float handleRaised = 1f - BoomboxItem.getTransferProgress(stack, f);
            var r = Minecraft.getInstance().level.registryAccess();
            var vc = ItemRenderer.getFoilBufferDirect(multiBufferSource, Sheets.cutoutBlockSheet(), true, stack.hasFoil());
            BoomboxBlockEntityRenderer.renderBoombox(poseStack, multiBufferSource, Direction.NORTH, i, OverlayTexture.NO_OVERLAY, f, BoomboxItem.getData(stack, r), handleRaised, vc);
            poseStack.popPose();
        }
    }
}
