package dev.felnull.imp.client.music.netmusic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.imp.client.music.netmusic.api.WebApi;
import dev.felnull.imp.client.music.netmusic.api.pojo.NetEaseMusicSong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetMusicUtil {
    public static final WebApi WEB_API = new WebApi(HashMap.newHashMap(0));
    public static final Logger LOGGER = LogManager.getLogger(NetMusicUtil.class);
    private static final Gson GSON = new Gson();
    public static URL resolveRedirect(URL originalUrl, int maxRedirects, Map<String, String> headers) throws IOException {
        URL currentUrl = originalUrl;
        HttpURLConnection connection;

        while (maxRedirects-- > 0) {
            connection = (HttpURLConnection) currentUrl.openConnection();
            connection.setInstanceFollowRedirects(false); // 手动处理重定向

            // 设置请求头
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            int responseCode = connection.getResponseCode();

            // 如果是 200，直接返回当前 URL
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return currentUrl;
            }

            // 处理 302/301/307/308 等重定向
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String location = connection.getHeaderField("Location");
                connection.disconnect(); // 关闭当前连接

                if (location == null) {
                    throw new IOException("Redirect with no Location header: " + responseCode);
                }

                // 处理相对路径的 Location
                currentUrl = new URL(currentUrl, location);
            } else {
                throw new IOException("Unexpected HTTP status: " + responseCode);
            }
        }

        throw new IOException("Too many redirects (max: " + maxRedirects + ")");
    }

    public static URL resolveRedirect(URL originalUrl, Map<String, String> headers) throws IOException {
        return resolveRedirect(originalUrl, 10, headers);
    }

    public static URL resolveRedirect(URL originalUrl) throws IOException {
        return resolveRedirect(originalUrl, 10, Map.of());
    }

    public static URL getNetMusicUrl(long id){
        final String baseURL = "https://music.163.com/song/media/outer/url?id=%d.mp3";
        try{
            return resolveRedirect(URL.of(new URI(String.format(baseURL, id)), null), WEB_API.getRequestPropertyData());
        }catch (Exception e){
            return null;
        }
    }

    @Nullable
    public static NetEaseMusicSong.Song getNetMusicSongData(String data){
        try {
            var song = GSON.fromJson(data, NetEaseMusicSong.class);
            return song.getSong();
        } catch (Exception e) {
            LOGGER.debug("获取音乐错误：", e);
            return null;
        }
    }

    @Nullable
    public static String getNetMusicJson(long id){
        try {
            return WEB_API.song(id);
        } catch (IOException e) {
            LOGGER.debug("获取音乐错误：", e);
            return null;
        }
    }

    @SuppressWarnings("all")
    public static URL getIconUrlFromData(String json) throws Exception{
        var data = (Map<String, Object>)GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        var song = (Map<String, Object>)((List<Object>)data.get("songs")).get(0);
        var album = song.get("album");
        // 大力出奇迹.png
        return new URL((String) ((Map<String, Object>)album).get("picUrl"));
    }

    public static Proxy getSystemProxy() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            String[] regCmd = {"reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", "ProxyServer"};
            try {
                Process p = Runtime.getRuntime().exec(regCmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("REG_SZ")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            String proxyStr = parts[parts.length - 1];
                            if (proxyStr.contains(":")) {
                                String[] hostPort = proxyStr.split(":");
                                if (hostPort.length == 2) {
                                    return new Proxy(Proxy.Type.HTTP,
                                            new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        } else if (os.contains("mac")) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"networksetup", "-getwebproxy", "Wi-Fi"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String host = null, port = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Server:")) {
                        host = line.split(":")[1].trim();
                    } else if (line.contains("Port:")) {
                        port = line.split(":")[1].trim();
                    }
                }
                if (host != null && port != null) {
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, Integer.parseInt(port)));
                }
            } catch (Exception e) {}
        } else if (os.contains("linux") || os.contains("nix")) {
            try {
                String desktop = System.getenv("XDG_CURRENT_DESKTOP");
                if (desktop != null && desktop.toLowerCase().contains("gnome")) {
                    Process p = Runtime.getRuntime().exec(new String[]{"gsettings", "get", "org.gnome.system.proxy.http", "host"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String host = reader.readLine();
                    if (host != null && !host.equals("''") && !host.equals("'none'")) {
                        host = host.trim().replaceAll("^'|'$", "");
                        p = Runtime.getRuntime().exec(new String[]{"gsettings", "get", "org.gnome.system.proxy.http", "port"});
                        reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String portStr = reader.readLine();
                        if (portStr != null) {
                            int port = Integer.parseInt(portStr.trim().replaceAll("'", ""));
                            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                        }
                    }
                }
            } catch (Exception e) {}

            String envProxy = System.getenv("http_proxy");
            if (envProxy == null) envProxy = System.getenv("HTTP_PROXY");
            if (envProxy != null && !envProxy.isEmpty()) {
                try {
                    java.net.URL proxyUrl = new java.net.URL(envProxy);
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort()));
                } catch (Exception e) {}
            }
        }

        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        if (host != null && port != null) {
            try {
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, Integer.parseInt(port)));
            } catch (NumberFormatException e) {}
        }

        return Proxy.NO_PROXY;
    }
}
