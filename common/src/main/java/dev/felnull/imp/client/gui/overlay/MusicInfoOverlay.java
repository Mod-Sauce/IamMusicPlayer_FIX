package dev.felnull.imp.client.gui.overlay;

import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.client.gui.components.MusicInfoWidget;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class MusicInfoOverlay {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final MusicInfoWidget musicInfoWidget = new MusicInfoWidget(10, 10, 200, 59, 37);

    public void render(GuiGraphics guiGraphics, float tickDelta) {
        var data = getPlayerData();
        if(data == null){return;}
        musicInfoWidget.setData(data);
        musicInfoWidget.setMusic(data.getSelectedMusic());
        musicInfoWidget.render(guiGraphics, 0, 0, tickDelta);
    }

    private BoomboxData getPlayerData(){
        var p = mc.player;
        if(p == null)return null;
        for (ItemStack stack: p.getInventory().items){
            if(stack.is(IMPBlocks.BOOMBOX.get().asItem())) {
                var data = BoomboxItem.getData(stack, p.level().registryAccess());
                if (!data.isPlaying())continue;
                return data;
            }
        }
        return null;
    }
}
