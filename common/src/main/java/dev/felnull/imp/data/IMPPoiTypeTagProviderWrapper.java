package dev.felnull.imp.data;

import dev.felnull.imp.entity.village.IMPPoiType;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.PoiTypeTagProviderWrapper;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;

import java.util.concurrent.CompletableFuture;

public class IMPPoiTypeTagProviderWrapper extends PoiTypeTagProviderWrapper {
    public IMPPoiTypeTagProviderWrapper(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup, CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(packOutput, lookup, crossDataGeneratorAccess);
    }

    @Override
    public void generateTag(TagProviderAccess<PoiType, TagAppenderWrapper<PoiType>> providerAccess) {
        providerAccess.tag(PoiTypeTags.ACQUIRABLE_JOB_SITE).add(IMPPoiType.getResourceKey(IMPPoiType.DJ.get()));
    }
}
