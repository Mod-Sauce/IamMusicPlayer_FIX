package dev.felnull.imp.create.displaySource;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.saveddata.MusicSaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class BoomboxPlayListSource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext displayLinkContext, DisplayTargetStats displayTargetStats) {
        if(!(displayLinkContext.getSourceBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity))
            return EMPTY;
        var select = boomboxBlockEntity.getBoomboxData().getSelectedMusic();
        if(select == null)return EMPTY;
        var mode = displayLinkContext.sourceConfig().getInt("add_number");
        var max_count = Math.clamp(Integer.parseInt(displayLinkContext.sourceConfig().getString("max_count")), 1, Integer.MAX_VALUE);

        var play_list = MusicManager.getInstance().getPlaylistByMusic(boomboxBlockEntity.getServerLevel().getServer(), select.getUuid());
        var musics = MusicSaveData.get(boomboxBlockEntity.getServerLevel().getServer()).getMusics();
        if(play_list == null)return EMPTY;
        var start = play_list.getMusicList().indexOf(select.getUuid());
        if(start < 0)return EMPTY;
        var texts = new ArrayList<MutableComponent>();
        for (int i = 0; i < max_count; i++){
            var index = start + i;
            if(index >= play_list.getMusicList().size())break;
            var text = Component.empty();
            if(mode == 0)text.append(String.format("%d.", i + 1));
            var music = musics.get(play_list.getMusicList().get(index));
            if(music == null)break;
            text.append(Component.literal(music.getName()));
            texts.add(text);
        }

        return texts;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if(isFirstLine)return;
        builder.addSelectionScrollInput(0, 68, (selectionScrollInput, label) -> selectionScrollInput.forOptions(
                        List.of(Component.translatable("imp.create.text.add_number.yes"),
                                Component.translatable("imp.create.text.add_number.no")))
                .titled(Component.translatable("imp.create.text.add_number.title")), "add_number"
        );
        builder.addIntegerTextInput(69, 68, (editBox, tooltipArea) -> {}, "max_count");
    }

    @Override
    public Component getName() {
        return Component.translatable("imp.create.text.play_list");
    }
}
