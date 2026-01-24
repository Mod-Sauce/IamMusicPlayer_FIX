package dev.felnull.imp.item;

import dev.felnull.imp.block.BoomboxBlock;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.handler.CommonHandler;
import dev.felnull.imp.server.music.ringer.IMusicRinger;
import dev.felnull.imp.server.music.ringer.MusicRingManager;
import dev.felnull.imp.util.IMPNBTItemUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.modsauce.otyacraftenginerenewed.item.IInstructionItem;
import org.modsauce.otyacraftenginerenewed.item.ItemContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BoomboxItem extends BlockItem implements IInstructionItem {

    public BoomboxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack itemStack, @NotNull Level level, @NotNull Entity entity, int i, boolean bl) {
        super.inventoryTick(itemStack, level, entity, i, bl);
        tick(level, entity, itemStack, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand interactionHand) {
        var itemStack = player.getItemInHand(interactionHand);
        if (player.isCrouching()) {
            if (!level.isClientSide())
                setPower(itemStack, !isPowered(itemStack));
        } else if (isPowered(itemStack) && getTransferProgress(itemStack) == 10) {
            if (!level.isClientSide())
                BoomboxItemContainer.openContainer((ServerPlayer) player, interactionHand, itemStack, level.registryAccess());

            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }
        return super.use(level, player, interactionHand);
    }

    @Override
    public InteractionResult place(BlockPlaceContext blockPlaceContext) {
        var itemStack = blockPlaceContext.getItemInHand();
        var player = blockPlaceContext.getPlayer();
        if (player != null && (player.isCrouching() || isPowered(itemStack))) {
            if (!blockPlaceContext.getLevel().isClientSide()) {
                if (player.isCrouching())
                    setPower(itemStack, !isPowered(itemStack));
                else if (getTransferProgress(itemStack) == 10)
                    BoomboxItemContainer.openContainer((ServerPlayer) player, blockPlaceContext.getHand(), itemStack, blockPlaceContext.getLevel().registryAccess());
            }
            return InteractionResult.FAIL;
        }
        return super.place(blockPlaceContext);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext blockPlaceContext, @NotNull BlockState blockState) {
        return getTransferProgress(blockPlaceContext.getItemInHand()) == 0 && super.canPlace(blockPlaceContext, blockState);
    }

    @Override
    public void onDestroyed(@NotNull ItemEntity itemEntity) {
        var p = itemEntity.level().registryAccess();
        if (this.getBlock() instanceof BoomboxBlock) {
            ItemUtils.onContainerDestroyed(itemEntity, getContainItem(itemEntity.getItem(), p).stream().toList());
        }
        super.onDestroyed(itemEntity);
    }

    public static void tick(Level level, Entity entity, ItemStack stack, boolean musicOnly) {
        if (!stack.is(IMPBlocks.BOOMBOX.get().asItem())) return;
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            var mr = MusicRingManager.getInstance();
            if (getRingerUUID(stack) == null || CommonHandler.itemBoomboxes.contains(getRingerUUID(stack)))
                setRingerUUID(stack, UUID.randomUUID());
            var uuid = getRingerUUID(stack);
            CommonHandler.itemBoomboxes.add(uuid);
            if (BoomboxEntityRinger.canRing(entity) && !mr.hasRinger(sl, uuid)) {
                mr.addRinger(sl, new BoomboxEntityRinger(entity, uuid));
            }
        }

        if (musicOnly) return;

        var data = getData(stack, level.registryAccess());
        data.tick(level);
        setData(stack, data);

        if (entity instanceof LivingEntity livingEntity && (livingEntity.getMainHandItem() == stack || livingEntity.getOffhandItem() == stack)) {
            boolean power = isPowered(stack);
            setTransferProgressOld(stack, getTransferProgress(stack));
            setTransferProgress(stack, getTransferProgress(stack) + (power ? 1 : -1));
        }
        var p = level.registryAccess();
        if(!stack.has(DataComponents.CONTAINER)) {
            // 临时的解决方案
            var l = NonNullList.withSize(2, ItemStack.EMPTY);
            l.set(0, BoomboxItem.getCassetteTape(stack, p));
            l.set(1, BoomboxItem.getAntenna(stack, p));
            stack.set(DataComponents.CONTAINER,
                    ItemContainerContents.fromItems(l));
        }
    }

    @Override
    protected boolean updateCustomBlockEntityTag(@NotNull BlockPos blockPos, Level level, @Nullable Player player, @NotNull ItemStack itemStack, @NotNull BlockState blockState) {
        if (!level.isClientSide) {
            var be = level.getBlockEntity(blockPos);
            if (be instanceof BoomboxBlockEntity boomboxBlockEntity) {
                boomboxBlockEntity.setByItem(itemStack);
                boomboxBlockEntity.setChanged();
                super.updateCustomBlockEntityTag(blockPos, level, player, itemStack, blockState);
                return true;
            }
        }
        return super.updateCustomBlockEntityTag(blockPos, level, player, itemStack, blockState);
    }

    public static boolean matches(ItemStack src, ItemStack target) {
        if (src == target)
            return true;
        var uid = getRingerUUID(src);
        return uid != null && uid.equals(getRingerUUID(target));
    }

    public static CompoundTag getBoomboxTag(ItemStack stack) {
        return IMPNBTItemUtil.getTag(stack) != null ? IMPNBTItemUtil.getTag(stack).getCompound("BoomboxTag") : null;
    }

    public static CompoundTag getOrCreateBoomboxTag(ItemStack stack) {
        var tag = IMPNBTItemUtil.getOrCreateTag(stack);
        if (!tag.contains("BoomboxTag"))
            tag.put("BoomboxTag", new CompoundTag());
        IMPNBTItemUtil.saveTag(stack, tag);
        return getBoomboxTag(stack);
    }

    public static CompoundTag getBaseTag(ItemStack stack){
        return IMPNBTItemUtil.getTag(stack);
    }

    public static CompoundTag getOrCreateBaseTag(ItemStack stack){
        return IMPNBTItemUtil.getOrCreateTag(stack);
    }

    public static void saveBaseTag(ItemStack stack, CompoundTag tag){
        IMPNBTItemUtil.saveTag(stack, tag);
    }

    public static void saveWithParent(ItemStack stack, CompoundTag tag){
        var newTag = new CompoundTag();
        newTag.put("BoomboxTag", tag);
        IMPNBTItemUtil.saveTag(stack, newTag);
    }

    public static BoomboxData getData(ItemStack stack, HolderLookup.Provider provider) {
        return new BoomboxData(getBoomboxTag(stack), new BoomboxData.DataAccess() {
            @Override
            public ItemStack getCassetteTape() {
                return BoomboxItem.getCassetteTape(stack, provider);
            }

            @Override
            public ItemStack getAntenna() {
                return BoomboxItem.getAntenna(stack, provider);
            }

            @Override
            public boolean isPowered() {
                return BoomboxItem.isPowered(stack);
            }

            @Override
            public void setPower(boolean power) {
                BoomboxItem.setPower(stack, power);
            }

            @Override
            public IMusicRinger getRinger() {
                return MusicRingManager.getInstance().getRinger(getRingerUUID(stack));
            }

            @Override
            public Vec3 getPosition() {
                return getRinger() != null ? getRinger().getRingerSpatialPosition() : Vec3.ZERO;
            }

            @Override
            public void setCassetteTape(ItemStack cassette) {
                BoomboxItem.setCassetteTape(stack, cassette, provider);
            }

            @Override
            public void dataUpdate(BoomboxData data) {
                setData(stack, data);
            }
        });
    }

    public static void setData(ItemStack stack, BoomboxData data) {
        var tag = getOrCreateBoomboxTag(stack);
        tag.put("BoomBoxData", data.save(new CompoundTag(), true, true));
        saveWithParent(stack, tag);
    }

    public static boolean isPowered(ItemStack itemStack) {
        if (getBoomboxTag(itemStack) != null)
            return getBoomboxTag(itemStack).getBoolean("Power");
        return false;
    }

    public static void setPower(ItemStack itemStack, boolean power) {
        var tag = getOrCreateBoomboxTag(itemStack);
        tag.putBoolean("Power", power);
        saveWithParent(itemStack, tag);
    }

    public static int getTransferProgress(ItemStack stack) {
        return getBoomboxTag(stack) != null ? getBoomboxTag(stack).getInt("Transfer") : 0;
    }

    public static void setTransferProgress(ItemStack stack, int num) {
        var tag = getOrCreateBoomboxTag(stack);
        tag.putInt("Transfer", Mth.clamp(num, 0, 10));
        saveWithParent(stack, tag);
    }

    public static int getTransferProgressOld(ItemStack stack) {
        return getBoomboxTag(stack) != null ? getBoomboxTag(stack).getInt("TransferOld") : 0;
    }

    public static void setTransferProgressOld(ItemStack stack, int num) {
        var tag = getOrCreateBoomboxTag(stack);
        tag.putInt("TransferOld", Mth.clamp(num, 0, 10));
        saveWithParent(stack, tag);
    }

    public static float getTransferProgress(ItemStack stack, float partialTicks) {
        return Mth.lerp(partialTicks, BoomboxItem.getTransferProgressOld(stack), BoomboxItem.getTransferProgress(stack)) / 10f;
    }

    public static UUID getRingerUUID(ItemStack stack) {
        if (getBoomboxTag(stack) != null && getBoomboxTag(stack).contains("Identification"))
            return getBoomboxTag(stack).getUUID("Identification");
        return null;
    }

    public static void setRingerUUID(ItemStack stack, UUID id) {
        var tag = getOrCreateBoomboxTag(stack);
        tag.putUUID("Identification", id);
        saveWithParent(stack, tag);
    }

    public static NonNullList<ItemStack> getContainItem(ItemStack stack, HolderLookup.Provider provider) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(2, ItemStack.EMPTY);
        ItemContainer.loadItemList(stack, stacks, "BoomboxItems", provider);
        return stacks;
    }

    public static void setContainItem(ItemStack stack, NonNullList<ItemStack> stacks, HolderLookup.Provider provider) {
        var tag = getOrCreateBoomboxTag(stack);
        ItemContainer.saveItemList(stack, stacks, "BoomboxItems", provider);
        saveWithParent(stack, tag);
    }

    public static void setCassetteTape(ItemStack stack, ItemStack cassette, HolderLookup.Provider provider) {
        var itms = getContainItem(stack, provider);
        itms.set(0, cassette);
        setContainItem(stack, itms, provider);
    }

    public static ItemStack getCassetteTape(ItemStack stack, HolderLookup.Provider provider) {
        return getContainItem(stack, provider).get(0);
    }

    public static ItemStack getAntenna(ItemStack stack, HolderLookup.Provider provider) {
        return getContainItem(stack, provider).get(1);
    }

    @Override
    public CompoundTag onInstruction(ItemStack itemStack, ServerPlayer player, String name, CompoundTag data) {
        return BoomboxItem.getData(itemStack, player.getServer().registryAccess()).onInstruction(player, name, data);
    }

    public static ItemStack createByBE(BoomboxBlockEntity blockEntity, boolean stopMusic) {
        var itemStack = new ItemStack(IMPBlocks.BOOMBOX.get());
        setContainItem(itemStack, blockEntity.getItems(), blockEntity.getLevel().registryAccess());
        setData(itemStack, blockEntity.getBoomboxData());
        var d = getData(itemStack, blockEntity.getLevel().registryAccess());
        if (stopMusic) {
            d.setPlaying(false);
            d.setMusicPosition(0);
        }
        setData(itemStack, d);
        setPower(itemStack, blockEntity.isPowered());
        if (blockEntity.isPowered()) {
            setTransferProgress(itemStack, 10);
            setTransferProgressOld(itemStack, 10);
        }
        if (blockEntity.hasCustomName())
            itemStack.set(DataComponents.ITEM_NAME, blockEntity.getCustomName());
        setRingerUUID(itemStack, UUID.randomUUID());
        return itemStack;
    }

    public static boolean checkDuplication(ItemStack stack, Entity entity) {
        var stackId = getRingerUUID(stack);
        if (stackId == null) return false;
        List<ItemStack> allItem = new ArrayList<>();

        if (entity instanceof LivingEntity livingEntity) {
            for (EquipmentSlot value : EquipmentSlot.values()) {
                allItem.add(livingEntity.getItemBySlot(value));
            }
        }
        if (entity instanceof Player player) {
            allItem.addAll(player.getInventory().items);
        }
        for (ItemStack item : allItem) {
            if (item != stack && !item.isEmpty() && stackId.equals(getRingerUUID(item)))
                return true;
        }
        return false;
    }
}
