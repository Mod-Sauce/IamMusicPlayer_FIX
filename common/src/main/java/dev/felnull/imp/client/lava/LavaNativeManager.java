package dev.felnull.imp.client.lava;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.felnull.fnjl.util.FNDataUtil;
import dev.felnull.imp.IamMusicPlayer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.codec.binary.Hex;
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
  private static final String NATIVES_VERSION = "2.2.3"; // Changed to 2.2.3 to match common version
  private static final String HASH_FILE_NAME = "hash.json";
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
          LOGGER.error("LavaPlayer natives download failed for {}", osAndArch);
          return false;
        }
        LOGGER.info("LavaPlayer natives download successful for {}", osAndArch);
      } catch (Exception e) {
        LOGGER.error("LavaPlayer natives download failed for {}", osAndArch, e);
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
            LOGGER.error("Failed to download natives for {}", osAndArch, e);
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
  private boolean downloadAndExtractNatives(String osAndArch) throws Exception {
    Path nativesDir = LavaPlayerLoader.getNaiveLibraryFolder().resolve(
      osAndArch
    );
    File nativesDirFile = nativesDir.toFile();

    // Create natives directory if it doesn't exist
    if (!nativesDirFile.exists() && !nativesDirFile.mkdirs()) {
      throw new IOException(
        "Failed to create the directory for native libraries: " + nativesDir
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
        "Native library version " + NATIVES_VERSION + " not found in manifest. Available versions: " + manifestJson.keySet()
      );
    }

    JsonObject versionJson = manifestJson.getAsJsonObject(NATIVES_VERSION);

    // Verify OS/arch exists in manifest version
    if (
      !versionJson.has(osAndArch) || !versionJson.get(osAndArch).isJsonObject()
    ) {
      throw new IllegalStateException(
        "Unsupported OS or architecture: " + osAndArch + ". Available platforms: " + versionJson.keySet()
      );
    }

    JsonObject platformJson = versionJson.getAsJsonObject(osAndArch);

    // Verify required fields exist
    if (!platformJson.has("hash")) {
      throw new IllegalStateException("Hash value not found for " + osAndArch);
    }

    if (!platformJson.has("url")) {
      throw new IllegalStateException(
        "Download URL not found for " + osAndArch
      );
    }

    // Create hash.json file
    JsonObject hashJson = new JsonObject();
    hashJson.add("hash", platformJson.get("hash"));
    Files.writeString(
      nativesDir.resolve(HASH_FILE_NAME),
      GSON.toJson(hashJson)
    );

    // Download and extract natives archive
    String downloadUrl = platformJson.get("url").getAsString();
    LOGGER.info("Downloading natives from: {}", downloadUrl);
    Path tempFile = downloadWithRetry(new URI(downloadUrl).toURL(), nativesDir);

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
    String manifestUrlString = IamMusicPlayer.getConfig().lavaPlayerNativesURL;
    LOGGER.info("Downloading natives manifest from: {}", manifestUrlString);
    
    URL manifestUrl = new URI(manifestUrlString).toURL();

    HttpURLConnection connection =
      (HttpURLConnection) manifestUrl.openConnection();
    connection.setConnectTimeout(CONNECTION_TIMEOUT);
    connection.setReadTimeout(READ_TIMEOUT);
    connection.setRequestProperty("User-Agent", "IamMusicPlayer");

    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Failed to download manifest. HTTP response code: " + responseCode);
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
          "Retrying download (attempt {}/{})",
          attempt,
          DOWNLOAD_RETRY_COUNT
        );
        Thread.sleep(DOWNLOAD_RETRY_DELAY_MS);
      }

      try {
        downloadFile(url, tempFile);
        LOGGER.info("Download completed successfully");
        return tempFile;
      } catch (IOException e) {
        lastException = e;
        LOGGER.warn("Download attempt {} failed: {}", attempt, e.getMessage());
        // Delete partial download
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException deleteEx) {
          LOGGER.debug("Failed to delete partial download", deleteEx);
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
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(CONNECTION_TIMEOUT);
    connection.setReadTimeout(READ_TIMEOUT);
    connection.setRequestProperty("User-Agent", "IamMusicPlayer");
    
    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Failed to download file. HTTP response code: " + responseCode + " for URL: " + url);
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
        "Downloading {} ({} bytes)",
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
            LOGGER.debug("Download progress: {}%", progress);
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
  private void extractNatives(Path zipFile, Path targetDir) throws IOException {
    LOGGER.info("Extracting natives from {} to {}", zipFile, targetDir);

    try (
      InputStream fileStream = Files.newInputStream(zipFile);
      BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
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
        if (entryName.contains("__MACOSX") || entryName.startsWith("._")) {
          continue;
        }

        Path entryPath = targetDir.resolve(entryName);
        LOGGER.debug("Extracting: {}", entryName);

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
          LOGGER.debug("Set executable flag for: {}", fileName);
        }
        
        zipStream.closeEntry();
      }
      
      LOGGER.info("Extracted {} files", fileCount);
      
      if (fileCount == 0) {
        throw new IOException("No files were extracted from the archive");
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
      LOGGER.debug("Validating native libraries integrity in {}", directory);

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

      // 3. Find hash.json
      Optional<File> hashFile = relevantFiles
        .stream()
        .filter(f -> f.getName().equals(HASH_FILE_NAME))
        .findAny();

      if (hashFile.isEmpty()) {
        LOGGER.error("Missing {} in directory: {}", HASH_FILE_NAME, directory);
        return false;
      }

      // 4. Parse hash.json
      JsonObject hashJson;
      try (
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(new FileInputStream(hashFile.get()))
        )
      ) {
        hashJson = GSON.fromJson(reader, JsonObject.class);
      }

      // 5. Verify hash.json format
      if (!hashJson.has("hash")) {
        LOGGER.error("{} missing required field 'hash'", HASH_FILE_NAME);
        return false;
      }

      // 6. Remove hash.json from the list for validation
      relevantFiles.remove(hashFile.get());

      // 7. Filter native libraries based on current OS
      List<File> nativeLibs = filterNativeLibrariesForCurrentOS(relevantFiles);

      if (nativeLibs.isEmpty()) {
        LOGGER.error("No native library files found in {}", directory);
        return false;
      }

      LOGGER.debug("Found {} native library file(s) to validate", nativeLibs.size());

      // 8. Validate hashes
      JsonElement hashElement = hashJson.get("hash");
      return validateHashes(hashElement, nativeLibs);
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
   * Validates native libraries against provided hash
   *
   * @param hashElement JsonElement containing hash information
   * @param nativeLibs List of native library files
   * @return true if all hashes match
   */
  private boolean validateHashes(
    JsonElement hashElement,
    List<File> nativeLibs
  ) {
    // Case 1: Single hash string for a single file
    if (hashElement.isJsonPrimitive()) {
      if (nativeLibs.isEmpty()) {
        LOGGER.error("No native library files found to validate against hash");
        return false;
      }

      if (nativeLibs.size() > 1) {
        LOGGER.warn(
          "Primitive hash provided but {} files found (expected 1). Validating first file only.",
          nativeLibs.size()
        );
      }

      File targetFile = nativeLibs.get(0);
      String expectedHash = hashElement.getAsString();
      String actualHash = calculateMD5Hash(targetFile.toPath());

      if (!expectedHash.equals(actualHash)) {
        LOGGER.error(
          "Hash mismatch for {}: expected {}, got {}",
          targetFile.getName(),
          expectedHash,
          actualHash
        );
        return false;
      }

      LOGGER.debug("Hash validated successfully for {}", targetFile.getName());
      return true;
    }

    // Case 2: Object with hashes for multiple files
    if (hashElement.isJsonObject()) {
      JsonObject hashesObject = hashElement.getAsJsonObject();

      // Create a map of filenames to files
      Map<String, File> filesByName = nativeLibs
        .stream()
        .collect(Collectors.toMap(File::getName, f -> f));

      // Validate each hash entry
      for (Map.Entry<String, JsonElement> entry : hashesObject.entrySet()) {
        String filename = entry.getKey();
        String expectedHash = entry.getValue().getAsString();

        File file = filesByName.get(filename);
        if (file == null) {
          LOGGER.error("Expected file {} not found", filename);
          return false;
        }

        String actualHash = calculateMD5Hash(file.toPath());
        if (!expectedHash.equals(actualHash)) {
          LOGGER.error(
            "Hash mismatch for {}: expected {}, got {}",
            filename,
            expectedHash,
            actualHash
          );
          return false;
        }
        
        LOGGER.debug("Hash validated successfully for {}", filename);
      }

      // Check for extra files not in hash object
      Set<String> expectedFiles = new HashSet<>(hashesObject.keySet());
      Set<String> actualFiles = new HashSet<>(filesByName.keySet());

      if (!expectedFiles.equals(actualFiles)) {
        Set<String> extraFiles = new HashSet<>(actualFiles);
        extraFiles.removeAll(expectedFiles);

        if (!extraFiles.isEmpty()) {
          LOGGER.warn("Found extra files not in hash.json: {}", extraFiles);
        }

        Set<String> missingFiles = new HashSet<>(expectedFiles);
        missingFiles.removeAll(actualFiles);

        if (!missingFiles.isEmpty()) {
          LOGGER.error(
            "Missing files that should be present according to hash.json: {}",
            missingFiles
          );
          return false;
        }
      }

      LOGGER.debug("All hashes validated successfully");
      return true;
    }

    // Invalid hash format
    LOGGER.error(
      "Invalid 'hash' format in {} (expected string or object). Found: {}",
      HASH_FILE_NAME,
      hashElement
    );
    return false;
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
   * Calculates MD5 hash of a file
   *
   * @param path Path to the file
   * @return MD5 hash as a hex string
   */
  private String calculateMD5Hash(Path path) {
    try {
      byte[] hash = FNDataUtil.createMD5Hash(Files.readAllBytes(path));
      return new String(Hex.encodeHex(hash));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new UncheckedIOException(
        "Failed to calculate MD5 hash for " + path,
        new IOException(e)
      );
    }
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
