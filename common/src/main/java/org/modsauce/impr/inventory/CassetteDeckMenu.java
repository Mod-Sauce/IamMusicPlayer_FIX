package org.modsauce.impr.inventory;

import org.modsauce.impr.inventory.slot.CassetteTapeSlot;
import org.modsauce.otyacraftenginerenewed.inventory.OEBEBaseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

public class CassetteDeckMenu extends OEBEBaseMenu {
    public CassetteDeckMenu(int windowId, Inventory playerInventory, BlockPos pos, Container container) {
        super(IMPMenus.CASSETTE_DECK.get(), windowId, playerInventory, container, pos, 8, 94);
    }

    @Override
    protected void setSlot() {
        this.addSlot(new CassetteTapeSlot(getContainer(), 0, 183, 99));
    }
}
