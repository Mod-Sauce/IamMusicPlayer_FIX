package dev.felnull.imp.client.gui.config.proxy;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.net.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class ProxyEntry extends AbstractConfigListEntry<UserProxy> {
    private UserProxy userProxy;
    private final UserProxy original;
    private final CycleButton<Proxy.Type> typeButton;
    private final EditBox host;
    private final EditBox port;
    private final UserProxy defaultValue;
    private final Button resetButton;

    private static final Component resetText = Component.translatable("text.cloth-config.reset_value");
    public ProxyEntry(Component fieldName, Consumer<UserProxy> saveConsumer, boolean requiresRestart, UserProxy defaultValue, UserProxy nowValue) {
        super(fieldName, requiresRestart);
        this.defaultValue = defaultValue;
        userProxy = nowValue;
        original = nowValue;
        typeButton = CycleButton.<Proxy.Type>builder(
                v -> Component.literal(v.name().toUpperCase(Locale.ROOT))
        ).withValues(Proxy.Type.values())
        .create(0, 0, 10, getItemHeight() - 4, Component.empty(), (cycleButton, object) -> changed());
        typeButton.setValue(nowValue.type());

        host = new EditBox(Minecraft.getInstance().font, 0, 0, 10, getItemHeight() - 4, Component.empty()) {
            public void renderWidget(GuiGraphics graphics, int int_1, int int_2, float float_1) {
                textFieldPreRender(this);
                super.renderWidget(graphics, int_1, int_2, float_1);
            }
        };
        host.setValue(nowValue.host());
        host.setResponder(s -> changed());
        host.setCursorPosition(0);  // fuck ojang
        host.setHighlightPos(0);

        port = new EditBox(Minecraft.getInstance().font, 0, 0, 10, getItemHeight() - 4, Component.empty()) {
            public void renderWidget(GuiGraphics graphics, int int_1, int int_2, float float_1) {
                textFieldPreRender(this);
                super.renderWidget(graphics, int_1, int_2, float_1);
            }
        };
        port.setValue(Objects.toString(nowValue.port()));
        port.setResponder(s -> changed());
        port.setFilter(text -> text.matches("\\d*"));
        port.setCursorPosition(0);
        port.setHighlightPos(0);

        resetButton = Button.builder(resetText, (widget) -> {
            userProxy = defaultValue;
            typeButton.setValue(defaultValue.type());
            host.setValue(defaultValue.host());
            port.setValue(Objects.toString(defaultValue.port()));
        })
                .bounds(0, 0, Minecraft.getInstance().font.width(resetText) + 6, 20)
                .build();

        saveCallback = saveConsumer;
    }

    protected void textFieldPreRender(EditBox widget) {
        widget.setTextColor(this.getConfigError().isPresent() ? 16733525 : 14737632);
    }

    @Override
    public Optional<UserProxy> getDefaultValue() {
        return Optional.of(defaultValue);
    }

    @Override
    public UserProxy getValue() {
        return userProxy;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(typeButton, host, port, resetButton);
    }

    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        return List.of(typeButton, host, port, resetButton);
    }

    private void changed(){
        try {
            userProxy = new UserProxy(typeButton.getValue(), host.getValue(), Integer.parseInt(port.getValue()));
        }catch (Exception ignore){}
    }

    @Override
    public Optional<Component> getError() {
        try{
            if(!new UserProxy(typeButton.getValue(), host.getValue(), Integer.parseInt(port.getValue())).isValid())
                throw new RuntimeException();
        }catch (Exception exception){
            return Optional.of(Component.translatable("imp.text.proxy.error"));
        }
        return Optional.empty();
    }

    @Override
    public boolean isEdited() {
        return super.isEdited() || !original.equals(userProxy);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        typeButton.setWidth(33);
        host.setWidth(50);
        port.setWidth(30);
        port.setPosition(x + entryWidth - resetButton.getWidth() - port.getWidth() - 4, y);
        host.setPosition(port.getX() - host.getWidth() - 4, y);
        typeButton.setPosition(host.getX() - typeButton.getWidth() - 4, y);
        host.setEditable(typeButton.getValue() != Proxy.Type.DIRECT);
        port.setEditable(typeButton.getValue() != Proxy.Type.DIRECT);
        resetButton.setX(x + entryWidth - resetButton.getWidth());
        resetButton.setY(y);
        resetButton.active = !userProxy.equals(defaultValue);
        typeButton.setMessage(Component.literal(typeButton.getValue().name().toUpperCase(Locale.ROOT)));

        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        graphics.drawString(Minecraft.getInstance().font, getDisplayedFieldName(), x, y + 6, 16777215);
        typeButton.render(graphics, mouseX, mouseY, delta);
        host.render(graphics, mouseX, mouseY, delta);
        port.render(graphics, mouseX, mouseY, delta);
        resetButton.render(graphics, mouseX, mouseY, delta);
    }
}
