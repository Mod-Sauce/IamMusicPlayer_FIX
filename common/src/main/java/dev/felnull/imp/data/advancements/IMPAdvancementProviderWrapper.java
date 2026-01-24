package dev.felnull.imp.data.advancements;

import com.google.common.collect.ImmutableList;
import net.minecraft.advancements.Advancement;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.AdvancementProviderWrapper;
import net.minecraft.data.PackOutput;

import java.util.function.Consumer;

public class IMPAdvancementProviderWrapper extends AdvancementProviderWrapper {

    public IMPAdvancementProviderWrapper(PackOutput packOutput, CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(packOutput, crossDataGeneratorAccess, ImmutableList.of(new IMPAdvancements(crossDataGeneratorAccess)));
    }
}
