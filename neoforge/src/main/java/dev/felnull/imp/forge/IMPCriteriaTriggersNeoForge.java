package dev.felnull.imp.forge;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.advancements.AddMusicTrigger;
import dev.felnull.imp.advancements.ListenToMusicTrigger;
import dev.felnull.imp.advancements.WriteCassetteTapeTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class IMPCriteriaTriggersNeoForge {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, IamMusicPlayer.MODID);
    public static final Supplier<SimpleCriterionTrigger<?>> ADD_MUSIC_TRIGGER = registry("add_music", AddMusicTrigger::new);
    public static final Supplier<SimpleCriterionTrigger<?>> WRITE_CASSETTE_TAPE_TRIGGER = registry("write_cassette_type", WriteCassetteTapeTrigger::new);
    public static final Supplier<SimpleCriterionTrigger<?>> LISTEN_TO_MUSIC_TRIGGER = registry("listen_to_music", ListenToMusicTrigger::new);

    public static Supplier<SimpleCriterionTrigger<?>> registry(String id, Supplier<SimpleCriterionTrigger<?>> supplier){
        return TRIGGERS.register(id, supplier);
    }

    public static void registry(IEventBus bus){
        TRIGGERS.register(bus);
    }
}
