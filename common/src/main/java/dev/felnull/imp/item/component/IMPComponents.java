package dev.felnull.imp.item.component;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

public class IMPComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(
            IamMusicPlayer.MODID,
            Registries.DATA_COMPONENT_TYPE
    );
    public static final RegistrySupplier<DataComponentType<CustomData>> NBT = DATA_COMPONENT_TYPES.register("nbt", () ->
            DataComponentType.<CustomData>builder()
                    .persistent(CustomData.CODEC)
                    .networkSynchronized(CustomData.STREAM_CODEC)
                    .build()
    );
    public static final RegistrySupplier<DataComponentType<UUID>> EARPHONE_UUID = DATA_COMPONENT_TYPES.register("earphone_uuid", () ->
            DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    public static void init(){
        DATA_COMPONENT_TYPES.register();
    }
}
