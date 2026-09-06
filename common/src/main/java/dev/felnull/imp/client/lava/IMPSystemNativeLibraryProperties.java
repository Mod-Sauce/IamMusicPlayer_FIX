package dev.felnull.imp.client.lava;

import com.sedmelluq.lava.common.natives.NativeLibraryProperties;
import com.sedmelluq.lava.common.natives.SystemNativeLibraryProperties;
import com.sedmelluq.lava.common.natives.architecture.SystemType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;

public class IMPSystemNativeLibraryProperties
  implements NativeLibraryProperties
{

  private static final Logger LOGGER = LogManager.getLogger(
    IMPSystemNativeLibraryProperties.class
  );
  private final Predicate<SystemType> systemFilter;
  private final String libraryName;
  private final SystemNativeLibraryProperties overrides;
  private String androidExtractionPath;

  public IMPSystemNativeLibraryProperties(
    String libraryName,
    Predicate<SystemType> systemFilter
  ) {
    this.systemFilter = systemFilter;
    this.libraryName = libraryName;
    this.overrides = new SystemNativeLibraryProperties(libraryName, "lava.native.");
  }

  @Override
  public String getLibraryPath() {
    String path = overrides.getLibraryPath();
    return isAndroid() ? AndroidNativeSupport.validateLibraryLocation(path) : path;
  }

  @Override
  public String getLibraryDirectory() {
    String directory = overrides.getLibraryDirectory();
    if (isAndroid()) {
      // The configured IMP folder may live on shared storage. Extract bundled Android natives instead.
      return AndroidNativeSupport.validateLibraryLocation(directory);
    }
    if (directory != null) return directory;
    var sys = detectMatchingSystemType(this, systemFilter);
    if (sys == null) return null;
    var natName = sys.formatSystemName();

    boolean hasConfiguredNative = LavaNativeManager.getInstance().load(
      natName,
      sys.formatLibraryName(libraryName)
    );
    if (!hasConfiguredNative) {
      return null;
    }
    var p = LavaPlayerLoader.getNaiveLibraryFolder().resolve(natName);
    LOGGER.info(
      "The path for lava loader is: " + p.toAbsolutePath().toString()
    );
    return p.toAbsolutePath().toString();
  }

  @Override
  public synchronized String getExtractionPath() {
    if (!isAndroid()) return overrides.getExtractionPath();
    if (androidExtractionPath == null) {
      androidExtractionPath = AndroidNativeSupport.createExtractionDirectory(overrides.getExtractionPath()).toString();
      LOGGER.info("Android LavaPlayer natives: target={}, extraction={}", getSystemName(), androidExtractionPath);
    }
    return androidExtractionPath;
  }

  @Override
  public String getSystemName() {
    String system = overrides.getSystemName();
    return system != null ? system : AndroidNativeSupport.detectSystem();
  }

  private boolean isAndroid() {
    String system = getSystemName();
    return system != null && system.startsWith("android-");
  }

  @Override
  public String getLibraryFileNamePrefix() {
    return overrides.getLibraryFileNamePrefix();
  }

  @Override
  public String getLibraryFileNameSuffix() {
    return overrides.getLibraryFileNameSuffix();
  }

  @Override
  public String getArchitectureName() {
    return overrides.getArchitectureName();
  }

  private static SystemType detectMatchingSystemType(
    NativeLibraryProperties properties,
    Predicate<SystemType> systemFilter
  ) {
    SystemType systemType;
    try {
      systemType = SystemType.detect(properties);
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (systemFilter != null && !systemFilter.test(systemType)) return null;
    return systemType;
  }

  private void showError(){
    Minecraft.getInstance().getToasts().addToast(SystemToast.multiline(
            Minecraft.getInstance(),
            SystemToast.SystemToastId.NARRATOR_TOGGLE,
            Component.translatable("imp.text.lava.failed1"),
            Component.translatable("imp.text.lava.failed2")
    ));
  }
}
