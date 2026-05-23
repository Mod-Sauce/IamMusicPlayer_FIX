package dev.felnull.imp.create.client.screen.widget;

import dev.felnull.imp.create.block_entity.data.BoomboxControllerData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

public class FunctionSelectWidget extends EditBox {
    private BoomboxControllerData.Mode mode;
    private final Runnable onChange;
    public FunctionSelectWidget(int x, int y, int w, int h, BoomboxControllerData.Mode mode, Runnable onChange) {
        super(Minecraft.getInstance().font, x, y, w, h, Component.empty());
        setEditable(false);
        setTooltip(null);
        setMaxLength(114514);
        this.mode = mode;
        this.onChange = onChange;
    }

    private void next() {
        var items = Arrays.asList(BoomboxControllerData.Mode.values());
        int index = (items.indexOf(mode) + 1) % items.size();
        mode = items.get(index);
        update();
        playSound();
    }

    private void previous() {
        var items = Arrays.asList(BoomboxControllerData.Mode.values());
        int index = items.indexOf(mode) - 1;
        if (index < 0) index = items.size() - 1;
        mode = items.get(index);
        update();
        playSound();
    }

    public BoomboxControllerData.Mode getMode() {
        return mode;
    }

    public void update(){
        setValue(mode.getName().getString());
        onChange.run();
    }

    public void playSound(){
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2));
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        if(g < 0.5f)
            next();
        if(g > -0.5f)
            previous();
        return true;
    }

    @Override
    public void renderWidget(GuiGraphics arg, int m, int n, float f) {
        super.renderWidget(arg, m, n, f);
        if(isHovered){
            var list = new ArrayList<Component>();
            var title = Component.translatable("imp.text.boombox_controller.modes");
            list.add(title);
            BoomboxControllerData.Mode[] values = BoomboxControllerData.Mode.values();
            for (BoomboxControllerData.Mode mode : values) {
                list.add(Component.literal("> ").append(mode.getName())
                        .withStyle(mode == getMode() ? ChatFormatting.BOLD : ChatFormatting.RESET));
            }
            arg.renderTooltip(Minecraft.getInstance().font, list, Optional.empty(), m, n);
        }
    }

    public void setMode(BoomboxControllerData.Mode mode) {
        this.mode = mode;
        update();
    }

    public void setModeWithoutUpdate(BoomboxControllerData.Mode mode) {
        this.mode = mode;
        setValue(mode.getName().getString());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {

    }
}
