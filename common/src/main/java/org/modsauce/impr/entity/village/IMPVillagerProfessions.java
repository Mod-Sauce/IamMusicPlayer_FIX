package org.modsauce.impr.entity.village;

import com.google.common.collect.ImmutableSet;
import dev.architectury.registry.level.entity.trade.SimpleTrade;
import dev.architectury.registry.level.entity.trade.TradeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.item.IMPItems;
import org.modsauce.impr.util.IMPItemUtil;
import org.modsauce.otyacraftenginerenewed.util.OERegisterUtils;

public class IMPVillagerProfessions {

  private static final DeferredRegister<
    VillagerProfession
  > VILLAGER_PROFESSIONS = DeferredRegister.create(
    IamMusicPlayer.MODID,
    Registries.VILLAGER_PROFESSION
  );
  public static final RegistrySupplier<VillagerProfession> DJ = register(
    "dj",
    IMPPoiType.DJ,
    SoundEvents.LANTERN_PLACE
  );

  private static RegistrySupplier<VillagerProfession> register(
    String name,
    Supplier<PoiType> poiType,
    SoundEvent soundEvent
  ) {
    return VILLAGER_PROFESSIONS.register(name, () ->
      new VillagerProfession(
        ResourceLocation.fromNamespaceAndPath(
          IamMusicPlayer.MODID,
          name
        ).toString(),
        holder -> holder.value().equals(poiType.get()),
        holder -> holder.value().equals(poiType.get()),
        ImmutableSet.of(),
        ImmutableSet.of(),
        soundEvent
      )
    );
  }

  public static void init() {
    VILLAGER_PROFESSIONS.register();
  }

  public static void setup() {
    registerBuying(DJ.get(), 1, IMPItems.CASSETTE_TAPE.get(), 1, 1, 10, 2);
    registerBuying(
      DJ.get(),
      1,
      IMPItems.CASSETTE_TAPE_GLASS.get(),
      1,
      1,
      10,
      2
    );
    registerBuying(DJ.get(), 2, IMPBlocks.MUSIC_MANAGER.get(), 18, 1, 1, 12);
    registerBuying(DJ.get(), 3, IMPBlocks.CASSETTE_DECK.get(), 13, 1, 1, 12);
    registerBuying(DJ.get(), 4, IMPBlocks.BOOMBOX.get(), 15, 1, 3, 12);
    registerBuying(DJ.get(), 5, IMPItems.RADIO_ANTENNA.get(), 15, 1, 1, 23);
    registerBuying(DJ.get(), 5, IMPItems.PARABOLIC_ANTENNA.get(), 33, 1, 1, 23);

    registerSelling(DJ.get(), 1, Items.DRIED_KELP, 12, 15, 2);
    registerSelling(DJ.get(), 2, Items.NOTE_BLOCK, 8, 10, 13);
    registerSelling(DJ.get(), 2, Items.REDSTONE, 12, 15, 2);
    registerSelling(DJ.get(), 3, Items.JUKEBOX, 1, 5, 20);

    // TODO: Fix SimpleTrade constructor for 1.21 - signature changed
    // The trading API changed in 1.21 and the old `SimpleTrade` constructor/signature no longer matches.
    // Keep this registration commented until the correct constructor or factory is determined from the
    // current mappings. Migration checklist / guidance:
    //  1) Inspect the `SimpleTrade` class in your 1.21 mappings/JAR to find available constructors or
    //     static factory methods and the expected parameter types (they may use different classes for
    //     the cost and result now — e.g. ItemCost vs other cost wrappers, ItemStack vs ItemLike, etc.).
    //  2) Update the instantiation below to match the new parameter order and types. The previous intent:
    //       register a wandering-trader trade that costs 42 emeralds and gives the Kamesuta antenna.
    //     Pseudocode (keep as reference when migrating):
    //       TradeRegistry.registerTradeForWanderingTrader(true, new SimpleTrade(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 42), ItemStack.EMPTY, IMPItemUtil.createKamesutaAntenna(), 1, 10, 0.05f));
    //  3) If `SimpleTrade` was moved or renamed, update imports or use a fully-qualified type.
    //  4) Compile, fix any type mismatches (ItemStack vs ItemLike vs custom cost classes), and run tests.
    //
    // We intentionally leave the registration commented out to avoid compile/runtime breakage until the
    // exact replacement is implemented against the project's current mappings.
    // TradeRegistry.registerTradeForWanderingTrader(true, new SimpleTrade(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 42), ItemStack.EMPTY, IMPItemUtil.createKamesutaAntenna(), 1, 10, 0.05f));
  }

  public static void registerBuying(
    VillagerProfession profession,
    int level,
    ItemLike item,
    int emeraldCost,
    int numberOfItems,
    int maxUses,
    int villagerXp
  ) {
    TradeRegistry.registerVillagerTrade(
      profession,
      level,
      OERegisterUtils.createTradeItemsForEmeralds(
        new ItemStack(item),
        emeraldCost,
        numberOfItems,
        maxUses,
        villagerXp
      )
    );
  }

  public static void registerSelling(
    VillagerProfession profession,
    int level,
    ItemLike item,
    int cost,
    int maxUses,
    int villagerXp
  ) {
    TradeRegistry.registerVillagerTrade(
      profession,
      level,
      OERegisterUtils.createTradeEmeraldForItems(
        item,
        cost,
        maxUses,
        villagerXp
      )
    );
  }
}
