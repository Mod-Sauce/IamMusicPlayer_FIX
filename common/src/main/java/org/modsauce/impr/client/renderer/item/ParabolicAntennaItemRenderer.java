package org.modsauce.impr.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.modsauce.impr.IMPHoliday;
import org.modsauce.impr.client.model.IMPModels;
import org.modsauce.otyacraftenginerenewed.client.renderer.item.BEWLItemRenderer;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

public class ParabolicAntennaItemRenderer implements BEWLItemRenderer {

  @Override
  public void render(
    ItemStack itemStack,
    ItemDisplayContext displayContext,
    PoseStack poseStack,
    MultiBufferSource multiBufferSource,
    float f,
    int i,
    int i1
  ) {
    var antenaModel = IMPModels.PARABOLIC_ANTENNA;

    if (IMPHoliday.isXmas() && IMPModels.XMAS_ANTENNA != null) antenaModel =
      IMPModels.XMAS_ANTENNA;
    else if (
      IMPHoliday.isAprilFool() && IMPModels.APRIL_FOOL_ANTENNA != null
    ) antenaModel = IMPModels.APRIL_FOOL_ANTENNA;

    var name = itemStack.getHoverName().getString();
    if (name.equalsIgnoreCase("kamesuta")) {
      antenaModel = IMPModels.KAMESUTA_ANTENNA;
    } else if (name.equalsIgnoreCase("ikisugi")) {
      antenaModel = IMPModels.IKISUGI_ANTENNA;
    } else if (
      name.equalsIgnoreCase("f.c.o.h") || name.equalsIgnoreCase("fcoh")
    ) {
      antenaModel = IMPModels.FCOH_ANTENNA;
    } else if (name.equalsIgnoreCase("katyou")) {
      antenaModel = IMPModels.KATYOU_ANTENNA;
    }
    var plM = antenaModel.get();

    // NOTE: In 1.21 the special-model-loader API changed and models obtained via `IMPModels.*.get()` may be null
    // if the loader's "load scope" (or equivalent registration) hasn't been performed for this mod namespace.
    // The null-check below already ensures we skip rendering safely when a model isn't available.
    // To resolve this long-term, register the model load scope (or update to the loader's new API) during
    // client initialization so models are guaranteed to be loaded before render-time.
    if (plM == null) {
      return; // Skip rendering if model is not loaded
    }

    var vc = ItemRenderer.getFoilBufferDirect(
      multiBufferSource,
      Sheets.solidBlockSheet(),
      true,
      itemStack.hasFoil()
    );

    poseStack.pushPose();
    if (displayContext == ItemDisplayContext.HEAD) {
      if (IMPHoliday.isXmas()) {
        OERenderUtils.poseRotateX(poseStack, 180f);
        OERenderUtils.poseScaleAll(poseStack, 3f);
        poseStack.translate(0, 2.5f, 0);
      } else {
        long time = System.currentTimeMillis();
        OERenderUtils.poseRotateY(
          poseStack,
          ((float) (time % 5000) / 5000f) * 360f
        );
        poseStack.translate(0, -5, 0);
        OERenderUtils.poseRotateX(
          poseStack,
          -15f + Math.abs(-1f + ((float) (time % 3000) / 1500)) * 30
        );
        poseStack.translate(0, 5, 0);

        OERenderUtils.poseScaleAll(poseStack, 2f);
        poseStack.translate(0, 1.45f, 0);
      }
    }
    OERenderUtils.renderModel(poseStack, vc, plM, i, i1);

    if (IMPHoliday.isXmas()) {
      if (IMPModels.XMAS_ANTENNA_SIDE != null) {
        var sideModel = IMPModels.XMAS_ANTENNA_SIDE.get();
        if (sideModel != null) OERenderUtils.renderModel(
          poseStack,
          vc,
          sideModel,
          i,
          i1
        );
      }

      if (
        displayContext == ItemDisplayContext.HEAD &&
        IMPModels.XMAS_ANTENNA_TAMA != null
      ) {
        var tamaModel = IMPModels.XMAS_ANTENNA_TAMA.get();
        if (tamaModel != null) OERenderUtils.renderModel(
          poseStack,
          vc,
          tamaModel,
          i,
          i1
        );
      }
    }

    poseStack.popPose();
  }
}
