package dev.felnull.imp.client.kugou;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * The few small helpers the KuGou path needs (MD5 / secure random).
 */
public final class KuGouCrypto {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] ALPHANUM =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private KuGouCrypto() {
    }

    /** Lowercase hex MD5 (every KuGou signature uses this form). */
    public static String md5(String input) {
        try {
            var digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // MD5 is a mandatory JDK algorithm, this is unreachable; if it happens the environment is broken, so throw
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /** A random alphanumeric string of length n (used as the AES key for device registration). */
    public static String randomString(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(ALPHANUM[SECURE_RANDOM.nextInt(ALPHANUM.length)]);
        return sb.toString();
    }

    /** A UUID v4 shaped guid (part of the device identifier). */
    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
}
