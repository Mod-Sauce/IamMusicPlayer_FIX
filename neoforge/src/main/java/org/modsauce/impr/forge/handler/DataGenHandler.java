package org.modsauce.impr.forge.handler;

import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.data.IamMusicPlayerDataGenerator;
import dev.felnull.otyacraftengine.forge.data.CrossDataGeneratorAccesses;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenHandler {
    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(event));
    }
}
