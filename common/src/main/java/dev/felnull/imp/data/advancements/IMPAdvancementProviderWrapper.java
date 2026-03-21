package dev.felnull.imp.data.advancements;

import com.google.common.collect.ImmutableList;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.AdvancementProviderWrapper;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class IMPAdvancementProviderWrapper extends AdvancementProviderWrapper {

    public IMPAdvancementProviderWrapper(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup, CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(packOutput, lookup, crossDataGeneratorAccess, ImmutableList.of(new IMPAdvancements(crossDataGeneratorAccess)));
    }
}
