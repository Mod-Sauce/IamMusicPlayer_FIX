package org.modsauce.impr.component;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import org.modsauce.impr.IamMusicPlayer;

public class IMPDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(IamMusicPlayer.MODID, Registries.DATA_COMPONENT_TYPE);

    // Music data component - stores the Music NBT as a CompoundTag
    public static final RegistrySupplier<DataComponentType<CompoundTag>> MUSIC =
            DATA_COMPONENT_TYPES.register("music", () ->
                    DataComponentType.<CompoundTag>builder()
                            .persistent(CompoundTag.CODEC)
                            .build()
            );

    // Tape percentage component - stores the cassette tape write progress
    public static final RegistrySupplier<DataComponentType<Float>> TAPE_PERCENTAGE =
            DATA_COMPONENT_TYPES.register("tape_percentage", () ->
                    DataComponentType.<Float>builder()
                            .persistent(Codec.FLOAT)
                            .build()
            );

    // Boombox data component - stores the boombox NBT data
    public static final RegistrySupplier<DataComponentType<CompoundTag>> BOOMBOX_DATA =
            DATA_COMPONENT_TYPES.register("boombox_data", () ->
                    DataComponentType.<CompoundTag>builder()
                            .persistent(CompoundTag.CODEC)
                            .build()
            );

    public static void register() {
        DATA_COMPONENT_TYPES.register();
    }
}

