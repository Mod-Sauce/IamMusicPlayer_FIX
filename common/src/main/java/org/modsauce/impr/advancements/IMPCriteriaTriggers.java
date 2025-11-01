package org.modsauce.impr.advancements;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import org.modsauce.impr.IamMusicPlayer;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;

public class IMPCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES =
            DeferredRegister.create(IamMusicPlayer.MODID, Registries.TRIGGER_TYPE);

    public static final RegistrySupplier<AddMusicTrigger> ADD_MUSIC =
            TRIGGER_TYPES.register("add_music", AddMusicTrigger::new);
    public static final RegistrySupplier<WriteCassetteTapeTrigger> WRITE_CASSETTE_TAPE =
            TRIGGER_TYPES.register("write_cassette_tape", WriteCassetteTapeTrigger::new);
    public static final RegistrySupplier<ListenToMusicTrigger> LISTEN_TO_MUSIC =
            TRIGGER_TYPES.register("listen_to_music", ListenToMusicTrigger::new);

    public static void init() {
        TRIGGER_TYPES.register();
    }
}
