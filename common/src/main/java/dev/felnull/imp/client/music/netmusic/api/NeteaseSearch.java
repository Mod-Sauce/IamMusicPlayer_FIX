package dev.felnull.imp.client.music.netmusic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.felnull.imp.IamMusicPlayer;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class NeteaseSearch {

    private static final String pubKey = "010001";
    private static final String modulus = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final String nonce = "0CoJUm6Qyw8W8jud";
    private static final String iv = "0102030405060708";

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String aesEncrypt(String text, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String rsaEncrypt(String text) {
        String reversed = new StringBuilder(text).reverse().toString();
        BigInteger bigText = new BigInteger(1, reversed.getBytes(StandardCharsets.UTF_8));
        BigInteger pub = new BigInteger(pubKey, 16);
        BigInteger mod = new BigInteger(modulus, 16);

        BigInteger result = bigText.modPow(pub, mod);
        return String.format("%0256x", result);
    }

    public static String randomKey(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static Map<String, String> encrypt(Map<String, Object> data) throws Exception {
        String json = mapper.writeValueAsString(data);

        String key = randomKey(16);

        String encText = aesEncrypt(json, nonce);
        encText = aesEncrypt(encText, key);

        String encSecKey = rsaEncrypt(key);

        Map<String, String> result = new HashMap<>();
        result.put("params", encText);
        result.put("encSecKey", encSecKey);
        return result;
    }

    public static String getCookie() {
        return IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie;
    }

    public static String search(String keyword) throws Exception {
        String url = "https://music.163.com/weapi/cloudsearch/get/web?csrf_token=";

        Map<String, Object> data = new HashMap<>();
        data.put("s", keyword);
        data.put("type", 1);
        data.put("offset", 0);
        data.put("limit", 30);
        data.put("total", true);
        data.put("csrf_token", "");

        Map<String, String> enc = encrypt(data);

        String cookie = getCookie();

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = new HttpPost(url);

            String body = "params=" + URLEncoder.encode(enc.get("params"), StandardCharsets.UTF_8) +
                    "&encSecKey=" + URLEncoder.encode(enc.get("encSecKey"), StandardCharsets.UTF_8);

            post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

            post.setHeader("User-Agent", "Mozilla/5.0");
            post.setHeader("Referer", "https://music.163.com/");
            post.setHeader("Origin", "https://music.163.com");
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setHeader("Accept", "*/*");
            post.setHeader("Cookie", cookie);

            try (CloseableHttpResponse response = client.execute(post)) {
                return new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}