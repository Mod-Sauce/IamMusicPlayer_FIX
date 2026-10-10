package dev.felnull.imp.client.kugou;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.link.LinkDebug;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * KuGou's device/login-state API: **device registration** + **signed URL resolve (v5/url)** + **QR login**. Registration
 * is **rate limited** (dfid/mid/guid must be persisted) and the {@code v5/url} {@code url} is an **array**, not a string.
 */
public final class KuGouApi {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String REGISTER_URL = "https://userservice.kugou.com/risk/v2/r_register_dev";
    private static final String V5_URL = "https://gateway.kugou.com/v5/url";
    /** privilege-lite API: checks whether each quality tier of a music is playable (8=free, 10=VIP). */
    private static final String PRIVILEGE_URL = "https://gateway.kugou.com/v2/get_res_privilege/lite";
    private static final String QR_KEY_URL = "https://login-user.kugou.com/v2/qrcode";
    private static final String QR_CHECK_URL = "https://login-user.kugou.com/v2/get_userinfo_qrcode";

    /** RSA public key for device registration (fixed value, used only to hand the AES key to the server). */
    private static final String RSA_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01"
                    + "rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLg"
                    + "FVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/"
                    + "q6ifSioBszvb3QiwIDAQAB";

    /** Client UA: both registration / URL resolve require this form (a browser UA gets flagged as "needs verification"). */
    private static final String ANDROID_UA = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";

    /** QR login lands on an H5 page; this prefix + qrcode builds the QR code content. */
    public static final String QR_CODE_URL_PREFIX =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + KuGouSignature.APPID + "&";

    private KuGouApi() {
    }

    // ------------------------------------------------------------------ device

    /**
     * Ensures "this device" is registered (gets a dfid), reusing the one saved in config — registration is rate limited.
     *
     * @return device info; null when registration fails (only the public API is usable then)
     */
    @Nullable
    public static synchronized KuGouDevice ensureDevice() {
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        if (cfg.dfid != null && !cfg.dfid.isEmpty() && cfg.mid != null && !cfg.mid.isEmpty())
            return new KuGouDevice(cfg.dfid, cfg.mid, cfg.guid);
        // [failure negative cache] Registration is rate limited and one playback passes through here several times
        // (URL resolve / quality privilege / metadata), so without caching a failure one playback fires 4 registration
        // requests, each with a 10s timeout.
        if (System.currentTimeMillis() - lastRegisterFailMs < REGISTER_RETRY_COOLDOWN_MS)
            return null;
        var dev = registerDevice();
        if (dev == null)
            lastRegisterFailMs = System.currentTimeMillis();
        return dev;
    }

    /** When the last device registration failed (0 = never failed). */
    private static volatile long lastRegisterFailMs;
    /** Quiet period after a failed registration: no registration attempts during it (avoids hammering the rate limit within one playback). */
    private static final long REGISTER_RETRY_COOLDOWN_MS = 60_000L;

