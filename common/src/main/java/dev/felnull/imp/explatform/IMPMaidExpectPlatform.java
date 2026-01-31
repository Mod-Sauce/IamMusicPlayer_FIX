package dev.felnull.imp.explatform;

import dev.architectury.event.events.common.TickEvent;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class IMPMaidExpectPlatform {
    public static void init(){
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            for (Entity entity: level.getAllEntities()) {
                tickMaid(entity);
            }
        });
    }

    @ExpectPlatform
    public static void tickMaid(Entity entity){
        throw new AssertionError();
    }

    @ExpectPlatform
    public static ItemStack getMaidBoombox(Entity entity){
        throw new AssertionError();
    }
}
