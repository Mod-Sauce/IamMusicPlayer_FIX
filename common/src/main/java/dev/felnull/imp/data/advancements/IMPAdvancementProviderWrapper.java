package dev.felnull.imp.data.advancements;

import com.google.common.collect.ImmutableList;
import net.minecraft.data.PackOutput;
import org.modsauce.otyacraftenginerenewed.data.CrossDataGeneratorAccess;
import org.modsauce.otyacraftenginerenewed.data.provider.AdvancementProviderWrapper;

public class IMPAdvancementProviderWrapper extends AdvancementProviderWrapper {

    public IMPAdvancementProviderWrapper(PackOutput packOutput, CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(packOutput, crossDataGeneratorAccess, ImmutableList.of(new IMPAdvancements(crossDataGeneratorAccess)));
    }

    /*public IMPAdvancementProviderWrapper(CrossDataGeneratorAccess crossDataGeneratorAccess) {
        super(crossDataGeneratorAccess);
    }

    @Override
    public void generateAdvancement(Consumer<Advancement> consumer) {
        advancements.accept(consumer);
    }*/
}
