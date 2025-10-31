package org.modsauce.impr.item;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.block.IMPBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class IMPCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(IamMusicPlayer.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MOD_TAB = TABS.register(IamMusicPlayer.MODID, () -> CreativeTabRegistry.create(Component.translatable("itemGroup." + IamMusicPlayer.MODID + "." + IamMusicPlayer.MODID), () -> new ItemStack(IMPBlocks.BOOMBOX.get())));

    public static void init() {
        TABS.register();
    }
}
