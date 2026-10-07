package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.client.music.link.LinkDebug;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Bilibili QR-code login (web flow) for the cookies that make the space APIs reliable: {@code …/qrcode/poll} every ~2 s — {@code 86101} unscanned, {@code 86090} scanned, {@code 0} logged in, {@code 86038} expired (the key lives 180 s); the cookie is a credential — store it in the config, never log it.
 */
public final class BiliBiliLogin {
    private BiliBiliLogin() {
    }

    private static final String GENERATE_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";
    private static final String POLL_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll";
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(15);

    /** Login states reported by the poll endpoint. */
    public static final int STATUS_OK = 0;
    public static final int STATUS_EXPIRED = 86038;
    public static final int STATUS_SCANNED = 86090;
    public static final int STATUS_WAITING = 86101;

    /** {@code url} is the QR content; {@code key} is the polling key (valid for 180 s). */
    public record QrCode(String url, String key) {
    }

    /**
     * @param status one of {@link #STATUS_OK} / {@link #STATUS_WAITING} / {@link #STATUS_SCANNED} / {@link #STATUS_EXPIRED}
     * @param cookie the assembled cookie string, only on {@link #STATUS_OK} (empty otherwise)
     */
    public record QrPoll(int status, String cookie, String message) {
        public boolean hasCookie() {
            return cookie != null && !cookie.isEmpty();
        }
    }

    public static QrCode generate() throws IOException, InterruptedException {
        var response = send(GENERATE_URL);
        if (response.statusCode() != 200)
            throw new IOException("Bilibili qrcode generate HTTP " + response.statusCode());

        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (jsonCode(json) != 0)
            throw new IOException("Bilibili qrcode generate: " + messageOf(json));
        var data = json.getAsJsonObject("data");
        var code = new QrCode(data.get("url").getAsString(), data.get("qrcode_key").getAsString());
        LinkDebug.log("BiliBili qr generate", "keyLen", code.key().length(),
                "urlHost", URI.create(code.url()).getHost());
        return code;
    }

    /** Poll once; the caller decides the interval (2 s is what the web page uses). */
    public static QrPoll poll(String key) throws IOException, InterruptedException {
        var params = new LinkedHashMap<String, String>();
        params.put("qrcode_key", key);
        var response = send(POLL_URL + "?" + queryOf(params));
        if (response.statusCode() != 200)
            throw new IOException("Bilibili qrcode poll HTTP " + response.statusCode());

        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (jsonCode(json) != 0)
            throw new IOException("Bilibili qrcode poll: " + messageOf(json));

        var data = json.getAsJsonObject("data");
        var status = data.has("code") ? data.get("code").getAsInt() : -1;
        var message = data.has("message") && !data.get("message").isJsonNull() ? data.get("message").getAsString() : "";
        var cookie = status == STATUS_OK ? cookieOf(response) : "";
        LinkDebug.log("BiliBili qr poll", "status", status, "message", message, "cookieEntries", countEntries(cookie));
        return new QrPoll(status, cookie, message);
    }

    /**
     * The poll sets the login cookies as separate {@code Set-Cookie} headers; keep only the {@code name=value} part of each ({@code Path}/{@code Expires} are not sent to the API).
     */
    private static String cookieOf(HttpResponse<String> response) {
        List<String> raw = response.headers().allValues("set-cookie");
        var sb = new StringBuilder();
        for (var one : raw) {
            var semi = one.indexOf(';');
            var pair = (semi >= 0 ? one.substring(0, semi) : one).strip();
            if (pair.isEmpty() || !pair.contains("="))
                continue;
            if (sb.length() > 0)
                sb.append("; ");
            sb.append(pair);
        }
        return sb.toString();
    }

    /** Number of cookie pairs — logged instead of the value, which is a credential. */
    private static int countEntries(String cookie) {
        if (cookie == null || cookie.isEmpty())
            return 0;
        return cookie.split(";").length;
    }

    private static HttpResponse<String> send(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", BiliBiliUtil.USER_AGENT)
                .header("Referer", BiliBiliUtil.REFERER)
                .header("Origin", BiliBiliUtil.ORIGIN)
                .GET()
                .build();
        return BiliBiliUtil.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static int jsonCode(JsonObject json) {
        return json.has("code") && !json.get("code").isJsonNull() ? json.get("code").getAsInt() : -1;
    }

    private static String messageOf(JsonObject json) {
        return json.has("message") && !json.get("message").isJsonNull() ? json.get("message").getAsString() : "?";
    }

    /** Same percent-encoding the signed calls use ({@link BiliBiliWbi#encode}); no signing is involved here. */
    private static String queryOf(java.util.Map<String, String> params) {
        var sb = new StringBuilder();
        for (var e : params.entrySet()) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(BiliBiliWbi.encode(e.getKey())).append('=').append(BiliBiliWbi.encode(e.getValue()));
        }
        return sb.toString();
    }
}
