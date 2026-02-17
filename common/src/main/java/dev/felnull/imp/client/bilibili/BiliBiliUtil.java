package dev.felnull.imp.client.bilibili;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.MusicSource;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BiliBiliUtil {
    private static final Gson GSON = new Gson();
    private static JsonObject getPlayInfo(Document document){
        var sl = document.select("script");
        for (Element element: sl){
            if(element.data().contains("window.__playinfo__=")){
                return GSON.fromJson(element.data().replace("window.__playinfo__=", ""), JsonObject.class);
            }
        }
        return null;
    }

    private static String getM4AUrl(Document document){
        var info = getPlayInfo(document);
        if(info == null)return null;
        try{
            return info.getAsJsonObject("data")
                    .getAsJsonObject("dash")
                    .getAsJsonArray("audio")
                    .get(0).getAsJsonObject().get("baseUrl")
                    .getAsString();
        } catch (Exception e) {
            return null;
        }
    }
    private static long getM4ALength(Document document){
        var info = getPlayInfo(document);
        if(info == null)return 0;
        try{
            return info.getAsJsonObject("data")
                    .getAsJsonObject("dash")
                    .get("duration").getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Document getDocument(String url){
        Document document;
        try {
            document = Jsoup.connect(url).get();
        } catch (IOException e) {
            return null;
        }
        return document;
    }

    private static String getTitle(Document document){
        var h1 = document.select("h1");
        if(h1.isEmpty())return null;
        return Objects.requireNonNull(h1.first()).text();
    }

    public static List<String> getAuthor(Document document){
        var elements = document.getElementsByClass("up-name");
        var elements1 = document.getElementsByClass("staff-name");
        var r = new ArrayList<String>();
        for (Element element: elements)
            r.add(element.text());
        for (Element element: elements1)
            r.add(element.text());
        return r;
    }

    @Nullable
    public static MusicMediaResult getMusicMediaResultFromBV(String BV){
        var url = String.format("https://www.bilibili.com/video/%s", BV);
        var doc = getDocument(url);
        if(doc == null)return null;
        var title = getTitle(doc);
        var author = getAuthor(doc);
        if(title == null)return null;
        return new MusicMediaResult(
                new MusicSource("bilibili", BV, getM4ALength(doc) * 1000),
                ImageInfo.EMPTY,
                title,
                String.join("、", author)
        );
    }

    @Nullable
    public static String getURLFromBV(String BV) {
        var url = String.format("https://www.bilibili.com/video/%s", BV);
        var doc = getDocument(url);
        if (doc == null) return null;
        return getM4AUrl(doc);
    }
}
