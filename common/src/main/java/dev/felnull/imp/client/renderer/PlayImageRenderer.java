package dev.felnull.imp.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.fnjl.util.FNMath;
import dev.felnull.fnjl.util.FNURLUtil;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.gui.PlayerNameUtil;
import dev.felnull.imp.music.resource.ImageInfo;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OETextureUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URL;
import java.util.regex.Pattern;

public class PlayImageRenderer {
    private static final ResourceLocation MISSING_YOUTUBE_THUMBNAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/image/missing_youtube_thumbnail.png");
    private static final ResourceLocation MISSING_SOUND_CLOUD_THUMBNAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/image/missing_sound_cloud_artwork.png");
    private static final PlayImageRenderer INSTANCE = new PlayImageRenderer();
    private static final String YOUTUBE_THUMBNAIL_URL = "https://i.ytimg.com/vi/%s/hqdefault.jpg";
    private static final Pattern YOUTUBE_THUMBNAIL_URL_REGEX = Pattern.compile("https://i.ytimg.com/vi/.+/hqdefault.jpg");
    private static final Pattern SOUND_CLOUD_URL_REGEX = Pattern.compile("https://soundcloud.com/.+");

    // ---- CDN whitelist for the cover images this mod itself produces ----
    // Otya's URL texture downloader has a hard allow-list (its checkUrl throws "Not allowed URL" on a mismatch) that only covers imgur / music.126,
    // so Bilibili (i*.hdslb.com) and QQ Music (*.gtimg.cn) covers could never load. This file implements that hook; the CDNs used here are allowed below.
    private static final Pattern BILIBILI_IMAGE_URL_REGEX = Pattern.compile("https?://([0-9a-z-]+\\.)*hdslb\\.com/.*");
    /** QQ Music / QQ image host: y.gtimg.cn, p.qpic.cn, etc. */
    private static final Pattern QQ_IMAGE_URL_REGEX = Pattern.compile("https?://([0-9a-z-]+\\.)*(gtimg\\.cn|qpic\\.cn|qq\\.com)/.*");
    /** NetEase image host: p1~p4.music.126.net (Otya's narrow list allows it too; listed explicitly here in case that list changes again). */
    private static final Pattern NETEASE_IMAGE_URL_REGEX = Pattern.compile("https?://([0-9a-z-]+\\.)*music\\.126\\.(net|com)/.*");
    /** Kugou image host: imge/imgessl.kugou.com (covers), singerimg.kugou.com (artist faces), etc. */
    private static final Pattern KUGOU_IMAGE_URL_REGEX = Pattern.compile("https?://([0-9a-z-]+\\.)*(kugou|kgimg|kglink)\\.com/.*");
    private static final String SCT_IMG_ST = "<img ";
    private static final String SCT_IMG_END = ">";
    private static final String SCT_SRC_ST = "src=\"";
    private static final String SCT_SRC_END = "\"";

    public static PlayImageRenderer getInstance() {
        return INSTANCE;
    }

    public String getSwapURL(String url) {
        if (url.startsWith("imp_sct_")) {
            var scurl = url.substring("imp_sct_".length());
            if (SOUND_CLOUD_URL_REGEX.matcher(scurl).matches()) {
                String ret = null;
                try {
                    ret = extractSoundCloudImage(FNURLUtil.getResponse(new URL(scurl)));
                } catch (Exception ignored) {
                }
                return ret;
            }
        } 
        return null;
    }

    public boolean isAllowURL(String url) {
        if (YOUTUBE_THUMBNAIL_URL_REGEX.matcher(url).matches()) return true;
        // Covers produced by this mod for each platform (Bilibili / QQ Music / NetEase) — see the comments on the three patterns above:
        // without allow-listing them Otya's checkUrl throws "Not allowed URL" outright and the image never loads.
        if (BILIBILI_IMAGE_URL_REGEX.matcher(url).matches()) return true;
        if (QQ_IMAGE_URL_REGEX.matcher(url).matches()) return true;
        if (NETEASE_IMAGE_URL_REGEX.matcher(url).matches()) return true;
        if (KUGOU_IMAGE_URL_REGEX.matcher(url).matches()) return true;
        if (url.startsWith("imp_sct_")) {
            var scurl = url.substring("imp_sct_".length());
            return SOUND_CLOUD_URL_REGEX.matcher(scurl).matches();
        } else if (url.startsWith("imp_ncmp_")) {
            var curl = url.substring("imp_ncmp_".length());
            return !curl.isEmpty();
        }
        return false;
    }

    public void draw(ImageInfo imageInfo, PoseStack poseStack, float x, float y, int size) {
        draw(imageInfo, poseStack, x, y, size, true);
    }

