package dev.felnull.imp.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ListenToMusicTrigger extends SimpleCriterionTrigger<ListenToMusicTrigger.TriggerInstance> {

    public void trigger(ServerPlayer serverPlayer, boolean radio, boolean remote, boolean kamesuta) {
        this.trigger(serverPlayer, (triggerInstance) -> triggerInstance.matches(radio, remote, kamesuta));
    }

    @Override
    public @NotNull Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, boolean radio, boolean remote, boolean kamesuta) implements SimpleInstance{
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(triggerInstanceInstance -> triggerInstanceInstance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.BOOL.fieldOf("radio").forGetter(TriggerInstance::radio),
                Codec.BOOL.fieldOf("remote").forGetter(TriggerInstance::remote),
                Codec.BOOL.fieldOf("kamesuta").forGetter(TriggerInstance::kamesuta))
                .apply(triggerInstanceInstance, TriggerInstance::new)
        );
        public boolean matches(boolean radio, boolean remote, boolean kamesuta) {
            if (this.radio && !radio)
                return false;
            if (this.remote && !remote)
                return false;
            return !this.kamesuta || kamesuta;
        }

        public static TriggerInstance listen(boolean radio, boolean remote, boolean kamesuta) {
            return new TriggerInstance(Optional.empty(), radio, remote, kamesuta);
        }

        @Override
        public @NotNull Optional<ContextAwarePredicate> player() {
            return player;
        }
    }
}
