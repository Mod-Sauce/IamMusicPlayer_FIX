package dev.felnull.imp.maid;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.api.animation.ICustomAnimation;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IModelRenderer;
import dev.felnull.imp.explatform.fabric.IMPMaidExpectPlatformImpl;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;

public class HeadSwayAnimation implements ICustomAnimation<Mob> {

    @Override
    public void setRotationAngles(Mob entity, HashMap<String, ? extends IModelRenderer> models,
                                  float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch) {
        var boombox = IMPMaidExpectPlatformImpl.getMaidBoombox(entity);
        if(boombox.isEmpty())return;
        var data = BoomboxItem.getData(boombox, entity.level().registryAccess());
        if(!data.isPlaying())return;
        BedrockPart head = ICustomAnimation.getPartOrNull(models, "head");
        if (head == null) {
            return;
        }
        float timeSeconds = ageInTicks / 10.0f;
        float angle = -3.0f * Mth.sin(Mth.PI * timeSeconds);
        head.zRot = (float) Math.toRadians(angle);
    }
}