package dev.felnull.imp.blockentity;

import dev.felnull.imp.block.BoomboxBlock;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.inventory.BoomboxMenu;
import dev.felnull.imp.inventory.IMPMenus;
import dev.felnull.imp.item.BoomboxItem;
import dev.felnull.imp.music.tracker.IMPMusicTrackers;
import dev.felnull.imp.music.tracker.MusicTrackerEntry;
import dev.felnull.imp.server.music.ringer.IBoomboxRinger;
import dev.felnull.imp.server.music.ringer.IMusicRinger;
import dev.felnull.imp.util.IMPItemUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BoomboxBlockEntity extends IMPBaseEntityBlockEntity implements IBoomboxRinger {
    private BoomboxData boomboxData;
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final UUID ringerUUID = UUID.randomUUID();

    public BoomboxBlockEntity(BlockPos blockPos, BlockState blockState) {

        super(IMPBlockEntities.BOOMBOX.get(), blockPos, blockState);
        this.boomboxData = new BoomboxData(null, new BoomboxData.DataAccess() {
            @Override
            public ItemStack getCassetteTape() {
                return BoomboxBlockEntity.this.getCassetteTape();
            }

            @Override
            public ItemStack getAntenna() {
                return BoomboxBlockEntity.this.getAntenna();
            }

            @Override
            public boolean isPowered() {
                return BoomboxBlockEntity.this.isPowered();
            }

            @Override
            public void setPower(boolean power) {
                BoomboxBlockEntity.this.setPower(power);
            }

            @Override
            public IMusicRinger getRinger() {
                return BoomboxBlockEntity.this;
            }

            @Override
            public Vec3 getPosition() {
                return new Vec3(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());
            }

            @Override
            public void setCassetteTape(ItemStack stack) {
                setItemNoUpdate(0, stack);
            }

            @Override
            public void dataUpdate(BoomboxData data) {
                setChanged();
            }
        });
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (i == 0) {
            var old = getItem(0).copy();
            var ret = super.removeItem(i, j);
            this.boomboxData.setOldCassetteTape(old);
            return ret;
        }
        return super.removeItem(i, j);
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (i == 0)
            this.boomboxData.onCassetteTapeChange(stack, getCassetteTape());
        setItemNoUpdate(i, stack);
    }

    public void setItemNoUpdate(int i, ItemStack stack) {
        super.setItem(i, stack);
    }

    public void setBoomboxData(BoomboxData data) {
        boomboxData.load(data.save(new CompoundTag(), false, false), false, false);
    }

    public void setBoomboxDataReally(BoomboxData data) {
        // Do not use this method under normal circumstances
        this.boomboxData = data;
    }

    @Override
    public ItemStack createRetainDropItem() {
        return BoomboxItem.createByBE(this, false);
    }

    @Override
    public boolean isRetainDrop() {
        return true;
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return IMPBlocks.BOOMBOX.get().getName();
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory inventory) {
        return new BoomboxMenu(IMPMenus.BOOMBOX.get(), i, inventory, this, getBlockPos(), ItemStack.EMPTY, null);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.boomboxData.load(tag.getCompound("BoomBoxData"), false, false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("BoomBoxData", this.boomboxData.save(new CompoundTag(), false, false));
    }

    public static void tick(Level level, BlockPos blockPos, BlockState blockState, BoomboxBlockEntity blockEntity) {
        blockEntity.boomboxData.tick(level);
        if (!level.isClientSide()) {
            blockEntity.ringerTick();
            blockEntity.setRaisedHandleState(blockEntity.boomboxData.getHandleRaisedProgress() >= blockEntity.boomboxData.getHandleRaisedMax());

            blockEntity.setChanged();
        }

        blockEntity.baseAfterTick();
    }


    @Override
    public void saveToUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveToUpdateTag(tag, registries);
        tag.put("BoomBoxData", this.boomboxData.save(new CompoundTag(), false, true));
    }

    @Override
    public void loadToUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadToUpdateTag(tag, registries);
        this.boomboxData.load(tag.getCompound("BoomBoxData"), false, true);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket clientboundBlockEntityDataPacket, HolderLookup.Provider lookupProvider) {
        loadToUpdateTag(clientboundBlockEntityDataPacket.getTag(), lookupProvider);
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemStack) {
        return (i == 0 && IMPItemUtil.isCassetteTape(itemStack)) || (i == 1 && IMPItemUtil.isAntenna(itemStack));
    }

    @Override
    public @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonNullList) {
        items = nonNullList;
    }

    public void setRaisedHandleState(boolean raised) {
        var bs = getBlockState().setValue(BoomboxBlock.RAISED, raised);
        getLevel().setBlock(getBlockPos(), bs, 2);
    }

    public BoomboxData getBoomboxData() {
        return boomboxData;
    }

    @Override
    public CompoundTag onInstruction(ServerPlayer player, String name, CompoundTag data) {
        var ret = boomboxData.onInstruction(player, name, data);
        if (ret != null)
            return ret;
        return super.onInstruction(player, name, data);
    }

    public ItemStack getCassetteTape() {
        return getItem(0);
    }

    public ItemStack getAntenna() {
        return getItem(1);
    }

    @Override
    public Component getRingerName() {
        return getDefaultName();
    }

    @Override
    public UUID getRingerUUID() {
        return ringerUUID;
    }

    @Override
    public boolean exists() {
        if (getLevel() == null || level != getLevel()) return false;
        return getBlockPos() != null && level.getBlockEntity(getBlockPos()) == this;
    }

    @Override
    public @NotNull BoomboxData getRingerBoomboxData() {
        return boomboxData;
    }

    @Override
    public ServerLevel getRingerLevel() {
        return (ServerLevel) this.level;
    }

    @Override
    public MusicTrackerEntry getRingerTracker() {
        return IMPMusicTrackers.createFixedTracker(getRingerSpatialPosition(), getRingerVolume(), getRingerRange());
//        return Pair.of(MusicRingManager.FIXED_TRACKER, MusicRingManager.createFixedTracker(getRingerSpatialPosition()));
    }

    @Override
    public @NotNull Vec3 getRingerSpatialPosition() {
        return new Vec3(getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5);
    }

    public void setByItem(ItemStack stack) {
        setPower(BoomboxItem.isPowered(stack));
        var p = level.registryAccess();
        setItemNoUpdate(0, BoomboxItem.getCassetteTape(stack, p));
        setItemNoUpdate(1, BoomboxItem.getAntenna(stack, p));
//        setItems(NonNullList.of(BoomboxItem.getCassetteTape(stack, p), BoomboxItem.getAntenna(stack, p)));
        setBoomboxData(BoomboxItem.getData(stack, p));
        setPower(BoomboxItem.isPowered(stack));
        if (BoomboxItem.getTransferProgress(stack) == 0) {
            boomboxData.setHandleRaising(true);
            boomboxData.setHandleRaisedProgress(boomboxData.getHandleRaisedMax());
            boomboxData.setHandleRaisedProgressOld(boomboxData.getHandleRaisedMax());
        }
        setChanged();
    }
}
