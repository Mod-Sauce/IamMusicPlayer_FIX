package dev.felnull.imp.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * This mod's key bindings: registered through Architectury's {@link KeyMappingRegistry} so the binding lives in
 * {@code common} and works on both loaders; vanilla's Controls screen rebinds or clears it, so there is no config option.
 */
public final class IMPKeyMappings {
    /**
     * Part jump: brings up the bottom input line, where the part number is typed and Enter jumps (Esc cancels), for a B站 video playing as one playlist entry. Default {@code ~}, which vanilla leaves unused.
     */
    public static final KeyMapping PART_JUMP = new KeyMapping("key.iammusicplayer.part_jump",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.iammusicplayer");

    private IMPKeyMappings() {
    }

    public static void init() {
        KeyMappingRegistry.register(PART_JUMP);
    }
}
