package dev.felnull.imp.handler;

import dev.architectury.event.events.common.TickEvent;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.swing.text.html.parser.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommonHandler {
    public static List<UUID> itemBoomboxes = new ArrayList<>();

    public static void init() {
        TickEvent.SERVER_POST.register(CommonHandler::onTickEnd);
        TickEvent.SERVER_LEVEL_POST.register(CommonHandler::tickSpectatorPlayer);
    }

    private static void onTickEnd(MinecraftServer minecraftServer) {
        itemBoomboxes.clear();
    }

    private static void tickSpectatorPlayer(ServerLevel level){
        for(ServerPlayer player: level.getPlayers(LivingEntity::isAlive)){
            if(player.isSpectator()){
                for(ItemStack stack: player.getInventory().items){
                    if(stack.is(IMPBlocks.BOOMBOX.get().asItem())){
                        BoomboxItem.tick(level, player, stack, false);
                    }
                }
            }
        }
    }
}
