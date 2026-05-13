package dev.felnull.imp.client.gui.config;

import com.sedmelluq.discord.lavaplayer.natives.ConnectorNativeLibLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class AllButtons {
    private static final Map<String, ButtonEntry> BUTTONS = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();
    public static void registry(String id, Component text, Button.OnPress onPress){
        BUTTONS.put(id, new ButtonEntry(text, onPress));
    }

    public static ButtonEntry get(String id){
        return BUTTONS.get(id);
    }

    static {
        registry("reloadLava", Component.translatable("imp.text.lava.reload"), button -> {
            try {
                ConnectorNativeLibLoader.loadConnectorLibrary();
            }catch (Exception e){
                Minecraft.getInstance().getToasts().addToast(SystemToast.multiline(
                        Minecraft.getInstance(),
                        SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                        Component.translatable("imp.text.lava.failed1"),
                        Component.translatable("imp.text.lava.failed2")
                ));
                LOGGER.error(e);
                return;
            }
            Minecraft.getInstance().getToasts().addToast(new SystemToast(SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                    Component.translatable("imp.text.lava.success"), null));
        });

        registry("openNetMusic", Component.translatable("text.autoconfig.iammusicplayer.option.openNetMusic"), button ->
                Util.getPlatform().openUri(Component.translatable("text.autoconfig.iammusicplayer.option.openNetMusic.url").getString()));

        registry("settingHud", Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos"), button ->
                Minecraft.getInstance().setScreen(new HUDSettingScreen(Minecraft.getInstance().screen)));

        registry("openMcedia", Component.translatable("text.autoconfig.iammusicplayer.option.openMcedia"), button ->
                Util.getPlatform().openUri(
                        "https://github.com/tobyprime/Mcedia"
                ));
    }
}
