package dev.felnull.imp.forge.maid;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.block.BoomboxBlock;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.client.renderer.blockentity.BoomboxBlockEntityRenderer;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.function.Consumer;

public class MaidBoomboxRenderer {
    public static boolean renderBoombox(PoseStack poseStack, MultiBufferSource bufferIn, int packedLightIn, Mob mob, float partialTicks, Consumer<PoseStack> rotateFunction, Consumer<PoseStack> scaleFunction){
        if(Minecraft.getInstance().level == null)return false;
        IMaid maid = IMaid.convert(mob);
        if (maid == null) return false;
        ItemStack stack = maid.getBackpackShowItem();
        if(!stack.is(IMPBlocks.BOOMBOX.get().asItem()))return false;
        float handleRaised = 1f - BoomboxItem.getTransferProgress(stack, partialTicks);
        var r = Minecraft.getInstance().level.registryAccess();
        var vc = ItemRenderer.getFoilBufferDirect(bufferIn, Sheets.cutoutBlockSheet(), true, stack.hasFoil());
        BlockState blockState = IMPBlocks.BOOMBOX.get().defaultBlockState().setValue(BoomboxBlock.FACING, Direction.SOUTH);
        poseStack.pushPose();
        rotateFunction.accept(poseStack);
        scaleFunction.accept(poseStack);
        poseStack.translate(-0.5F, 0.625F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(blockState, poseStack, bufferIn, packedLightIn, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        BoomboxBlockEntityRenderer.renderBoombox(poseStack, bufferIn, Direction.SOUTH, packedLightIn, OverlayTexture.NO_OVERLAY, partialTicks, BoomboxItem.getData(stack, r), handleRaised, vc);
        poseStack.popPose();
        return true;
    }
}
