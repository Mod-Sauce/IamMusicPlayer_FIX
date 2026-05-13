// 网络音乐机：登登你的？
package dev.felnull.imp.client.music.netmusic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.netmusic.api.NetWorker;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.felnull.imp.client.music.netmusic.NetMusicUtil.WEB_API;

public class LoginNeedUtil {
    private static final Gson GSON = new Gson();
    private static final String BASE_URL = "https://music.163.com/api/song/enhance/player/url/v1?encodeType=mp3&ids=[%s]&level=standard";

    @SuppressWarnings("all")
    public static String getSongUrl(String json) throws Exception{
        var j = GSON.fromJson(json, TypeToken.get(Object.class));
        return (String)((Map<String, Object>)((List<Object>)((Map<String, Object>)j).get("data")).get(0)).get("url");
    }

    @Nullable
    public static String pasteVIPUrl(long id){
        if(IamMusicPlayer.getConfig().netMusicConfig.neteaseCookie.isEmpty())return null;
        var args = new HashMap<>(WEB_API.getRequestPropertyData());
        try {
            var json = NetWorker.get(String.format(BASE_URL, id), args);
            return getSongUrl(json);
        } catch (Exception ignored) {

        }
        return null;
    }
}
