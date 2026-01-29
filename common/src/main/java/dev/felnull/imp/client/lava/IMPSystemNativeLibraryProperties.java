package dev.felnull.imp.client.lava;

import com.sedmelluq.lava.common.natives.NativeLibraryProperties;
import com.sedmelluq.lava.common.natives.architecture.DefaultOperatingSystemTypes;
import com.sedmelluq.lava.common.natives.architecture.SystemType;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.*;

public class IMPSystemNativeLibraryProperties
  implements NativeLibraryProperties
{

  private static final Logger LOGGER = LogManager.getLogger(
    IMPSystemNativeLibraryProperties.class
  );
  private final Predicate<SystemType> systemFilter;
  private final String libraryName;

  public IMPSystemNativeLibraryProperties(
    String libraryName,
    Predicate<SystemType> systemFilter
  ) {
    this.systemFilter = systemFilter;
    this.libraryName = libraryName;
  }

  @Override
  public String getLibraryPath() {
    return null;
  }

  @Override
  public String getLibraryDirectory() {
    var sys = detectMatchingSystemType(this, systemFilter);
    if (sys == null) throw new IllegalStateException("System type is null");
    //   var natName = sys.osType.identifier() + "-" + sys.architectureType.identifier();

    var natName = sys.osType.identifier();
    if (sys.osType != DefaultOperatingSystemTypes.DARWIN) natName +=
      "-" + sys.architectureType.identifier();

    boolean ret = LavaNativeManager.getInstance().load(
      natName,
      sys.formatLibraryName(libraryName)
    );
    if (!ret) {
        Minecraft.getInstance().getToasts().addToast(SystemToast.multiline(
                Minecraft.getInstance(),
                SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.translatable("imp.text.lava.failed1"),
                Component.translatable("imp.text.lava.failed2")
        ));
        throw new UnsatisfiedLinkError("Failed to load the library");
    };
    var p = LavaPlayerLoader.getNaiveLibraryFolder().resolve(natName);
    LOGGER.info(
      "The path for lava loader is: " + p.toAbsolutePath().toString()
    );
    return p.toAbsolutePath().toString();
  }

  @Override
  public String getExtractionPath() {
    return null;
  }

  @Override
  public String getSystemName() {
    return null;
  }

  @Override
  public String getLibraryFileNamePrefix() {
    return null;
  }

  @Override
  public String getLibraryFileNameSuffix() {
    return null;
  }

  @Override
  public String getArchitectureName() {
    return null;
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
}
