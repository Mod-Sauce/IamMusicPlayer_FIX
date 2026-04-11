package dev.felnull.imp.create.behaviour;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.network.ContraptionChangedPacketSTC;
import dev.felnull.imp.create.ringer.MovingBoomboxRinger;
import dev.felnull.imp.forge.mixin.UpdateTagsAccessor;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.UUID;

public class BoomboxMovementBehaviour implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        if(!context.world.isClientSide) {
            var be = new BoomboxBlockEntity(BlockPos.ZERO, context.state);
            be.loadCustomOnly(context.blockEntityData, context.world.registryAccess());
            CompoundTag tag = context.blockEntityData;
            UUID uuid = tag.contains("RingerUUID") ? tag.getUUID("RingerUUID") : null;
            var ringers = MusicRingManager.getInstance().getMusicRing((ServerLevel) context.world);
            MovingBoomboxRinger instance;
            if (uuid != null && ringers.hasRinger(uuid)) {
                instance = (MovingBoomboxRinger)
                        ringers.getRingers().get(uuid);
            } else {
                instance = MovingBoomboxRinger.load(context.localPos, context.contraption.entity, context.blockEntityData);
            }
            be.getBoomboxData().tick(context.world);
            instance.ringerTick();
            var data = be.saveWithoutMetadata(context.world.registryAccess());
            data.putUUID("RingerUUID", instance.getRingerUUID());
            saveData(context, data);
        }
    }

    public static void saveData(MovementContext context, CompoundTag data){
        saveData(context, context.state, data);
    }

    public static void saveData(MovementContext content, BlockState blockState) {
        saveData(content, blockState, content.blockEntityData);
    }

    public static void saveData(MovementContext context, BlockState state, CompoundTag data){
        var localPos = context.localPos;
        var entity = context.contraption.entity;
        if(!(entity instanceof AbstractContraptionEntity contraptionEntity))return;
        Contraption contraption = contraptionEntity.getContraption();
        MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> actor = contraption.getActorAt(localPos);
        if(actor == null)return;
        MovementContext ctx = actor.right;
        if(ctx == null)return;

        if(context.blockEntityData.contains("RingerUUID") && !data.contains("RingerUUID"))
            data.putUUID("RingerUUID", context.blockEntityData.getUUID("RingerUUID"));

        ctx.blockEntityData = data;
        ctx.state = state;
        var old = contraption.getBlocks().remove(localPos);
        contraption.getBlocks().put(localPos,
                new StructureTemplate.StructureBlockInfo(old.pos(), state, data));

        var updateTags = ((UpdateTagsAccessor)context.contraption).getUpdateTags();
        updateTags.put(context.localPos, context.blockEntityData);
        if(context.world instanceof ServerLevel serverLevel)
            NetworkManager.sendToPlayers(serverLevel.players(),
                    new ContraptionChangedPacketSTC(entity.getId(), localPos, state, data));
    }
}
