package dev.felnull.imp.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;

public class IMPMaidPlugin implements ILittleMaid {
    @Override
    public void addHardcodeAnimation(HardcodedAnimationManger manger) {
        manger.addMaidAnimation(new HeadSwayAnimation());
    }
}
