package org.modsauce.impr.client.handler;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.client.gui.overlay.MusicLinesOverlay;
import org.modsauce.impr.client.renderer.item.hand.BoomboxHandRenderer;
import org.modsauce.impr.item.BoomboxItem;
import org.modsauce.otyacraftenginerenewed.client.event.MoreRenderEvent;

public class RenderHandler {

  private static final MusicLinesOverlay MUSIC_LINES_OVERLAY =
    new MusicLinesOverlay();

  public static void init() {
    MoreRenderEvent.RENDER_ITEM_IN_HAND.register(
      RenderHandler::onRenderItemInHand
    );
    MoreRenderEvent.RENDER_ARM_WITH_ITEM.register(
      RenderHandler::onRenderArmWithItem
    );
    // Render HUD registration is intentionally disabled for 1.21+.
    // The `ClientGuiEvent.RENDER_HUD` listener signature changed in 1.21: it now provides a DeltaTracker-like
    // parameter instead of a raw float `tickDelta`. Re-enabling HUD rendering requires adapting the existing
    // `renderHud` method to accept the new parameter (or writing a small adapter that extracts an interpolated
    // tick delta from the DeltaTracker and forwards it to the overlay).
    // When an adapter is implemented, re-enable with:
    // ClientGuiEvent.RENDER_HUD.register(RenderHandler::renderHud);
  }

  // HUD renderer kept for reference and manual adaptation.
  // In 1.21 the render event provides a DeltaTracker (or equivalent) instead of a single float tickDelta.
  // To restore HUD rendering, implement an adapter that extracts the required interpolated tick delta
  // from the DeltaTracker and call the overlay's render method below.
  /*
    Example (pseudo):
    private static void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        // float tickDelta = extract/interpolate from deltaTracker
        // if (IamMusicPlayer.getConfig().showMusicLines)
        //     MUSIC_LINES_OVERLAY.render(guiGraphics, tickDelta);
    }
    */
  /*private static void renderHud(GuiGraphics guiGraphics, float tickDelta) {
        if (IamMusicPlayer.getConfig().showMusicLines)
            MUSIC_LINES_OVERLAY.render(guiGraphics, tickDelta);
    }*/

  private static EventResult onRenderItemInHand(
    PoseStack poseStack,
    MultiBufferSource multiBufferSource,
    InteractionHand hand,
    int packedLight,
    float partialTicks,
    float interpolatedPitch,
    float swingProgress,
    float equipProgress,
    ItemStack stack
  ) {
    if (stack.is(IMPBlocks.BOOMBOX.get().asItem())) {
      BoomboxHandRenderer.render(
        poseStack,
        multiBufferSource,
        hand,
        packedLight,
        partialTicks,
        interpolatedPitch,
        swingProgress,
        equipProgress,
        stack
      );
      return EventResult.interruptFalse();
    }
    return EventResult.pass();
  }

  private static EventResult onRenderArmWithItem(
    ItemInHandLayer<
      ? extends LivingEntity,
      ? extends EntityModel<?>
    > itemInHandLayer,
    LivingEntity livingEntity,
    ItemStack itemStack,
    ItemDisplayContext displayContext,
    HumanoidArm humanoidArm,
    PoseStack poseStack,
    MultiBufferSource multiBufferSource,
    int i
  ) {
    if (
      itemStack.is(IMPBlocks.BOOMBOX.get().asItem()) &&
      BoomboxItem.getTransferProgress(itemStack) >= 1f
    ) {
      BoomboxHandRenderer.renderArmWithItem(
        itemInHandLayer,
        livingEntity,
        itemStack,
        displayContext,
        humanoidArm,
        poseStack,
        multiBufferSource,
        i
      );
      return EventResult.interruptFalse();
    }
    return EventResult.pass();
  }
}
