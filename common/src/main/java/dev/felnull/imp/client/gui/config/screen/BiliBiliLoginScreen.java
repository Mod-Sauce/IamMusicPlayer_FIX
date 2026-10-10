package dev.felnull.imp.client.gui.config.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.bilibili.BiliBiliLogin;
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
 * Bilibili QR login screen; needed because the space and favourites APIs only work anonymously with the
 * {@code buvid3/buvid4/b_nut} trio, and risk control still answers 412 to a burst. Flow: {@code qrcode/generate} →
 * {@code qrcode/poll} every 2 s → {@code code=0} hands the login cookies over as {@code Set-Cookie} into {@code bilibiliConfig.bilibiliCookie} (never logged).
 */
public class BiliBiliLoginScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int POLL_MS = 2000;
    /** The key lives 180 s; poll a little less than that so the screen stops by itself. */
    private static final int MAX_POLLS = 85;

    private final Screen parent;
    private volatile boolean running = true;
    /** {@code init} runs again whenever the window is resized; the login must not be started a second time. */
    private boolean started;
    private volatile Component status;

    private String qrcodeKey = "";
    private ResourceLocation qrTexture;
    private DynamicTexture dynamicTexture;
    /** The bitmap behind {@link #dynamicTexture}, kept only so a later attempt can close it. */
    private NativeImage qrImage;

    public BiliBiliLoginScreen(Screen parent) {
        super(Component.translatable("imp.text.login.bilibili.title"));
        this.parent = parent;
        this.status = Component.translatable("imp.text.login.bilibili.loading");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("imp.text.login.bilibili.cancel"), btn -> {
            running = false;
            Minecraft.getInstance().setScreen(parent);
        }).bounds(width / 2 - 50, height / 2 + 100, 100, 20).build());

        if (!started) {
            started = true;
            CompletableFuture.runAsync(this::startLogin);
        }
    }

    private void startLogin() {
        try {
            var code = BiliBiliLogin.generate();
            qrcodeKey = code.key();
            var image = createQRCode(code.url());
            Minecraft.getInstance().execute(() -> registerTexture(image));
            status = Component.translatable("imp.text.login.bilibili.scan");
            poll();
        } catch (Exception e) {
            LOGGER.error("Failed to start bilibili login", e);
            status = Component.translatable("imp.text.login.bilibili.failed");
        }
    }

    private void poll() {
        for (int i = 0; i < MAX_POLLS && running; i++) {
            try {
                Thread.sleep(POLL_MS);
                if (!running)
                    return;
                var poll = BiliBiliLogin.poll(qrcodeKey);
                switch (poll.status()) {
                    case BiliBiliLogin.STATUS_WAITING -> status = Component.translatable("imp.text.login.bilibili.scan");
                    case BiliBiliLogin.STATUS_SCANNED -> status = Component.translatable("imp.text.login.bilibili.confirm");
                    case BiliBiliLogin.STATUS_OK -> {
                        if (poll.hasCookie()) {
                            running = false;
                            saveLogin(poll.cookie());
                            return;
                        }
                        LOGGER.error("Bilibili login reported success without a cookie");
                        status = Component.translatable("imp.text.login.bilibili.failed");
                    }
                    case BiliBiliLogin.STATUS_EXPIRED -> {
                        status = Component.translatable("imp.text.login.bilibili.expired");
                        return;
                    }
                    default -> {
                        // Anything else means the key is no longer usable — stop instead of polling a dead key
                        LOGGER.error("Bilibili login poll stopped with status {} ({})", poll.status(), poll.message());
                        status = Component.translatable("imp.text.login.bilibili.failed");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("Failed to poll bilibili login", e);
            }
        }
    }

    /** The cookie is written into the config and saved; the value itself is never logged. */
    private void saveLogin(String cookie) {
        Minecraft.getInstance().execute(() -> {
            try {
                var cfg = IamMusicPlayer.getConfig().bilibiliConfig;
                cfg.bilibiliCookie = cookie;
                AutoConfig.getConfigHolder(IMPConfig.class).save();
            } catch (Exception e) {
                LOGGER.error("Failed to save bilibili login state", e);
            }
            Minecraft.getInstance().getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.translatable("imp.text.login.bilibili.success"), null));
            Minecraft.getInstance().setScreen(null);
        });
    }

    private NativeImage createQRCode(String text) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 150, 150);
        var image = new NativeImage(matrix.getWidth(), matrix.getHeight(), false);
        for (int x = 0; x < matrix.getWidth(); x++)
            for (int y = 0; y < matrix.getHeight(); y++)
                image.setPixelRGBA(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        return image;
    }

    private void registerTexture(NativeImage image) {
        try {
            releaseTexture();
            dynamicTexture = new DynamicTexture(image);
            qrTexture = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "bilibili_qr");
            qrImage = image;
            Minecraft.getInstance().getTextureManager().register(qrTexture, dynamicTexture);
        } catch (Exception e) {
            status = Component.translatable("imp.text.login.bilibili.failed");
        }
    }

    /**
     * Drops the bitmap of a previous attempt: {@code register} replaces the texture in the manager without closing the
     * one it replaces, so every reopen or retry would otherwise keep one {@link NativeImage} alive. The pixels are
     * already uploaded into the GL texture when this runs, so closing the image cannot affect what is drawn.
     */
    private void releaseTexture() {
        if (qrImage != null) {
            try {
                qrImage.close();
            } catch (Exception ignored) {
                // a bitmap that refuses to close is not worth failing the login over
            }
            qrImage = null;
        }
        dynamicTexture = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        if (qrTexture != null)
            graphics.blit(qrTexture, cx - 75, cy - 90, 0, 0, 150, 150, 150, 150);
        graphics.drawCenteredString(font, Component.translatable("imp.text.login.bilibili.title"), cx, cy - 120, 0xFFFFFF);
        graphics.drawCenteredString(font, status, cx, cy + 70, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("imp.text.login.bilibili.note"), cx, cy + 84, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        running = false;
        Minecraft.getInstance().setScreen(parent);
    }
}
