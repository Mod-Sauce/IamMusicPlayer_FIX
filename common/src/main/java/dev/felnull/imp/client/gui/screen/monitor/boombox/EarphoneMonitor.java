package dev.felnull.imp.client.gui.screen.monitor.boombox;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.gui.components.EarphoneFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EarphoneMonitor extends BoomboxMonitor{
    private SmartButton backButton;
    private EarphoneFixedListWidget earphoneLocationListWidget;
    private final List<EarphoneSaveData.EarphoneLocation> earphoneLocationList = new ArrayList<>();
    public EarphoneMonitor(BoomboxData.MonitorType monitorType, BoomboxScreen screen) {
        super(monitorType, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.backButton = this.addRenderWidget(new SmartButton(getStartX() + width - 15, getStartY() + height - 12, 14, 11, Component.translatable("gui.back"), n -> setMonitor(BoomboxData.MonitorType.PLAYBACK)));
        this.backButton.setHideText(true);
        this.backButton.setIcon(MusicManagerMonitor.WIDGETS_TEXTURE, 11, 123, 8, 8);

        initList();

        getScreen().instruction("earphone", new CompoundTag());
    }

    public void setEarphoneLocationList(List<EarphoneSaveData.EarphoneLocation> earphoneLocationList) {
        this.earphoneLocationList.clear();
        this.earphoneLocationList.addAll(earphoneLocationList);

        earphoneLocationListWidget.setEntryList(earphoneLocationList);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        drawSmartCenterText(guiGraphics, Component.translatable("imp.text.earphone.tip"), getStartX() + (float) width / 2, getStartY() + earphoneLocationListWidget.getHeight() + 3);
    }

    private void initList(){
        this.earphoneLocationListWidget = this.addRenderWidget(new EarphoneFixedListWidget(
                getStartX() + 1, getStartY() + 1, 200, 35 - 12, Component.translatable("imp.text.earphone"), earphoneLocationList, (widget, location) -> {
            if(!Objects.equals(location.earphoneUUID(), getScreen().getBoomBoxData().getEarphoneUUID())){
                var data = new CompoundTag();
                data.putUUID("uuid", location.earphoneUUID());
                getScreen().instruction("connect_earphone", data);
                earphoneLocationListWidget.setConnected(location.earphoneUUID());
                getScreen().getBoomBoxData().setEarphoneUUID(location.earphoneUUID());
            }else {
                getScreen().instruction("connect_earphone", new CompoundTag());
                earphoneLocationListWidget.setConnected(null);
                getScreen().getBoomBoxData().setEarphoneUUID(null);
            }
        }
        ));
        earphoneLocationListWidget.setConnected(getScreen().getBoomBoxData().getEarphoneUUID());
    }

    @Override
    public void renderAppearance(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight, BoomboxData data) {
        super.renderAppearance(poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight, data);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        renderFixedListSprite(poseStack, multiBufferSource, 1, 1, OERenderUtils.MIN_BREADTH * 2f, 200, 35 - 12, i, j, onPxW, onPxH, monitorHeight, earphoneLocationList, 5, (poseStack1, multiBufferSource1, x, y, z, w, h, i1, j1, entry) -> {
            Component name;
            var item = EarphoneSaveData.findEarphone(Objects.requireNonNull(Minecraft.getInstance().level), entry);
            if(item != null)name = item.getHoverName();
            else name = Component.translatable("imp.text.earphone");
            renderSmartTextSprite(poseStack1, multiBufferSource1, Component.literal(OEClientUtils.getWidthOmitText(name.getString(), w - (h + 7), "...")), x + h + 3f, y + (h - 6.5f) / 2f, z + OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, i1);
        });
        renderSmartButtonSprite(poseStack, multiBufferSource, width - 15, height - 12, OERenderUtils.MIN_BREADTH * 2f, 14, 11, i, j, onPxW, onPxH, monitorHeight, MusicManagerMonitor.WIDGETS_TEXTURE, 11, 123, 8, 8, 256, 256);
        renderSmartCenterTextSprite(poseStack, multiBufferSource, Component.translatable("imp.text.earphone.tip"), width / 2f, height / 2f + 10, OERenderUtils.MIN_BREADTH * 2f, onPxW, onPxH, monitorHeight, i);
    }
}
