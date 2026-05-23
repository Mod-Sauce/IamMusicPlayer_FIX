package dev.felnull.imp.create.client.menu;

import dev.felnull.imp.create.IMPCreate;
import dev.felnull.imp.inventory.BoomboxMenu;
import dev.felnull.imp.inventory.slot.AntennaSlot;
import dev.felnull.imp.inventory.slot.CassetteTapeSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.modsauce.otyacraftenginerenewed.item.location.PlayerItemLocation;

public class MovingBoomboxMenu extends BoomboxMenu {
    public final int entityID;
    public MovingBoomboxMenu(int i, Inventory playerInventory, Container container, BlockPos pos, ItemStack itemStack, PlayerItemLocation location, int entityID) {
        super(IMPCreate.MOVING_BOOMBOX_MENU.get(), i, playerInventory, container, pos, itemStack, location);
        this.entityID = entityID;
    }

    @Override
    protected void setSlot() {
        this.addSlot(new CassetteTapeSlot(getContainer(), 0, 183, 98));
        this.addSlot(new AntennaSlot(getContainer(), 1, 183, 124));
    }

    @Override
    public void setItem(int i, int j, ItemStack arg) {
        super.setItem(i, j, arg);
    }
}
