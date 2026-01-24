package dev.felnull.imp.util;

import dev.felnull.imp.item.IMPComponents;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class IMPNBTItemUtil {
    private static final Logger LOGGER = LogManager.getLogger();
    @Nullable
    public static CompoundTag getTag(ItemStack stack){
        var data = stack.get(IMPComponents.NBT.get());
        if(data instanceof CustomData customData)
            return customData.copyTag();
        return null;
    }

    @NotNull
    public static CompoundTag getOrCreateTag(ItemStack stack){
        if(!stack.has(IMPComponents.NBT.get()))
            stack.set(IMPComponents.NBT.get(), CustomData.of(new CompoundTag()));

        CompoundTag tag = getTag(stack);
        if (tag == null) {
            tag = new CompoundTag();
            LOGGER.error("WDF Tag is null");
            stack.set(IMPComponents.NBT.get(), CustomData.of(tag));
        }
        return tag;
    }

    public static void saveTag(ItemStack stack, CompoundTag tag){
        stack.set(IMPComponents.NBT.get(), CustomData.of(tag));
    }
}
