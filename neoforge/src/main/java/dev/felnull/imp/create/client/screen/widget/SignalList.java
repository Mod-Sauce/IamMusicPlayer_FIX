package dev.felnull.imp.create.client.screen.widget;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.create.block_entity.data.BoomboxControllerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.function.Consumer;

public class SignalList extends ObjectSelectionList<SignalList.SignalListEntry> {
    public static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox_controller/boombox_controller_base.png");
    public final Consumer<BoomboxControllerData.Signal> signalConsumer;
    public SignalList(int x, int y, int w, int h, Consumer<BoomboxControllerData.Signal> onSelected) {
        super(Minecraft.getInstance(), w, h, y, 25);
        this.setX(x);
        this.setY(y);
        this.signalConsumer = onSelected;
    }

    public void setData(BoomboxControllerData data) {
        var oldSelected = getSelected();
        var needChangeSelect = data.getEditableSignals().size() != children().size();
        var index = getSelectedIndex();

        clearEntries();

        for (BoomboxControllerData.Signal signal : data.signals()) {
            addEntry(new SignalListEntry(signal));
        }

        if (oldSelected == null) return;

        if(!needChangeSelect){
            setSelected(children().get(index));
            return;
        }

        for (var child : children()) {
            if (child instanceof SignalListEntry entry) {
                if (entry.signal.equals(oldSelected.signal)) {
                    setSelected(entry);
                    break;
                }
            }
        }
    }

    public static class SignalListEntry extends Entry<SignalListEntry>{
        public BoomboxControllerData.Signal signal;

        public SignalListEntry(BoomboxControllerData.Signal signal){
            this.signal = signal;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.empty();
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            OERenderUtils.drawTexture(BG_TEXTURE, guiGraphics.pose(), left + 10, top + 1, 380, 0, 18, 18, 512, 256);
            OERenderUtils.drawTexture(BG_TEXTURE, guiGraphics.pose(), left + 10 + 18, top + 1, 380, 0, 18, 18, 512, 256);
            guiGraphics.renderFakeItem(signal.left(), left + 11, top + 2);
            guiGraphics.renderFakeItem(signal.right(), left + 11 + 18, top + 2);

            if(signal.mode() != BoomboxControllerData.Mode.NONE)
                OERenderUtils.drawTexture(BG_TEXTURE, guiGraphics.pose(), left + 70, top + 1,
                        signal.mode().input ? 380 + 19 : 380 + 19 + 19,
                        0, 18, 18, 512, 256);

            var text = signal.mode().getName();
            var font = Minecraft.getInstance().font;
            guiGraphics.drawString(font, text, left + width - font.width(text) - 16, top + height / 2 - font.lineHeight / 2, 0XFFFFFFFF, false);
        }
    }

    @Override
    protected void renderListBackground(@NotNull GuiGraphics guiGraphics) {}

    @Override
    protected int getScrollbarPosition() {
        return getX() + width - 6;
    }

    public int getSelectedIndex(){
        return children().indexOf(getSelected());
    }

    @Override
    public void setSelected(@Nullable SignalList.SignalListEntry arg) {
        var old = getSelected();
        super.setSelected(arg);
        if(old == getSelected())return;
        if (arg != null) {
            signalConsumer.accept(arg.signal);
        }else signalConsumer.accept(null);
    }
}
