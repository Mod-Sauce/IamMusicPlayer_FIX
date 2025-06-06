package dev.felnull.imp.fabric.data;

import dev.felnull.imp.data.IamMusicPlayerDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import org.modsauce.otyacraftenginerenewed.fabric.data.CrossDataGeneratorAccesses;

public class IamMusicPlayerDataGeneratorFabric implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(fabricDataGenerator));
    }
}
