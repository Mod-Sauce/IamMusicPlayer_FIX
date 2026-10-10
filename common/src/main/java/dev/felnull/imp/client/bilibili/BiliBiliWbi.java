package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonParser;
import dev.felnull.imp.client.music.link.LinkDebug;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * WBI signature ({@code w_rid} + {@code wts}) for the web APIs that need it: unsigned requests get {@code code=-352},
 * a client that does not look like the web page can get HTTP 412. Take {@code img_key}/{@code sub_key} from the {@code nav}
 * endpoint's {@code wbi_img} urls (never fetch them — disguised tokens), re-order through {@link #MIXIN_KEY_ENC_TAB}, keep
 * 32 chars, then append {@code wts}, sort by key, percent-encode ({@code %20}, uppercase hex) + mixin key, MD5 = {@code w_rid}.
 */
public final class BiliBiliWbi {
    private BiliBiliWbi() {
    }

    /** Character re-ordering table, 64 entries (copied from the API doc; index = position in {@code img_key + sub_key}). */
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
    };

    private static final String NAV_URL = "https://api.bilibili.com/x/web-interface/nav";
    /** The keys rotate daily; caching for a few hours keeps the request count down without risking staleness. */
    private static final long KEY_TTL_MS = 6L * 60 * 60 * 1000;

    private static volatile String mixinKey;
    private static volatile long mixinKeyAt;

    /**
     * Re-orders {@code img_key + sub_key} into the 32-character mixin key. Pure function, so the doc's example can be asserted without network access.
     */
    public static String mixinKeyOf(String imgKey, String subKey) {
        var raw = imgKey + subKey;
        var sb = new StringBuilder(64);
        for (var idx : MIXIN_KEY_ENC_TAB)
            sb.append(raw.charAt(idx));
        return sb.substring(0, 32);
    }

    /** A ready-to-append query string: the (sorted, encoded) params + {@code wts} + {@code w_rid}. */
    public static String signedQuery(Map<String, String> params) throws Exception {
        var mk = currentMixinKey();
        // TreeMap: the signature sorts the parameters by key name (String order, as in the doc's JS/Rust samples)
        var all = new TreeMap<String, String>();
        all.putAll(params);
        all.put("wts", String.valueOf(System.currentTimeMillis() / 1000));

        var query = new StringBuilder();
        for (var e : all.entrySet()) {
            if (query.length() > 0)
                query.append('&');
            query.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }

        var q = query.toString();
        return q + "&w_rid=" + md5Hex(q + mk);
    }

    /** The mixin key for the current day (fetched from {@code nav} and cached). */
    public static String currentMixinKey() throws Exception {
        var cached = mixinKey;
        if (cached != null && System.currentTimeMillis() - mixinKeyAt < KEY_TTL_MS)
            return cached;

        var request = BiliBiliUtil.apiRequest(NAV_URL, null).GET().build();
        var response = BiliBiliUtil.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new java.io.IOException("Bilibili nav HTTP " + response.statusCode());

        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("data") || json.get("data").isJsonNull())
            throw new java.io.IOException("Bilibili nav has no data");
        var data = json.getAsJsonObject("data");
        if (!data.has("wbi_img") || data.get("wbi_img").isJsonNull())
            throw new java.io.IOException("Bilibili nav has no wbi_img");

        var wbi = data.getAsJsonObject("wbi_img");
        var imgKey = fileNameOf(wbi.get("img_url").getAsString());
        var subKey = fileNameOf(wbi.get("sub_url").getAsString());
        var mk = mixinKeyOf(imgKey, subKey);

        mixinKey = mk;
        mixinKeyAt = System.currentTimeMillis();
        LinkDebug.log("BiliBili wbi key", "imgKey", imgKey, "subKey", subKey, "mixinKey", mk);
        return mk;
    }

    /** {@code https://i0.hdslb.com/bfs/wbi/<key>.png} → {@code <key>}. */
    private static String fileNameOf(String url) {
        var name = url.substring(url.lastIndexOf('/') + 1);
        var dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * {@code encodeURIComponent} semantics: space becomes {@code %20} (not {@code +}) and the hex digits must be uppercase, or the signature fails. Unreserved characters are kept as they are.
     */
    static String encode(String s) {
        var bytes = s.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length);
        for (var b : bytes) {
            var c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*' || c == '\'' || c == '(' || c == ')')
                sb.append(c);
            else
                sb.append('%').append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static String md5Hex(String s) throws Exception {
        var md = MessageDigest.getInstance("MD5");
        var digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        var sb = new StringBuilder(digest.length * 2);
        for (var b : digest)
            sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    /** Test hook: drop the cached key so the next call refetches (used when a signed request comes back rejected). */
    public static void invalidate() {
        mixinKey = null;
        mixinKeyAt = 0;
    }

    /** The {@code URI} builder used by callers: {@code <endpoint>?<signedQuery>}. */
    public static HttpRequest.Builder signedRequest(String endpoint, Map<String, String> params) throws Exception {
        return HttpRequest.newBuilder(URI.create(endpoint + "?" + signedQuery(params)));
    }
}
