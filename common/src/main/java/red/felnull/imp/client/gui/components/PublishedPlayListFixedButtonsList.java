package red.felnull.imp.client.gui.components;

import net.minecraft.network.chat.Component;
import red.felnull.imp.client.gui.screen.monitor.MSDBaseMonitor;
import red.felnull.imp.music.resource.MusicPlayList;
import red.felnull.otyacraftengine.client.gui.components.FixedButtonsList;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PublishedPlayListFixedButtonsList extends FixedButtonsList<MusicPlayList> {
    public PublishedPlayListFixedButtonsList(int x, int y, int w, int h, int num, Component name, List<MusicPlayList> list, Function<MusicPlayList, Component> listName, Consumer<PressState<MusicPlayList>> onPress) {
        super(x, y, w, h, MSDBaseMonitor.MSD_WIDGETS, 0, 20, 256, 256, num, name, list, listName, onPress);
    }
}
