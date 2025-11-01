package org.modsauce.impr.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.modsauce.impr.IamMusicPlayer;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class ListenToMusicTrigger extends SimpleCriterionTrigger<ListenToMusicTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer serverPlayer, boolean radio, boolean remote, boolean kamesuta) {
        this.trigger(serverPlayer, (triggerInstance) -> triggerInstance.matches(radio, remote, kamesuta));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, boolean radio, boolean remote, boolean kamesuta) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.BOOL.optionalFieldOf("radio", false).forGetter(TriggerInstance::radio),
                        Codec.BOOL.optionalFieldOf("remote", false).forGetter(TriggerInstance::remote),
                        Codec.BOOL.optionalFieldOf("kamesuta", false).forGetter(TriggerInstance::kamesuta)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean matches(boolean radio, boolean remote, boolean kamesuta) {
            if (this.radio && !radio)
                return false;
            if (this.remote && !remote)
                return false;
            return !this.kamesuta || kamesuta;
        }

        public static Criterion<TriggerInstance> listen(boolean radio, boolean remote, boolean kamesuta) {
            return IMPCriteriaTriggers.LISTEN_TO_MUSIC.get().createCriterion(new TriggerInstance(Optional.empty(), radio, remote, kamesuta));
        }
    }
}
