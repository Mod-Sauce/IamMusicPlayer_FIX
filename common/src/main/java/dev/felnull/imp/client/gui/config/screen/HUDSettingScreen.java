package dev.felnull.imp.client.gui.config.screen;

import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.gui.components.MusicInfoWidget;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class HUDSettingScreen extends Screen {
    public int x;
    public int y;
    private final Screen parent;
    private static final int hudWidth = 200;
    private static final int hudHeight = 59;
    private MusicInfoWidget widget;
    public HUDSettingScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.reset"), button -> {
                    x = 10;
                    y = 10;
                    widget.setPosition(x, y);
                }).pos(width - 170, height - 30)
                .size(50, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.ok"), button -> onClose()).pos(width - 60, height - 30)
                .size(50, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.cancel"), button -> Minecraft.getInstance().setScreen(parent)).pos(width - 115, height - 30)
                .size(50, 20)
                .build());
        var slider = new AbstractSliderButton(width - 170 - 110, height - 30, 100, 20, Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.scale"),
                IamMusicPlayer.getConfig().hudScale) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.scale", value));
            }

            @Override
            protected void applyValue() {
                IamMusicPlayer.getConfig().hudScale = (float) Math.clamp(value + 0.5, 0.5, 1.5);
            }
        };
        slider.updateMessage();
        this.addRenderableWidget(slider);
        widget = new MusicInfoWidget(0, 0, hudWidth, hudHeight, 37);
        widget.setPosition((int) (x * width / 2f), y * hudHeight);
        widget.setMusic(new Music(UUID.randomUUID(),
                Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.example_title").getString(),
                Component.translatable("text.autoconfig.iammusicplayer.option.hud_pos.example_author").getString()
                , MusicSource.EMPTY, ImageInfo.EMPTY, UUID.randomUUID(), 0));
        widget.setData(new BoomboxData(new CompoundTag(), null));
        widget.active = false;
        addRenderableWidget(widget);
        x = IamMusicPlayer.getConfig().hudX;
        y = IamMusicPlayer.getConfig().hudY;
        widget.setPosition(x, y);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(super.mouseDragged(mouseX, mouseY, button, dragX, dragY))return false;
        if(!widget.isHovered())return false;
        x = (int) mouseX - hudWidth / 2;
        y = (int) mouseY - hudHeight / 2;
        widget.setPosition(x, y);
        return true;
    }

    @Override
    public void onClose() {
        IamMusicPlayer.getConfig().hudX = widget.getX();
        IamMusicPlayer.getConfig().hudY = widget.getY();
        AutoConfig.getConfigHolder(IMPConfig.class).save();
        Minecraft.getInstance().setScreen(parent);
    }
}
