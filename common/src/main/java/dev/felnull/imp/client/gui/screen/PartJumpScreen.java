package dev.felnull.imp.client.gui.screen;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.gui.overlay.HandheldMusicHud;
import dev.felnull.imp.client.gui.overlay.MusicInfoOverlay;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.util.BiliBiliIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The in-world part-jump line ({@code IMPKeyMappings#PART_JUMP}): a real {@link Screen}, so {@code 2} does not switch hotbar slots and {@code E} does not open the inventory while it is up; empty {@link #renderBackground}, no pause.
 */
public class PartJumpScreen extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();
    /** Digits accepted: three covers any real video, and the line stays well inside {@code hudBottomMaxWidth}. */
    public static final int MAX_DIGITS = 3;

    private String input = "";

    public PartJumpScreen() {
        super(Component.translatable("imp.text.partJumpTitle"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Empty on purpose: this is a HUD line, not a menu — no dimming, no blur, the world stays visible. */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawWorldPrompt(guiGraphics, currentMusic(), input);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty())
                input = input.substring(0, input.length() - 1);
            return true;
        }
        int digit = digitOf(keyCode);
        if (digit >= 0) {
            input = append(input, digit);
            return true;
        }
        // Every other key is swallowed on purpose: the line owns the keyboard while it is up (see the class comment).
        return true;
    }

    private void submit() {
        int page = parsePage(input);
        // Closed **before** sending: the jump replaces what the boombox plays, and the bottom HUD behind this line is
        // what shows the result.
        onClose();
        if (page < 1)
            return;
        // Nothing to jump at: do not send — a packet that silently does nothing is harder to explain than a line that says there is no boombox.
        if (currentMusic() == null)
            return;
        NetworkManager.sendToServer(IMPPackets.BOOMBOX_PART_JUMP,
                new IMPPackets.BoomboxPartJumpMessage(page).toRFBB());
    }

    /** The music the bottom HUD is showing, i.e. what this line talks about; {@code null} when no boombox is playing. */
    @Nullable
    public static Music currentMusic() {
        var data = MusicInfoOverlay.getPlayerData();
        if (data == null)
            return null;
        return data.getSelectedMusic() == null ? data.getCassetteTapeMusic() : data.getSelectedMusic();
    }

    /**
     * Draws the prompt into the bottom strip of the world HUD: one line in the upper slot (the song name's), so the lyric line right below stays readable.
     */
    public static void drawWorldPrompt(GuiGraphics guiGraphics, @Nullable Music music, String input) {
        var cfg = IamMusicPlayer.getConfig();
        HandheldMusicHud.drawLine(guiGraphics, Component.literal(promptText(music, input)),
                mc.getWindow().getGuiScaledWidth() / 2, HandheldMusicHud.topLineY(), 0xFFFFFFFF,
                Math.max(60, cfg.hudBottomMaxWidth), cfg.hudBottomStyle, 1f);
    }

    /**
     * Draws the prompt inside a GUI at the position the caller picked from that screen's layout; always the translucent strip, whatever {@code hudBottomStyle} says — the on-screen HUD position lands on the hotbar row.
     */
    public static void drawScreenPrompt(GuiGraphics guiGraphics, @Nullable Music music, String input,
                                        int centerX, int y, int maxWidth) {
        HandheldMusicHud.drawLine(guiGraphics, Component.literal(promptText(music, input)), centerX, y, 0xFFFFFFFF,
                Math.max(60, maxWidth), IMPConfig.HudBottomStyle.STRIP, 1f);
    }

    /**
     * {@code jump to part P2/7 ▸ 3_}: the part marker is left out when the parts are unknown, and the whole line is
     * replaced by an explanation when there is no boombox.
     */
    public static String promptText(@Nullable Music music, String input) {
        var source = music == null ? null : music.getSource();
        if (source == null)
            return Component.translatable("imp.text.partJumpNoBoombox").getString();
        var prefix = Component.translatable("imp.text.partJumpTitle").getString();
        var parts = BiliBiliParts.known(source.getIdentifier());
        if (parts.size() > 1)
            prefix = prefix + " P" + BiliBiliIds.pageOf(source.getIdentifier()) + "/" + parts.size();
        return prefix + " ▸ " + input + "_";
    }

    /** The digit a key stands for ({@code 0-9} on the main row or on the numpad), or {@code -1}. */
    public static int digitOf(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9)
            return keyCode - GLFW.GLFW_KEY_0;
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9)
            return keyCode - GLFW.GLFW_KEY_KP_0;
        return -1;
    }

    /** Appends a digit, capped at {@link #MAX_DIGITS} (a leading zero would only be a part number nobody has). */
    public static String append(String input, int digit) {
        if (input.isEmpty() && digit == 0)
            return input;
        if (input.length() >= MAX_DIGITS)
            return input;
        return input + digit;
    }

    /** Parses the typed number; {@code -1} when nothing usable was typed. */
    public static int parsePage(@Nullable String input) {
        if (input == null || input.isEmpty())
            return -1;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;   // at most MAX_DIGITS digits, so this cannot really happen
        }
    }
}
