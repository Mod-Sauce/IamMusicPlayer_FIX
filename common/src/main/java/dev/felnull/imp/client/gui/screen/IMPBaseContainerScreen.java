package dev.felnull.imp.client.gui.screen;

import dev.felnull.imp.blockentity.IMPBaseEntityBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.modsauce.otyacraftenginerenewed.client.gui.screen.OEBEContainerBasedScreen;
import org.modsauce.otyacraftenginerenewed.inventory.OEBEBaseMenu;

public abstract class IMPBaseContainerScreen<T extends OEBEBaseMenu> extends OEBEContainerBasedScreen<T> {
    public IMPBaseContainerScreen(T abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
    }

    public void insPower(boolean on) {
        var tag = new CompoundTag();
        tag.putBoolean("power", on);
        instruction("power", tag);
    }

    public boolean isPowered() {
        if (getBlockEntity() instanceof IMPBaseEntityBlockEntity impBase)
            return impBase.isPowered();
        return false;
    }
}
