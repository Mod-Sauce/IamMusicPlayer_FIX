package org.modsauce.impr.data;

import java.nio.file.Paths;
import net.minecraft.data.PackOutput;
import org.modsauce.impr.data.advancements.IMPAdvancementProviderWrapper;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.DataProviderWrapper;
import org.modsauce.otyacraftenginerenewed.data.provider.DirectCopyProviderWrapper;

public class IamMusicPlayerDataGenerator {

  public static void init(CrossDataGeneratorAccess access) {
    access.addResourceInputFolders(Paths.get("../../resources"));

    //access.addProviderWrapper(IMPRecipeProviderWrapper::new);
    var btp = access.addProviderWrapper(IMPBlockTagProviderWrapper::new);

    access.addProviderWrapper(
      (DataProviderWrapper.LookupGeneratorAccessedFactory<
        DataProviderWrapper<?>
      >) (packOutput, lookup, generatorAccess) ->
        new IMPItemTagProviderWrapper(packOutput, lookup, generatorAccess, btp)
    );
    access.addProviderWrapper(IMPPoiTypeTagProviderWrapper::new);
    access.addProviderWrapper(packOutput ->
      new DirectCopyProviderWrapper(
        packOutput,
        PackOutput.Target.DATA_PACK,
        "patchouli_books",
        access
      )
    );
    access.addProviderWrapper(IMPBlockLootTableProviderWrapper::new);
    access.addProviderWrapper(IMPAdvancementProviderWrapper::new);
  }
}
