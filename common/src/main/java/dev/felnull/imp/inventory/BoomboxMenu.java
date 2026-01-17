package dev.felnull.imp.inventory;

import dev.felnull.imp.inventory.slot.AntennaSlot;
import dev.felnull.imp.inventory.slot.CassetteTapeSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.modsauce.otyacraftenginerenewed.inventory.OEItemBEBaseMenu;
import org.modsauce.otyacraftenginerenewed.item.location.PlayerItemLocation;

public class BoomboxMenu extends OEItemBEBaseMenu {

    public BoomboxMenu(int i, Inventory playerInventory, Container container, BlockPos pos, ItemStack itemStack, PlayerItemLocation location) {
        super(IMPMenus.BOOMBOX.get(), i, playerInventory, container, pos, itemStack, location, 8, 93);
    }

    @Override
    protected void setSlot() {
        this.addSlot(new CassetteTapeSlot(getContainer(), 0, 183, 98));
        this.addSlot(new AntennaSlot(getContainer(), 1, 183, 124));
    }
}
