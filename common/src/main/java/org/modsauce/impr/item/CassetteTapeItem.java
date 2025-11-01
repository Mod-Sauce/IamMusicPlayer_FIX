package org.modsauce.impr.item;

import org.modsauce.impr.component.IMPDataComponents;
import org.modsauce.impr.music.resource.Music;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CassetteTapeItem extends Item {
    private final BaseType type;

    public CassetteTapeItem(Properties properties, BaseType type) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (getTapePercentage(itemStack) != 0) {
            if (!level.isClientSide())
                setTapePercentage(itemStack, 0f);
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }
        return super.use(level, player, interactionHand);
    }

    public BaseType getType() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, List<Component> list, TooltipFlag tooltipFlag) {
        var m = getMusic(itemStack);
        if (m != null)
            list.add(Component.literal(m.getName()).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    public static Music getMusic(ItemStack stack) {
        CompoundTag musicTag = stack.get(IMPDataComponents.MUSIC.get());
        if (musicTag != null)
            return TagSerializable.loadSavedTag(musicTag, new Music());
        return null;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getMusic(stack) != null)
            return Component.translatable(this.getDescriptionId(stack) + ".written");
        return super.getName(stack);
    }

    public static ItemStack setMusic(ItemStack stack, Music music) {
        stack.set(IMPDataComponents.MUSIC.get(), music.createSavedTag());
        return stack;
    }

    public static float getTapePercentage(ItemStack stack) {
        return stack.getOrDefault(IMPDataComponents.TAPE_PERCENTAGE.get(), 0.0f);
    }

    public static ItemStack setTapePercentage(ItemStack stack, float par) {
        stack.set(IMPDataComponents.TAPE_PERCENTAGE.get(), par);
        return stack;
    }

    public static boolean isSameCassetteTape(ItemStack stack, ItemStack stack2) {
        if (ItemStack.matches(stack, stack2)) return true;
        if (!stack.is(stack2.getItem())) return false;
        if (stack.getItem() instanceof CassetteTapeItem && stack2.getItem() instanceof CassetteTapeItem) {
            var m1 = getMusic(stack);
            var m2 = getMusic(stack2);
            if (m1 == null && m2 == null) return true;
            if (m1 == null || m2 == null) return false;
            return m1.equals(m2);
        }
        return false;
    }

    public static enum BaseType {
        NORMAL, GLASS
    }
}