    public void draw(ImageInfo imageInfo, PoseStack poseStack, float x, float y, float size, boolean cash) {
        if (imageInfo.isEmpty()) return;

        if (imageInfo.getImageType() == ImageInfo.ImageType.PLAYER_FACE) {
            drawPlayerFaceImage(imageInfo.getIdentifier(), poseStack, x, y, size);
            return;
        }
        var td = getImageTexture(imageInfo, cash);
        if (td != null) drawTextureImage(td.getLeft(), poseStack, td.getRight().x, td.getRight().y, x, y, size);
    }

    /**
     * "Player face" cover ({@code ImageType.PLAYER_FACE}) — the data **stores a name**.
     */
    public void drawPlayerFaceImage(String name, PoseStack poseStack, float x, float y, float size) {
        var playerId = PlayerNameUtil.uuidByName(name);
        // [Diagnostic] The cover stores a **name**: log which UUID it resolved to (and when it did not resolve),
        // because a wrong UUID here shows up as "a real but wrong skin" on the cover.
        if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("coverFace:" + name))
            dev.felnull.imp.client.music.link.LinkDebug.log("cover face", "name", name,
                    "uuid", playerId == null ? "null" : playerId.toString(),
                    "path", playerId != null ? "PlayerFaceRenderer" : "Otya-fallback");
        if (playerId != null) {
            PlayerFaceRenderer.drawFace(poseStack, playerId, x, y, size);
            return;
        }
        var texture = OETextureUtils.getPlayerSkinTexture(name);
        OERenderUtils.drawTexture(texture, poseStack, x, y, size, size, size, size, size * 8, size * 8);
        OERenderUtils.drawTexture(texture, poseStack, x, y, size * 5, size, size, size, size * 8, size * 8);
    }

    private void drawTextureImage(ResourceLocation location, PoseStack poseStack, float wScale, float hScale, float x, float y, float size) {
        if (location == null) return;
        float w = size * wScale;
        float h = size * hScale;
        OERenderUtils.drawTexture(location, poseStack, x + (size - w) / 2, y + (size - h) / 2, 0, 0, w, h, w, h);
    }

    // ------------------------------------------------------------------ "only once it is loaded" variants

    /**
     * Like {@link #draw}, but draws nothing while the image is still downloading: the URL loader answers with an animated "loading" GIF until then, so every row flashed that GIF.
     *
     * @param cash whether the loader may also write this URL into its on-disk cache; the playlist rows pass {@code true}
     */
    public void drawIfLoaded(ImageInfo imageInfo, PoseStack poseStack, float x, float y, float size, boolean cash) {
        if (imageInfo == null || imageInfo.isEmpty())
            return;
        if (imageInfo.getImageType() != ImageInfo.ImageType.URL) {
            // Player faces / YouTube / SoundCloud have no "still downloading" state of their own — keep the old behavior
            draw(imageInfo, poseStack, x, y, size, cash);
            return;
        }
        var loaded = loadedImage(imageInfo, cash);
        if (loaded == null)
            return;
        drawTextureImage(loaded.getLeft(), poseStack, loaded.getRight().x, loaded.getRight().y, x, y, size);
    }

    /** The in-world display version of {@link #drawIfLoaded(ImageInfo, PoseStack, float, float, float, boolean)}. */
    public void renderSpriteIfLoaded(ImageInfo imageInfo, PoseStack poseStack, MultiBufferSource multiBufferSource,
                                     float x, float y, float z, float size, int i, int j) {
        if (imageInfo == null || imageInfo.isEmpty())
            return;
        if (imageInfo.getImageType() != ImageInfo.ImageType.URL) {
            renderSprite(imageInfo, poseStack, multiBufferSource, x, y, z, size, i, j);
            return;
        }
        var loaded = loadedImage(imageInfo, true);
        if (loaded == null)
            return;
        renderTextureImageSprite(loaded.getLeft(), poseStack, multiBufferSource, loaded.getRight().x, loaded.getRight().y,
                x, y, z, size, i, j);
    }

    /**
     * Whether a URL image is ready to draw (used to keep the previous one until the new one arrives); non-URL images are always ready — answering {@code false} would keep the previous cover forever.
     */
    public boolean isLoaded(ImageInfo imageInfo, boolean cash) {
        if (imageInfo == null || imageInfo.isEmpty())
            return false;
        return imageInfo.getImageType() != ImageInfo.ImageType.URL || loadedImage(imageInfo, cash) != null;
    }

    /** {@code null} while a URL image is still downloading (or failed): its location + aspect scale otherwise. */
    private Pair<ResourceLocation, Vec2> loadedImage(ImageInfo imageInfo, boolean cash) {
        var result = OETextureUtils.getAndLoadURLTextureAsync(imageInfo.getIdentifier(), cash);
        if (result == null || !result.isSuccess())
            return null;
        var location = result.getLocation();
        if (location == null)
            return null;
        var scale = OETextureUtils.getTextureScale(location);
        return Pair.of(location, scale == null ? new Vec2(1f, 1f) : new Vec2((float) scale.w(), (float) scale.h()));
    }

    public void renderSprite(ImageInfo imageInfo, PoseStack poseStack, MultiBufferSource multiBufferSource, float x, float y, float z, float size, int i, int j) {
        renderSprite(imageInfo, poseStack, multiBufferSource, x, y, z, size, i, j, true);
    }

    public void renderSprite(ImageInfo imageInfo, PoseStack poseStack, MultiBufferSource multiBufferSource, float x, float y, float z, float size, int i, int j, boolean cash) {
        if (imageInfo.isEmpty()) return;
        if (imageInfo.getImageType() == ImageInfo.ImageType.PLAYER_FACE) {
            renderPlayerFaceImageSprite(imageInfo.getIdentifier(), poseStack, multiBufferSource, x, y, z, size, i, j);
            return;
        }
        var td = getImageTexture(imageInfo, cash);
        if (td != null)
            renderTextureImageSprite(td.getLeft(), poseStack, multiBufferSource, td.getRight().x, td.getRight().y, x, y, z, size, i, j);
    }

    /** sprite version (the in-world display path) — the same change as {@link #drawPlayerFaceImage} (resolve the name to a UUID first). */
    private void renderPlayerFaceImageSprite(String name, PoseStack poseStack, MultiBufferSource multiBufferSource, float x, float y, float z, float size, int i, int j) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        var playerId = PlayerNameUtil.uuidByName(name);
        if (playerId != null) {
            PlayerFaceRenderer.drawFaceSprite(poseStack, multiBufferSource, playerId, size, i, j);
        } else {
            OERenderUtils.renderPlayerFaceSprite(poseStack, multiBufferSource, name, size, i, j);
        }
        poseStack.popPose();
    }

    private void renderTextureImageSprite(ResourceLocation location, PoseStack poseStack, MultiBufferSource multiBufferSource, float wScale, float hScale, float x, float y, float z, float size, int i, int j) {
        if (location == null) return;
        float w = size * wScale;
        float h = size * hScale;
        OERenderUtils.renderTextureSprite(location, poseStack, multiBufferSource, x + (size - w) / 2, y + (size - h) / 2, z, 0, 0, 0, w, h, 0, 0, w, h, w, h, i, j);
    }


    private Pair<ResourceLocation, Vec2> getImageTexture(ImageInfo imageInfo, boolean cash) {
        if (imageInfo.getImageType() == ImageInfo.ImageType.URL) {
            var loc = OETextureUtils.getAndLoadURLTextureAsync(imageInfo.getIdentifier(), cash).of();
            var scale = OETextureUtils.getTextureScale(loc);
            float w = 1;
            float h = 1;
            if (scale != null) {
                w = (float) scale.w();
                h = (float) scale.h();
            }
            return Pair.of(loc, new Vec2(w, h));
        } else if (imageInfo.getImageType() == ImageInfo.ImageType.YOUTUBE_THUMBNAIL) {
            var loc = OETextureUtils.getAndLoadURLTextureAsync(String.format(YOUTUBE_THUMBNAIL_URL, imageInfo.getIdentifier()), cash).of(MISSING_YOUTUBE_THUMBNAIL_TEXTURE);
            var scale = OETextureUtils.getTextureScale(loc);
            float w = 1;
            float h = 1;
            if (loc == MISSING_YOUTUBE_THUMBNAIL_TEXTURE) {
                var st = FNMath.scale(120, 90);
                w = (float) st.getX();
                h = (float) st.getY();
            } else if (scale != null) {
                w = (float) scale.w();
                h = (float) scale.h();
            }
            return Pair.of(loc, new Vec2(w, h));
        } else if (imageInfo.getImageType() == ImageInfo.ImageType.SOUND_CLOUD_ARTWORK) {
            var idf = imageInfo.getIdentifier();
            var loc = OETextureUtils.getAndLoadURLTextureAsync("imp_sct_" + idf, cash).of(MISSING_SOUND_CLOUD_THUMBNAIL_TEXTURE);
            var scale = OETextureUtils.getTextureScale(loc);
            float w = 1;
            float h = 1;
            if (loc == MISSING_SOUND_CLOUD_THUMBNAIL_TEXTURE) {
                var st = FNMath.scale(1, 1);
                w = (float) st.getX();
                h = (float) st.getY();
            } else if (scale != null) {
                w = (float) scale.w();
                h = (float) scale.h();
            }
            return Pair.of(loc, new Vec2(w, h));
    }
        return null;
    }

    private static String extractSoundCloudImage(String html) {
        var ts = html.substring(html.indexOf(SCT_IMG_ST) + SCT_IMG_ST.length());
        ts = ts.substring(0, ts.indexOf(SCT_IMG_END));
        ts = ts.substring(ts.indexOf(SCT_SRC_ST) + SCT_SRC_ST.length());
        ts = ts.substring(0, ts.indexOf(SCT_SRC_END));
        return ts;
    }
}
