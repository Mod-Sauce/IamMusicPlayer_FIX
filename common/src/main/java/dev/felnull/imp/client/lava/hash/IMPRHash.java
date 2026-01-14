package dev.felnull.imp.client.lava.hash;

import dev.felnull.fnjl.os.OSs;
import dev.felnull.fnjl.os.OSs.Type;
import dev.felnull.fnjl.util.FNDataUtil;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.LavaNativeManager;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class IMPRHash {

  private static final Logger LOGGER = LogManager.getLogger(IMPRHash.class);

  public boolean FileIsValid() {
    Type os = OSs.getOS();
    String arch = OSs.getArch();
    if (os == Type.WINDOWS) {
      Boolean isvalid = Windows(os, arch);
      LOGGER.info("File intagity ok: " + isvalid.toString());
      return isvalid;
    }
    if (os == Type.LINUX) {
      Boolean isvalid = Linux(os, arch);
      LOGGER.info("File intagity ok: " + isvalid.toString());
      return isvalid;
    }
    if (os == Type.MAC) {
      Boolean isvalid = Mac(os);
      LOGGER.info("File intagity ok: " + isvalid.toString());
      return isvalid;
    } else {
      LOGGER.error("Your os is {}, with is unsupported", os);
      return false;
    }
  }

  private boolean Linux(@NotNull Type os, String arch) {
    // Convert x64 to x86-64 if needed
    String normalizedArch = arch.equals("x64") ? "x86-64" : arch;

    String hash = HashUnix(os.toString(), normalizedArch, true);
    Path file_path = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "linux-" + normalizedArch,
      "libconnector.so"
    );
    LOGGER.info("Path to lava native is: " + file_path.toString()); // iammusicplayerrenewed/lavaplayer_natives/linux-x86-64/libconnector.so
    String file_hash = "";

    if (Files.exists(file_path)) {
      file_hash = calculateMD5Hash(file_path);
      LOGGER.info("File hash is: " + file_hash);
    } else {
      LOGGER.info(
        "Something is fucked in line 59" +
          //
          //    ░▒▓████████▓▒░▒▓█▓▒░░▒▓█▓▒░░▒▓██████▓▒░░▒▓█▓▒░░▒▓█▓▒░
          //    ░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░
          //    ░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░
          //    ░▒▓██████▓▒░ ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░      ░▒▓███████▓▒░
          //    ░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░
          //    ░▒▓█▓▒░      ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░
          //    ░▒▓█▓▒░       ░▒▓██████▓▒░ ░▒▓██████▓▒░░▒▓█▓▒░░▒▓█▓▒░
          //
          Files.exists(file_path)
      );
      return false;
    }
    String expectedHash = hash.trim();
    String actualHash = file_hash.trim();

    LOGGER.debug("Expected hash: " + expectedHash);
    LOGGER.debug("Actual hash:   " + actualHash);

    if (!Objects.equals(expectedHash, actualHash)) {
      LOGGER.debug("Hash mismatch detected!");
      int minLength = Math.min(expectedHash.length(), actualHash.length());
      for (int i = 0; i < minLength; i++) {
        if (expectedHash.charAt(i) != actualHash.charAt(i)) {
          LOGGER.debug(
            "First difference at position " +
              i +
              ": expected '" +
              expectedHash.charAt(i) +
              "' but got '" +
              actualHash.charAt(i) +
              "'"
          );
          break;
        }
      }
      if (expectedHash.length() != actualHash.length()) {
        LOGGER.info(
          "Length mismatch: expected " +
            expectedHash.length() +
            " but got " +
            actualHash.length()
        );
      }
    }

    return Objects.equals(expectedHash, actualHash);
  }

  private boolean Mac(Type os) {
    String hash = HashUnix(os.toString(), "", false);
    Path file_path = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "darwin",
      "libconnector.dylib"
    );
    LOGGER.info("Path to lava native is: " + file_path.toString()); // iammusicplayerrenewed/lavaplayer_natives/linux-x86-64/libconnector.so
    String file_hash = "";

    if (Files.exists(file_path)) {
      file_hash = calculateMD5Hash(file_path);
      LOGGER.info("File hash is: " + file_hash);
    } else {
      LOGGER.fatal("File dosen't exist");
      return false;
    }
    String expectedHash = hash.trim();
    String actualHash = file_hash.trim();
    return Objects.equals(expectedHash, actualHash);
  }

  private boolean Windows(Type os, String arch) {
    // Convert x64 to x86-64 if needed
    String normalizedArch = arch.equals("x64") ? "x86-64" : arch;

    Path file_path_lib = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "windows-" + normalizedArch,
      "libmpg123-0.dll"
    );
    Path file_path_connector = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      "windows-" + normalizedArch,
      "connector.dll"
    );
    String hash_lib = HashWinLib(os.toString(), arch);
    String hash_connector = HashWinConnector(os.toString(), arch);

    String file_hash_connector = "";
    String file_hash_lib = "";
    if (Files.exists(file_path_lib) && Files.exists(file_path_connector)) {
      file_hash_lib = calculateMD5Hash(file_path_lib);
      LOGGER.info("File of hash lib is: " + file_hash_lib);
      LOGGER.info("File hash of connector is: " + file_hash_lib);
    } else {
      LOGGER.fatal("File dosen't exist");
      return false;
    }

    String expectedHash_lib = hash_lib.trim();
    String actualHash_lib = file_hash_lib.trim();
    String expectedHash_connector = hash_connector.trim();
    String actualHash_connector = file_hash_connector.trim();
    return (
      Objects.equals(expectedHash_lib, actualHash_lib) &&
      Objects.equals(expectedHash_connector, actualHash_connector)
    );
  }

  private String HashUnix(String os, String arch, Boolean archisneeded) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    if (archisneeded) {
      String full_url =
        IamMusicPlayer.getConfig().hashBaseUrl +
        hash_folder +
        "/" +
        snakeos +
        "-" +
        arch +
        ".hash";
      try {
        LOGGER.debug("Hash url: " + full_url);
        return GetHash(full_url);
      } catch (Exception e) {
        LOGGER.fatal("Something Failed during hash download: " + e);
        return null;
      }
    } else {
      String full_url =
        IamMusicPlayer.getConfig().hashBaseUrl +
        hash_folder +
        natives_version +
        "/" +
        snakeos +
        ".hash";
      try {
        return GetHash(full_url);
      } catch (Exception e) {
        LOGGER.fatal("Something Failed during hash download: " + e);
        return null;
      }
    }
  }

  private String GetHash(String url) throws Exception {
    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();
      HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      return response.body();
    } catch (Exception e) {
      LOGGER.fatal("Something Failed during hash download: " + e);
      return null;
    }
  }

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

  private String HashWinLib(String os, String arch) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    String full_url_lib =
      IamMusicPlayer.getConfig().hashBaseUrl +
      hash_folder +
      "/" +
      snakeos +
      "-" +
      arch +
      "-lib.hash";
    try {
      LOGGER.debug("Hash url for lib: " + full_url_lib);
      return GetHash(full_url_lib);
    } catch (Exception e) {
      LOGGER.fatal("Something Failed during hash download: " + e);
      return null;
    }
  }

  private String HashWinConnector(String os, String arch) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "hash-" + natives_version;
    String snakeos = os.toLowerCase();
    String full_url_connector =
      IamMusicPlayer.getConfig().hashBaseUrl +
      hash_folder +
      "/" +
      snakeos +
      "-" +
      arch +
      "-connector.hash";
    try {
      LOGGER.debug("Hash url for connector: " + full_url_connector);
      return GetHash(full_url_connector);
    } catch (Exception e) {
      LOGGER.fatal("Something Failed during hash download: " + e);
      return null;
    }
  }
}
