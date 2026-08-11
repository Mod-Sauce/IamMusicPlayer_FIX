package dev.felnull.imp.client.qqMusic.resource;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class QQMusicURLData {
    @SerializedName("code")
    public int code;

    @SerializedName("req_0")
    public REQ req;

    public static class REQ{
        @SerializedName("data")
        public Data data;
    }

    public static class Data{
        @SerializedName("sip")
        public List<String> sip;

        @SerializedName("midurlinfo")
        public List<UrlInfo> urlInfos;
    }

    public static class UrlInfo{
        @SerializedName("purl")
        public String purl;
    }

    @Nullable
    public String getUrl(){
        if(req == null)return null;
        if(req.data == null)return null;
        if(req.data.sip.isEmpty())return null;
        if(req.data.urlInfos.isEmpty())return null;
        var sip = req.data.sip.get(0);
        var purl = req.data.urlInfos.get(0).purl;
        return sip + purl;
    }
}
