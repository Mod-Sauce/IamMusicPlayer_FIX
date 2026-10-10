package dev.felnull.imp.client.kugou;

import java.util.Map;
import java.util.TreeMap;

/**
 * The signature algorithm of the KuGou APIs.
 */
public final class KuGouSignature {
    /** The fixed Android-side salt (both secrets are used for signing only, **not account credentials**). */
    private static final String ANDROID_SECRET = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";
    /** The salt used for the resolve API's {@code key} parameter. */
    private static final String KEY_SECRET = "185672dd44712f60bb1736df5a377e82";
    /**
     * The Web-side signature salt: **the two QR login APIs use this one**.
     */
    private static final String WEB_SECRET = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    /** The {@code srcappid} the QR APIs require (not the same value as appid). */
    public static final int SRCAPPID = 2919;

    /** The appid of the KuGou Android client (used by resolve/registration/QR link). */
    public static final int APPID = 3116;
    /** Client version (the registration API uses 11440; the resolve API uses 11430, see KuGouApi). */
    public static final int CLIENTVER = 11440;
    /** The client version the resolve API uses (different from registration). */
    public static final int CLIENTVER_TRACK = 11430;

    private KuGouSignature() {
    }

    /** Web signature: {@code md5(WEB_SECRET + params joined in ascending key order as k=v, no separator + WEB_SECRET)};
     * the QR login APIs use it; unlike {@link #android}, no body takes part. */
    public static String web(Map<String, Object> params) {
        var sorted = new TreeMap<>(params);
        var sb = new StringBuilder(WEB_SECRET);
        for (var e : sorted.entrySet())
            sb.append(e.getKey()).append('=').append(stringify(e.getValue()));
        sb.append(WEB_SECRET);
        return KuGouCrypto.md5(sb.toString());
    }

    /** Android signature: {@code data} is the POST body (pass an empty string for GET requests). */
    public static String android(Map<String, Object> params, String data) {
        var sorted = new TreeMap<>(params);
        var sb = new StringBuilder(ANDROID_SECRET);
        for (var e : sorted.entrySet())
            sb.append(e.getKey()).append('=').append(stringify(e.getValue()));
        sb.append(data == null ? "" : data).append(ANDROID_SECRET);
        return KuGouCrypto.md5(sb.toString());
    }

    /** The resolve API's {@code key} parameter: {@code md5(hash + KEY_SECRET + appid + mid + userid)}. */
    public static String trackKey(String hash, int appid, String mid, String userid) {
        return KuGouCrypto.md5(hash + KEY_SECRET + appid + mid + userid);
    }

    /** Parameter value -> the signed literal. Only primitives: the signature concatenates "the text the API
     * receives", so {@code String.valueOf} is enough (JSON would make {@code null} a different literal). */
    private static String stringify(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
