package dev.felnull.imp.data;

import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.IMPItemTags;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.otyacraftengine.data.CrossDataGeneratorAccess;
import dev.felnull.otyacraftengine.data.provider.RecipeProviderWrapper;
import dev.felnull.otyacraftengine.tag.PlatformItemTags;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;

import java.util.function.Consumer;

public class IMPRecipeProviderWrapper extends RecipeProviderWrapper {

    public IMPRecipeProviderWrapper(PackOutput packOutput, CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(packOutput, crossDataGeneratorAccess);
    }

    @Override
    public void generateRecipe(Consumer<FinishedRecipe> exporter, RecipeProviderAccess providerAccess) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.CASSETTE_TAPE_GLASS.get())
                .define('B', AllBlocks.BELT)
                .define('C', AllBlocks.COGWHEEL)
                .define('K', Items.COPPER_INGOT)
                .define('G', PlatformItemTags.glassBlocks())
                .pattern(" B ")
                .pattern("CKC")
                .pattern(" G ")
                .unlockedBy(providerAccess.getHasName(Items.DRIED_KELP), providerAccess.has(Items.DRIED_KELP))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.CASSETTE_TAPE.get())
                .define('B', AllBlocks.BELT)
                .define('C', AllBlocks.COGWHEEL)
                .define('K', Items.COPPER_INGOT)
                .pattern(" B ")
                .pattern("CKC")
                .pattern(" B ")
                .unlockedBy(providerAccess.getHasName(Items.DRIED_KELP), providerAccess.has(Items.DRIED_KELP))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.RADIO_ANTENNA.get())
                .define('R', Items.REDSTONE_TORCH)
                .define('S', AllBlocks.SHAFT)
                .define('P', AllItems.IRON_SHEET)
                .pattern("R")
                .pattern("S")
                .pattern("P")
                .group("antenna")
                .unlockedBy(providerAccess.getHasName(Items.ENDER_PEARL), providerAccess.has(PlatformItemTags.enderPearls().getKey()))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.PARABOLIC_ANTENNA.get())
                .define('A', IMPItems.RADIO_ANTENNA.get())
                .define('P', AllItems.IRON_SHEET)
                .define('L', AllBlocks.REDSTONE_LINK)
                .pattern("APA")
                .pattern("PLP")
                .pattern("APA")
                .group("antenna")
                .unlockedBy(providerAccess.getHasName(AllBlocks.REDSTONE_LINK), providerAccess.has(PlatformItemTags.enderPearls().getKey()))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.BOOMBOX.get())
                .define('E', AllItems.ELECTRON_TUBE)
                .define('P', AllItems.IRON_SHEET)
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .define('B', ItemTags.BUTTONS)
                .pattern("CBC")
                .pattern("NJN")
                .pattern("PEP")
                .unlockedBy(providerAccess.getHasName(Items.JUKEBOX), providerAccess.has(Items.JUKEBOX))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.CASSETTE_DECK.get())
                .define('E', AllItems.ELECTRON_TUBE)
                .define('P', AllItems.IRON_SHEET)
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .define('Z', AllBlocks.COGWHEEL)
                .define('B', ItemTags.BUTTONS)
                .define('C', PlatformItemTags.copperIngots())
                .pattern("ZNZ")
                .pattern("BJC")
                .pattern("PEP")
                .unlockedBy(providerAccess.getHasName(Items.JUKEBOX), providerAccess.has(Items.JUKEBOX))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.MUSIC_MANAGER.get())
                .define('C', AllBlocks.BRASS_CASING)
                .define('P', AllItems.IRON_SHEET)
                .define('G', PlatformItemTags.glassPanes())
                .define('R', AllBlocks.REDSTONE_LINK)
                .define('B', AllBlocks.BELT)
                .define('K', Items.COPPER_INGOT)
                .define('E', AllItems.ELECTRON_TUBE)
                .pattern("PPC")
                .pattern("GKR")
                .pattern("BBE")
                .unlockedBy(providerAccess.getHasName(Items.DIAMOND), providerAccess.has(Items.DIAMOND))
                .save(exporter);
    }
}
