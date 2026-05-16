package dev.felnull.imp.create.displaySource;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class BoomboxPlayModeSource extends SingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext displayLinkContext, DisplayTargetStats displayTargetStats) {
        if(!(displayLinkContext.getSourceBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity))
            return EMPTY_LINE;
        return boomboxBlockEntity.getBoomboxData().getContinuousType().getComponent().copy();
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext displayLinkContext) {
        return true;
    }

    @Override
    public Component getName() {
        return Component.translatable("imp.create.text.play_mode");
    }
}
