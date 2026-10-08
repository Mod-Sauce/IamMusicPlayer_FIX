package dev.felnull.imp.client.qqMusic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.felnull.imp.client.music.link.LinkDebug;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 音乐扫码登录：结果是 {@code uin=…; qm_keyst=…} Cookie，{@link QQMusicUtil} 本来就在每个请求上带着它。流程里的固定
 * appid 与参数名是服务端在核的**指纹**，不许"顺手整理"；Cookie 是凭据，日志里只许出现键值对数量。
 */
public final class QQMusicLogin {
    private QQMusicLogin() {
    }

    private static final String APP_ID = "716027609";
    private static final String THIRD_APP_ID = "100497308";
    private static final String LOGIN_JUMP = "https://graph.qq.com/oauth2.0/login_jump";
    private static final String REDIRECT_URI = "https://y.qq.com/wk_v17/common_login.html?type=QQ&&redirect=";
    private static final String QR_SHOW_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrshow";
    private static final String QR_LOGIN_URL = "https://xui.ptlogin2.qq.com/ssl/ptqrlogin";
    private static final String AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize";
    private static final String MUSICU_URL = "https://u6.y.qq.com/cgi-bin/musicu.fcg";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** {@code ptuiCB('code','?','url','?','message')} — the plain-JS callback the poll endpoint returns. */
    private static final Pattern PTUI_CB = Pattern.compile("ptuiCB\\('(\\d+)','[^']*','([^']*)','[^']*','([^']*)'");
    private static final Pattern CODE_PARAM = Pattern.compile("[?&]code=([^&]+)");

    /** Where the login currently stands. */
    public enum State {
        /** Nobody has scanned the code yet. */
        WAITING,
        /** Scanned, waiting for the phone to confirm. */
        SCANNED,
        /** The code timed out; the screen has to fetch a new one. */
        EXPIRED,
        /** Logged in — {@link QrPoll#cookie()} is filled in. */
        SUCCESS,
        /** Anything unexpected (network error, a step that returned nothing usable). */
        FAILED
    }

    /** The QR image to draw, plus the {@code qrsig} that ties the polling to it. */
    public record QrCode(byte[] png, String qrsig) {
    }

    /**
     * @param cookie the assembled {@code uin=…; qm_keyst=…} cookie, only on {@link State#SUCCESS}
     */
    public record QrPoll(State state, String cookie) {
        public boolean hasCookie() {
            return cookie != null && !cookie.isEmpty();
        }
    }

