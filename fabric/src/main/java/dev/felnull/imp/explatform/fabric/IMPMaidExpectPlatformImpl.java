package dev.felnull.imp.explatform.fabric;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class IMPMaidExpectPlatformImpl {
    public static void tickMaid(Entity entity) {
        if (!(entity instanceof EntityMaid entityMaid)) {
            return;
        }
        var inv = entityMaid.getMaidInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            var item = inv.getStackInSlot(i);
            if(item.is(IMPBlocks.BOOMBOX.get().asItem()))
                BoomboxItem.tick(entityMaid.level(), entity, item, true);
        }
        ItemStack stack = entityMaid.getMainHandItem();
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem()))
            BoomboxItem.tick(entityMaid.level(), entity, stack, true);
        stack = entityMaid.getOffhandItem();
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem()))
            BoomboxItem.tick(entityMaid.level(), entity, stack, true);
    }

    public static ItemStack getMaidBoombox(Entity entity) {
        if (!(entity instanceof EntityMaid entityMaid)) {
            return ItemStack.EMPTY;
        }
        var inv = entityMaid.getMaidInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            var item = inv.getStackInSlot(i);
            if(item.is(IMPBlocks.BOOMBOX.get().asItem()))
                return item;
        }
        ItemStack stack = entityMaid.getMainHandItem();
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem()))
            BoomboxItem.tick(entityMaid.level(), entity, stack, true);
        stack = entityMaid.getOffhandItem();
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem()))
            BoomboxItem.tick(entityMaid.level(), entity, stack, true);
        return ItemStack.EMPTY;
    }
}
