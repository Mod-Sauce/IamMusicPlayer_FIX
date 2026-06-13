package dev.felnull.imp.util;

import dev.felnull.imp.IamMusicPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class ProxyUtil {
    private static final Logger LOGGER = LogManager.getLogger();
    public static Proxy getSystemProxy() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                return getWindowsProxy();
            } else if (os.contains("mac")) {
                return getMacProxy();
            } else if (os.contains("linux") || os.contains("nix")) {
                Proxy p = getLinuxProxy();
                if (p != null) return p;
            }

            String host = System.getProperty("http.proxyHost");
            String port = System.getProperty("http.proxyPort");

            if (host != null && port != null) {
                return new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, Integer.parseInt(port)));
            }

        } catch (Exception e) {
            LOGGER.error(e);
        }

        return Proxy.NO_PROXY;
    }

    private static Proxy getWindowsProxy() throws Exception {
        String[] cmd = {
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyEnable"
        };

        Process p = Runtime.getRuntime().exec(cmd);
        String output = readProcess(p);
        p.waitFor();

        if (!output.contains("0x1")) {
            return Proxy.NO_PROXY;
        }

        String[] cmd2 = {
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyServer"
        };

        Process p2 = Runtime.getRuntime().exec(cmd2);
        String out2 = readProcess(p2);
        p2.waitFor();

        String proxy = extractRegValue(out2);
        if (proxy == null) return Proxy.NO_PROXY;

        if (proxy.contains("=")) {
            String[] parts = proxy.split(";");
            for (String part : parts) {
                if (part.startsWith("http=") || part.contains(":")) {
                    String v = part.contains("=") ? part.split("=")[1] : part;

                    String[] hp = v.split(":");
                    if (hp.length == 2) {
                        return new Proxy(
                                Proxy.Type.HTTP,
                                new InetSocketAddress(hp[0], Integer.parseInt(hp[1]))
                        );
                    }
                }
            }
        }

        if (proxy.contains(":")) {
            String[] hp = proxy.split(":");
            return new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(hp[0], Integer.parseInt(hp[1]))
            );
        }

        return Proxy.NO_PROXY;
    }

    private static Proxy getMacProxy() throws Exception {
        String[] servicesCmd = {"networksetup", "-listallnetworkservices"};
        Process p = Runtime.getRuntime().exec(servicesCmd);
        String services = readProcess(p);
        p.waitFor();

        for (String service : services.split("\n")) {
            service = service.trim();
            if (service.isEmpty() || service.contains("*")) continue;

            String[] cmd = {"networksetup", "-getwebproxy", service};
            Process p2 = Runtime.getRuntime().exec(cmd);
            String out = readProcess(p2);
            p2.waitFor();

            if (!out.contains("Enabled: Yes")) continue;

            String host = null;
            Integer port = null;

            for (String line : out.split("\n")) {
                if (line.startsWith("Server:")) {
                    host = line.split(":")[1].trim();
                } else if (line.startsWith("Port:")) {
                    port = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            if (host != null && port != null) {
                return new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, port));
            }
        }

        return Proxy.NO_PROXY;
    }

    private static Proxy getLinuxProxy() {

        // GNOME proxy
        try {
            String mode = readProcess(Runtime.getRuntime().exec(
                    new String[]{"gsettings", "get", "org.gnome.system.proxy", "mode"}
            ));

            if (mode.contains("none")) {
                return null;
            }

            String host = readProcess(Runtime.getRuntime().exec(
                    new String[]{"gsettings", "get", "org.gnome.system.proxy.http", "host"}
            ));

            String portStr = readProcess(Runtime.getRuntime().exec(
                    new String[]{"gsettings", "get", "org.gnome.system.proxy.http", "port"}
            ));

            host = clean(host);
            portStr = clean(portStr);

            if (!host.isEmpty()) {
                return new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, Integer.parseInt(portStr)));
            }
        } catch (Exception ignored) {}

        // env fallback
        String env = System.getenv("http_proxy");
        if (env == null) env = System.getenv("HTTP_PROXY");

        if (env != null) {
            try {
                if (!env.contains("://")) {
                    env = "http://" + env;
                }

                URL url = new URL(env);
                return new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(url.getHost(), url.getPort()));
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static String readProcess(Process p) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static String extractRegValue(String regOutput) {
        for (String line : regOutput.split("\n")) {
            if (line.contains("REG_SZ")) {
                return line.substring(line.indexOf("REG_SZ") + 6).trim();
            }
        }
        return null;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("'", "").trim();
    }

    public static Proxy getProxy(){
        if(IamMusicPlayer.getConfig().autoProxy)return getSystemProxy();
        return IamMusicPlayer.getConfig().proxy.getProxy();
    }
}
