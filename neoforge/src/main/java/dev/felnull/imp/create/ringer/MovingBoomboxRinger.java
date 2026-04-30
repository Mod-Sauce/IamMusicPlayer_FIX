package dev.felnull.imp.create.ringer;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBaseEntityBlock;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.music.tracker.IMPMusicTrackers;
import dev.felnull.imp.music.tracker.MusicTrackerEntry;
import dev.felnull.imp.server.music.ringer.IBoomboxRinger;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MovingBoomboxRinger implements IBoomboxRinger {
    private final BlockPos localPos;
    private final AbstractContraptionEntity entity;
    private UUID ringerUUID = UUID.randomUUID();

    public MovingBoomboxRinger(BlockPos localPos, AbstractContraptionEntity contraptionEntity){
        this.localPos = localPos;
        this.entity = contraptionEntity;
    }

    public void setRingerUUID(UUID ringerUUID) {
        this.ringerUUID = ringerUUID;
    }

    public static MovingBoomboxRinger load(BlockPos localPos, AbstractContraptionEntity contraptionEntity, CompoundTag tag){
        var instance = new MovingBoomboxRinger(localPos, contraptionEntity);
        if(tag.contains("RingerUUID"))
            instance.setRingerUUID(tag.getUUID("RingerUUID"));
        return instance;
    }

    public static BoomboxData getBoomboxData(BlockPos localPos, AbstractContraptionEntity contraptionEntity){
        var be = getBoomboxEntity(localPos, contraptionEntity);
        if(be == null)return null;
        return be.getBoomboxData();
    }

    public static BoomboxBlockEntity getBoomboxEntity(BlockPos localPos, AbstractContraptionEntity contraptionEntity){
        var context = getContext(localPos, contraptionEntity);
        if(context == null)return null;

        var be = new BoomboxBlockEntity(localPos, context.state){
            @Override
            public void setChanged() {
                BoomboxMovementBehaviour.saveData(context, getBlockState().setValue(IMPBaseEntityBlock.POWERED, isPowered()),
                        saveWithoutMetadata(context.world.registryAccess()));
            }
        };
        be.loadCustomOnly(context.blockEntityData, context.world.registryAccess());
        return be;
    }

    public static MovementContext getContext(BlockPos localPos, AbstractContraptionEntity contraptionEntity){
        var actor = contraptionEntity.getContraption().getActorAt(localPos);
        if(actor == null)return null;
        return actor.right;
    }

    @Override
    public @NotNull BoomboxData getRingerBoomboxData() {
        return getBoomboxData(localPos, entity);
    }

    @Override
    public Component getRingerName() {
        return Component.translatable("imp.ringer.have", IMPBlocks.BOOMBOX.get().getName(), entity.getName());
    }

    @Override
    public ServerLevel getRingerLevel() {
        return (ServerLevel) entity.level();
    }

    @Override
    public UUID getRingerUUID() {
        return ringerUUID;
    }

    @Override
    public boolean exists() {
        return entity.isAlive() && getRingerBoomboxData().isPlaying();
    }

    @Override
    public MusicTrackerEntry getRingerTracker() {
        if(getRingerBoomboxData().getEarphoneUUID() != null){
            var loc = EarphoneSaveData.getInstance(getServerLevel()).get(getRingerBoomboxData().getEarphoneUUID());
            if(loc != null) {
                var entity = getServerLevel().getEntity(loc.ownerUUID());
                if(entity != null)
                    return IMPMusicTrackers.createEntityTracker(entity, getRingerVolume(), getRingerRange());
            }
        }
        return IMPMusicTrackers.createFixedTracker(getRingerSpatialPosition(), getRingerVolume(), getRingerRange());
    }

    @Override
    public @NotNull Vec3 getRingerSpatialPosition() {
        if(getRingerBoomboxData().getEarphoneUUID() != null){
            var loc = EarphoneSaveData.getInstance(getServerLevel()).get(getRingerBoomboxData().getEarphoneUUID());
            if(loc != null) {
                var entity = getServerLevel().getEntity(loc.ownerUUID());
                if(entity != null)return entity.position();
            }
        }
        return getContext(localPos, entity).position;
    }
}