    /** Force a device re-registration (used when dfid expires). */
    @Nullable
    public static synchronized KuGouDevice registerDevice() {
        try {
            var guid = KuGouCrypto.generateGuid();
            var mid = midFromGuid(guid);
            var rawKey = KuGouCrypto.randomString(6).toLowerCase(java.util.Locale.ROOT);
            var body = aesEncrypt(deviceInfoJson(guid), rawKey);
            var p = rsaEncryptHex("{\"aes\":\"" + rawKey + "\",\"uid\":"
                    + parseUid(IamMusicPlayer.getConfig().kugouConfig.userid)
                    + ",\"token\":\"" + nvl(IamMusicPlayer.getConfig().kugouConfig.token) + "\"}");

            var cltime = System.currentTimeMillis() / 1000L;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("part", 1);
            params.put("platid", 1);
            params.put("p", p);
            params.put("appid", KuGouSignature.APPID);
            params.put("clientver", KuGouSignature.CLIENTVER);
            params.put("clienttime", cltime);
            params.put("dfid", "-");
            params.put("mid", mid);
            params.put("uuid", "-");
            params.put("signature", KuGouSignature.android(params, body));

            var extra = new LinkedHashMap<String, String>();
            extra.put("Content-Type", "application/x-www-form-urlencoded");
            var resp = post(REGISTER_URL + "?" + query(params), deviceHeaders("-", mid, cltime, extra), body);
            if (resp == null)
                return null;

            var json = aesDecrypt(Base64.getEncoder().encodeToString(resp), rawKey);
            var root = JsonParser.parseString(json).getAsJsonObject();
            var dfid = firstString(root, "dfid");
            if (dfid == null || dfid.isEmpty()) {
                LinkDebug.log("KuGou register FAILED", "resp", json.length() > 200 ? json.substring(0, 200) : json);
                return null;
            }

            var cfg = IamMusicPlayer.getConfig().kugouConfig;
            cfg.dfid = dfid;
            cfg.mid = mid;
            cfg.guid = guid;
            saveConfig();
            LinkDebug.log("KuGou register OK", "dfid", dfid);
            return new KuGouDevice(dfid, mid, guid);
        } catch (Exception e) {
            LinkDebug.log("KuGou register EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private static String deviceInfoJson(String guid) {
        // Fields only need to match the client (the server validates the structure, not these hardware values)
        var o = new JsonObject();
        o.addProperty("availableRamSize", 4983533568L);
        o.addProperty("availableRomSize", 48114719L);
        o.addProperty("availableSDSize", 48114717L);
        o.addProperty("basebandVer", "");
        o.addProperty("batteryLevel", 100);
        o.addProperty("batteryStatus", 3);
        o.addProperty("brand", "Redmi");
        o.addProperty("buildSerial", "unknown");
        o.addProperty("device", "marble");
        o.addProperty("imei", guid);
        o.addProperty("imsi", "");
        o.addProperty("manufacturer", "Xiaomi");
        o.addProperty("uuid", guid);
        for (var k : new String[]{"accelerometer", "gravity", "gyroscope", "light", "magnetic",
                "orientation", "pressure", "step_counter", "temperature"}) {
            o.addProperty(k, false);
            o.addProperty(k + "Value", "");
        }
        return o.toString();
    }

    /** mid = md5(guid) read as a hexadecimal big integer (decimal string). */
    public static String midFromGuid(String guid) {
        return new BigInteger(KuGouCrypto.md5(guid), 16).toString();
    }

    // ------------------------------------------------------------------ resolve URL

    /** Signed URL resolve ({@code v5/url}). */
    @Nullable
    public static String songUrl(String hash, @Nullable String quality) {
        return songUrl(hash, quality, null);
    }

    /**
     * Same, but {@code detail} receives the platform's own words (e.g. {@code status=2 fail=["pkg","buy"]}) — that
     * signature means "not signed in" **or** "must buy", so the caller has to combine it with the login state.
     */
    @Nullable
    public static String songUrl(String hash, @Nullable String quality, @Nullable StringBuilder detail) {
        var dev = ensureDevice();
        if (dev == null) {
            LinkDebug.log("KuGou v5/url SKIP", "reason", "no device (register failed)");
            if (detail != null)
                detail.append("device registration failed");
            return null;
        }
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        // The mid in the v5/url query and the "mid" parameter of the signature must be derived from **dfid**
        // (md5(dfid) read as hexadecimal into decimal), not the guid-mid of registration: the wrong one makes the
        // server treat the resolve as "restricted".
        var mid = midFromGuid(dev.dfid());
        var userid = parseUid(cfg.userid);

        try {
            var cltime = System.currentTimeMillis() / 1000L;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("album_id", 0);
            params.put("area_code", 1);
            params.put("hash", hash.toLowerCase(java.util.Locale.ROOT));
            params.put("ssa_flag", "is_fromtrack");
            params.put("version", KuGouSignature.CLIENTVER_TRACK);
            params.put("page_id", 967177915);
            if (quality != null && !quality.isEmpty())
                params.put("quality", quality);
            params.put("album_audio_id", 0);
            params.put("behavior", "play");
            params.put("pid", 411);
            params.put("cmd", 26);
            params.put("pidversion", 3001);
            params.put("IsFreePart", 0);
            params.put("ppage_id", "356753938,823673182,967485191");
            params.put("cdnBackup", 1);
            params.put("module", "");
            params.put("clientver", KuGouSignature.CLIENTVER_TRACK);
            params.put("dfid", dev.dfid());
            params.put("mid", mid);
            // uuid = md5(dfid + mid), derived from dfid like mid (not the constant "-")
            params.put("uuid", KuGouCrypto.md5(dev.dfid() + mid));
            params.put("appid", KuGouSignature.APPID);
            params.put("clienttime", cltime);
            if (cfg.token != null && !cfg.token.isEmpty())
                params.put("token", cfg.token);
            // userid always takes part in the signature ("0" when logged out); omitting it breaks the signature
            params.put("userid", userid);
            params.put("key", KuGouSignature.trackKey(hash.toLowerCase(java.util.Locale.ROOT),
                    KuGouSignature.APPID, mid, userid));
            params.put("signature", KuGouSignature.android(params, ""));

            var extra = new LinkedHashMap<String, String>();
            extra.put("x-router", "trackercdn.kugou.com");
            var cookie = buildCookie(cfg.token, cfg.userid, cfg.cookie);
            if (!cookie.isEmpty())
                extra.put("Cookie", cookie);

            var resp = get(V5_URL + "?" + query(params), deviceHeaders(dev.dfid(), mid, cltime, extra));
            if (resp == null) {
                if (detail != null)
                    detail.append("no response");
                return null;
            }

            var root = JsonParser.parseString(resp).getAsJsonObject();
            var url = firstUrl(root);
            var err = root.has("error") ? root.get("error").getAsString() : "";
            var fail = root.has("fail_process") ? root.get("fail_process").toString() : "";
            var status = root.has("status") ? root.get("status").getAsString() : "?";
            // The address must be sanitized before logging: KuGou links carry the token in the **path**
            // (sharefs.kugou.com/<time>/<token>/v3/…), so stripping the query string is not enough — always use
            // LinkDebug.safeUrl.
            LinkDebug.log("KuGou v5/url", "hash", hash, "quality", quality,
                    "status", status,
                    "error", err, "fail", fail,
                    "url", LinkDebug.safeUrl(url));
            if (detail != null && (url == null || url.isEmpty()))
                detail.append("status=").append(status)
                        .append(fail.isEmpty() ? "" : " fail=" + fail)
                        .append(err.isEmpty() ? "" : " error=" + err);
            return url;
        } catch (Exception e) {
            LinkDebug.log("KuGou v5/url EX", "hash", hash, "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (detail != null)
                detail.append("exception ").append(e.getClass().getSimpleName());
            return null;
        }
    }

    /** Takes the address from the v5/url response: {@code url} is an **array**, take the first item; otherwise fall back to {@code backupUrl}. */
    @Nullable
    private static String firstUrl(JsonObject root) {
        for (var key : new String[]{"url", "backupUrl"}) {
            if (!root.has(key))
                continue;
            var el = root.get(key);
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                for (var item : arr)
                    if (!item.isJsonNull() && !item.getAsString().isEmpty())
                        return item.getAsString();
            } else if (el.isJsonPrimitive() && !el.getAsString().isEmpty()) {
                return el.getAsString();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ quality privilege

    /** Checks whether each quality tier of a music is playable (privilege-lite). Returns the set of playable tiers; null when the API fails. */
    @Nullable
    public static java.util.Set<String> privilegeLite(String hash) {
        var root = postPrivilegeLite(hash);
        if (root == null)
            return null;
        var status = root.has("status") ? root.get("status").getAsInt() : -1;
        var out = new java.util.LinkedHashSet<String>();
        if (root.has("data") && root.get("data").isJsonArray()) {
            for (var el : root.getAsJsonArray("data"))
                collectPrivilege(el, out);
        }
        LinkDebug.log("KuGou privilege-lite", "hash", hash, "status", status, "qualities", out);
        return out;
    }

    /** Takes the cover from the privilege-lite response ({@code data[0].info.image}, falling back to {@code union_cover}); null when the API fails or there is no cover. */
    @Nullable
    public static String privilegeLiteCover(String hash) {
        var root = postPrivilegeLite(hash);
        if (root == null)
            return null;
        JsonObject d = null;
        if (root.has("data")) {
            var el = root.get("data");
            if (el.isJsonArray() && !el.getAsJsonArray().isEmpty() && el.getAsJsonArray().get(0).isJsonObject())
                d = el.getAsJsonArray().get(0).getAsJsonObject();
            else if (el.isJsonObject())
                d = el.getAsJsonObject();
        }
        if (d == null)
            return null;
        if (d.has("info") && d.get("info").isJsonObject()) {
            var img = optStr(d.getAsJsonObject("info"), "image");
            if (img != null)
                return img;
        }
        if (d.has("trans_param") && d.get("trans_param").isJsonObject())
            return optStr(d.getAsJsonObject("trans_param"), "union_cover");
        return null;
    }

    /** Builds and POSTs a privilege-lite request (shared by {@link #privilegeLite} and {@link #privilegeLiteCover}). */
    @Nullable
    private static JsonObject postPrivilegeLite(String hash) {
        var h = hash.toLowerCase(java.util.Locale.ROOT);
        var body = new JsonObject();
        body.addProperty("appid", String.valueOf(KuGouSignature.APPID));
        body.addProperty("area_code", 1);
        body.addProperty("behavior", "play");
        body.addProperty("clientver", String.valueOf(KuGouSignature.CLIENTVER));
        body.addProperty("need_hash_offset", 1);
        body.addProperty("relate", 1);
        body.addProperty("support_verify", 1);
        var resource = new JsonArray();
        var res = new JsonObject();
        res.addProperty("type", "audio");
        res.addProperty("page_id", 0);
        res.addProperty("hash", h);
        res.addProperty("album_id", "0");
        resource.add(res);
        body.add("resource", resource);
        var qualities = new JsonArray();
        for (var q : new String[]{"128", "320", "flac", "high", "viper_atmos", "viper_tape",
                "viper_clear", "super", "multitrack"})
            qualities.add(q);
        body.add("qualities", qualities);
        return postJson(PRIVILEGE_URL, "media.store.kugou.com", body.toString());
    }

    /** Walks privilege-lite's {@code data[]} (including the {@code relate_goods} recursion) and collects the playable quality tiers. */
    private static void collectPrivilege(com.google.gson.JsonElement el, java.util.Set<String> out) {
        if (el == null || !el.isJsonObject())
            return;
        var o = el.getAsJsonObject();
        var q = optStr(o, "quality");
        // Only the quality tiers we know are collected (others such as viper_atmos / multitrack do not take part in v5/url resolve)
        if (q != null && ("128".equals(q) || "320".equals(q) || "flac".equals(q) || "high".equals(q)))
            out.add(q);
        if (o.has("relate_goods") && o.get("relate_goods").isJsonArray()) {
            for (var sub : o.getAsJsonArray("relate_goods"))
                collectPrivilege(sub, out);
        }
    }

    /** POST + common signature (LiteSalt) + device headers, returns the response object; null on non-200 or parse failure. */
    @Nullable
    private static JsonObject postJson(String url, String router, String bodyJson) {
        var dev = ensureDevice();
        if (dev == null) {
            LinkDebug.log("KuGou postJson SKIP", "reason", "no device");
            return null;
        }
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        var dfid = dev.dfid() == null || dev.dfid().isEmpty() ? "-" : dev.dfid();
        var queryMid = midFromGuid(dfid);
        var userid = parseUid(cfg.userid);
        var token = cfg.token == null ? "" : cfg.token;
        var seconds = System.currentTimeMillis() / 1000L;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appid", KuGouSignature.APPID);
        params.put("clientver", KuGouSignature.CLIENTVER);
        params.put("dfid", dfid);
        params.put("mid", queryMid);
        params.put("uuid", KuGouCrypto.md5(dfid + queryMid));
        params.put("userid", userid);
        params.put("clienttime", seconds);
        if (!token.isEmpty())
            params.put("token", token);
        params.put("signature", KuGouSignature.android(params, bodyJson));
        var extra = new LinkedHashMap<String, String>();
        extra.put("x-router", router);
        extra.put("Content-Type", "application/json");
        var resp = postText(url + "?" + query(params), deviceHeaders(dfid, queryMid, seconds, extra), bodyJson);
        if (resp == null)
            return null;
        try {
            return JsonParser.parseString(resp).getAsJsonObject();
        } catch (Exception e) {
            LinkDebug.log("KuGou postJson EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ VIP (the daily free 1-day membership)

    /** Account VIP state API (overall state plus one entry per business line). */
    private static final String VIP_INFO_URL = "https://kugouvip.kugou.com/v1/get_union_vip";
    /** This month's daily-VIP claim records. */
    private static final String VIP_RECORD_URL = "https://gateway.kugou.com/youth/v1/activity/get_month_vip_record";
    /** Claims **today's** free 24h VIP (the desktop clients' daily gift). Once per day: a second call answers {@code error_code=131001}. */
    private static final String VIP_RECEIVE_URL = "https://gateway.kugou.com/youth/v1/recharge/receive_vip_listen_song";
    /** Upgrades that free VIP (tvip -> svip). Once per day: a second call answers {@code error_code=297002}. */
    private static final String VIP_UPGRADE_URL = "https://gateway.kugou.com/youth/v1/listen_song/upgrade_vip_reward";
    /** The {@code source_id} the client sends when claiming the "listen to a song" VIP. */
    private static final String VIP_RECEIVE_SOURCE_ID = "90139";
    /** Guards the claim bookkeeping (one claim attempt at a time). */
    private static final Object VIP_LOCK = new Object();
    /** The local date the daily-VIP question was last settled, so one playback pass does not ask again. */
    @Nullable
    private static volatile String vipClaimDay;
    /** When the next claim attempt may happen after a failed one (0 = immediately). */
    private static volatile long vipClaimRetryAtMs;
    /** Quiet period after a failed claim: the platform rate limits, and one playback passes through here several times. */
    private static final long VIP_RETRY_COOLDOWN_MS = 10 * 60_000L;

    /**
     * What the platform says about this account's VIP right now; {@code is_vip} can be 0 while a live 24h entry sits in
     * {@code busi_vip[]}, so "has VIP" must also be read from the business entries.
     *
     * @param active    whether some VIP is active **now**
     * @param vipType   the active entry's {@code product_type} (e.g. {@code svip}); empty when unknown
     * @param until     end time of the active VIP (platform format {@code yyyy-MM-dd HH:mm:ss}); empty when unknown
     * @param expiredAt the overall VIP's end time when nothing is active
     */
    public record KuGouVipState(boolean active, String vipType, String until, String expiredAt) {
    }

    /** Queries the account's VIP state; null when the request fails or the answer cannot be read. */
    @Nullable
    public static KuGouVipState vipState() {
        var root = signedGet(VIP_INFO_URL, null, Map.<String, Object>of("busi_type", "concept"));
        if (root == null)
            return null;
        var data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
        if (data == null) {
            LinkDebug.log("KuGou vip state", "result", "no data");
            return null;
        }
        var active = data.has("is_vip") && data.get("is_vip").getAsInt() == 1;
        var overallEnd = nvl(optStr(data, "vip_end_time"));
        String latest = null;
        var type = "";
        if (data.has("busi_vip") && data.get("busi_vip").isJsonArray()) {
            for (var el : data.getAsJsonArray("busi_vip")) {
                if (!el.isJsonObject())
                    continue;
                var o = el.getAsJsonObject();
                if (!o.has("is_vip") || o.get("is_vip").getAsInt() != 1)
                    continue;
                active = true;
                var end = nvl(optStr(o, "vip_end_time"));
                if (!end.isEmpty() && (latest == null || end.compareTo(latest) > 0)) {
                    latest = end;
                    type = nvl(optStr(o, "product_type"));
                }
            }
        }
        var state = new KuGouVipState(active, type, latest == null ? overallEnd : latest, overallEnd);
        LinkDebug.log("KuGou vip state", "active", state.active(), "type", state.vipType(),
                "until", state.until(), "expiredAt", state.expiredAt());
        return state;
    }

    /**
     * Makes sure **today's** free 24h VIP has been claimed: without it VIP-only tracks answer
     * {@code status=2 fail=["pkg","buy"]}. Idempotent but rate limited, so at most once per process per day.
     *
     * @return a short tag for logging/UI: {@code received} / {@code upgraded} / {@code already} / {@code cached} /
     * {@code cooldown} / {@code nologin} / {@code fail-<error_code>}
     */
    public static String ensureDailyVip() {
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        if (cfg.token == null || cfg.token.isEmpty())
            return "nologin";
        var today = java.time.LocalDate.now().toString();
        synchronized (VIP_LOCK) {
            if (today.equals(vipClaimDay))
                return "cached";
            if (System.currentTimeMillis() < vipClaimRetryAtMs)
                return "cooldown";

            // 1) has today been claimed already? (the record lists day + vip_type for the whole month)
            var record = signedGet(VIP_RECORD_URL, null, Map.<String, Object>of("latest_limit", 100));
            String action;
            if (record == null) {
                // The record is only an optimisation: go ahead and claim (the platform answers "already claimed" anyway).
                action = "receive";
                LinkDebug.log("KuGou vip record", "result", "unavailable", "action", action);
            } else {
                var claimed = todayVipType(record, today);
                action = claimed == null ? "receive" : ("tvip".equals(claimed) ? "upgrade" : "none");
                LinkDebug.log("KuGou vip record", "today", today, "claimed", claimed == null ? "no" : claimed, "action", action);
            }
            if ("none".equals(action)) {
                vipClaimDay = today;
                return "already";
            }

            var granted = false;
            if ("receive".equals(action)) {
                var r = signedPostJson(VIP_RECEIVE_URL, null,
                        Map.<String, Object>of("source_id", VIP_RECEIVE_SOURCE_ID, "receive_day", today), "");
                var status = intField(r, "status");
                var code = intField(r, "error_code");
                LinkDebug.log("KuGou vip receive", "status", status, "error_code", code);
                if (status == 1) {
                    granted = true;
                } else if (code == 131001) {   // already claimed today
                    vipClaimDay = today;
                    return "already";
                }
            }

            // 2) upgrade the free VIP (tvip -> svip); the platform answers 297002 when that reward was already taken
            try {
                Thread.sleep(1000L);   // the client waits ~1s between the two steps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var up = signedPostJson(VIP_UPGRADE_URL, null,
                    Map.<String, Object>of("kugouid", parseUid(cfg.userid), "ad_type", 1), "");
            var upStatus = intField(up, "status");
            var upCode = intField(up, "error_code");
            LinkDebug.log("KuGou vip upgrade", "status", upStatus, "error_code", upCode);
            if (upStatus == 1 || upCode == 297002) {
                vipClaimDay = today;
                return granted ? "received" : "upgraded";
            }
            vipClaimRetryAtMs = System.currentTimeMillis() + VIP_RETRY_COOLDOWN_MS;
            return "fail-" + upCode;
        }
    }

    /** Whether today's entry exists in a month record, and its {@code vip_type} ({@code svip} = upgraded, {@code tvip} = not yet). */
    @Nullable
    private static String todayVipType(JsonObject record, String today) {
        if (!record.has("data") || !record.get("data").isJsonObject())
            return null;
        var data = record.getAsJsonObject("data");
        if (!data.has("list") || !data.get("list").isJsonArray())
            return null;
        for (var el : data.getAsJsonArray("list")) {
            if (!el.isJsonObject())
                continue;
            var o = el.getAsJsonObject();
            if (today.equals(optStr(o, "day")))
                return optStr(o, "vip_type");
        }
        return null;
    }

    /** An int field of a response object ({@code -1} when the response is missing or the field is absent). */
    private static int intField(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive())
            return -1;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    /** The standard device parameters every signed KuGou request carries (before {@code signature}). */
    private static Map<String, Object> signedParams(String dfid, String mid, long cltime, @Nullable Map<String, Object> extra) {
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appid", KuGouSignature.APPID);
        params.put("clientver", KuGouSignature.CLIENTVER);
        params.put("dfid", dfid);
        params.put("mid", mid);
        params.put("uuid", KuGouCrypto.md5(dfid + mid));
        params.put("userid", parseUid(cfg.userid));
        params.put("clienttime", cltime);
        var token = cfg.token == null ? "" : cfg.token;
        if (!token.isEmpty())
            params.put("token", token);
        if (extra != null)
            params.putAll(extra);
        return params;
    }

    /** Device headers + cookie (+ {@code x-router} when the endpoint needs one) for a signed call. */
    private static Map<String, String> signedExtra(@Nullable String router) {
        var cfg = IamMusicPlayer.getConfig().kugouConfig;
        var extra = new LinkedHashMap<String, String>();
        if (router != null && !router.isEmpty())
            extra.put("x-router", router);
        var cookie = buildCookie(cfg.token, cfg.userid, cfg.cookie);
        if (!cookie.isEmpty())
            extra.put("Cookie", cookie);
        return extra;
    }

    /** Signed GET with the standard device parameters; null when there is no device or the call fails. */
    @Nullable
    private static JsonObject signedGet(String url, @Nullable String router, @Nullable Map<String, Object> extra) {
        var dev = ensureDevice();
        if (dev == null) {
            LinkDebug.log("KuGou signedGet SKIP", "reason", "no device (register failed)");
            return null;
        }
        var dfid = dev.dfid() == null || dev.dfid().isEmpty() ? "-" : dev.dfid();
        var mid = midFromGuid(dfid);
        var cltime = System.currentTimeMillis() / 1000L;
        var params = signedParams(dfid, mid, cltime, extra);
        params.put("signature", KuGouSignature.android(params, ""));
        return parseObject(get(url + "?" + query(params), deviceHeaders(dfid, mid, cltime, signedExtra(router))), "signedGet");
    }

    /** Signed POST (JSON content type; the signature covers the body exactly as sent); null when there is no device or the call fails. */
    @Nullable
    private static JsonObject signedPostJson(String url, @Nullable String router, @Nullable Map<String, Object> extra, String bodyJson) {
        var dev = ensureDevice();
        if (dev == null) {
            LinkDebug.log("KuGou signedPost SKIP", "reason", "no device (register failed)");
            return null;
        }
        var dfid = dev.dfid() == null || dev.dfid().isEmpty() ? "-" : dev.dfid();
        var mid = midFromGuid(dfid);
        var cltime = System.currentTimeMillis() / 1000L;
        var params = signedParams(dfid, mid, cltime, extra);
        params.put("signature", KuGouSignature.android(params, bodyJson));
        var headers = signedExtra(router);
        headers.put("Content-Type", "application/json");
        return parseObject(postText(url + "?" + query(params), deviceHeaders(dfid, mid, cltime, headers), bodyJson), "signedPost");
    }

    /** Parses a response body into an object; null when the call failed or the body is not JSON. */
    @Nullable
    private static JsonObject parseObject(@Nullable String resp, String tag) {
        if (resp == null)
            return null;
        try {
            return JsonParser.parseString(resp).getAsJsonObject();
        } catch (Exception e) {
            LinkDebug.log("KuGou " + tag + " bad json", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ QR login

    /** Fetches a QR code key; null on failure. */
    @Nullable
    public static String fetchQrKey() {
        try {
            var cltime = System.currentTimeMillis() / 1000L;
            // The two QR APIs use the **Web signature + srcappid**, not the Android signature: the wrong salt returns
            // {"data":null,"status":0,"error_code":20010}.
            Map<String, Object> params = new TreeMap<>();
            params.put("plat", 4);
            params.put("appid", KuGouSignature.APPID);
            params.put("srcappid", KuGouSignature.SRCAPPID);
            params.put("type", 1);
            params.put("qrcode_txt", QR_CODE_URL_PREFIX);
            params.put("dfid", "-");
            params.put("mid", "-");
            params.put("uuid", "-");
            params.put("clientver", KuGouSignature.CLIENTVER);
            params.put("clienttime", cltime);
            params.put("signature", KuGouSignature.web(params));

            var resp = get(QR_KEY_URL + "?" + query(params), qrHeaders(cltime));
            if (resp == null)
                return null;
            var root = JsonParser.parseString(resp).getAsJsonObject();
            var status = root.has("status") ? root.get("status").getAsInt() : -1;
            var data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
            var qrcode = data != null && data.has("qrcode") ? data.get("qrcode").getAsString()
                    : (data != null && data.has("key") ? data.get("key").getAsString() : null);
            LinkDebug.log("KuGou qr key", "status", status, "qrcode", qrcode == null ? "null" : "ok",
                    "resp", resp.length() > 200 ? resp.substring(0, 200) : resp);
            return qrcode;
        } catch (Exception e) {
            LinkDebug.log("KuGou qr key EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Polls the QR status. qrStatus: 0/1 waiting for scan or confirmation / 2 confirmed / 4 login succeeded / other = failed. */
    public static KuGouQrStatus checkQr(String qrcode) {
        try {
            var cltime = System.currentTimeMillis() / 1000L;
            Map<String, Object> params = new TreeMap<>();
            params.put("plat", 4);
            params.put("appid", KuGouSignature.APPID);
            params.put("srcappid", KuGouSignature.SRCAPPID);
            params.put("qrcode", qrcode);
            params.put("dfid", "-");
            params.put("mid", "-");
            params.put("uuid", "-");
            params.put("clientver", KuGouSignature.CLIENTVER);
            params.put("clienttime", cltime);
            params.put("signature", KuGouSignature.web(params));

            var resp = get(QR_CHECK_URL + "?" + query(params), qrHeaders(cltime));
            if (resp == null)
                return new KuGouQrStatus(-1, null, null, null);

            var root = JsonParser.parseString(resp).getAsJsonObject();
            var apiStatus = root.has("status") ? root.get("status").getAsInt() : -1;
            // Outer status = "did the API succeed"; the real QR status is in data.status
            // (while waiting to scan outer status=1, data.status=1; when the token arrives data.status=4)
            var data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
            if (apiStatus != 1 || data == null) {
                LinkDebug.log("KuGou qr check api-fail", "apiStatus", apiStatus,
                        "msg", root.has("error_msg") ? root.get("error_msg").getAsString() : "-");
                return new KuGouQrStatus(-1, null, null, null);
            }
            var qrStatus = data.has("status") ? data.get("status").getAsInt() : -1;
            var token = data.has("token") ? data.get("token").getAsString() : null;
            var userid = data.has("userid") ? data.get("userid").getAsString() : null;
            var vipToken = data.has("vip_token") ? data.get("vip_token").getAsString() : null;
            LinkDebug.log("KuGou qr check", "qrStatus", qrStatus, "hasToken", token != null, "userid", userid);
            return new KuGouQrStatus(qrStatus, token, userid, vipToken);
        } catch (Exception e) {
            LinkDebug.log("KuGou qr check EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return new KuGouQrStatus(-1, null, null, null);
        }
    }

    /** Request headers for the QR APIs (UA version 11440, unlike 11083 for the resolve API). */
    private static Map<String, String> qrHeaders(long cltime) {
        var h = new LinkedHashMap<String, String>();
        h.put("User-Agent", "Android15-1070-11440-46-0-DiscoveryDRADProtocol-wifi");
        h.put("dfid", "-");
        h.put("mid", "-");
        h.put("clienttime", String.valueOf(cltime));
        return h;
    }

    // ------------------------------------------------------------------ crypto / HTTP

    /** AES-128-CBC/PKCS5: key = md5(rawKey)[0..16), iv = md5(rawKey)[16..32) (all UTF-8 bytes). */
    private static String aesEncrypt(String data, String rawKey) throws Exception {
        var md5Hex = KuGouCrypto.md5(rawKey);
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(md5Hex.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(md5Hex.substring(16).getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String aesDecrypt(String base64, String rawKey) throws Exception {
        var md5Hex = KuGouCrypto.md5(rawKey);
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(md5Hex.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(md5Hex.substring(16).getBytes(StandardCharsets.UTF_8)));
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }

    /** RSA/ECB/PKCS1Padding, outputs lowercase hex. */
    private static String rsaEncryptHex(String data) throws Exception {
        PublicKey pub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(RSA_PUBLIC_KEY)));
        var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        var sb = new StringBuilder();
        for (byte b : cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)))
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static Map<String, String> deviceHeaders(String dfid, String mid, long cltime, Map<String, String> extra) {
        var h = new LinkedHashMap<String, String>();
        h.put("User-Agent", ANDROID_UA);
        h.put("dfid", dfid);
        h.put("mid", mid);
        if (cltime > 0)
            h.put("clienttime", String.valueOf(cltime));
        h.put("kg-rc", "1");
        h.put("kg-thash", "5d816a0");
        h.put("kg-rec", "1");
        h.put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
        if (extra != null)
            h.putAll(extra);
        return h;
    }

    /** Joins query parameters into a URL string (values are URL encoded; the order is insertion order, the signature sorts on its own). */
    private static String query(Map<String, Object> params) {
        var sb = new StringBuilder();
        for (var e : params.entrySet()) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Nullable
    private static String get(String url, Map<String, String> headers) {
        try {
            var b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET();
            headers.forEach(b::header);
            var resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200)
                LinkDebug.log("KuGou HTTP", "url", url.substring(0, Math.min(60, url.length())), "status", resp.statusCode());
            return resp.body();
        } catch (Exception e) {
            LinkDebug.log("KuGou HTTP EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** POSTs a text body, returns the response text (null on non-200). */
    @Nullable
    private static String postText(String url, Map<String, String> headers, String body) {
        try {
            var b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(b::header);
            var resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                LinkDebug.log("KuGou postText HTTP", "status", resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            LinkDebug.log("KuGou postText EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static byte[] post(String url, Map<String, String> headers, String body) {
        try {
            var b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(b::header);
            var resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                LinkDebug.log("KuGou register HTTP", "status", resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            LinkDebug.log("KuGou register EX", "ex", e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static String buildCookie(@Nullable String token, @Nullable String userid, @Nullable String extraCookie) {
        var sb = new StringBuilder();
        if (token != null && !token.isEmpty())
            sb.append("token=").append(token).append("; ");
        if (userid != null && !userid.isEmpty() && !"0".equals(userid))
            sb.append("userid=").append(userid).append("; ");
        if (extraCookie != null && !extraCookie.isEmpty())
            sb.append(extraCookie.trim());
        return sb.toString().trim();
    }

    /** An empty string means logged out (uid is filled with 0 so the JSON stays valid). */
    private static String parseUid(@Nullable String userid) {
        if (userid == null || userid.isEmpty())
            return "0";
        try {
            Long.parseLong(userid);
            return userid;
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private static String nvl(@Nullable String s) {
        return s == null ? "" : s;
    }

    /** Takes a string field from an object; missing/empty counts as null. */
    @Nullable
    private static String optStr(JsonObject o, String key) {
        if (o == null || !o.has(key))
            return null;
        var el = o.get(key);
        if (el.isJsonNull() || !el.isJsonPrimitive())
            return null;
        var s = el.getAsString();
        return s == null || s.isEmpty() ? null : s;
    }

    /** In the registration response dfid may sit in the {@code data} object or in the first item of the {@code data} array. */
    @Nullable
    private static String firstString(JsonObject root, String field) {
        if (!root.has("data"))
            return null;
        var data = root.get("data");
        if (data.isJsonObject())
            return data.getAsJsonObject().has(field) ? data.getAsJsonObject().get(field).getAsString() : null;
        if (data.isJsonArray()) {
            var arr = data.getAsJsonArray();
            if (!arr.isEmpty() && arr.get(0).isJsonObject() && arr.get(0).getAsJsonObject().has(field))
                return arr.get(0).getAsJsonObject().get(field).getAsString();
        }
        return null;
    }

    private static void saveConfig() {
        try {
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(dev.felnull.imp.IMPConfig.class).save();
        } catch (Exception e) {
            LinkDebug.log("KuGou config save EX", "ex", e.getMessage());
        }
    }

    /** The registered device triple. */
    public record KuGouDevice(String dfid, String mid, String guid) {
    }

    /** QR poll result. */
    public record KuGouQrStatus(int status, @Nullable String token,
                               @Nullable String userid, @Nullable String vipToken) {
        public boolean success() {
            return status == 4 && token != null && !token.isEmpty();
        }
    }
}
