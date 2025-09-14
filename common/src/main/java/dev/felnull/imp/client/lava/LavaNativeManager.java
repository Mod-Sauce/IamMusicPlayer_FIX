package dev.felnull.imp.client.lava;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.felnull.fnjl.util.FNDataUtil;
import dev.felnull.fnjl.util.FNURLUtil;
import dev.felnull.imp.IamMusicPlayer;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LavaNativeManager {

  private static final Logger LOGGER = LogManager.getLogger(
    LavaNativeManager.class
  );
  private static final Gson GSON = new Gson();
  private static final LavaNativeManager INSTANCE = new LavaNativeManager();
  private static final String nativesVersion = "2.2.4";

  public static LavaNativeManager getInstance() {
    return INSTANCE;
  }

  public boolean load(String osAndArch, String name) {
    var npF = LavaPlayerLoader.getNaiveLibraryFolder()
      .resolve(osAndArch)
      .toFile();
    if (!checked(npF)) {
      try {
        LOGGER.info("LavaPlayer natives download start");
        downloadNatives(osAndArch);
        LOGGER.info("LavaPlayer natives download successful");
      } catch (Exception e) {
        LOGGER.error("LavaPlayer natives download failed", e);
        return false;
      }
    }
    LOGGER.info("LavaPlayer native(" + name + ") check successful");

    return npF.toPath().resolve(name).toFile().exists();
  }

  private void downloadNatives(String osAndArch) throws Exception {
    var npF = LavaPlayerLoader.getNaiveLibraryFolder()
      .resolve(osAndArch)
      .toFile();
    if (!npF.exists() && !npF.mkdirs()) throw new IllegalStateException(
      "Failed to create the folder of the native library"
    );

    JsonObject jo;
    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(
          FNURLUtil.getStream(
            new URI(IamMusicPlayer.getConfig().lavaPlayerNativesURL).toURL()
          )
        )
      )
    ) {
      jo = GSON.fromJson(reader, JsonObject.class);
    }

    if (
      !jo.has(nativesVersion) || !jo.get(nativesVersion).isJsonObject()
    ) throw new IllegalStateException(
      "Could not find version of native library to support"
    );

    var joo = jo.getAsJsonObject(nativesVersion);

    if (
      !joo.has(osAndArch) || !joo.get(osAndArch).isJsonObject()
    ) throw new IllegalStateException("Unsupported OS or architecture");

    var jooo = joo.getAsJsonObject(osAndArch);

    if (!jooo.has("hash")) throw new IllegalStateException(
      "The hash value was not found"
    );

    if (!jooo.has("url")) throw new IllegalStateException(
      "Native library URL not found"
    );

    var ho = new JsonObject();
    ho.add("hash", jooo.get("hash"));

    Files.writeString(npF.toPath().resolve("hash.json"), GSON.toJson(ho));

    FNDataUtil.readZipStreamed(
      new BufferedInputStream(
        FNURLUtil.getStream(new URI(jooo.get("url").getAsString()).toURL())
      ),
      (zipEntry, inputStream) -> {
        var fl = npF.toPath().resolve(zipEntry.getName()).toFile();
        try (
          InputStream is = inputStream;
          OutputStream os = new FileOutputStream(fl)
        ) {
          FNDataUtil.bufInputToOutput(is, os);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    );

    if (!checked(npF)) throw new IllegalStateException(
      "Consistency check failed"
    );
  }

  private boolean checked(File file) {
    String os = System.getProperty("os.name").toLowerCase();

    // 1. Check directory
    File[] fs = file.listFiles();
    if (fs == null) {
      LOGGER.error("Directory does not exist or cannot be read: {}", file);
      return false;
    }

    // 2. Build file list, ignoring hidden/junk files
    List<File> fls = Arrays.stream(fs)
      .filter(f -> !f.isHidden()) // skip hidden files (.DS_Store, etc.)
      .filter(f -> !f.getName().equalsIgnoreCase("Thumbs.db"))
      .collect(Collectors.toList());

    // 3. Find hash.json
    Optional<File> hf = fls
      .stream()
      .filter(n -> n.getName().equals("hash.json"))
      .findAny();
    if (hf.isEmpty()) {
      LOGGER.error("Missing hash.json in directory: {}", file);
      return false;
    }

    // 4. Parse JSON
    JsonObject jo;
    try (
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(hf.get()))
      )
    ) {
      jo = GSON.fromJson(reader, JsonObject.class);
    } catch (IOException e) {
      LOGGER.error("Failed to read/parse hash.json: {}", e.getMessage());
      return false;
    }

    // 5. Ensure "hash" key exists
    if (!jo.has("hash")) {
      LOGGER.error("hash.json missing required field 'hash'");
      return false;
    }

    // 6. Remove hash.json from file list
    fls.remove(hf.get());

    if (fls.isEmpty()) {
      LOGGER.error("No native files found (only hash.json present)");
      return false;
    }

    JsonElement hashElem = jo.get("hash");

    // 7. Case A: Single hash string → validate first file
    if (hashElem.isJsonPrimitive()) {
      if (fls.size() != 1) {
        LOGGER.warn(
          "Primitive hash provided but {} files found (expected 1). Validating first file anyway.",
          fls.size()
        );
      }

      try {
        File target = fls.get(0);
        String expected = hashElem.getAsString();
        String actual = new String(
          Hex.encodeHex(
            FNDataUtil.createMD5Hash(Files.readAllBytes(target.toPath()))
          )
        );
        if (expected.equals(actual)) {
          return true;
        } else {
          LOGGER.error(
            "Hash mismatch for {}: expected {}, got {}",
            target.getName(),
            expected,
            actual
          );
          return false;
        }
      } catch (Exception e) {
        LOGGER.error(
          "Error computing hash for {}: {}",
          fls.get(0).getName(),
          e.getMessage()
        );
        return false;
      }
    }

    // 8. Case B: Object of hashes → validate each file
    if (hashElem.isJsonObject()) {
      JsonObject hjo = hashElem.getAsJsonObject();

      if (hjo.size() != fls.size()) {
        LOGGER.error(
          "Mismatch between hash entries ({}) and file count ({})",
          hjo.size(),
          fls.size()
        );
        return false;
      }

      for (Map.Entry<String, JsonElement> entry : hjo.entrySet()) {
        String filename = entry.getKey();
        String expected = entry.getValue().getAsString();

        Optional<File> lf = fls
          .stream()
          .filter(n -> n.getName().equals(filename))
          .findAny();

        if (lf.isEmpty()) {
          LOGGER.error("Expected file {} not found in directory", filename);
          return false;
        }

        try {
          String actual = new String(
            Hex.encodeHex(
              FNDataUtil.createMD5Hash(Files.readAllBytes(lf.get().toPath()))
            )
          );
          if (!expected.equals(actual)) {
            LOGGER.error(
              "Hash mismatch for {}: expected {}, got {}",
              filename,
              expected,
              actual
            );
            return false;
          }
        } catch (Exception e) {
          LOGGER.error(
            "Error computing hash for {}: {}",
            filename,
            e.getMessage()
          );
          return false;
        }
      }
      return true;
    }

    // 9. Fallback
    LOGGER.error(
      "Invalid 'hash' format in hash.json (expected string or object). Found: {}",
      hashElem
    );
    return false;
  }
}
