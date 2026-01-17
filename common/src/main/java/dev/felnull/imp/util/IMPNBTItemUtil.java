package dev.felnull.imp.util;

import dev.felnull.imp.item.IMPComponents;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class IMPNBTItemUtil {
    @Nullable
    public static CompoundTag getTag(ItemStack stack){
        var data = stack.get(IMPComponents.NBT.get());
        if(data instanceof CustomData customData)
            return customData.copyTag();
        return null;
    }

    @NotNull
    public static CompoundTag getOrCreateTag(ItemStack stack){
        if(stack.has(IMPComponents.NBT.get()))
            stack.set(IMPComponents.NBT.get(), CustomData.EMPTY);
        return Objects.requireNonNull(getTag(stack));
    }
}
