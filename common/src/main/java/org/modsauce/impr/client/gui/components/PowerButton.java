package org.modsauce.impr.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.modsauce.impr.client.gui.screen.IMPBaseContainerScreen;

// NOTE: ImageButton constructor signature changed in newer mappings (1.21+).
// The implementation below already constructs and passes a `WidgetSprites` instance.
// This comment documents the API change and can be removed once the codebase no longer needs to reference it.
public class PowerButton extends ImageButton {

  private final IMPBaseContainerScreen<?> screen;
  private final ResourceLocation resourceLocation;
  private final int xTexStart;
  private final int yTexStart;
  private final int textureWidth;
  private final int textureHeight;

  public PowerButton(
    IMPBaseContainerScreen<?> screen,
    int x,
    int y,
    int width,
    int height,
    int xTexStart,
    int yTexStart,
    ResourceLocation resourceLocation,
    int textureWidth,
    int textureHeight
  ) {
    // NOTE: Constructor uses `WidgetSprites` directly to provide texture(s) for the ImageButton.
    // The previous versions used individual texture parameters; this line documents why the `WidgetSprites` usage is present.
    super(
      x,
      y,
      width,
      height,
      new net.minecraft.client.gui.components.WidgetSprites(
        resourceLocation,
        resourceLocation
      ),
      button -> onPower(screen)
    );
    this.screen = screen;
    this.resourceLocation = resourceLocation;
    this.xTexStart = xTexStart;
    this.yTexStart = yTexStart;
    this.textureWidth = textureWidth;
    this.textureHeight = textureHeight;
  }

  @Override
  public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
    // RenderSystem.setShader(GameRenderer::getPositionTexShader);
    //  RenderSystem.setShaderTexture(0, this.resourceLocation);
    int tx = this.xTexStart;
    int ty = this.yTexStart;

    if (this.isHoveredOrFocused()) ty += this.height;

    if (screen.isPowered()) tx += this.width;

    //  RenderSystem.enableDepthTest();
    guiGraphics.blit(
      this.resourceLocation,
      this.getX(),
      this.getY(),
      (int) tx,
      (int) ty,
      this.width,
      this.height,
      this.textureWidth,
      this.textureHeight
    );
    /*if (this.isHoveredOrFocused())
            this.renderToolTip(poseStack, i, j);*/
  }

  private static void onPower(IMPBaseContainerScreen<?> screen) {
    screen.insPower(!screen.isPowered());
  }
}
