package dev.felnull.imp.create.client.screen.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

public class FitterSlotWidget extends AbstractWidget {
    @NotNull
    private ItemStack itemStack = ItemStack.EMPTY;
    public static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox_controller/boombox_controller_base.png");
    private final Runnable onChange;
    public FitterSlotWidget(int x, int y, Runnable onChange) {
        super(x, y, 18, 18, Component.empty());
        setFGColor(-2130706433);
        this.onChange = onChange;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mx, int my, float d) {
        OERenderUtils.drawTexture(BG_TEXTURE, guiGraphics.pose(), getX(), getY(), 380, 0, 18, 18, 512, 256);
        guiGraphics.renderItem(itemStack, getX() + 1, getY() + 1);
        if(isHovered()) {
            RenderSystem.enableBlend();
            guiGraphics.fill(RenderType.GUI_OVERLAY, getX() + 1, getY() + 1, getX() + 17, getY() + 17, getFGColor());
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {

    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().player == null) return;
        ItemStack carried = Minecraft.getInstance().player.containerMenu.getCarried();
        if(carried.isEmpty())
            itemStack = ItemStack.EMPTY;
        else itemStack = carried.copyWithCount(1);
        onChange.run();
    }

    public @NotNull ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
    }
}
