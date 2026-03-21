package dev.felnull.imp.data;

import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.IMPItems;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.RecipeProviderWrapper;
import org.modsauce.otyacraftenginerenewed.tag.PlatformItemTags;

import java.util.concurrent.CompletableFuture;

public class IMPRecipeProviderWrapper extends RecipeProviderWrapper {

    public IMPRecipeProviderWrapper(
            PackOutput packOutput,
            CompletableFuture<HolderLookup.Provider> lookup,
            CrossDataGeneratorAccess crossDataGeneratorAccess
    ) {
        super(packOutput, lookup, crossDataGeneratorAccess);
    }

    @Override
    public void generateRecipe(
            RecipeOutput consumer, RecipeProviderAccess providerAccess
    ) {
        ShapelessRecipeBuilder.shapeless(
                        RecipeCategory.MISC,
                        IMPItems.CASSETTE_TAPE.get()
                )
                .requires(PlatformItemTags.ironNuggets())
                .requires(PlatformItemTags.stone())
                .requires(PlatformItemTags.redstoneDusts())
                .requires(Items.DRIED_KELP)
                .unlockedBy(
                        providerAccess.getHasName(Items.DRIED_KELP),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP)
                )
                .save(consumer);

        ShapelessRecipeBuilder.shapeless(
                        RecipeCategory.MISC,
                        IMPItems.CASSETTE_TAPE_GLASS.get()
                )
                .requires(PlatformItemTags.ironNuggets())
                .requires(PlatformItemTags.glassBlocks())
                .requires(PlatformItemTags.redstoneDusts())
                .requires(Items.DRIED_KELP)
                .unlockedBy(
                        providerAccess.getHasName(Items.DRIED_KELP),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.DRIED_KELP)
                )
                .save(consumer);

        ShapedRecipeBuilder.shaped(
                        RecipeCategory.MISC,
                        IMPItems.RADIO_ANTENNA.get()
                )
                .define('E', PlatformItemTags.enderPearls().getKey())
                .define('I', PlatformItemTags.ironIngots())
                .pattern("E")
                .pattern("I")
                .pattern("I")
                .group("antenna")
                .unlockedBy(
                        providerAccess.getHasName(Items.ENDER_PEARL),
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(PlatformItemTags.enderPearls().getKey()))
                )
                .save(consumer);

        ShapedRecipeBuilder.shaped(
                        RecipeCategory.MISC,
                        IMPItems.PARABOLIC_ANTENNA.get()
                )
                .define('E', PlatformItemTags.enderPearls().getKey())
                .define('I', PlatformItemTags.ironIngots())
                .define('B', Blocks.IRON_BLOCK)
                .pattern("IEI")
                .pattern("I I")
                .pattern(" B ")
                .group("antenna")
                .unlockedBy(
                        providerAccess.getHasName(Items.ENDER_PEARL),
                        InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(PlatformItemTags.enderPearls().getKey()))
                )
                .save(consumer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.BOOMBOX.get())
                .define('T', PlatformItemTags.ironNuggets())
                .define('I', PlatformItemTags.ironIngots())
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .define('B', ItemTags.BUTTONS)
                .pattern("TBT")
                .pattern("NJN")
                .pattern("III")
                .unlockedBy(
                        providerAccess.getHasName(Items.JUKEBOX),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.JUKEBOX)
                )
                .save(consumer);

        ShapedRecipeBuilder.shaped(
                        RecipeCategory.MISC,
                        IMPBlocks.CASSETTE_DECK.get()
                )
                .define('R', PlatformItemTags.redstoneDusts())
                .define('I', PlatformItemTags.ironIngots())
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .pattern("IRI")
                .pattern("NJN")
                .pattern("III")
                .unlockedBy(
                        providerAccess.getHasName(Items.JUKEBOX),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.JUKEBOX)
                )
                .save(consumer);

        ShapedRecipeBuilder.shaped(
                        RecipeCategory.MISC,
                        IMPBlocks.MUSIC_MANAGER.get()
                )
                .define('D', PlatformItemTags.diamonds())
                .define('I', PlatformItemTags.ironIngots())
                .define('G', PlatformItemTags.glassPanes())
                .define('R', PlatformItemTags.redstoneDusts())
                .define('B', PlatformItemTags.diamonds())
                .pattern("III")
                .pattern("DGR")
                .pattern("BII")
                .unlockedBy(
                        providerAccess.getHasName(Items.DIAMOND),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.DIAMOND)
                )
                .save(consumer);

        ShapelessRecipeBuilder.shapeless(
                RecipeCategory.MISC,
                IMPItems.MANUAL.get()
        ).requires(PlatformItemTags.books())
                .unlockedBy(
                        providerAccess.getHasName(Items.BOOK),
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.BOOK)
                )
                .requires(Items.NOTE_BLOCK)
                .save(consumer);
    }
}
