package dev.felnull.imp.create.displaySource;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class BoomboxPercentSource extends PercentOrProgressBarDisplaySource {
    @Override
    protected @Nullable Float getProgress(DisplayLinkContext displayLinkContext) {
        if(!(displayLinkContext.getSourceBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity) ||
                boomboxBlockEntity.getRingerMusicSource() == null)
            return null;
        return boomboxBlockEntity.getRingerPosition() / (float)boomboxBlockEntity.getRingerMusicSource().getDuration();
    }

    @Override
    protected boolean progressBarActive(DisplayLinkContext displayLinkContext) {
        return true;
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext displayLinkContext) {
        return true;
    }

    @Override
    public Component getName() {
        return Component.translatable("imp.create.text.music_percent");
    }
}
