package dev.felnull.imp.client.gui.config.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.client.qqMusic.QQMusicLogin;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * QQ 音乐扫码登录页（外观与轮询照 {@link KugouLoginScreen}）：QQ 给的是 **PNG**，直接进贴图；成功后 Cookie
 * 写进 {@code QQMusicConfig.QQMusicCookie}（与手贴 Cookie 同一个字段）。
 */
public class QQMusicLoginScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int POLL_MS = 2000;
    private static final int MAX_POLLS = 90;   // about 3 minutes

    private final Screen parent;
    private volatile boolean running = true;

    private ResourceLocation qrTexture;
    private DynamicTexture dynamicTexture;
    private volatile Component status;

    public QQMusicLoginScreen(Screen parent) {
        super(Component.translatable("imp.text.login.qq.title"));
        this.parent = parent;
        this.status = Component.translatable("imp.text.login.qq.loading");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("imp.text.login.qq.cancel"), btn -> {
            running = false;
            Minecraft.getInstance().setScreen(parent);
        }).bounds(width / 2 - 50, height / 2 + 100, 100, 20).build());

        CompletableFuture.runAsync(this::startLogin);
    }

    private void startLogin() {
        try {
            var qr = QQMusicLogin.generate();
            var image = NativeImage.read(qr.png());
            Minecraft.getInstance().execute(() -> registerTexture(image));
            status = Component.translatable("imp.text.login.qq.scan");
            poll(qr);
        } catch (Exception e) {
            LOGGER.error("Failed to start QQ login", e);
            status = Component.translatable("imp.text.login.qq.failed");
        }
    }

    private void poll(QQMusicLogin.QrCode qr) {
        for (int i = 0; i < MAX_POLLS && running; i++) {
            try {
                Thread.sleep(POLL_MS);
                if (!running)
                    return;
                var result = QQMusicLogin.poll(qr);
                switch (result.state()) {
                    case WAITING -> status = Component.translatable("imp.text.login.qq.scan");
                    case SCANNED -> status = Component.translatable("imp.text.login.qq.confirm");
                    case SUCCESS -> {
                        if (result.hasCookie()) {
                            running = false;
                            saveLogin(result.cookie());
                            return;
                        }
                        LOGGER.error("QQ login reported success without a cookie");
                        status = Component.translatable("imp.text.login.qq.failed");
                    }
                    case EXPIRED -> {
                        status = Component.translatable("imp.text.login.qq.expired");
                        return;
                    }
                    case FAILED -> {
                        status = Component.translatable("imp.text.login.qq.failed");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("Failed to poll QQ login", e);
            }
        }
    }

    /** The cookie goes into the same config field a hand-pasted one uses; the value itself is never logged. */
    private void saveLogin(String cookie) {
        Minecraft.getInstance().execute(() -> {
            try {
                var cfg = IamMusicPlayer.getConfig().QQMusicConfig;
                cfg.QQMusicCookie = cookie;
                AutoConfig.getConfigHolder(IMPConfig.class).save();
            } catch (Exception e) {
                LOGGER.error("Failed to save QQ login cookie", e);
            }
            Minecraft.getInstance().getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.translatable("imp.text.login.qq.success"), null));
            Minecraft.getInstance().setScreen(null);
        });
    }

    private void registerTexture(NativeImage image) {
        try {
            dynamicTexture = new DynamicTexture(image);
            qrTexture = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "qq_login_qr");
            Minecraft.getInstance().getTextureManager().register(qrTexture, dynamicTexture);
        } catch (Exception e) {
            status = Component.translatable("imp.text.login.qq.failed");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        if (qrTexture != null)
            graphics.blit(qrTexture, cx - 75, cy - 90, 0, 0, 150, 150, 150, 150);
        graphics.drawCenteredString(font, Component.translatable("imp.text.login.qq.title"), cx, cy - 120, 0xFFFFFF);
        graphics.drawCenteredString(font, status, cx, cy + 70, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("imp.text.login.qq.note"), cx, cy + 84, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        running = false;
        Minecraft.getInstance().setScreen(parent);
    }
}
