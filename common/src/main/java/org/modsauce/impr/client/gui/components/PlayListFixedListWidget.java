package org.modsauce.impr.client.gui.components;

import org.modsauce.impr.music.resource.MusicPlayList;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PlayListFixedListWidget extends IMPBaseFixedListWidget<MusicPlayList> {
    public PlayListFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount, @NotNull List<MusicPlayList> entryList, @Nullable PressEntry<MusicPlayList> onPressEntry, boolean selectable, @Nullable FixedListWidget<MusicPlayList> old) {
        super(x, y, width, height, message, entryShowCount, entryList, n -> Component.literal(n.getName()), onPressEntry, selectable, old);
    }
}
