package dev.felnull.imp.client.lava;

import dev.lavalink.youtube.clients.Web;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class YoutubeRemoteConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("IamMusicPlayer/YouTubeRemote");

    private static final String DEFAULT_CIPHER_URL = "https://cipher.kikkia.dev/";
    private static final String USER_AGENT = "iammusicplayer-modpack/1.0";

    private static final String DEFAULT_PROPERTIES = """
            # IamMusicPlayer YouTube remote configuration
            #
            # Remote cipher service decodes YouTube's signature on a server so this
            # mod does not need to keep up with YouTube player JS changes. The public
            # instance below is free and rate limited to 10 req/s, which is plenty for
            # a single modpack. To self-host, run kikkia/yt-cipher in docker and set
            # cipher.url to your instance plus cipher.password to your API_TOKEN.
            cipher.url=https://cipher.kikkia.dev/
            cipher.password=

            # PoToken + VisitorData bypass YouTube's "Sign in to confirm you're not a bot"
            # check on the Web client. Without these, only the Android-family clients
            # work. Generate a pair ONCE with:
            #
            #   npx youtube-po-token-generator
            #
            # Paste the resulting values below. The pair is IP-region bound (works for
            # everyone on roughly the same continent) and typically lasts days to weeks.
            po.token=
            po.visitorData=
            """;

    public String cipherUrl = DEFAULT_CIPHER_URL;
    public String cipherPassword;
    public String poToken;
    public String visitorData;

    public static YoutubeRemoteConfig load() {
        YoutubeRemoteConfig config = new YoutubeRemoteConfig();
        Path configFile = resolveConfigFile();

        if (!Files.exists(configFile)) {
            writeDefault(configFile);
            LOGGER.info("Wrote default YouTube remote config to {}", configFile);
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to read YouTube remote config at {}, using built-in defaults", configFile, ex);
            return config;
        }

        config.cipherUrl = trimToNullOr(properties.getProperty("cipher.url"), DEFAULT_CIPHER_URL);
        config.cipherPassword = trimToNull(properties.getProperty("cipher.password"));
        config.poToken = trimToNull(properties.getProperty("po.token"));
        config.visitorData = trimToNull(properties.getProperty("po.visitorData"));
        return config;
    }

    public void applyClientPoToken() {
        if (poToken != null && visitorData != null) {
            Web.setPoTokenAndVisitorData(poToken, visitorData);
            LOGGER.info("Applied PoToken + VisitorData to YouTube Web client");
        } else {
            LOGGER.info("No PoToken / VisitorData configured. Web / MWeb clients will hit bot detection until you generate a pair with `npx youtube-po-token-generator` and add po.token + po.visitorData to {}", resolveConfigFile());
        }
    }

    public String getUserAgent() {
        return USER_AGENT;
    }

    private static Path resolveConfigFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("iammusicplayer")
                .resolve("youtube_remote.properties");
    }

    private static void writeDefault(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, DEFAULT_PROPERTIES);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to create default YouTube remote config at " + configFile, ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNullOr(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }
}