    /** Step 1: fetch the QR image (PNG bytes, drawn as-is) and the polling handle. */
    public static QrCode generate() throws IOException, InterruptedException {
        // `t` is only a cache buster; keeping it random avoids two sessions sharing one QR image.
        var url = QR_SHOW_URL + "?appid=" + APP_ID + "&e=2&l=M&s=3&d=72&v=4&t="
                + String.format(Locale.ROOT, "%.3f", Math.random())
                + "&daid=383&pt_3rd_aid=" + THIRD_APP_ID
                + "&u1=" + encode(LOGIN_JUMP);

        var response = get(url, Map.of(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200)
            throw new IOException("QQ qr show HTTP " + response.statusCode());

        var qrsig = cookieValue(response, "qrsig");
        var png = response.body();
        if (png == null || png.length == 0)
            throw new IOException("QQ qr show returned no image");
        if (qrsig == null || qrsig.isEmpty())
            throw new IOException("QQ qr show returned no qrsig cookie");
        LinkDebug.log("QQ qr generate", "bytes", png.length, "qrsigLen", qrsig.length());
        return new QrCode(png, qrsig);
    }

    /** Step 2 (poll once; 2 s apart is what the web page uses): on {@code 0} it finishes steps 3-5. */
    public static QrPoll poll(QrCode qr) throws IOException, InterruptedException {
        var url = QR_LOGIN_URL
                + "?u1=" + encode(LOGIN_JUMP)
                + "&ptqrtoken=" + hash33(qr.qrsig(), 0)
                + "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052"
                + "&js_ver=25072815&js_type=1&login_sig=&pt_uistyle=40"
                + "&aid=" + APP_ID + "&daid=383&pt_3rd_aid=" + THIRD_APP_ID;

        var response = get(url, Map.of("Cookie", "qrsig=" + qr.qrsig()), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new IOException("QQ qr poll HTTP " + response.statusCode());

        var matcher = PTUI_CB.matcher(response.body());
        if (!matcher.find()) {
            LinkDebug.log("QQ qr poll", "state", "no-ptuiCB", "bodyHead", head(response.body(), 120));
            return new QrPoll(State.WAITING, "");
        }

        var code = matcher.group(1);
        if ("65".equals(code)) {
            LinkDebug.log("QQ qr poll", "state", "expired");
            return new QrPoll(State.EXPIRED, "");
        }
        if (!"0".equals(code)) {
            var state = "67".equals(code) ? State.SCANNED : State.WAITING;
            LinkDebug.log("QQ qr poll", "state", state, "code", code);
            return new QrPoll(state, "");
        }

        var checkUrl = matcher.group(2) == null || matcher.group(2).isBlank() ? matcher.group(3) : matcher.group(2);
        if (checkUrl == null || checkUrl.isBlank())
            throw new IOException("QQ login succeeded but no check url came back");
        var cookie = finish(checkUrl);
        LinkDebug.log("QQ qr poll", "state", "success", "cookieEntries", cookie.split(";").length);
        return new QrPoll(State.SUCCESS, cookie);
    }

    /** Steps 3-5: check url -> authorize -> LoginServer -> the cookie QQ's APIs accept. */
    private static String finish(String checkUrl) throws IOException, InterruptedException {
        // The check url answers with a redirect; the cookies are on that first answer, so redirects must stay off.
        var check = get(checkUrl, Map.of(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var uin = cookieValue(check, "pt2gguin");
        var token = cookieValue(check, "pt_oauth_token");
        var skey = cookieValue(check, "p_skey");
        if (uin == null || token == null || skey == null) {
            LinkDebug.log("QQ login check", "uin", uin != null, "token", token != null, "skey", skey != null);
            throw new IOException("QQ login: check url did not set the expected cookies");
        }

        // `pt2gguin` keeps its leading "o" (o01234…); QQ expects that spelling in p_uin.
        var form = new LinkedHashMap<String, String>();
        form.put("response_type", "code");
        form.put("client_id", THIRD_APP_ID);
        form.put("redirect_uri", REDIRECT_URI);
        form.put("scope", "get_user_info");
        form.put("state", "y_new.top.pop.logout");
        form.put("switch", "");
        form.put("from_ptlogin", "1");
        form.put("src", "1");
        form.put("update_auth", "1");
        form.put("openapi", "1010");
        form.put("g_tk", String.valueOf(hash33(skey, 5381)));
        form.put("auth_time", String.valueOf(System.currentTimeMillis() / 1000));

        var authorize = send(AUTHORIZE_URL, Map.of(
                "Cookie", "p_uin=" + uin + "; pt_oauth_token=" + token + "; p_skey=" + skey,
                "Content-Type", "application/x-www-form-urlencoded"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                formBody(form));
        var location = authorize.headers().firstValue("location").orElse(null);
        if (location == null) {
            LinkDebug.log("QQ login authorize", "httpStatus", authorize.statusCode(), "location", false);
            throw new IOException("QQ login: authorize returned no redirect");
        }
        var codeMatcher = CODE_PARAM.matcher(location);
        if (!codeMatcher.find())
            throw new IOException("QQ login: authorize redirect carries no code");
        var authCode = codeMatcher.group(1);
        LinkDebug.log("QQ login authorize", "httpStatus", authorize.statusCode(), "codeLen", authCode.length());

        var login = loginServer(uin, authCode);
        var musicId = login.has("musicid") ? login.get("musicid").getAsString() : "";
        var musicKey = login.has("musickey") ? login.get("musickey").getAsString() : "";
        if (musicId.isEmpty() || musicKey.isEmpty())
            throw new IOException("QQ login: LoginServer returned no credential");
        LinkDebug.log("QQ login server", "musicIdLen", musicId.length(), "musicKeyLen", musicKey.length(),
                "expiresIn", login.has("keyExpiresIn") ? login.get("keyExpiresIn").getAsString() : "?");
        return "uin=" + musicId + "; qm_keyst=" + musicKey;
    }

    /** The {@code music.login.LoginServer.Login} call: exchanges the oauth code for musicid/musickey. */
    private static JsonObject loginServer(String uin, String authCode) throws IOException, InterruptedException {
        var comm = new JsonObject();
        comm.addProperty("_channelid", "208");
        comm.addProperty("_os_version", "6.2.9200-2");
        comm.addProperty("authst", "");
        comm.addProperty("ct", "19");
        comm.addProperty("cv", "2121");
        comm.addProperty("guid", "");
        comm.addProperty("patch", "118");
        comm.addProperty("tmeAppID", "qqmusic");
        comm.addProperty("tmeLoginType", 2);
        comm.addProperty("uin", uin);

        var param = new JsonObject();
        param.addProperty("appid", Integer.parseInt(THIRD_APP_ID));
        param.addProperty("code", authCode);
        param.addProperty("deviceName", "minecraft");
        param.addProperty("forceRefreshToken", 0);
        param.addProperty("onlyNeedAccessToken", 0);

        var loginReq = new JsonObject();
        loginReq.addProperty("module", "music.login.LoginServer");
        loginReq.addProperty("method", "Login");
        loginReq.add("param", param);

        var body = new JsonObject();
        body.add("comm", comm);
        body.add("music.login.LoginServer.Login", loginReq);

        var response = send(MUSICU_URL + "?pcachetime=" + System.currentTimeMillis() / 1000,
                Map.of("Content-Type", "application/json; charset=utf-8"),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                body.toString().getBytes(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new IOException("QQ login server HTTP " + response.statusCode());

        var tree = JsonParser.parseString(response.body()).getAsJsonObject();
        var node = tree.has("music.login.LoginServer.Login") ? tree.getAsJsonObject("music.login.LoginServer.Login") : null;
        var code = node != null && node.has("code") ? node.get("code").getAsInt() : -1;
        if (code != 0) {
            LinkDebug.log("QQ login server", "code", code, "bodyHead", head(response.body(), 120));
            throw new IOException("QQ login server refused the code: " + code);
        }
        return node.has("data") ? node.getAsJsonObject("data") : new JsonObject();
    }

    /**
     * QQ's hash33: {@code ptqrtoken} with seed 0, {@code g_tk} with seed 5381 — a wrong token is answered with HTTP 403.
     */
    static long hash33(String text, long seed) {
        long hash = seed;
        for (int i = 0; i < text.length(); i++) {
            hash += (hash << 5) + text.charAt(i);
            hash &= 0x7FFFFFFFL;
        }
        return hash;
    }

    private static String cookieValue(HttpResponse<?> response, String name) {
        for (var raw : response.headers().allValues("set-cookie")) {
            var matcher = Pattern.compile(Pattern.quote(name) + "=([^;,]*)").matcher(raw);
            if (matcher.find())
                return matcher.group(1);
        }
        return null;
    }

    private static byte[] formBody(Map<String, String> form) {
        var sb = new StringBuilder();
        for (var entry : form.entrySet()) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String head(String body, int max) {
        if (body == null)
            return null;
        return body.substring(0, Math.min(max, body.length()));
    }

    private static <T> HttpResponse<T> get(String url, Map<String, String> headers, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return send(url, headers, handler, null);
    }

    private static <T> HttpResponse<T> send(String url, Map<String, String> headers, HttpResponse.BodyHandler<T> handler, byte[] body)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/");
        headers.forEach(builder::header);
        if (body == null)
            builder.GET();
        else
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
        // QQMusicUtil's client never follows redirects, which is exactly what the authorize step needs (the code is in `Location`).
        return QQMusicUtil.HTTP_CLIENT.send(builder.build(), handler);
    }
}
