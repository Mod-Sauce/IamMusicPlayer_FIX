package dev.felnull.imp.create.displaySource;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class BoomboxMusicNameSource extends SingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext displayLinkContext, DisplayTargetStats displayTargetStats) {
        if(!(displayLinkContext.getSourceBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity))
            return EMPTY_LINE;
        Music music;
        if(boomboxBlockEntity.getBoomboxData().isRadioRemote())
            music = boomboxBlockEntity.getBoomboxData().getSelectedMusic();
        else music = boomboxBlockEntity.getBoomboxData().getCassetteTapeMusic();
        if(music == null)return EMPTY_LINE;
        return Component.literal(music.getName());
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext displayLinkContext) {
        return true;
    }

    @Override
    public Component getName() {
        return Component.translatable("imp.create.text.music_name");
    }
}
