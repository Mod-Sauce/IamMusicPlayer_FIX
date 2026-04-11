package dev.felnull.imp.item;

import dev.felnull.imp.inventory.BoomboxMenu;
import dev.felnull.imp.inventory.IMPMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.modsauce.otyacraftenginerenewed.item.ItemContainer;
import org.modsauce.otyacraftenginerenewed.item.location.HandItemLocation;
import org.modsauce.otyacraftenginerenewed.item.location.PlayerItemLocation;
import org.modsauce.otyacraftenginerenewed.util.OEMenuUtil;

import java.util.function.Function;

public class BoomboxItemContainer extends ItemContainer {

    public BoomboxItemContainer(ItemStack itemStack, PlayerItemLocation location, int size, String tagName, Function<Player, Boolean> valid, HolderLookup.Provider provider) {
        super(itemStack, location, size, tagName, valid, provider);
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (i == 0) {
            var old = getItem(0).copy();
            var ret = super.removeItem(i, j);
            var data = BoomboxItem.getData(getItemStack(), provider);
            data.setOldCassetteTape(old);
            return ret;
        }
        return super.removeItem(i, j);
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (i == 0) {
            var data = BoomboxItem.getData(getItemStack(), provider);
            data.onCassetteTapeChange(stack, data.getCassetteTape());
        }
        super.setItem(i, stack);
    }

    public static void openContainer(ServerPlayer player, InteractionHand hand, ItemStack stack, HolderLookup.Provider provider) {
        var loc = new HandItemLocation(hand);
        OEMenuUtil.openItemMenu(player, createBoomboxMenuProvider(stack, loc, 2, "BoomboxItems", BoomboxMenu::create, provider), loc, stack, 2);
    }

    private static MenuProvider createBoomboxMenuProvider(ItemStack stack, PlayerItemLocation location, int size, String tagName, MenuFactory factory, HolderLookup.Provider provider) {
        var con = new BoomboxItemContainer(stack, location, size, tagName, player -> {
            if (location.getItem(player).isEmpty() || stack.isEmpty())
                return false;
            return location.getItem(player) == stack;
        }, provider);
        return new SimpleMenuProvider((i, inventory, player1) -> factory.createMenu(i, inventory, con, BlockPos.ZERO, stack, location), stack.getHoverName());
    }
}
