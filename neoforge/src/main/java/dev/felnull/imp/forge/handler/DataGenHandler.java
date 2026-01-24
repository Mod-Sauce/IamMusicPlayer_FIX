package dev.felnull.imp.forge.handler;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.data.IamMusicPlayerDataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.modsauce.otyacraftenginerenewed.neoforge.data.CrossDataGeneratorAccesses;

@EventBusSubscriber(modid = IamMusicPlayer.MODID)
public class DataGenHandler {
    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(event));
    }
}
