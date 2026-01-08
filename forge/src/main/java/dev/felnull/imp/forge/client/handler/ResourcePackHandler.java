package dev.felnull.imp.forge.client.handler;

import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ResourcePackHandler {
    private static final Logger LOGGER = LogManager.getLogger(ResourcePackHandler.class);

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            registerBuiltinResourcePack(event);
        }
    }

    private static void registerBuiltinResourcePack(AddPackFindersEvent event) {
        try {
            var path = ModList.get().getModFileById(IamMusicPlayer.MODID).getFile().findResource("resourcepacks/dev_textures");

            Pack pack = Pack.readMetaAndCreate(
                "builtin/iammusicplayer_dev_textures",
                Component.literal("IMPR Dev Textures"),
                false,
                (packId) -> new PathPackResources(packId, path, true),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.BUILT_IN
            );

            if (pack != null) {
                event.addRepositorySource((packConsumer) -> packConsumer.accept(pack));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register built-in resource pack", e);
        }
    }
}

