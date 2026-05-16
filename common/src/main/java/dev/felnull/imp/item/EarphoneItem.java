package dev.felnull.imp.item;

import dev.felnull.imp.item.component.IMPComponents;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.item.EquipmentItem;
import org.modsauce.otyacraftenginerenewed.item.IInstructionItem;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EarphoneItem extends Item implements IInstructionItem, EquipmentItem {
    public EarphoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public CompoundTag onInstruction(ItemStack stack, ServerPlayer player, String name, CompoundTag data) {
        return new CompoundTag();
    }

    @Override
    public @Nullable EquipmentSlot getEquipmentSlotType(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    public static void initEarphone(ServerPlayer player, ItemStack stack){
        if(!stack.is(IMPItems.EARPHONE.get()))return;
        var data = stack.get(IMPComponents.EARPHONE_UUID.get());
        if(data != null && EarphoneSaveData.getInstance(player.serverLevel()).has(data)){
            if(!Objects.equals(EarphoneSaveData.getInstance(player.serverLevel()).get(data).ownerUUID(), player.getUUID()))
                EarphoneSaveData.getInstance(player.serverLevel()).removeEarphone(data);
            return;
        }
        var uuid = UUID.randomUUID();
        stack.set(IMPComponents.EARPHONE_UUID.get(), uuid);
        EarphoneSaveData.getInstance(player.serverLevel()).addEarphone(new EarphoneSaveData.EarphoneLocation(
                uuid, player.getUUID(), false
        ));
    }

    @Override
    public void inventoryTick(@NotNull ItemStack itemStack, @NotNull Level level, @NotNull Entity entity, int slot, boolean bl) {
        if(level.isClientSide())return;
        if (entity instanceof ServerPlayer player)
            initEarphone(player, itemStack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, @NotNull TooltipContext tooltipContext, @NotNull List<Component> list, @NotNull TooltipFlag tooltipFlag) {
        list.add(Component.translatable("item.iammusicplayer.earphone.desc").withStyle(ChatFormatting.GRAY));
    }
}
