package dev.felnull.imp.data;

import dev.felnull.imp.item.IMPItemTags;
import dev.felnull.imp.item.IMPItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.NotNull;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.BlockTagProviderWrapper;
import org.modsauce.otyacraftenginerenewed.data.provider.ItemTagProviderWrapper;
import org.modsauce.otyacraftenginerenewed.tag.PlatformItemTags;

import java.util.concurrent.CompletableFuture;

public class IMPItemTagProviderWrapper extends ItemTagProviderWrapper {
    public IMPItemTagProviderWrapper(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookup, CrossDataGeneratorAccess crossDataGeneratorAccess, @NotNull BlockTagProviderWrapper blockTagProviderWrapper) {
        super(packOutput, lookup, crossDataGeneratorAccess, blockTagProviderWrapper);
    }

    @Override
    public void generateTag(ItemTagProviderAccess providerAccess) {
        providerAccess.tag(IMPItemTags.CASSETTE_TAPE).add(IMPItems.CASSETTE_TAPE.get(), IMPItems.CASSETTE_TAPE_GLASS.get());

        PlatformItemTags.enderPearls().registering(providerAccess);
        PlatformItemTags.ironNuggets().registering(providerAccess);
        PlatformItemTags.redstoneBlocks().registering(providerAccess);
        PlatformItemTags.stone().registering(providerAccess);
    }
    /*public IMPItemTagProviderWrapper(CrossDataGeneratorAccess crossDataGeneratorAccess, @NotNull BlockTagProviderWrapper blockTagProviderWrapper) {
        super(crossDataGeneratorAccess, blockTagProviderWrapper);
    }

    @Override
    public void generateTag(ItemTagProviderAccess providerAccess) {
        providerAccess.tag(IMPItemTags.CASSETTE_TAPE).add(IMPItems.CASSETTE_TAPE.get(), IMPItems.CASSETTE_TAPE_GLASS.get());

        PlatformItemTags.enderPearls().registering(providerAccess);
        PlatformItemTags.ironNuggets().registering(providerAccess);
        PlatformItemTags.redstoneBlocks().registering(providerAccess);
        PlatformItemTags.stone().registering(providerAccess);
    }*/
}
