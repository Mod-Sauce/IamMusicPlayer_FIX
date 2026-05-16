package dev.felnull.imp.forge.mixin;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.create.pointType.IMPPointTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity", remap = false)
@Pseudo
public class ArmDancingMixin {
    @Inject(method = "checkForMusicAmong", at = @At("RETURN"), cancellable = true)
    public void dance(List<ArmInteractionPoint> list, CallbackInfoReturnable<Boolean> cir){
        // The compatibility is implemented in some strange places...
        for(ArmInteractionPoint point: list){
            if(!(point instanceof IMPPointTypes.BoomboxType.BoomboxArmInteractionPoint point1))continue;
            if(!(point1.getLevel().getBlockEntity(point1.getPos()) instanceof BoomboxBlockEntity be))continue;
            if(be.isPlaying())cir.setReturnValue(true);
        }
    }
}
