package dev.felnull.imp.server.webdav;

import dev.felnull.imp.IamMusicPlayer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ServerWebDAVCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String FALLBACK_SECRET =
        IamMusicPlayer.MODID + ":webdav:fallback-secret";

    private ServerWebDAVCrypto() {}

    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key(),
                new GCMParameterSpec(128, iv)
            );
            byte[] encrypted = cipher.doFinal(
                plainText.getBytes(StandardCharsets.UTF_8)
            );
            ByteBuffer buffer = ByteBuffer.allocate(
                iv.length + encrypted.length
            );
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt WebDAV secret", e);
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            byte[] raw = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                new GCMParameterSpec(128, iv)
            );
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt WebDAV secret", e);
        }
    }

    public static boolean isUsingFallbackSecret() {
        String secret = System.getenv("IMP_WEBDAV_SECRET");
        return secret == null || secret.isBlank();
    }

    private static SecretKeySpec key() throws Exception {
        String secret = System.getenv("IMP_WEBDAV_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = FALLBACK_SECRET;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(
            secret.getBytes(StandardCharsets.UTF_8)
        );
        return new SecretKeySpec(keyBytes, 0, 16, "AES");
    }
}
