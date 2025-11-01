package org.modsauce.impr.fabric.data;

import org.modsauce.impr.data.IamMusicPlayerDataGenerator;
import org.modsauce.otyacraftenginerenewed.fabric.data.CrossDataGeneratorAccesses;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class IamMusicPlayerDataGeneratorFabric implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        IamMusicPlayerDataGenerator.init(CrossDataGeneratorAccesses.create(fabricDataGenerator));
    }
}
