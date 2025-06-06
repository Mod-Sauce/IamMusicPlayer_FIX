package dev.felnull.imp.forge.handler;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.data.IamMusicPlayerDataGenerator;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.modsauce.otyacraftenginerenewed.forge.data.CrossDataGeneratorAccesses;

@Mod.EventBusSubscriber(modid = IamMusicPlayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenHandler {
    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(event));
    }
}
