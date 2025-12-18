package dev.felnull.imp.client.lava.hash;

import dev.felnull.fnjl.os.OSs;
import dev.felnull.fnjl.os.OSs.Type;
import dev.felnull.imp.client.lava.LavaNativeManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IMPRHash {

  private static final int READ_TIMEOUT = 30000; // 30 seconds
  private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds

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
      LOGGER.error("Your os is {}, with is unsuported", os);
      return false;
    }
  }

  private boolean Linux(Type os, String arch) {
    String hash = HashUnix(os.toString(), Optional.of(arch));
    String file_hash = ""; // TODO!
    if (hash == file_hash) {
      return true;
    } else {
      return false;
    }
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
    String hash_url = ""; // TODO!
    if (arch.isPresent()) {
      String full_url =
        hash_url +
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
        hash_url + hash_folder + natives_version + "/" + os + ".hash";
      try {
        return GetHash(full_url);
      } catch (Exception e) {
        LOGGER.fatal("Something Failed during hash download: " + e);
        return null;
      }
    }
  }

  private String GetHash(String url) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URI(url)
      .toURL()
      .openConnection();
    connection.setConnectTimeout(CONNECTION_TIMEOUT);
    connection.setReadTimeout(READ_TIMEOUT);
    connection.setRequestProperty("User-Agent", "IamMusicPlayer");

    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException(
        "Failed to download manifest. HTTP response code: " + responseCode
      );
    }

    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(connection.getInputStream())
      )
    ) {
      return String.valueOf(reader);
    }
  }
}
