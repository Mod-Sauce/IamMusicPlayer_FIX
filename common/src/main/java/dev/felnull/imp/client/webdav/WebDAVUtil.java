package dev.felnull.imp.client.webdav;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.webdav.WebDAVSourceUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class WebDAVUtil {
    private static final Logger LOGGER = LogManager.getLogger(WebDAVUtil.class);

    public static List<MusicMediaResult> list(String path) {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        if (!config.enableWebDAV || config.baseUrl.isBlank()) return List.of();

        var normalizedPath = normalizeRelativePath(WebDAVSourceUtil.extractPath(path));
        var body = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:displayname/></d:prop></d:propfind>";

        try (var client = createClient()) {
            var targetUrl = joinUrl(config.baseUrl, buildDavPath(normalizedPath, true));
            LOGGER.info("[WebDAV] PROPFIND list path='{}' normalized='{}' baseUrl='{}' rootPath='{}' url='{}'", path, normalizedPath, config.baseUrl, config.rootPath, targetUrl);
            var request = new WebDAVPropfind(targetUrl);
            applyAuthHeader(request);
            request.setHeader(HttpHeaders.DEPTH, "1");
            request.setHeader(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8");
            request.setEntity(new org.apache.http.entity.StringEntity(body, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = client.execute(request)) {
                var status = response.getStatusLine().getStatusCode();
                LOGGER.info("[WebDAV] PROPFIND response status={} reason='{}' entity={}", status, response.getStatusLine().getReasonPhrase(), response.getEntity() != null);
                if (status >= HttpStatus.SC_BAD_REQUEST || response.getEntity() == null) return List.of();
                var xml = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                LOGGER.info("[WebDAV] PROPFIND response body: {}", xml.length() > 4000 ? xml.substring(0, 4000) + "..." : xml);
                var results = parseListResponse(xml, normalizedPath);
                LOGGER.info("[WebDAV] Parsed {} visible entries for currentPath='{}'", results.size(), normalizedPath);
                return results;
            }
        } catch (Exception e) {
            LOGGER.error("[WebDAV] Failed to list path='{}'", path, e);
            return List.of();
        }
    }

    public static WebDAVFileMetadata getFileMetadata(String path) {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        if (!config.enableWebDAV || config.baseUrl.isBlank()) return null;

        var normalizedPath = normalizeRelativePath(WebDAVSourceUtil.extractPath(path));
        try (var client = createClient()) {
            var request = new HttpHead(getFileUrl(normalizedPath));
            applyAuthHeader(request);
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() >= HttpStatus.SC_BAD_REQUEST) return null;
                long size = 0L;
                var lengthHeader = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
                if (lengthHeader != null) {
                    try {
                        size = Long.parseLong(lengthHeader.getValue());
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new WebDAVFileMetadata(normalizedPath, getDisplayName(normalizedPath), false, size, 0L);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Path downloadToTempFile(String path) throws Exception {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        if (!config.enableWebDAV || config.baseUrl.isBlank()) return null;

        var normalizedPath = normalizeRelativePath(WebDAVSourceUtil.extractPath(path));
        try (var client = createClient()) {
            var request = new org.apache.http.client.methods.HttpGet(getFileUrl(normalizedPath));
            applyAuthHeader(request);
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getEntity() == null || response.getStatusLine().getStatusCode() >= HttpStatus.SC_BAD_REQUEST) return null;
                Path dir = dev.felnull.imp.client.cache.AudioCacheManager.getBaseDir().resolve("webdav_direct");
                Files.createDirectories(dir);
                String ext = "";
                int idx = normalizedPath.lastIndexOf('.');
                if (idx >= 0) ext = normalizedPath.substring(idx);
                Path file = dir.resolve(UUID.randomUUID() + ext);
                try (InputStream in = response.getEntity().getContent()) {
                    Files.copy(in, file);
                }
                return file;
            }
        }
    }

    public static String getFileUrl(String path) {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        if (!config.enableWebDAV || config.baseUrl.isBlank()) return null;
        return joinUrl(config.baseUrl, buildDavPath(WebDAVSourceUtil.extractPath(path), false));
    }

    public static String getDisplayName(String path) {
        var normalizedPath = normalizeRelativePath(WebDAVSourceUtil.extractPath(path));
        if (normalizedPath.isEmpty()) return "/";
        var parts = normalizedPath.split("/");
        return parts[parts.length - 1];
    }

    public static String normalizeRelativePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) return "";
        var normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String buildDavPath(String relativePath, boolean directory) {
        var root = normalizeRelativePath(IamMusicPlayer.getConfig().webDAVConfig.rootPath);
        var path = normalizeRelativePath(relativePath);
        var combined = root.isEmpty() ? path : (path.isEmpty() ? root : root + "/" + path);
        if (combined.isEmpty()) return "/";
        return "/" + encodePath(combined) + (directory ? "/" : "");
    }

    private static String encodePath(String path) {
        var parts = path.split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }

    private static String joinUrl(String baseUrl, String path) {
        var normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }

    private static CloseableHttpClient createClient() {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        var provider = new BasicCredentialsProvider();
        if (!config.username.isBlank() || !config.password.isBlank()) {
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.username, config.password));
        }
        return HttpClients.custom().setDefaultCredentialsProvider(provider).build();
    }

    private static List<MusicMediaResult> parseListResponse(String xml, String currentPath) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        LOGGER.info("[WebDAV] parseListResponse currentPath='{}' baseUrlPath='{}' rootPath='{}' responseCount={}", currentPath, getBaseUrlPath(), normalizeRelativePath(IamMusicPlayer.getConfig().webDAVConfig.rootPath), document.getElementsByTagNameNS("DAV:", "response").getLength());
        NodeList responses = document.getElementsByTagNameNS("DAV:", "response");
        List<WebDAVFileMetadata> items = new ArrayList<>();

        for (int i = 0; i < responses.getLength(); i++) {
            Node node = responses.item(i);
            if (!(node instanceof Element responseElement)) continue;
            var href = getFirstText(responseElement, "DAV:", "href");
            var prop = getFirstElement(responseElement, "DAV:", "prop");
            if (href == null || prop == null) continue;

            var decodedHrefPath = decodeHrefPath(href);
            var relativePath = toRelativePath(decodedHrefPath);
            LOGGER.info("[WebDAV] href='{}' decoded='{}' -> relative='{}'", href, decodedHrefPath, relativePath);
            if (relativePath == null) {
                LOGGER.info("[WebDAV] Skipping href because it is outside configured root/base path");
                continue;
            }
            if (relativePath.equals(normalizeRelativePath(currentPath))) {
                LOGGER.info("[WebDAV] Skipping href because it resolves to the current directory");
                continue;
            }

            var displayName = getFirstText(prop, "DAV:", "displayname");
            if (displayName == null || displayName.isBlank()) displayName = getDisplayName(relativePath);

            items.add(new WebDAVFileMetadata(
                    relativePath,
                    displayName,
                    hasCollection(prop),
                    parseLong(getFirstText(prop, "DAV:", "getcontentlength")),
                    parseDate(getFirstText(prop, "DAV:", "getlastmodified"))
            ));
        }

        items.sort(Comparator.comparing(WebDAVFileMetadata::directory).reversed().thenComparing(metadata -> metadata.name().toLowerCase(Locale.ROOT)));
        List<MusicMediaResult> results = new ArrayList<>();
        var parent = getParentPath(currentPath);
        if (parent != null) {
            results.add(new MusicMediaResult(new MusicSource("webdav", parent, 0), ImageInfo.EMPTY, "..", "Parent Folder", true));
        }

        for (WebDAVFileMetadata item : items) {
            if (item.directory()) {
                LOGGER.info("[WebDAV] Visible directory path='{}' name='{}'", item.path(), item.name());
                results.add(new MusicMediaResult(new MusicSource("webdav", item.path(), 0), ImageInfo.EMPTY, item.name(), "Folder", true));
            } else if (isSupportedAudioFile(item.path())) {
                LOGGER.info("[WebDAV] Visible audio file path='{}' name='{}'", item.path(), item.name());
                results.add(new MusicMediaResult(new MusicSource("webdav", item.path(), item.size()), ImageInfo.EMPTY, item.name(), "WebDAV", false));
            } else {
                LOGGER.info("[WebDAV] Ignoring non-audio file path='{}' name='{}'", item.path(), item.name());
            }
        }
        return results;
    }

    private static String toRelativePath(String fullPath) {
        var normalized = normalizeRelativePath(fullPath);
        var basePath = getBaseUrlPath();
        if (!basePath.isEmpty()) {
            if (normalized.equals(basePath)) normalized = "";
            else if (normalized.startsWith(basePath + "/")) normalized = normalized.substring(basePath.length() + 1);
        }

        var root = normalizeRelativePath(IamMusicPlayer.getConfig().webDAVConfig.rootPath);
        if (root.isEmpty()) return normalized;
        if (normalized.equals(root)) return "";
        if (!normalized.startsWith(root + "/")) return null;
        return normalized.substring(root.length() + 1);
    }

    private static String decodeHrefPath(String href) {
        try {
            var uri = URI.create(href);
            var path = uri.getPath();
            return path != null ? URLDecoder.decode(path, StandardCharsets.UTF_8) : href;
        } catch (Exception ignored) {
            return href;
        }
    }

    private static String getBaseUrlPath() {
        var baseUrl = IamMusicPlayer.getConfig().webDAVConfig.baseUrl;
        if (baseUrl == null || baseUrl.isBlank()) return "";
        try {
            var path = URI.create(baseUrl).getPath();
            return normalizeRelativePath(path);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void applyAuthHeader(org.apache.http.client.methods.HttpRequestBase request) {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        if (config.username.isBlank() && config.password.isBlank()) return;
        var token = Base64.getEncoder().encodeToString((config.username + ":" + config.password).getBytes(StandardCharsets.UTF_8));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
    }

    private static boolean hasCollection(Element prop) {
        var resourceType = getFirstElement(prop, "DAV:", "resourcetype");
        return resourceType != null && getFirstElement(resourceType, "DAV:", "collection") != null;
    }

    private static Element getFirstElement(Element parent, String namespace, String name) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, name);
        if (nodes.getLength() == 0) return null;
        var node = nodes.item(0);
        return node instanceof Element element ? element : null;
    }

    private static String getFirstText(Element parent, String namespace, String name) {
        var element = getFirstElement(parent, namespace, name);
        return element != null ? element.getTextContent() : null;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static long parseDate(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String getParentPath(String currentPath) {
        var normalized = normalizeRelativePath(currentPath);
        if (normalized.isEmpty()) return null;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
    }

    private static boolean isSupportedAudioFile(String path) {
        var lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".opus") || lower.endsWith(".mp4");
    }

    private static class WebDAVPropfind extends org.apache.http.client.methods.HttpEntityEnclosingRequestBase {
        public WebDAVPropfind(String uri) {
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return "PROPFIND";
        }
    }

    public record WebDAVFileMetadata(String path, String name, boolean directory, long size, long modified) {
    }
}
