package dev.felnull.imp.client.bilibili;

import com.google.gson.*;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Lyric;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.util.ProxyUtil;
import it.unimi.dsi.fastutil.floats.Float2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BiliBiliUtil {
    // Reference from Mcedia
    private static final Gson GSON = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final HttpClient client = HttpClient.newBuilder()
            .proxy(ProxySelector.of((InetSocketAddress) ProxyUtil.getProxy().address())).build();

    public static String fetchAudioUrl(String bvid) throws IOException, InterruptedException {
        return fetchAudioUrl(bvid, 1, null);
    }

    public static String fetchAudioUrl(String bvid, int page, String cookie) throws IOException, InterruptedException {
        String viewApi = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest.Builder viewRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(viewApi))
                .header("User-Agent", USER_AGENT);

        if (cookie != null && !cookie.isEmpty()) {
            viewRequestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> viewResponse = client.send(viewRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject viewJson = JsonParser.parseString(viewResponse.body()).getAsJsonObject();

        if (viewJson.get("code").getAsInt() != 0) {
            throw new IOException("Failed to get video info: " + viewJson.get("message").getAsString());
        }

        JsonObject viewData = viewJson.getAsJsonObject("data");
        long cid;

        JsonArray pagesArray = viewData.getAsJsonArray("pages");
        if (pagesArray != null && pagesArray.size() > 1) {
            if (page > 0 && page <= pagesArray.size()) {
                JsonObject pageData = pagesArray.get(page - 1).getAsJsonObject();
                cid = pageData.get("cid").getAsLong();
            } else {
                JsonObject pageData = pagesArray.get(0).getAsJsonObject();
                cid = pageData.get("cid").getAsLong();
            }
        } else {
            cid = viewData.get("cid").getAsLong();
        }

        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&fnval=4048";

        HttpRequest.Builder playRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/");

        if (cookie != null && !cookie.isEmpty()) {
            playRequestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> playResponse = client.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject playJson = JsonParser.parseString(playResponse.body()).getAsJsonObject();

        if (playJson.get("code").getAsInt() != 0) {
            throw new IOException("Bilibili API error: " + playJson.get("message").getAsString());
        }

        JsonObject data = playJson.getAsJsonObject("data");
        if (!data.has("dash")) {
            throw new IOException("DASH stream not available");
        }

        JsonObject dash = data.getAsJsonObject("dash");
        JsonArray audioArray = dash.getAsJsonArray("audio");
        if (audioArray == null || audioArray.isEmpty()) {
            throw new IOException("No audio stream found");
        }

        JsonObject bestAudio = audioArray.get(0).getAsJsonObject();
        for (int i = 1; i < audioArray.size(); i++) {
            JsonObject current = audioArray.get(i).getAsJsonObject();
            if (current.get("bandwidth").getAsInt() > bestAudio.get("bandwidth").getAsInt()) {
                bestAudio = current;
            }
        }

        return bestAudio.get("baseUrl").getAsString();
    }

    public static MusicMediaResult getMusicMediaResultFromBV(String BV) {
        try {
            String apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + BV;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json.get("code").getAsInt() != 0) return null;

            JsonObject data = json.getAsJsonObject("data");
            String title = data.get("title").getAsString();
            String pic = data.get("pic").getAsString();
            long duration = data.get("duration").getAsLong();

            List<String> authors = new ArrayList<>();
            JsonObject owner = data.getAsJsonObject("owner");
            authors.add(owner.get("name").getAsString());

            JsonArray staff = data.getAsJsonArray("staff");
            if (staff != null) {
                for (JsonElement elem : staff) {
                    JsonObject staffObj = elem.getAsJsonObject();
                    authors.add(staffObj.get("name").getAsString());
                }
            }

            ImageInfo imageInfo = new ImageInfo(ImageInfo.ImageType.URL, pic);
            MusicSource source = new MusicSource("bilibili", BV, duration * 1000);
            return new MusicMediaResult(source, imageInfo, title, String.join("、", authors));

        } catch (Exception e) {
            return null;
        }
    }

    public static Lyric fetchLyrics(String bvid, String cookie) throws IOException, InterruptedException {
        if (cookie == null || cookie.isEmpty()) {
            return Lyric.EMPTY;
        }
        List<Pair<String, String>> rawLyrics = fetchRawLyrics(bvid, cookie);
        if (rawLyrics.isEmpty()) {
            return Lyric.EMPTY;
        }
        return choiceLyric(rawLyrics);
    }

    private static List<Pair<String, String>> fetchRawLyrics(String bvid, String cookie) throws IOException, InterruptedException {
        List<Pair<String, String>> result = new ArrayList<>();

        String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest.Builder viewReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(viewUrl))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            viewReqBuilder.header("Cookie", cookie);
        }
        HttpResponse<String> viewResp = client.send(viewReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (viewResp.statusCode() != 200) return result;

        JsonObject viewJson = JsonParser.parseString(viewResp.body()).getAsJsonObject();
        if (viewJson.get("code").getAsInt() != 0) return result;

        JsonObject data = viewJson.getAsJsonObject("data");
        long aid = data.get("aid").getAsLong();
        long cid = data.get("cid").getAsLong();

        String wbiUrl = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid + "&cid=" + cid;
        HttpRequest.Builder wbiReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(wbiUrl))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            wbiReqBuilder.header("Cookie", cookie);
        }
        HttpResponse<String> wbiResp = client.send(wbiReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (wbiResp.statusCode() != 200) return result;

        JsonObject wbiJson = JsonParser.parseString(wbiResp.body()).getAsJsonObject();
        if (wbiJson.get("code").getAsInt() != 0) return result;

        JsonArray subtitles = wbiJson.getAsJsonObject("data")
                .getAsJsonObject("subtitle")
                .getAsJsonArray("subtitles");
        if (subtitles == null) return result;

        for (JsonElement elem : subtitles) {
            JsonObject sub = elem.getAsJsonObject();
            String lang = sub.get("lan").getAsString();
            if(lang.contains("ai"))continue;
            String url = sub.get("subtitle_url").getAsString();
            if (url.startsWith("//")) url = "https:" + url;

            HttpRequest subReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<String> subResp = client.send(subReq, HttpResponse.BodyHandlers.ofString());
            if (subResp.statusCode() == 200) {
                result.add(new Pair<>(lang, subResp.body()));
            }
        }
        return result;
    }

    public static Lyric choiceLyric(List<Pair<String, String>> lyricsByLanguage) {
        if (lyricsByLanguage == null || lyricsByLanguage.isEmpty()) {
            return Lyric.EMPTY;
        }

        String originalJson = null;
        String transJson = null;

        for (Pair<String, String> p : lyricsByLanguage) {
            String lang = p.getA();
            if ("zh".equals(lang) || "zh-CN".equals(lang) || "zh-Hans".equals(lang)) {
                if (transJson == null) transJson = p.getB();
            } else {
                if (originalJson == null) originalJson = p.getB();
            }
        }

        if (originalJson == null) {
            originalJson = lyricsByLanguage.get(0).getB();
            transJson = null;
        }

        Float2ObjectSortedMap<String> originalMap = parseLyricJson(originalJson);
        Float2ObjectSortedMap<String> transMap = parseLyricJson(transJson);

        if (originalMap == null) {
            return Lyric.EMPTY;
        }

        return new Lyric(originalMap, transMap);
    }

    private static Float2ObjectSortedMap<String> parseLyricJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) return null;

        try {
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonArray body = root.getAsJsonArray("body");
            if (body == null || body.isEmpty()) return null;

            Float2ObjectSortedMap<String> map = new Float2ObjectRBTreeMap<>();
            for (JsonElement elem : body) {
                JsonObject line = elem.getAsJsonObject();
                float from = line.get("from").getAsFloat();
                String content = line.get("content").getAsString();
                map.put(from, content);
            }
            return map.isEmpty() ? null : map;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> fetchFavBvids(long fid) throws Exception {
        List<String> list = new ArrayList<>();
        int pn = 1;

        while (true) {

            String url = "https://api.bilibili.com/x/v3/fav/resource/list"
                    + "?media_id=" + fid
                    + "&pn=" + pn
                    + "&ps=20"
                    + "&order=mtime"
                    + "&platform=web";

            JsonObject json = request(url);

            if (json.get("code").getAsInt() != 0)
                throw new RuntimeException(json.toString());

            JsonObject data = json.getAsJsonObject("data");
            JsonArray medias = data.getAsJsonArray("medias");

            if (medias == null || medias.isEmpty())
                break;

            for (JsonElement e : medias) {
                list.add(e.getAsJsonObject().get("bvid").getAsString());
            }

            pn++;

            if(!data.get("has_more").getAsBoolean())
                break;
        }

        return list;
    }

    public static VideoInfo fetchVideo(String bvid) throws Exception {
        String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;

        JsonObject json = request(url);

        if (json.get("code").getAsInt() != 0)
            return null;

        JsonObject v = json.getAsJsonObject("data");

        return new VideoInfo(
                bvid,
                v.get("title").getAsString(),
                v.get("pic").getAsString(),
                v.getAsJsonObject("owner").get("name").getAsString(),
                v.get("duration").getAsInt()
        );
    }

    private static JsonObject request(String url) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/")
                .GET();
        var cookie = IamMusicPlayer.getConfig().bilibiliConfig.bilibiliCookie;
        if(cookie != null && !cookie.isEmpty())
            builder = builder.header("Cookie", cookie);
        HttpRequest request = builder.build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return GSON.fromJson(response.body(), JsonObject.class);
    }

    public static FavInfo fetchFavInfo(long fid) throws Exception {
        String url = "https://api.bilibili.com/x/v3/fav/folder/info?media_id=" + fid;

        JsonObject json = request(url);

        if (json.get("code").getAsInt() != 0)
            throw new RuntimeException(json.toString());

        JsonObject data = json.getAsJsonObject("data");

        return new FavInfo(
                data.get("title").getAsString(),
                data.get("cover").getAsString(),
                data.get("media_count").getAsInt(),
                data.getAsJsonObject("upper").get("name").getAsString()
        );
    }

    public record FavInfo(
            String title,
            String cover,
            int count,
            String author
    ) {}

    public record VideoInfo(
            String bvid,
            String title,
            String cover,
            String author,
            int duration
    ) {}
}
