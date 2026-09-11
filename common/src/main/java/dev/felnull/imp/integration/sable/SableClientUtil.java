package dev.felnull.imp.integration.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class SableClientUtil {
    public static Vec3 getLookVector(BlockPos blockPos, Camera camera, float partialTicks) {
        var subLevel = SableCompanion.INSTANCE.getContainingClient(blockPos);
        if (subLevel == null) {
            return null;
        }
        Vector3f lookVector = camera.getLookVector();
        Vec3 vec3 = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        return subLevel.renderPose(partialTicks).transformNormalInverse(vec3);
    }
}
