package dev.felnull.imp.client.lava;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.hash.IMPRHash;
import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manager for handling LavaPlayer native library downloads and loading
 */
public class LavaNativeManager {

    private static final Logger LOGGER = LogManager.getLogger(
            LavaNativeManager.class
    );
    private static final Gson GSON = new Gson();
    private static final LavaNativeManager INSTANCE = new LavaNativeManager();
    public static final String NATIVES_VERSION = "2.2.6";
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    private static final int DOWNLOAD_RETRY_COUNT = 3;
    private static final long DOWNLOAD_RETRY_DELAY_MS = 1000;

    // Executor for background downloads
    private final ExecutorService downloadExecutor =
            Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder()
                            .setNameFormat("lava-natives-downloader-%d")
                            .setDaemon(true)
                            .build()
            );

    // In-progress downloads to prevent duplicate download attempts
    private final Map<String, CompletableFuture<Boolean>> activeDownloads =
            new ConcurrentHashMap<>();

    private LavaNativeManager() {
        // Private constructor for singleton
    }

    public static LavaNativeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Loads the native library for the specified OS/architecture
     *
     * @param osAndArch OS and architecture identifier (e.g. "windows-x86-64")
     * @param name Name of the native library file to load
     * @return true if library is available, false otherwise
     */
    public boolean load(String osAndArch, String name) {
        Path nativesDir = LavaPlayerLoader.getNaiveLibraryFolder().resolve(
                osAndArch
        );
        File nativesDirFile = nativesDir.toFile();
        Path nativeLibPath = nativesDir.resolve(name);

        // Check if natives directory exists and is valid
        if (!isValidNativesDirectory(nativesDirFile)) {
            try {
                LOGGER.info(
                        "LavaPlayer natives for {} need to be downloaded",
                        osAndArch
                );
                boolean success = downloadNatives(osAndArch).join();
                if (!success) {
                    LOGGER.error(
                            "LavaPlayer natives download failed for {}",
                            osAndArch
                    );
                    return false;
                }
                LOGGER.info(
                        "LavaPlayer natives download successful for {}",
                        osAndArch
                );
            } catch (Exception e) {
                LOGGER.error(
                        "LavaPlayer natives download failed for {}",
                        osAndArch,
                        e
                );
                return false;
            }
        }

        LOGGER.info("LavaPlayer native({}) check successful", name);
        return Files.exists(nativeLibPath);
    }

    /**
     * Downloads native libraries for specified OS and architecture
     *
     * @param osAndArch OS and architecture identifier
     * @return CompletableFuture that resolves to true if download succeeded
     */
    public CompletableFuture<Boolean> downloadNatives(String osAndArch) {
        // Return existing download if one is in progress for this OS/arch
        return activeDownloads.computeIfAbsent(osAndArch, key ->
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return downloadAndExtractNatives(osAndArch);
                            } catch (Exception e) {
                                LOGGER.error(
                                        "Failed to download natives for {}",
                                        osAndArch,
                                        e
                                );
                                return false;
                            } finally {
                                activeDownloads.remove(osAndArch);
                            }
                        },
                        downloadExecutor
                )
        );
    }

    /**
     * Performs the actual download and extraction of native libraries
     *
     * @param osAndArch OS and architecture identifier
     * @return true if download and extraction succeeded
     */
    private boolean downloadAndExtractNatives(String osAndArch)
            throws Exception {
        Path nativesDir = Paths.get(
                IamMusicPlayer.getConfig().IMPRFolder,
                IamMusicPlayer.getConfig().lavaNativesFolder,
                osAndArch
        );
        File nativesDirFile = nativesDir.toFile();

        // Create natives directory if it doesn't exist
        if (!nativesDirFile.exists() && !nativesDirFile.mkdirs()) {
            throw new IOException(
                    "Failed to create the directory for native libraries: " +
                            nativesDir
            );
        }

        // Download natives manifest
        JsonObject manifestJson = downloadManifest();

        // Verify version exists in manifest
        if (
                !manifestJson.has(NATIVES_VERSION) ||
                        !manifestJson.get(NATIVES_VERSION).isJsonObject()
        ) {
            throw new IllegalStateException(
                    "Native library version " +
                            NATIVES_VERSION +
                            " not found in manifest. Available versions: " +
                            manifestJson.keySet()
            );
        }

        JsonObject versionJson = manifestJson.getAsJsonObject(NATIVES_VERSION);

        // Verify OS/arch exists in manifest version
        if (
                !versionJson.has(osAndArch) ||
                        !versionJson.get(osAndArch).isJsonObject()
        ) {
            throw new IllegalStateException(
                    "Unsupported OS or architecture: " +
                            osAndArch +
                            ". Available platforms: " +
                            versionJson.keySet()
            );
        }

        JsonObject platformJson = versionJson.getAsJsonObject(osAndArch);

        // Verify required fields exist
        if (!platformJson.has("hash")) {
            throw new IllegalStateException(
                    "Hash value not found for " + osAndArch
            );
        }

        if (!platformJson.has("url")) {
            throw new IllegalStateException(
                    "Download URL not found for " + osAndArch
            );
        }

        // Download and extract natives archive
        String downloadUrl = platformJson.get("url").getAsString();
        LOGGER.info("Downloading natives from: {}", downloadUrl);
        Path tempFile = downloadWithRetry(
                new URI(downloadUrl).toURL(),
                nativesDir
        );

        try {
            extractNatives(tempFile, nativesDir);
        } finally {
            // Clean up temp file
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                LOGGER.warn(
                        "Failed to delete temporary download file: {}",
                        tempFile,
                        e
                );
            }
        }

        // Verify integrity of extracted files
        if (!validateNativesIntegrity(nativesDirFile)) {
            // If validation fails, clean up the directory to force a fresh download next time
            try {
                deleteDirectoryContents(nativesDirFile);
            } catch (IOException e) {
                LOGGER.warn(
                        "Failed to clean up invalid natives directory: {}",
                        nativesDir,
                        e
                );
            }
            throw new IllegalStateException(
                    "Native library integrity check failed for " + osAndArch
            );
        }

        return true;
    }

    /**
     * Downloads the natives manifest from the configured URL
     *
     * @return JsonObject containing the natives manifest
     */
    private JsonObject downloadManifest() throws Exception {
        String manifestUrlString =
                IamMusicPlayer.getConfig().lavaPlayerNativesURL;
        LOGGER.info("Downloading natives manifest from: {}", manifestUrlString);

        URL manifestUrl = new URI(manifestUrlString).toURL();

        HttpURLConnection connection =
                (HttpURLConnection) manifestUrl.openConnection(NetMusicUtil.getSystemProxy());
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "IamMusicPlayer");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException(
                    "Failed to download manifest. HTTP response code: " +
                            responseCode
            );
        }

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                )
        ) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    /**
     * Downloads a file from URL with retry logic
     *
     * @param url URL to download from
     * @param targetDir Directory to save the downloaded file
     * @return Path to the downloaded temporary file
     */
    private Path downloadWithRetry(URL url, Path targetDir) throws Exception {
        Path tempFile = targetDir.resolve(
                "natives_download_" + System.currentTimeMillis() + ".tmp"
        );

        Exception lastException = null;
        for (int attempt = 1; attempt <= DOWNLOAD_RETRY_COUNT; attempt++) {
            if (attempt > 1) {
                LOGGER.info(
                        "Retrying download from {} (attempt {}/{})",
                        url,
                        attempt,
                        DOWNLOAD_RETRY_COUNT
                );
                Thread.sleep(DOWNLOAD_RETRY_DELAY_MS);
            }

            try {
                downloadFile(url, tempFile);
                LOGGER.info("Download completed successfully from: {}", url);
                return tempFile;
            } catch (IOException e) {
                lastException = e;
                LOGGER.warn(
                        "Download attempt {} failed: {}",
                        attempt,
                        e.getMessage()
                );
                // Delete partial download
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException deleteEx) {
                    LOGGER.info(
                            "Failed to delete partial download for URL: {}",
                            url,
                            deleteEx
                    );
                }
            }
        }

        throw new IOException(
                "Failed to download after " + DOWNLOAD_RETRY_COUNT + " attempts",
                lastException
        );
    }

    /**
     * Downloads a file from URL using NIO channels for better performance
     *
     * @param url URL to download from
     * @param destination Path to save the file to
     */
    private void downloadFile(URL url, Path destination) throws IOException {
        LOGGER.info("Opening connection to: {}", url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(NetMusicUtil.getSystemProxy());
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "IamMusicPlayer");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException(
                    "Failed to download file. HTTP response code: " +
                            responseCode +
                            " for URL: " +
                            url
            );
        }

        try (
                ReadableByteChannel readChannel = Channels.newChannel(
                        connection.getInputStream()
                );
                FileChannel writeChannel = FileChannel.open(
                        destination,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                )
        ) {
            long fileSize = connection.getContentLengthLong();
            LOGGER.info(
                    "Starting transfer from {} ({} bytes)",
                    url,
                    fileSize > 0 ? String.format("%,d", fileSize) : "unknown size"
            );

            // Transfer in chunks
            long position = 0;
            long bytesTransferred;
            long chunkSize = 1024 * 1024; // 1MB chunks

            while (
                    (bytesTransferred = writeChannel.transferFrom(
                            readChannel,
                            position,
                            chunkSize
                    )) >
                            0
            ) {
                position += bytesTransferred;
                if (fileSize > 0) {
                    int progress = (int) ((position * 100) / fileSize);
                    if (progress % 10 == 0) {
                        LOGGER.info(
                                "Download progress for {}: {}%",
                                url,
                                progress
                        );
                    }
                }
            }

            LOGGER.info("Downloaded {} bytes", position);
        }
    }

    /**
     * Extracts a zip file to the specified directory
     *
     * @param zipFile Path to the zip file
     * @param targetDir Directory to extract to
     */
    private void extractNatives(Path zipFile, Path targetDir)
            throws IOException {
        LOGGER.info("Extracting natives from {} to {}", zipFile, targetDir);

        try (
                InputStream fileStream = Files.newInputStream(zipFile);
                BufferedInputStream bufferedStream = new BufferedInputStream(
                        fileStream
                );
                ZipInputStream zipStream = new ZipInputStream(bufferedStream)
        ) {
            ZipEntry entry;
            int fileCount = 0;

            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                // Skip macOS metadata files
                if (
                        entryName.contains("__MACOSX") || entryName.startsWith("._")
                ) {
                    continue;
                }

                Path entryPath = targetDir.resolve(entryName);
                LOGGER.info("Extracting: {}", entryName);

                // Create parent directories
                Files.createDirectories(entryPath.getParent());

                // Copy entry to file
                try (
                        OutputStream os = Files.newOutputStream(
                                entryPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        )
                ) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = zipStream.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    fileCount++;
                }

                // Set executable flag for libraries on Unix-like systems
                String fileName = entryPath.getFileName().toString();
                if (
                        isUnixSystem() &&
                                (fileName.endsWith(".so") || fileName.endsWith(".dylib"))
                ) {
                    entryPath.toFile().setExecutable(true, false);
                    LOGGER.info("Set executable flag for: {}", fileName);
                }

                zipStream.closeEntry();
            }

            LOGGER.info("Extracted {} files", fileCount);

            if (fileCount == 0) {
                throw new IOException(
                        "No files were extracted from the archive"
                );
            }
        }
    }

    /**
     * Checks if natives directory is valid and contains all required files
     *
     * @param directory Directory to check
     * @return true if directory is valid
     */
    private boolean isValidNativesDirectory(File directory) {
        // Check if directory exists and is a directory
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }

        // Validate contents against hash
        return validateNativesIntegrity(directory);
    }

    /**
     * Validates integrity of extracted native files against hash
     *
     * @param directory Directory containing native files
     * @return true if all files match their hashes
     */
    private boolean validateNativesIntegrity(File directory) {
        try {
            LOGGER.info(
                    "Validating native libraries integrity in {}",
                    directory
            );

            // 1. List files in the directory
            File[] files = directory.listFiles();
            if (files == null) {
                LOGGER.error(
                        "Directory does not exist or cannot be read: {}",
                        directory
                );
                return false;
            }

            // 2. Filter relevant files (ignore hidden/system files)
            List<File> relevantFiles = Arrays.stream(files)
                    .filter(f -> !f.isHidden())
                    .filter(f -> !f.getName().equalsIgnoreCase("Thumbs.db"))
                    .filter(f -> !f.getName().startsWith("."))
                    .collect(Collectors.toList());

            List<File> nativeLibs = filterNativeLibrariesForCurrentOS(
                    relevantFiles
            );

            if (nativeLibs.isEmpty()) {
                LOGGER.error("No native library files found in {}", directory);
                return false;
            }

            LOGGER.info(
                    "Found {} native library file(s) to validate",
                    nativeLibs.size()
            );

            // Validate hashes
            IMPRHash imprhash = new IMPRHash();
            return imprhash.FileIsValid();
        } catch (Exception e) {
            LOGGER.error(
                    "Unexpected error during native libraries integrity check: {}",
                    e.getMessage(),
                    e
            );
            return false;
        }
    }

    /**
     * Filters the list of files to only include native libraries for the current OS
     *
     * @param files List of files to filter
     * @return List of native library files for the current OS
     */
    private List<File> filterNativeLibrariesForCurrentOS(List<File> files) {
        String os = System.getProperty("os.name").toLowerCase();

        return files
                .stream()
                .filter(f -> {
                    String name = f.getName();
                    if (os.contains("linux") && name.endsWith(".so")) return true;
                    if (os.contains("mac") && name.endsWith(".dylib")) return true;
                    if (os.contains("win") && name.endsWith(".dll")) return true;
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Deletes all contents of a directory without deleting the directory itself
     *
     * @param directory Directory to clear
     */
    private void deleteDirectoryContents(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectoryContents(file);
                if (!file.delete()) {
                    LOGGER.warn("Failed to delete directory: {}", file);
                }
            } else {
                if (!file.delete()) {
                    LOGGER.warn("Failed to delete file: {}", file);
                }
            }
        }
    }

    /**
     * Checks if running on a Unix-like system
     *
     * @return true if running on Linux, macOS, etc.
     */
    private boolean isUnixSystem() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("mac");
    }

    /**
     * Shuts down the download executor service
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            downloadExecutor.shutdownNow();
        }
    }
}
