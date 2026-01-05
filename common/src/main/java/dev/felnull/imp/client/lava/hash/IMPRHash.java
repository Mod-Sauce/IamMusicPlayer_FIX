package dev.felnull.imp.client.lava.hash;

import dev.felnull.fnjl.os.OSs;
import dev.felnull.fnjl.os.OSs.Type;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.client.lava.LavaNativeManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class IMPRHash {

  private static final Logger LOGGER = LogManager.getLogger(IMPRHash.class);

  public boolean FileIsValid() {
    Type os = OSs.getOS();
    String arch = OSs.getArch();
    if (os == Type.WINDOWS) {
      return Windows(os, arch);
    }
    if (os == Type.LINUX) {
      return Linux(os, arch);
    }
    if (os == Type.MAC) {
      return Mac(os, arch);
    } else {
      LOGGER.error("Your os is {}, with is unsupported", os);
      return false;
    }
  }

  private boolean Linux(@NotNull Type os, String arch) {
    String hash = HashUnix(os.toString(), Optional.of(arch));
    Path file_path = Paths.get(
      IamMusicPlayer.getConfig().IMPRFolder,
      IamMusicPlayer.getConfig().lavaNativesFolder,
      os.toString(),
      arch
    );
    String file_hash = ""; // TODO!
    return Objects.equals(hash, file_hash);
  }

  private boolean Mac(Type os, String arch) {
    return false;
  }

  private boolean Windows(Type os, String arch) {
    return false;
  }

  private String HashUnix(String os, Optional<String> arch) {
    String natives_version = LavaNativeManager.NATIVES_VERSION;
    String hash_folder = "/hash-" + natives_version;
    if (arch.isPresent()) {
      String full_url =
        IamMusicPlayer.getConfig().hashBaseUrl +
        hash_folder +
        natives_version +
        "/" +
        os +
        "-" +
        arch +
        ".hash";
      try {
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
        os +
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
}
