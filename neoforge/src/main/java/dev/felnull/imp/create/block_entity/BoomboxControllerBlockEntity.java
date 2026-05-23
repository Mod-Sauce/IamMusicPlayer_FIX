package dev.felnull.imp.create.block_entity;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.block_entity.data.BoomboxControllerData;
import dev.felnull.imp.create.block_entity.data.BoomboxControllerLinkable;
import dev.felnull.imp.create.client.menu.BoomboxControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.blockentity.OEBaseContainerBlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoomboxControllerBlockEntity extends OEBaseContainerBlockEntity{
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private BoomboxControllerData boomboxControllerData = new BoomboxControllerData(List.of());

    @Nullable
    private BoomboxBlockEntity boomboxBlockEntity;

    private final Map<BoomboxControllerData.Signal, BoomboxControllerLinkable> linkCache = new HashMap<>();
    public BoomboxControllerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.iammusicplayer.boombox_controller");
    }

    @Override
    public @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> arg) {
        items = arg;
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int i, @NotNull Inventory arg) {
        return new BoomboxControllerMenu(i, arg, getBlockPos(), this);
    }

    @Override
    public void onDataPacket(@NotNull Connection connection, ClientboundBlockEntityDataPacket clientboundBlockEntityDataPacket, HolderLookup.Provider lookupProvider) {
        loadToUpdateTag(clientboundBlockEntityDataPacket.getTag(), lookupProvider);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boomboxControllerData = BoomboxControllerData.loadFromNbt(tag, "data");
    }

    @Override
    public void loadToUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadToUpdateTag(tag, registries);
        boomboxControllerData = BoomboxControllerData.loadFromNbt(tag, "data");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        BoomboxControllerData.saveToNbt(tag, boomboxControllerData, "data");
    }

    @Override
    public void saveToUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveToUpdateTag(tag, registries);
        BoomboxControllerData.saveToNbt(tag, boomboxControllerData, "data");
    }

    public BoomboxControllerData getBoomboxControllerData() {
        return boomboxControllerData;
    }

    public void setBoomboxControllerData(BoomboxControllerData boomboxControllerData) {
        this.boomboxControllerData = boomboxControllerData;
        rebuildNetwork();
        setChanged();
    }

    public void removeSignal(int index){
        if(!(index < boomboxControllerData.signals().size() && index >= 0))return;
        var l = getBoomboxControllerData().getEditableSignals();
        l.remove(index);
        setBoomboxControllerData(new BoomboxControllerData(l));
    }

    public void addSignal(){
        var l = getBoomboxControllerData().getEditableSignals();
        l.add(new BoomboxControllerData.Signal(ItemStack.EMPTY, ItemStack.EMPTY, BoomboxControllerData.Mode.NONE));
        setBoomboxControllerData(new BoomboxControllerData(l));
    }

    public void editSignal(int index, BoomboxControllerData.Signal signal){
        if(!(index < boomboxControllerData.signals().size() && index >= 0))return;
        var l = getBoomboxControllerData().getEditableSignals();
        l.set(index, signal);
        setBoomboxControllerData(new BoomboxControllerData(l));
    }

    @Override
    public CompoundTag onInstruction(ServerPlayer player, String name, CompoundTag data) {
        var back = new CompoundTag();
        switch (name){
            case "add" -> addSignal();
            case "remove" -> {
                if(data.contains("index"))
                    removeSignal(data.getInt("index"));
            }
            case "edit" -> {
                if(data.contains("index") && data.contains("signal"))
                    editSignal(data.getInt("index"),
                            BoomboxControllerData.Signal.loadFromNbt(data, "signal"));
            }
        }
        return back;
    }

    public static void tick(Level level, BlockPos blockPos, BlockState blockState, BoomboxControllerBlockEntity blockEntity) {
        if (level == null || level.isClientSide) return;

        RedstoneLinkNetworkHandler handler = Create.REDSTONE_LINK_NETWORK_HANDLER;
        for (BoomboxControllerLinkable link : blockEntity.linkCache.values()) {
            handler.updateNetworkOf(level, link);
        }

        if(blockEntity.boomboxBlockEntity != null && blockEntity.boomboxBlockEntity.isRemoved())
            blockEntity.boomboxBlockEntity = null;
        if(blockEntity.boomboxBlockEntity == null && level.getBlockEntity(blockPos.above(1)) instanceof BoomboxBlockEntity boomboxBlockEntity)
            blockEntity.boomboxBlockEntity = boomboxBlockEntity;

        blockEntity.baseAfterTick();
    }

    public int getSignalPower(BoomboxControllerData.Signal signal){
        if(boomboxBlockEntity == null)return 0;
        if(signal.mode().input)return 0;
        switch (signal.mode()){
            case PLAYING -> {return boomboxBlockEntity.isPlaying() ? 15 : 0;}
        }
        return 0;
    }

    public void onSignalPowered(BoomboxControllerData.Signal signal){
        if(boomboxBlockEntity == null)return;
        if(!signal.mode().input)return;
        switch (signal.mode()){
            case PLAY -> {
                boomboxBlockEntity.getBoomboxData().setMusicPosition(0);
                boomboxBlockEntity.getBoomboxData().setPlaying(true);
            }
            case STOP -> {
                boomboxBlockEntity.getBoomboxData().setPlaying(false);
                boomboxBlockEntity.getBoomboxData().setMusicPosition(0);
            }
            case PAUSE -> boomboxBlockEntity.getBoomboxData().setPlaying(!boomboxBlockEntity.isPlaying());
            case SWITCH_PLAY_MODE -> boomboxBlockEntity.getBoomboxData().setContinuousType(
                    BoomboxData.ContinuousType.values()[(boomboxBlockEntity.getBoomboxData().getContinuousType().ordinal() + 1) %
                            BoomboxData.ContinuousType.values().length]);
            case NEXT -> {
                boomboxBlockEntity.clearLyric();
                boomboxBlockEntity.setRingerPosition(0);
                boomboxBlockEntity.ringerEnd();
                boomboxBlockEntity.ringerRestart();
            }
        }
    }

    public void rebuildNetwork() {
        if (level == null || level.isClientSide) return;

        RedstoneLinkNetworkHandler handler = Create.REDSTONE_LINK_NETWORK_HANDLER;

        for (BoomboxControllerLinkable link : linkCache.values()) {
            handler.removeFromNetwork(level, link);
        }

        linkCache.clear();

        for (BoomboxControllerData.Signal signal : getBoomboxControllerData().signals()) {
            BoomboxControllerLinkable link = getLink(signal);
            linkCache.put(signal, link);
            handler.addToNetwork(level, link);
        }
    }

    private BoomboxControllerLinkable getLink(BoomboxControllerData.Signal signal) {
        return linkCache.computeIfAbsent(signal,
                s -> new BoomboxControllerLinkable(this, s)
        );
    }

    @Override
    public void onLoad() {
        super.onLoad();
        rebuildNetwork();
    }

    public boolean hasLink(BoomboxControllerLinkable linkable){
        return linkCache.containsValue(linkable);
    }
}
