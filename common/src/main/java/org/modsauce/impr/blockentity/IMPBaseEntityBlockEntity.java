package org.modsauce.impr.blockentity;

import org.modsauce.impr.block.IMPBaseEntityBlock;
import org.modsauce.otyacraftenginerenewed.blockentity.OEBaseContainerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class IMPBaseEntityBlockEntity extends OEBaseContainerBlockEntity {
    protected IMPBaseEntityBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    public boolean isPowered() {
        return getBlockState().getValue(IMPBaseEntityBlock.POWERED);
    }

    public void setPower(boolean on) {
        if (on != isPowered())
            getLevel().setBlock(getBlockPos(), getBlockState().setValue(IMPBaseEntityBlock.POWERED, on), 3);
    }

    @Override
    public CompoundTag onInstruction(ServerPlayer player, String name, CompoundTag data) {
        if ("power".equals(name)) {
            setPower(data.getBoolean("power"));
            return null;
        }
        return super.onInstruction(player, name, data);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            loadToUpdateTag(packet.getTag());
        }
    }
}
