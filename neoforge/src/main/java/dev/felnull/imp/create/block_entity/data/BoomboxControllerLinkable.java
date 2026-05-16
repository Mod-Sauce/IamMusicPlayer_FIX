package dev.felnull.imp.create.block_entity.data;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dev.felnull.imp.create.block_entity.BoomboxControllerBlockEntity;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;

public class BoomboxControllerLinkable implements IRedstoneLinkable {
    private final BoomboxControllerBlockEntity blockEntity;
    private final BoomboxControllerData.Signal signal;
    private int lastPower = -1;
    public BoomboxControllerLinkable(BoomboxControllerBlockEntity blockEntity, BoomboxControllerData.Signal signal){
        this.blockEntity = blockEntity;
        this.signal = signal;
    }

    @Override
    public int getTransmittedStrength() {
        if(signal.mode() == BoomboxControllerData.Mode.NONE)return 0;
        if(isListening())return 0;
        return blockEntity.getSignalPower(signal);
    }

    @Override
    public void setReceivedStrength(int i) {
        if(signal.mode() == BoomboxControllerData.Mode.NONE)return;
        if (i == 0) {
            lastPower = -1;
            return;
        }
        if (i != lastPower) {
            blockEntity.onSignalPowered(signal);
        }
        lastPower = i;
    }

    @Override
    public boolean isListening() {
        return signal.mode().input;
    }

    @Override
    public boolean isAlive() {
        return !blockEntity.isRemoved() && blockEntity.hasLink(this);
    }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(signal.left()),
                RedstoneLinkNetworkHandler.Frequency.of(signal.right())
        );
    }

    @Override
    public BlockPos getLocation() {
        return blockEntity.getBlockPos();
    }
}
