package dev.felnull.imp.client.music.netmusic;

import java.util.regex.Pattern;

public enum URLType {
    SONG("https://.*163.*song.*?(?!user)id=([0-9]+)"),
    SONG_LIST("https://.*?163.*?playlist.*?(?!user)id=([0-9]+)"),
    DJ("https://.*?163.*?program.*?[(?!user)(?!dj)]id=([0-9]+)"),
    OTHER("(.*)");
    final Pattern pattern;
    URLType(String pattern){
        this.pattern = Pattern.compile(pattern);
    }

    public boolean isMatch(String url){
        return this.pattern.matcher(url).find();
    }

    public String getMatch(String url){
        var m = this.pattern.matcher(url);
        if(m.find())
            return m.group(1);
        return null;
    }
}
