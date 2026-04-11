package dev.felnull.imp.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption")
@Pseudo
public interface UpdateTagsAccessor {
    @Accessor
    Map<BlockPos, CompoundTag> getUpdateTags();
}
