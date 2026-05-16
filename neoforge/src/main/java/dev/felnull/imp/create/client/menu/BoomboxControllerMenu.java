package dev.felnull.imp.create.client.menu;

import dev.felnull.imp.create.IMPCreate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import org.modsauce.otyacraftenginerenewed.inventory.OEBEBaseMenu;

public class BoomboxControllerMenu extends OEBEBaseMenu {
    public BoomboxControllerMenu(int windowId, Inventory playerInventory, BlockPos pos, Container container) {
        super(IMPCreate.BOOMBOX_CONTROLLER_MENU.get(), windowId, playerInventory, container, pos, 214, 96);
    }

    @Override
    protected void setSlot() {

    }
}
