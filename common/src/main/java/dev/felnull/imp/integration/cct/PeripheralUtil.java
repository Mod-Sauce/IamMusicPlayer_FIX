package dev.felnull.imp.integration.cct;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class PeripheralUtil {
    public static UUID getUUID(String uuid) throws LuaException {
        try{
            return UUID.fromString(uuid);
        } catch (Exception e) {
            throw new LuaException("Invalid UUID format.");
        }
    }

    public static Container findContainer(IComputerAccess computerAccess, String name) throws LuaException {
        var chest = computerAccess.getAvailablePeripheral(name);
        if(chest == null){
            throw new LuaException("404 Not Found");
        }
        if (!(chest.getTarget() instanceof Container container)) {
            throw new LuaException("Target is not a container");
        }
        return container;
    }

    // The method for storing items does not exist in NeoForge; it is automatically registered in Fabric. This is reserved for NeoForge use.
    public static void transferExact(
            Container from,
            Container to,
            int fromSlot,
            Integer toSlot,
            Integer amount
    ) throws LuaException {

        ItemStack sourceStack = from.getItem(fromSlot);

        if (sourceStack.isEmpty()) {
            throw new LuaException("Source slot is empty");
        }

        int moveAmount = (amount == null) ? sourceStack.getCount() : amount;

        if (moveAmount <= 0) {
            throw new LuaException("Amount must be > 0");
        }

        if (sourceStack.getCount() < moveAmount) {
            throw new LuaException("Not enough items in source slot");
        }

        ItemStack toMove = sourceStack.copy();
        toMove.setCount(moveAmount);

        ItemStack remaining;

        if (toSlot != null) {
            remaining = tryInsertIntoSlot(to, toSlot, toMove.copy());
        } else {
            remaining = toMove.copy();
            for (int i = 0; i < to.getContainerSize(); i++) {
                remaining = tryInsertIntoSlot(to, i, remaining);
                if (remaining.isEmpty()) break;
            }
        }

        if (!remaining.isEmpty()) {
            throw new LuaException("Not enough space in target container");
        }

        if (toSlot != null) {
            insertIntoSlotReal(to, toSlot, toMove.copy());
        } else {
            ItemStack moving = toMove.copy();
            for (int i = 0; i < to.getContainerSize(); i++) {
                moving = insertIntoSlotReal(to, i, moving);
                if (moving.isEmpty()) break;
            }
        }

        sourceStack.shrink(moveAmount);
        if (sourceStack.isEmpty()) {
            from.setItem(fromSlot, ItemStack.EMPTY);
        } else {
            from.setChanged();
        }

        to.setChanged();
    }

    public static ItemStack tryInsertIntoSlot(Container container, int slot, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack target = container.getItem(slot);

        if (!container.canPlaceItem(slot, stack)) {
            return stack;
        }

        if (target.isEmpty()) {
            int max = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
            if (stack.getCount() <= max) {
                return ItemStack.EMPTY;
            } else {
                ItemStack remaining = stack.copy();
                remaining.shrink(max);
                return remaining;
            }
        }

        if (!ItemStack.isSameItemSameComponents(target, stack)) {
            return stack;
        }

        int max = Math.min(target.getMaxStackSize(), container.getMaxStackSize());
        int space = max - target.getCount();

        if (space <= 0) return stack;

        if (stack.getCount() <= space) {
            return ItemStack.EMPTY;
        } else {
            ItemStack remaining = stack.copy();
            remaining.shrink(space);
            return remaining;
        }
    }

    public static ItemStack insertIntoSlotReal(Container container, int slot, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack target = container.getItem(slot);

        if (!container.canPlaceItem(slot, stack)) {
            return stack;
        }

        if (target.isEmpty()) {
            int max = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
            int move = Math.min(max, stack.getCount());

            ItemStack newStack = stack.copy();
            newStack.setCount(move);
            container.setItem(slot, newStack);

            ItemStack remaining = stack.copy();
            remaining.shrink(move);
            return remaining;
        }

        if (!ItemStack.isSameItemSameComponents(target, stack)) {
            return stack;
        }

        int max = Math.min(target.getMaxStackSize(), container.getMaxStackSize());
        int space = max - target.getCount();

        if (space <= 0) return stack;

        int move = Math.min(space, stack.getCount());
        target.grow(move);

        ItemStack remaining = stack.copy();
        remaining.shrink(move);
        return remaining;
    }
}
