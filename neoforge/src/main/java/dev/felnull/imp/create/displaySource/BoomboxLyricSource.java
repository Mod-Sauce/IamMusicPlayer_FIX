package dev.felnull.imp.create.displaySource;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collections;
import java.util.List;

public class BoomboxLyricSource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext displayLinkContext, DisplayTargetStats displayTargetStats) {
        if(!IamMusicPlayer.getConfig().serverLyric)return List.of(
                Component.translatable("imp.create.text.lyric.disable")
        );
        if(!(displayLinkContext.getSourceBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity))
            return EMPTY;
        boomboxBlockEntity.updateLyric();
        var lyric = boomboxBlockEntity.getLyric();
        if(lyric == null)return EMPTY;
        if(lyric.isEmpty())return EMPTY;
        var part = lyric.getPart(boomboxBlockEntity.getRingerPosition() / 1000f);
        if(part.getA() == null)return EMPTY;
        if(displayLinkContext.sourceConfig().getInt("showTrans") == 0 && part.getB() != null)
            return List.of(Component.literal(part.getA()), Component.literal(part.getB()));
        return List.of(Component.literal(part.getA()));
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if(isFirstLine)return;
        builder.addSelectionScrollInput(0, 137, (selectionScrollInput, label) -> selectionScrollInput.forOptions(
                List.of(Component.translatable("imp.create.text.showTrans.yes"),
                        Component.translatable("imp.create.text.showTrans.no")))
                .titled(Component.translatable("imp.create.text.showTrans.title")), "showTrans"
        );
    }

    @Override
    public Component getName() {
        return Component.translatable("imp.create.text.lyric");
    }
}
