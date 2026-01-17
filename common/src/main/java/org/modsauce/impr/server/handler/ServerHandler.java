package org.modsauce.impr.server.handler;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LootEvent;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jetbrains.annotations.Nullable;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.item.IMPItems;
import org.modsauce.impr.server.commands.MusicCommand;

public class ServerHandler {

  private static final List<String> LOOT_NORMAL = List.of(
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/nether_bridge",
    "minecraft:chests/desert_pyramid",
    "minecraft:chests/abandoned_mineshaft",
    "minecraft:chests/bastion_treasure",
    "minecraft:chests/jungle_temple",
    "minecraft:chests/underwater_ruin_big"
  );
  private static final List<String> LOOT_RARE = List.of(
    "minecraft:chests/buried_treasure",
    "minecraft:chests/end_city_treasure",
    "minecraft:chests/woodland_mansion"
  );

  public static void init() {
    CommandRegistrationEvent.EVENT.register(ServerHandler::registerCommand);
    // Loot table modification registration is intentionally disabled for now.
    // NOTE: In Minecraft 1.21 the loot modification callback signature changed:
    // the event now provides a ResourceKey<net.minecraft.world.level.storage.loot.LootTable>
    // (and the context object shape may have been adjusted). The previous implementation
    // used a ResourceLocation and a different context type.
    //
    // To re-enable loot table modification you must:
    // 1) Update the handler method signature to accept the new ResourceKey<LootTable>
    //    and the updated context type.
    // 2) Convert the ResourceKey to a ResourceLocation (if you still need a namespaced id)
    //    using the ResourceKey.location() or appropriate API.
    // 3) Register the handler again:
    //    LootEvent.MODIFY_LOOT_TABLE.register(ServerHandler::modifyLootTable);
    //
    // The legacy implementation is kept further below as a commented reference.
  }

  // Loot table modification handler (legacy implementation kept as a reference).
  // When adapting for 1.21 you will likely need to change the method signature to accept
  // a ResourceKey<net.minecraft.world.level.storage.loot.LootTable> (instead of ResourceLocation)
  // and the updated modification context type. Convert the ResourceKey to a ResourceLocation
  // (or otherwise resolve its namespace/path) if you need to compare against the string ids
  // contained in LOOT_NORMAL / LOOT_RARE.
  /*
    public static void modifyLootTable(ResourceLocation id, LootEvent.LootTableModificationContext context, boolean builtin) {
        boolean normal = LOOT_NORMAL.contains(id.toString());
        boolean rare = LOOT_RARE.contains(id.toString());

        if (normal || rare) {
            var antennaPoolB = LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(rare ? 0.364364f : 0.1919810f))
                    .add(LootItem.lootTableItem(IMPItems.PARABOLIC_ANTENNA.get()).setWeight(1))
                    .add(LootItem.lootTableItem(IMPItems.RADIO_ANTENNA.get()).setWeight(rare ? 1 : 4));
            context.addPool(antennaPoolB);

            var djKitPoolB = LootPool.lootPool().setRolls(UniformGenerator.between(1, 3))
                    .when(LootItemRandomChanceCondition.randomChance(0.114514f))
                    .add(LootItem.lootTableItem(IMPBlocks.BOOMBOX.get()).setWeight(1))
                    .add(LootItem.lootTableItem(IMPItems.CASSETTE_TAPE.get()).setWeight(rare ? 3 : 6));
            context.addPool(djKitPoolB);
        }
    }
    */

  private static void registerCommand(
    CommandDispatcher<CommandSourceStack> dispatcher,
    CommandBuildContext registry,
    Commands.CommandSelection selectioncommandSelection
  ) {
    MusicCommand.register(dispatcher);
  }
}
