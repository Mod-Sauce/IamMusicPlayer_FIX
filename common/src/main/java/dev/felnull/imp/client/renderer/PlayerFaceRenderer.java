package dev.felnull.imp.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.client.gui.PlayerNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.renderer.OERenderTypes;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Unified drawing entry for player faces (GUI and in-world): only the skin source is swapped, everything else is
 * Otya's. The source ({@code SkinManager#getInsecureSkin}) is asynchronous, so the fallback skin is drawn first.
 */
public final class PlayerFaceRenderer {
    /**
     * Wait after the n-th retry while no real skin is resolved (ms); the last gaps are long because a cold
     * CustomSkinLoader lookup takes roughly 10-14s.
     */
    private static final long[] RETRY_DELAYS_MS = {300L, 1000L, 2000L, 5000L, 10000L, 20000L};
    /** Retry cap per online session, derived from {@link #RETRY_DELAYS_MS}; once exhausted the fallback skin stays. */
    private static final int MAX_ATTEMPTS = RETRY_DELAYS_MS.length;

    /** UUID → that player's face. Only read/written on the client thread (render path). */
    private static final Map<UUID, CachedSkin> SKINS = new HashMap<>();

    /** The client level the cache above belongs to (null = not in a world). Used to detect a world switch. */
    @Nullable
    private static Object lastLevel;

    /** Faces queued for the warm-up, in sync order; emptied as they resolve, so one pass only, never a poll. */
    private static final Set<UUID> WARM_UP = new LinkedHashSet<>();
    /** Cap: a busy server can name a lot of people, and beyond this the warm-up is not worth the requests. */
    private static final int WARM_UP_MAX = 200;
    /** One lookup every N ticks (≈4/s): the skin source is the slow part and must not be flooded. */
    private static final int WARM_UP_GAP_TICKS = 5;
    private static int warmUpTicks;

    /**
     * Queues the faces the UI is about to draw; gentle and self-terminating — {@link #tick()} takes one face per
     * {@link #WARM_UP_GAP_TICKS} ticks and uses the same retry schedule as drawing.
     */
    public static void warmUp(@NotNull Collection<UUID> playerIds) {
        for (var playerId : playerIds) {
            if (WARM_UP.size() >= WARM_UP_MAX)
                return;
            if (playerId != null)
                WARM_UP.add(playerId);
        }
    }

    /** One step of the warm-up pass; driven by the client tick. */
    public static void tick() {
        if (WARM_UP.isEmpty() || ++warmUpTicks < WARM_UP_GAP_TICKS)
            return;
        warmUpTicks = 0;

        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;   // outside a world there is nothing to warm up for (and no name source either)
        resetSkinCacheOnWorldChange();

        var now = System.currentTimeMillis();
        for (Iterator<UUID> it = WARM_UP.iterator(); it.hasNext(); ) {
            var playerId = it.next();
            var cached = SKINS.get(playerId);
            if (cached != null && cached.resolved() && (!isOnline(playerId) || cached.onlineChecked())) {
                it.remove();   // already good enough to draw: offline keeps the cached one, online has been checked
                continue;
            }
            if (cached != null && cached.attempts() >= MAX_ATTEMPTS) {
                it.remove();   // its budget is used up: this face keeps the fallback (same as the drawing path)
                continue;
            }
            if (cached != null && now < cached.nextTryAt())
                continue;      // not due yet — leave it in the queue for a later step
            texture(playerId); // the very same path drawing uses, just earlier
            return;            // one lookup per step
        }
    }

