package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EarphoneFixedListWidget extends IMPBaseFixedListWidget<EarphoneSaveData.EarphoneLocation>{
    private UUID connected;
    public EarphoneFixedListWidget(int x, int y, int width, int height, @NotNull Component message, @NotNull List<EarphoneSaveData.EarphoneLocation> entryList, @Nullable PressEntry<EarphoneSaveData.EarphoneLocation> onPressEntry) {
        super(x, y, width, height, message, 2, entryList, e -> {
            var item = EarphoneSaveData.findEarphone(Objects.requireNonNull(Minecraft.getInstance().level), e);
            if(item != null)return item.getHoverName();
            return Component.translatable("imp.text.earphone");
        }, onPressEntry, false, null);
    }

    @Override
    public Component getMessage(int index) {
        var r = super.getMessage(index);
        if(Objects.equals(getEntryList().get(index).earphoneUUID(), connected))
            r = r.copy().setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GREEN));
        return r;
    }

    public void setConnected(@Nullable UUID connected) {
        this.connected = connected;
    }
}
