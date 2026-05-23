package dev.felnull.imp.util;

import dev.felnull.imp.IamMusicPlayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class ProxyUtil {
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
            } catch (NumberFormatException ignored) {}
        }

        return Proxy.NO_PROXY;
    }

    public static Proxy getProxy(){
        return IamMusicPlayer.getConfig().proxy.getProxy();
    }
}
