package dev.felnull.imp.client.gui.overlay;

import dev.felnull.imp.client.music.MusicSyncManager;
import dev.felnull.imp.music.resource.MusicPlayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The "someone invited you to join a playlist" HUD line: it does not presume "accept" (the row accepts and rejects), shows
 * for 7 s with the timer restarting when the invite set changes, and with several invites shows the first + "(and N more)".
 * Hooked on {@code ClientGuiEvent.RENDER_HUD}, gated by {@code uiConfig.enablePlayListInviteNotice}.
 */
public class PlayListInviteOverlay {
    private static final Minecraft mc = Minecraft.getInstance();
    /** Display duration (milliseconds): 7 seconds. */
    private static final long SHOW_MS = 7000L;
    /** Distance from the bottom of the screen (pixels): above the hotbar/chat, near the action bar text. */
    private static final int BOTTOM_MARGIN = 55;

    private long hideAt;
    /** The invite set "already notified" last time (used to decide whether to restart the display timer). */
    private Set<UUID> lastShown = Set.of();

    public void render(GuiGraphics guiGraphics, float tickDelta) {
        if (mc.player == null || mc.level == null || mc.options.hideGui)
            return;

        var invites = MusicSyncManager.getInstance().getInvitedPlayList();
        Set<UUID> current = new HashSet<>();
        if (invites != null)
            for (MusicPlayList pl : invites)
                current.add(pl.getUuid());

        // The set changed (new invite / just logged in and got the list) → show again for 7 seconds
        if (!current.equals(lastShown)) {
            lastShown = current;
            hideAt = current.isEmpty() ? 0L : System.currentTimeMillis() + SHOW_MS;
        }

        if (invites == null || invites.isEmpty() || System.currentTimeMillis() >= hideAt)
            return;

        var first = invites.get(0);
        Component text = Component.translatable("imp.text.playlistInviteNotice",
                first.getAuthority().getOwnerName(), first.getName());
        if (invites.size() > 1)
            text = Component.empty().append(text)
                    .append(Component.translatable("imp.text.playlistInviteMore", invites.size() - 1));

        int x = (guiGraphics.guiWidth() - mc.font.width(text)) / 2;
        int y = guiGraphics.guiHeight() - BOTTOM_MARGIN;
        // With shadow (shadow=true): the game view is behind it, and without a shadow it is unreadable on bright terrain
        guiGraphics.drawString(mc.font, text, x, y, 0xFFFFD24A, true);
    }
}
