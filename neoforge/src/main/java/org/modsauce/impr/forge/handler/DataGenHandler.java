package org.modsauce.impr.forge.handler;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.data.IamMusicPlayerDataGenerator;
import org.modsauce.otyacraftenginerenewed.neoforge.data.CrossDataGeneratorAccesses;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenHandler {
    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(event));
    }
}
