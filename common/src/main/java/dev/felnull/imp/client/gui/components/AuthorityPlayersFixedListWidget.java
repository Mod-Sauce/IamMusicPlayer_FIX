package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.PlayerNameUtil;
import dev.felnull.imp.music.resource.MusicPlayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;
import dev.felnull.imp.client.renderer.PlayerFaceRenderer;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class AuthorityPlayersFixedListWidget extends PlayersFixedListWidget {
    private final Function<UUID, Boolean> selected;
    /**
     * The playlist source, read while rendering so it follows the selected playlist; only the screen path ({@code getSelectedMusicPlayList()}) is allowed — the in-world display draws its rows in the monitor.
     */
    private final Supplier<MusicPlayList> playListSupplier;

    public AuthorityPlayersFixedListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount, @NotNull List<UUID> entryList, @Nullable PressEntry<UUID> onPressEntry, @Nullable FixedListWidget<UUID> old, Function<UUID, Boolean> selected, Supplier<MusicPlayList> playListSupplier) {
        super(x, y, width, height, message, entryShowCount, entryList, onPressEntry, old);
        this.selected = selected;
        this.playListSupplier = playListSupplier;
    }

   /* @Override
    protected void renderOneButton(PoseStack poseStack, UUID item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        int k = this.getYImage(this.isEntryHovered(bnum));
        if (this.selected.apply(item))
            k = 0;
        drawSmartButtonBox(poseStack, bX, bY, getIndividualWidth(), getIndividualHeight(), k);
        OERenderUtils.drawPlayerFace(poseStack, item, bX + 1, bY + 1, getIndividualHeight() - 2);
        drawSmartFixedWidthText(poseStack, getMessage(lnum), bX + getIndividualHeight() + 1, bY + (getIndividualHeight() - 6.5f) / 2f, getIndividualWidth() - 2 - getIndividualHeight() - 1);
    }*/

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, UUID item, int lnum, int bnum, int bX, int bY, int mx, int my, float parTick, boolean selected) {
        int k = this.getYImage(this.isEntryHovered(bnum));
        if (this.selected.apply(item))
            k = 0;
        drawSmartButtonBox(guiGraphics, bX, bY, getIndividualWidth(), getIndividualHeight(), k);
        PlayerFaceRenderer.drawFace(guiGraphics.pose(), item, bX + 1, bY + 1, getIndividualHeight() - 2);
        // Names go through PlayerNameUtil: for the playlist owner use the name stored in the data (still there when offline), the rest go through the original lookup chain.
        drawSmartFixedWidthText(guiGraphics, PlayerNameUtil.playerNameComponent(playListSupplier.get(), item), bX + getIndividualHeight() + 1, bY + (getIndividualHeight() - 6.5f) / 2f, getIndividualWidth() - 2 - getIndividualHeight() - 1);
    }
}
