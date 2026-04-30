package dev.felnull.imp.integration.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PosGetter {
    public static Vec3 getReallyPos(Level level, BlockPos blockPos){
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, (Position) blockPos.getCenter());
    }
}
