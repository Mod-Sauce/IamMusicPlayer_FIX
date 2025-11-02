package org.modsauce.impr.neoforge.handler;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.data.IamMusicPlayerDataGenerator;
import org.modsauce.otyacraftenginerenewed.neoforge.data.CrossDataGeneratorAccesses;

@EventBusSubscriber(
  modid = IamMusicPlayer.MODID,
  bus = EventBusSubscriber.Bus.MOD
)
public class DataGenHandler {

  @SubscribeEvent
  public static void onDataGen(GatherDataEvent event) {
    IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(event));
  }
}
