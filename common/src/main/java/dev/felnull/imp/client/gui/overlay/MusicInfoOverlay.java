package dev.felnull.imp.client.gui.overlay;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.client.gui.components.MusicInfoWidget;
import dev.felnull.imp.explatform.IMPCreateHUDExpectPlatform;
import dev.felnull.imp.integration.CreateIntegration;
import dev.felnull.imp.item.BoomboxItem;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class MusicInfoOverlay {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final MusicInfoWidget musicInfoWidget = new MusicInfoWidget(10, 10, 200, 59, 37);
    /** Bottom-centre bar used for the handheld boombox instead of the corner detail box. */
    private static final HandheldMusicHud HANDHELD_HUD = new HandheldMusicHud();

    public void render(GuiGraphics guiGraphics, float tickDelta) {
        var data = getPlayerData();
        if(data == null){return;}
        // With the handheld bottom bar on, the corner detail box is not drawn at all; turning the switch off brings it back.
        if (IamMusicPlayer.getConfig().hudHandheldBottom) {
            HANDHELD_HUD.render(guiGraphics, data);
            return;
        }
        musicInfoWidget.setPosition(IamMusicPlayer.getConfig().hudX, IamMusicPlayer.getConfig().hudY);
        musicInfoWidget.setData(data);
        musicInfoWidget.setMusic(data.getSelectedMusic() == null ? data.getCassetteTapeMusic() : data.getSelectedMusic());
        musicInfoWidget.render(guiGraphics, 0, 0, tickDelta);
    }

    /**
     * The boombox this overlay is drawn for: the first playing one in the inventory, else the off hand, else a Create
     * contraption; {@code null} when there is none. Public because the part-jump input line must talk about the same
     * boombox as the HUD, and the server scans identically ({@code ServerMessageHandler#findPlayingHandheldBoombox}).
     */
    public static BoomboxData getPlayerData(){
        var p = mc.player;
        if(p == null)return null;
        for (ItemStack stack: p.getInventory().items){
            if(stack.is(IMPBlocks.BOOMBOX.get().asItem())) {
                var data = BoomboxItem.getData(stack, p.level().registryAccess());
                if (!data.isPlaying())continue;
                return data;
            }
        }

        var stack = p.getItemInHand(InteractionHand.OFF_HAND);
        if(stack.is(IMPBlocks.BOOMBOX.get().asItem())) {
            var data = BoomboxItem.getData(stack, p.level().registryAccess());
            if (data.isPlaying())
                return data;
        }

        if(CreateIntegration.INSTANCE.isEnable()){
            var data = IMPCreateHUDExpectPlatform.getDataFromPlayer(p);
            if(data != null && data.isPlaying())
                return data;
        }

        return null;
    }
}