    /** Drop the cache on a world change so a fresh world gets a fresh retry budget, never the "dead" entries. */
    private static void resetSkinCacheOnWorldChange() {
        var level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            SKINS.clear();
            lastLevel = level;
        }
    }

    /** Wait time after the {@code attempts}-th retry (clamped to both ends of the table when out of range). */
    private static long nextRetryDelay(int attempts) {
        var idx = Math.max(1, Math.min(attempts, RETRY_DELAYS_MS.length)) - 1;
        return RETRY_DELAYS_MS[idx];
    }

    private PlayerFaceRenderer() {
    }

    /** GUI: 8×8 player face (with the hat layer), sized exactly like Otya's {@code OERenderUtils.drawPlayerFace}. */
    public static void drawFace(@NotNull PoseStack poseStack, @NotNull UUID playerId, float x, float y, float size) {
        var texture = texture(playerId);
        if (texture == null) {
            // No name ⇒ cannot ask the skin source ⇒ keep the original behavior
            OERenderUtils.drawPlayerFace(poseStack, playerId, x, y, size);
            return;
        }
        poseStack.pushPose();
        float sc = size / 8f;
        OERenderUtils.drawTexture(texture, poseStack, x, y, 8f * sc, 8f * sc, 8f * sc, 8f * sc, 64f * sc, 64f * sc);
        OERenderUtils.drawTexture(texture, poseStack, x, y, 40f * sc, 8f * sc, 8f * sc, 8f * sc, 64f * sc, 64f * sc);
        poseStack.popPose();
    }

    /** In-world sprite path: same semantics as Otya's method of the same name, only the skin source is swapped. */
    public static void drawFaceSprite(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, @NotNull UUID playerId, float size, int light, int overlay) {
        var texture = texture(playerId);
        if (texture == null) {
            OERenderUtils.renderPlayerFaceSprite(poseStack, multiBufferSource, playerId, size, light, overlay);
            return;
        }
        OERenderUtils.renderPlayerFaceSprite(poseStack, multiBufferSource.getBuffer(OERenderTypes.simpleSpriteCutout(texture)), size, light, overlay);
    }

    /** This player's face texture. Returning null ⇒ the caller falls back to Otya's original path. */
    @Nullable
    private static ResourceLocation texture(@NotNull UUID playerId) {
        // World switch (or entering / leaving a world): start over with a full retry budget
        resetSkinCacheOnWorldChange();

        var online = isOnline(playerId);
        var now = System.currentTimeMillis();
        var cached = SKINS.get(playerId);
        // Is a name available right now (tab list / local cache) — only these two sources, never synchronous HTTP
        var name = PlayerNameUtil.cachedName(playerId);

        // Only the join direction does anything: going online gets a fresh attempt budget and clears the "checked this session" flag (exactly one verification);
        // going offline resets nothing, so a leave can never cause another fetch later.
        // (A player's skin and name are taken as fixed while online.)
        if (cached != null && cached.online() != online) {
            var joining = online;
            cached = new CachedSkin(cached.texture(), cached.resolved(),
                    joining ? false : cached.onlineChecked(),
                    joining ? 0 : cached.attempts(),
                    joining ? 0L : cached.nextTryAt(),
                    online, cached.lastName());
            SKINS.put(playerId, cached);
        }

        if (cached != null && cached.resolved()) {
            if (!online)
                return cached.texture();                 // offline = no lookup, no update, keep it as it was
            if (cached.onlineChecked())
                return cached.texture();                 // already verified during this online session ⇒ do not load again
            var skin = fetchSkin(playerId, name);        // just joined the server: same-request load (**upgrade only, never downgrade**)
            if (skin != null && isResolved(skin, playerId)) {
                // This verification did get a real skin: update only if it differs from the current one (if identical, do nothing)
                if (!skin.texture().equals(cached.texture()))
                    SKINS.put(playerId, new CachedSkin(skin.texture(), true, true, 0, Long.MAX_VALUE, true, name));
                else
                    SKINS.put(playerId, new CachedSkin(cached.texture(), true, true, cached.attempts(), cached.nextTryAt(), true, name));
                return skin.texture();
            }
            SKINS.put(playerId, new CachedSkin(cached.texture(), true, true, cached.attempts(), cached.nextTryAt(), true, name));
            return cached.texture();
        }

        // No real skin resolved yet: retry with backoff (also while offline — CSL is asynchronous), capped at MAX_ATTEMPTS
        if (cached != null && (cached.attempts() >= MAX_ATTEMPTS || now < cached.nextTryAt()))
            return cached.texture();

        var attempts = (cached == null ? 0 : cached.attempts()) + 1;
        var skin = fetchSkin(playerId, name);
        if (skin == null) {
            SKINS.put(playerId, new CachedSkin(cached == null ? null : cached.texture(), false, false, attempts,
                    attempts >= MAX_ATTEMPTS ? Long.MAX_VALUE : now + nextRetryDelay(attempts), online, name));
            return cached == null ? null : cached.texture();
        }
        if (isResolved(skin, playerId)) {
            SKINS.put(playerId, new CachedSkin(skin.texture(), true, online, 0, Long.MAX_VALUE, online, name));
            return skin.texture();
        }
        SKINS.put(playerId, new CachedSkin(skin.texture(), false, false, attempts,
                attempts >= MAX_ATTEMPTS ? Long.MAX_VALUE : now + nextRetryDelay(attempts), online, name));
        return skin.texture();
    }

    /** Ask the skin source once; returns null when no name is available at all. */
    @Nullable
    private static PlayerSkin fetchSkin(@NotNull UUID playerId, @Nullable String knownName) {
        // A player who is right here already has the skin the game renders (CustomSkinLoader patched that path too), so take it from the entity.
        // Synthesizing a GameProfile instead is what broke the local player's own face: an offline launcher account carries no textures in its own profile,
        // so the lookup answered "default skin" every time and the retry budget burned through.
        var mc = Minecraft.getInstance();
        var self = mc.player;
        if (self != null && self.getUUID().equals(playerId)) {
            if (faceSkinLog("face skin self", playerId, self.getGameProfile().getName(), self.getSkin()))
                return self.getSkin();
        }
        var level = mc.level;
        if (level != null) {
            var player = level.getPlayerByUUID(playerId);
            if (player instanceof net.minecraft.client.player.AbstractClientPlayer here) {
                faceSkinLog("face skin entity", playerId, here.getGameProfile().getName(), here.getSkin());
                return here.getSkin();
            }
        }

        var name = knownName != null ? knownName : PlayerNameUtil.cachedName(playerId);
        if (name == null || name.isBlank()) {
            // [Diagnostic] No name ⇒ the skin source cannot be asked at all (the caller keeps the fallback face).
            if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("faceNoName:" + playerId))
                dev.felnull.imp.client.music.link.LinkDebug.log("face no-name", "uuid", playerId);
            return null;
        }
        var skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(playerId, name));
        // [Diagnostic] Which identity was asked and what came back: this is what tells "wrong skin" apart from
        // "no skin" (texture + textureUrl + whether it is still the default one).
        faceSkinLog("face skin", playerId, name, skin);
        return skin;
    }

    /** The diagnostic line shared by both skin sources ({@code stage} tells which one answered). */
    private static boolean faceSkinLog(String stage, @NotNull UUID playerId, @Nullable String name,
                                       @Nullable PlayerSkin skin) {
        if (skin == null)
            return false;
        if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("faceSkin:" + playerId))
            dev.felnull.imp.client.music.link.LinkDebug.log(stage, "uuid", playerId,
                    "name", name,
                    "texture", skin.texture().toString(),
                    "textureUrl", skin.textureUrl() == null ? "null" : skin.textureUrl(),
                    "isDefault", skin.texture().equals(DefaultPlayerSkin.get(playerId).texture()));
        return true;
    }

    /**
     * Whether this face is a "real skin" (and not the fallback skin handed out by SkinManager).
     */
    private static boolean isResolved(@NotNull PlayerSkin skin, @NotNull UUID playerId) {
        if (skin.textureUrl() != null)
            return true;
        return !skin.texture().equals(DefaultPlayerSkin.get(playerId).texture());
    }

    /** Whether he is online (his profile is in the tab list). Yourself also counts as online. */
    private static boolean isOnline(@NotNull UUID playerId) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null)
            return false;
        if (playerId.equals(player.getGameProfile().getId()))
            return true;
        ClientPacketListener connection = mc.getConnection();
        return connection != null && connection.getPlayerInfo(playerId) != null;
    }

    /**
     * @param texture       this player's face, null = no name obtained yet (the caller falls back to Otya)
     * @param resolved      a real skin was obtained and is used long-term
     * @param onlineChecked verified during this online session (reset when the online state changes)
     * @param attempts      retry count this session, capped at the {@link #RETRY_DELAYS_MS} table length
     * @param nextTryAt     next retry time
     * @param online        online state seen last time (detects the join/leave transition)
     */
    private record CachedSkin(@Nullable ResourceLocation texture, boolean resolved, boolean onlineChecked, int attempts, long nextTryAt, boolean online, @Nullable String lastName) {
    }
}
