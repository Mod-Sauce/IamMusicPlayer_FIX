package org.modsauce.impr.data;

import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.item.IMPItemTags;
import org.modsauce.impr.item.IMPItems;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.RecipeProviderWrapper;
import org.modsauce.otyacraftenginerenewed.tag.PlatformItemTags;

public class IMPRecipeProviderWrapper extends RecipeProviderWrapper {

  public IMPRecipeProviderWrapper(
    PackOutput packOutput,
    CrossDataGeneratorAccess crossDataGeneratorAccess
  ) {
    super(packOutput, crossDataGeneratorAccess);
  }

  // NOTE: Legacy recipe generation retained for reference.
  // The recipe provider API changed in 1.21; TriggerInstance-based criteria must be migrated to the new Criterion/Advancement API.
  // Keep the implementation below commented out as a reference for a future migration to the 1.21 recipe/criterion API.
  /*@Override
    public void generateRecipe(RecipeOutput exporter, RecipeProviderAccess providerAccess) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, IMPItems.MANUAL.get())
                .requires(Items.BOOK)
                .requires(IMPItemTags.CASSETTE_TAPE)
                .unlockedBy(providerAccess.getHasName(Items.BOOK), providerAccess.has(Items.BOOK))
                .save(exporter);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, IMPItems.CASSETTE_TAPE.get())
                .requires(PlatformItemTags.ironNuggets().getKey())
                .requires(PlatformItemTags.stone().getKey())
                .requires(PlatformItemTags.redstoneDusts())
                .requires(Items.DRIED_KELP)
                .unlockedBy(providerAccess.getHasName(Items.DRIED_KELP), providerAccess.has(Items.DRIED_KELP))
                .save(exporter);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, IMPItems.CASSETTE_TAPE_GLASS.get())
                .requires(PlatformItemTags.ironNuggets().getKey())
                .requires(PlatformItemTags.glassBlocks())
                .requires(PlatformItemTags.redstoneDusts())
                .requires(Items.DRIED_KELP)
                .unlockedBy(providerAccess.getHasName(Items.DRIED_KELP), providerAccess.has(Items.DRIED_KELP))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.RADIO_ANTENNA.get())
                .define('E', PlatformItemTags.enderPearls().getKey())
                .define('I', PlatformItemTags.ironIngots())
                .pattern("E")
                .pattern("I")
                .pattern("I")
                .group("antenna")
                .unlockedBy(providerAccess.getHasName(Items.ENDER_PEARL), providerAccess.has(PlatformItemTags.enderPearls().getKey()))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPItems.PARABOLIC_ANTENNA.get())
                .define('E', PlatformItemTags.enderPearls().getKey())
                .define('I', PlatformItemTags.ironIngots())
                .define('B', Blocks.IRON_BLOCK)
                .pattern("IEI")
                .pattern("I I")
                .pattern(" B ")
                .group("antenna")
                .unlockedBy(providerAccess.getHasName(Items.ENDER_PEARL), providerAccess.has(PlatformItemTags.enderPearls().getKey()))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.BOOMBOX.get())
                .define('T', PlatformItemTags.ironNuggets().getKey())
                .define('I', PlatformItemTags.ironIngots())
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .define('B', ItemTags.BUTTONS)
                .pattern("TBT")
                .pattern("NJN")
                .pattern("III")
                .unlockedBy(providerAccess.getHasName(Items.JUKEBOX), providerAccess.has(Items.JUKEBOX))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.CASSETTE_DECK.get())
                .define('R', PlatformItemTags.redstoneDusts())
                .define('I', PlatformItemTags.ironIngots())
                .define('N', Items.NOTE_BLOCK)
                .define('J', Items.JUKEBOX)
                .pattern("IRI")
                .pattern("NJN")
                .pattern("III")
                .unlockedBy(providerAccess.getHasName(Items.JUKEBOX), providerAccess.has(Items.JUKEBOX))
                .save(exporter);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, IMPBlocks.MUSIC_MANAGER.get())
                .define('D', PlatformItemTags.diamonds())
                .define('I', PlatformItemTags.ironIngots())
                .define('G', PlatformItemTags.glassPanes())
                .define('R', PlatformItemTags.redstoneDusts())
                .define('B', PlatformItemTags.diamonds())
                .pattern("III")
                .pattern("DGR")
                .pattern("BII")
                .unlockedBy(providerAccess.getHasName(Items.DIAMOND), providerAccess.has(Items.DIAMOND))
                .save(exporter);
    }*/

  @Override
  public void generateRecipe(
    Consumer<RecipeOutput> exporter,
    RecipeProviderAccess providerAccess
  ) {
    // TODO: Implement recipe generation for 1.21
  }
}
