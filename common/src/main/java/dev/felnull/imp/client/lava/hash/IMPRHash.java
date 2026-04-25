package dev.felnull.imp.client.lava.hash;

import dev.felnull.fnjl.os.OSs;
import dev.felnull.fnjl.os.OSs.Type;
import dev.felnull.fnjl.util.FNDataUtil;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.LavaNativeManager;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import dev.felnull.imp.util.ProxyUtil;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IMPRHash {

  private static final Logger LOGGER = LogManager.getLogger(
    IMPRHash.class
  );

  public boolean FileIsValid() {
    Type os = OSs.getOS();
    String arch = OSs.getArch();
    if (os == Type.WINDOWS) {
      Boolean isvalid = Windows(os, arch);
      LOGGER.info("File integrity ok: {}", isvalid);
      return isvalid;
    }
    if (os == Type.LINUX) {
      Boolean isvalid = Linux(os, arch);
      LOGGER.info("File integrity ok: {}", isvalid);
      return isvalid;
    }
    if (os == Type.MAC) {
      Boolean isvalid = Mac(os);
      LOGGER.info("File integrity ok: {}", isvalid);
      return isvalid;
    } else {
      LOGGER.error("Your os is {}, which is unsupported", os);
      return false;
    }
  }

  private boolean Linux(Type os, String arch) {
    // Convert x64 to x86-64 if needed
    String normalizedArch = arch.equals("x64") ? "x86-64" : arch;

    String expectedHashRaw = HashUnix(
      os.toString(),
      normalizedArch,
      true
    );
    Path file_path = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "linux-" + normalizedArch,
      "libconnector.so"
    );
    LOGGER.info("Path to lava native is: {}", file_path);
    String actualHashRaw = "";

    if (Files.exists(file_path)) {
      actualHashRaw = calculateMD5Hash(file_path);
      LOGGER.info("File hash of lib is: {}", actualHashRaw);
    } else {
      LOGGER.error("Native library file not found at: {}", file_path);
      return false;
    }

    String expectedHash =
      expectedHashRaw != null ? expectedHashRaw.trim() : "";
    String actualHash = actualHashRaw.trim();

    LOGGER.info("Expected hash (lib): {}", expectedHash);
    LOGGER.info("Actual hash (lib):   {}", actualHash);

    if (!Objects.equals(expectedHash, actualHash)) {
      LOGGER.warn("Hash mismatch detected for Linux native library!");
      int minLength = Math.min(
        expectedHash.length(),
        actualHash.length()
      );
      for (int i = 0; i < minLength; i++) {
        if (expectedHash.charAt(i) != actualHash.charAt(i)) {
          LOGGER.info(
            "First difference at position {}: expected '{}' but got '{}'",
            i,
            expectedHash.charAt(i),
            actualHash.charAt(i)
          );
          break;
        }
      }
      if (expectedHash.length() != actualHash.length()) {
        LOGGER.info(
          "Length mismatch: expected {} but got {}",
          expectedHash.length(),
          actualHash.length()
        );
      }
    }

    return Objects.equals(expectedHash, actualHash);
  }

  private boolean Mac(Type os) {
    String expectedHashRaw = HashUnix("darwin", "", false);
    Path file_path = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "darwin",
      "libconnector.dylib"
    );
    LOGGER.info("Path to lava native is: {}", file_path);
    String actualHashRaw = "";

    if (Files.exists(file_path)) {
      actualHashRaw = calculateMD5Hash(file_path);
      LOGGER.info("File hash is: {}", actualHashRaw);
    } else {
      LOGGER.error("Native library file not found at: {}", file_path);
      return false;
    }

    String expectedHash =
      expectedHashRaw != null ? expectedHashRaw.trim() : "";
    String actualHash = actualHashRaw.trim();

    LOGGER.info("Expected hash (Mac): {}", expectedHash);
    LOGGER.info("Actual hash (Mac):   {}", actualHash);

    if (!Objects.equals(expectedHash, actualHash)) {
      LOGGER.warn("Hash mismatch detected for Mac native library!");
      int minLength = Math.min(
        expectedHash.length(),
        actualHash.length()
      );
      for (int i = 0; i < minLength; i++) {
        if (expectedHash.charAt(i) != actualHash.charAt(i)) {
          LOGGER.info(
            "First difference at position {}: expected '{}' but got '{}'",
            i,
            expectedHash.charAt(i),
            actualHash.charAt(i)
          );
          break;
        }
      }
      if (expectedHash.length() != actualHash.length()) {
        LOGGER.info(
          "Length mismatch: expected {} but got {}",
          expectedHash.length(),
          actualHash.length()
        );
      }
    }

    return Objects.equals(expectedHash, actualHash);
  }

  private boolean Windows(Type os, String arch) {
    // Convert x64 to x86-64 if needed
    String normalizedArch = arch.equals("x64") ? "x86-64" : arch;

    Path file_path_lib = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "win-" + normalizedArch,
      "libmpg123-0.dll"
    );
    Path file_path_connector = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "win-" + normalizedArch,
      "connector.dll"
    );
    String hash_lib_raw = HashWinLib(os.toString(), normalizedArch);
    String hash_connector_raw = HashWinConnector(
      os.toString(),
      normalizedArch
    );

    String file_hash_connector = "";
    String file_hash_lib = "";
    if (
      Files.exists(file_path_lib) && Files.exists(file_path_connector)
    ) {
      file_hash_lib = calculateMD5Hash(file_path_lib);
      file_hash_connector = calculateMD5Hash(file_path_connector);
      LOGGER.info("File hash of lib is: {}", file_hash_lib);
      LOGGER.info(
        "File hash of connector is: {}",
        file_hash_connector
      );
    } else {
      LOGGER.error(
        "Native library files not found at: {} or {}",
        file_path_lib,
        file_path_connector
      );
      return false;
    }

    String expectedHash_lib =
      hash_lib_raw != null ? hash_lib_raw.trim() : "";
    String actualHash_lib = file_hash_lib.trim();
    String expectedHash_connector =
      hash_connector_raw != null ? hash_connector_raw.trim() : "";
    String actualHash_connector = file_hash_connector.trim();

    LOGGER.info("Expected hash (lib): {}", expectedHash_lib);
    LOGGER.info("Actual hash (lib):   {}", actualHash_lib);
    LOGGER.info(
      "Expected hash (connector): {}",
      expectedHash_connector
    );
    LOGGER.info(
      "Actual hash (connector):   {}",
      actualHash_connector
    );

    boolean libMatch = Objects.equals(
      expectedHash_lib,
      actualHash_lib
    );
    boolean connectorMatch = Objects.equals(
      expectedHash_connector,
      actualHash_connector
    );

    if (!libMatch) {
      LOGGER.warn("Hash mismatch detected for Windows library file!");
      int minLength = Math.min(
        expectedHash_lib.length(),
        actualHash_lib.length()
      );
      for (int i = 0; i < minLength; i++) {
        if (expectedHash_lib.charAt(i) != actualHash_lib.charAt(i)) {
          LOGGER.info(
            "First difference at position {}: expected '{}' but got '{}'",
            i,
            expectedHash_lib.charAt(i),
            actualHash_lib.charAt(i)
          );
          break;
        }
      }
      if (expectedHash_lib.length() != actualHash_lib.length()) {
        LOGGER.info(
          "Length mismatch: expected {} but got {}",
          expectedHash_lib.length(),
          actualHash_lib.length()
        );
      }
    }

    if (!connectorMatch) {
      LOGGER.warn(
        "Hash mismatch detected for Windows connector file!"
      );
      int minLength = Math.min(
        expectedHash_connector.length(),
        actualHash_connector.length()
      );
      for (int i = 0; i < minLength; i++) {
        if (
          expectedHash_connector.charAt(i) !=
          actualHash_connector.charAt(i)
        ) {
          LOGGER.info(
            "First difference at position {}: expected '{}' but got '{}'",
            i,
            expectedHash_connector.charAt(i),
            actualHash_connector.charAt(i)
          );
          break;
        }
      }
      if (
        expectedHash_connector.length() !=
        actualHash_connector.length()
      ) {
        LOGGER.info(
          "Length mismatch: expected {} but got {}",
          expectedHash_connector.length(),
          actualHash_connector.length()
        );
      }
    }

    return libMatch && connectorMatch;
  }

  private String HashUnix(
    String os,
    String arch,
    Boolean archisneeded
  ) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    if (archisneeded) {
      String full_url =
        IamMusicPlayer.getConfig().hashBaseUrl.replaceFirst(
          "/*$",
          ""
        ) +
        "/" +
        hash_folder +
        "/" +
        snakeos +
        "-" +
        arch +
        ".hash";
      try {
        LOGGER.info("Downloading hash from: {}", full_url);
        return GetHash(full_url);
      } catch (Exception e) {
        LOGGER.fatal("Something Failed during hash download: {}", e);
        return null;
      }
    } else {
      String full_url =
        IamMusicPlayer.getConfig().hashBaseUrl.replaceFirst(
          "/*$",
          ""
        ) +
        "/" +
        hash_folder +
        "/" +
        snakeos +
        ".hash";
      try {
        LOGGER.info("Downloading hash from: {}", full_url);
        return GetHash(full_url);
      } catch (Exception e) {
        LOGGER.fatal("Something Failed during hash download: {}", e);
        return null;
      }
    }
  }

  private String GetHash(String url) throws Exception {
    try {
      HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.of((InetSocketAddress) ProxyUtil.getSystemProxy().address())).build();
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();
      HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      String responseBody = response.body();
      LOGGER.info("Downloaded hash from {}: {}", url, responseBody);
      return responseBody;
    } catch (Exception e) {
      LOGGER.fatal(
        "Something Failed during hash download from {}: {}",
        url,
        e
      );
      return null;
    }
  }

  private String calculateMD5Hash(Path path) {
    try {
      byte[] hash = FNDataUtil.createMD5Hash(
        Files.readAllBytes(path)
      );
      return new String(Hex.encodeHex(hash));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new UncheckedIOException(
        "Failed to calculate MD5 hash for " + path,
        new IOException(e)
      );
    }
  }

  private String HashWinLib(String os, String arch) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    String full_url_lib =
      IamMusicPlayer.getConfig().hashBaseUrl.replaceFirst("/*$", "") +
      "/" +
      hash_folder +
      "/" +
      snakeos +
      "-" +
      arch +
      "-lib.hash";
    try {
      LOGGER.info("Downloading hash from: {}", full_url_lib);
      String hash = GetHash(full_url_lib);
      LOGGER.info("Retrieved hash for lib: {}", hash);
      return hash;
    } catch (Exception e) {
      LOGGER.fatal("Something Failed during hash download: {}", e);
      return null;
    }
  }

  private String HashWinConnector(String os, String arch) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    String full_url_connector =
      IamMusicPlayer.getConfig().hashBaseUrl.replaceFirst("/*$", "") +
      "/" +
      hash_folder +
      "/" +
      snakeos +
      "-" +
      arch +
      "-connector.hash";
    try {
      LOGGER.info("Downloading hash from: {}", full_url_connector);
      String hash = GetHash(full_url_connector);
      LOGGER.info("Retrieved hash for connector: {}", hash);
      return hash;
    } catch (Exception e) {
      LOGGER.fatal("Something Failed during hash download: {}", e);
      return null;
    }
  }
}
