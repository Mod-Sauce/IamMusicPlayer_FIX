package dev.felnull.imp.fabric.client;

import dev.felnull.imp.IamMusicPlayer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourcePackHandler {
    private static final Logger LOGGER = LogManager.getLogger(ResourcePackHandler.class);

    public static void init() {
        registerBuiltinResourcePack();
    }

    private static void registerBuiltinResourcePack() {
        FabricLoader.getInstance().getModContainer(IamMusicPlayer.MODID).ifPresent(modContainer -> {
            boolean registered = ResourceManagerHelper.registerBuiltinResourcePack(
                new ResourceLocation(IamMusicPlayer.MODID, "dev_textures"),
                modContainer,
                Component.literal("IMPR Dev Textures"),
                ResourcePackActivationType.NORMAL
            );

            if (registered) {
                LOGGER.info("Successfully registered built-in resource pack: IMPR Dev Textures");
            } else {
                LOGGER.error("Failed to register built-in resource pack: IMPR Dev Textures");
            }
        });
    }
}

