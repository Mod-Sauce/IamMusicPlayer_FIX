package dev.felnull.imp.client.gui.config.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.IamMusicPlayer;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NeteaseLoginScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Screen parent;
    private volatile boolean running = true;

    private String key;

    private ResourceLocation qrTexture;
    private DynamicTexture dynamicTexture;

    private Component status = Component.translatable("imp.text.login.netease.loading");

    private final Map<String, String> requestPropertyData = new HashMap<>() {{
        put("Host", "music.163.com");
        put("Origin", "https://music.163.com");
        put("Referer", "https://music.163.com/");
        put("Content-Type", "application/x-www-form-urlencoded");
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) " + "AppleWebKit/537.36 " + "(KHTML, like Gecko) " + "Chrome/91.0.4472.164 " + "NeteaseMusicDesktop/3.1.6");
    }};

    public NeteaseLoginScreen(Screen parent) {
        super(Component.translatable("imp.text.login.netease.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("imp.text.login.netease.cancel"), btn -> {
            running = false;
            Minecraft.getInstance().setScreen(parent);
        }).bounds(width / 2 - 50, height / 2 + 100, 100, 20).build());

        CompletableFuture.runAsync(this::startLogin);
    }

    private void startLogin() {
        try {
            String body = connToString(get("https://music.163.com/api/login/qrcode/unikey?type=3"));

            key = body.replaceAll(".*\"unikey\":\"(.*?)\".*", "$1");

            NativeImage image = createQRCode("https://music.163.com/login?codekey=" + key);

            Minecraft.getInstance().execute(() -> registerTexture(image));

            status = Component.translatable("imp.text.login.netease.scan");

            poll();

        } catch (Exception e) {
            LOGGER.error("Failed to start netease login", e);

            status = Component.translatable("imp.text.login.netease.failed");
        }
    }

    private void poll() {
        while (running) {
            try {
                Thread.sleep(2000);

                HttpURLConnection connection = get("https://music.163.com/api/login/qrcode/client/login?key=" + key + "&type=3");

                String body = connToString(connection);

                int code = Integer.parseInt(body.replaceAll(".*\"code\":(\\d+).*", "$1"));

                if (code == 802) {
                    status = Component.translatable("imp.text.login.netease.confirm");
                }

                if (code == 803) {
                    running = false;

                    String cookie = getCookies(connection);

                    Minecraft.getInstance().execute(() -> {
                        setCookie(cookie);
                        Minecraft.getInstance().setScreen(null);
                        Minecraft.getInstance().getToasts().addToast(new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE,
                                Component.translatable("imp.text.login.netease.success"), null));
                    });

                    return;
                }

                if (code == 800) {
                    status = Component.translatable("imp.text.login.netease.expired");
                    return;
                }

            } catch (Exception e) {
                LOGGER.error("Failed to poll netease login", e);
            }
        }
    }

    private String getCookies(HttpURLConnection connection) {
        Map<String, String> cookieMap = new HashMap<>();

        connection.getHeaderFields().forEach((key, values) -> {
            if (key != null && key.equalsIgnoreCase("Set-Cookie")) {
                for (String value : values) {
                    String cookie = value.split(";", 2)[0];

                    String[] parts = cookie.split("=", 2);

                    if (parts.length == 2) {
                        cookieMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        });

        return cookieMap.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + "; " + b).orElse("");
    }

    protected void setCookie(String cookie) {
        IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie = cookie;

        AutoConfig.getConfigHolder(IMPConfig.class).save();
    }

    private HttpURLConnection get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        requestPropertyData.forEach(conn::setRequestProperty);

        conn.setConnectTimeout(12000);
        conn.setDoInput(true);

        return conn;
    }

    private String connToString(HttpURLConnection connection) {
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private NativeImage createQRCode(String text) throws Exception {
        BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 150, 150);

        NativeImage nativeImage = new NativeImage(bitMatrix.getWidth(), bitMatrix.getHeight(), false);

        for (int x = 0; x < bitMatrix.getWidth(); x++) {
            for (int y = 0; y < bitMatrix.getHeight(); y++) {
                nativeImage.setPixelRGBA(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        return nativeImage;
    }

    private void registerTexture(NativeImage image) {
        try {
            dynamicTexture = new DynamicTexture(image);

            qrTexture = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "netease_qr");

            Minecraft.getInstance().getTextureManager().register(qrTexture, dynamicTexture);

        } catch (Exception e) {
            status = Component.translatable("imp.text.login.netease.failed");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int cx = width / 2;
        int cy = height / 2;

        if (qrTexture != null) {
            graphics.blit(qrTexture, cx - 75, cy - 80, 0, 0, 150, 150, 150, 150);
        }

        graphics.drawCenteredString(font, Component.translatable("imp.text.login.netease.title"), cx, cy - 120, 0xffffff);

        graphics.drawCenteredString(font, status, cx, cy + 90, 0xffffff);
    }

    @Override
    public void onClose() {
        running = false;
        Minecraft.getInstance().setScreen(parent);
    }
}