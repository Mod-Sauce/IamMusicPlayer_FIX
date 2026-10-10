package dev.felnull.imp.client.gui.config.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.kugou.KuGouApi;
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
 * The KuGou QR login screen: {@code login-user.kugou.com/v2/qrcode} → scan {@code h5.kugou.com/apps/loginQRCode/…?appid=3116}
 * with the KuGou app → poll {@code /v2/get_userinfo_qrcode}, {@code status=4} succeeds and returns token/userid → config.
 */
public class KugouLoginScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int POLL_MS = 2000;
    private static final int MAX_POLLS = 90;   // about 3 minutes

    private final Screen parent;
    private volatile boolean running = true;
    /** {@code init} runs again whenever the window is resized; the login must not be started a second time. */
    private boolean started;

    private String qrcode;
    private ResourceLocation qrTexture;
    private DynamicTexture dynamicTexture;
    /** The bitmap behind {@link #dynamicTexture}, kept only so a later attempt can close it. */
    private NativeImage qrImage;
    private volatile Component status;

    public KugouLoginScreen(Screen parent) {
        super(Component.translatable("imp.text.login.kugou.title"));
        this.parent = parent;
        this.status = Component.translatable("imp.text.login.kugou.loading");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("imp.text.login.kugou.cancel"), btn -> {
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
            qrcode = KuGouApi.fetchQrKey();
            if (qrcode == null || qrcode.isEmpty()) {
                status = Component.translatable("imp.text.login.kugou.failed");
                return;
            }
            var image = createQRCode(KuGouApi.QR_CODE_URL_PREFIX + "qrcode=" + qrcode);
            Minecraft.getInstance().execute(() -> registerTexture(image));
            status = Component.translatable("imp.text.login.kugou.scan");
            poll();
        } catch (Exception e) {
            LOGGER.error("Failed to start kugou login", e);
            status = Component.translatable("imp.text.login.kugou.failed");
        }
    }

    private void poll() {
        for (int i = 0; i < MAX_POLLS && running; i++) {
            try {
                Thread.sleep(POLL_MS);
                if (!running)
                    return;
                var st = KuGouApi.checkQr(qrcode);
                switch (st.status()) {
                    case 0, 1 -> status = Component.translatable("imp.text.login.kugou.scan");
                    case 2 -> status = Component.translatable("imp.text.login.kugou.confirm");
                    case 4 -> {
                        if (st.success()) {
                            running = false;
                            var token = st.token();
                            var userid = st.userid();
                            var vipToken = st.vipToken();
                            Minecraft.getInstance().execute(() -> {
                                saveLogin(token, userid, vipToken);
                                Minecraft.getInstance().setScreen(null);
                                Minecraft.getInstance().getToasts().addToast(new SystemToast(
                                        SystemToast.SystemToastId.NARRATOR_TOGGLE,
                                        Component.translatable("imp.text.login.kugou.success"), null));
                            });
                            return;
                        }
                    }
                    default -> {
                        // Negative/unknown: most likely the QR code expired, finish right away and tell the user
                        status = Component.translatable("imp.text.login.kugou.expired");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("Failed to poll kugou login", e);
            }
        }
    }

    private void saveLogin(String token, String userid, String vipToken) {
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        cfg.token = token == null ? "" : token;
        cfg.userid = userid == null ? "" : userid;
        cfg.vipToken = vipToken == null ? "" : vipToken;
        // Mirror the Cookie field as well, for manual copying/troubleshooting (URL resolve reads it too)
        var sb = new StringBuilder();
        if (!cfg.token.isEmpty())
            sb.append("token=").append(cfg.token);
        if (!cfg.userid.isEmpty())
            sb.append(sb.length() > 0 ? "; " : "").append("userid=").append(cfg.userid);
        if (sb.length() > 0)
            cfg.cookie = sb.toString();
        try {
            AutoConfig.getConfigHolder(IMPConfig.class).save();
        } catch (Exception e) {
            LOGGER.error("Failed to save kugou login state", e);
        }
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
            qrTexture = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "kugou_qr");
            qrImage = image;
            Minecraft.getInstance().getTextureManager().register(qrTexture, dynamicTexture);
        } catch (Exception e) {
            status = Component.translatable("imp.text.login.kugou.failed");
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
            graphics.blit(qrTexture, cx - 75, cy - 80, 0, 0, 150, 150, 150, 150);
        graphics.drawCenteredString(font, Component.translatable("imp.text.login.kugou.title"), cx, cy - 120, 0xFFFFFF);
        graphics.drawCenteredString(font, status, cx, cy + 90, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        running = false;
        Minecraft.getInstance().setScreen(parent);
    }
}
