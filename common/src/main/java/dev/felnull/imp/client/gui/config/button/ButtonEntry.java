package dev.felnull.imp.client.gui.config.button;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class ButtonEntry extends AbstractConfigListEntry<Object> {
    private final Component fieldName;
    private final Button button;
    public ButtonEntry(Component fieldName, Button.OnPress onPress){
        super(fieldName, false);
        button = Button.builder(fieldName, onPress)
                .size(10, 24)
                .build();
        this.fieldName = fieldName;
    }
    @Override
    public boolean isRequiresRestart() {
        return false;
    }

    public void isEnable(boolean enable){
        button.active = enable;
    }

    @Override
    public void setRequiresRestart(boolean b) {

    }

    @Override
    public Component getFieldName() {
        return fieldName;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        button.setPosition(x, y);
        button.setWidth(entryWidth);
        button.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return this.button.mouseClicked(x, y, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        this.button.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double d, double e, int i) {
        return this.button.mouseReleased(d, e, i);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    public void setTooltip(Tooltip tooltip){
        button.setTooltip(tooltip);
    }
}
