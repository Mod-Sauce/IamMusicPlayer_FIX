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

    public void trigger(ServerPlayer serverPlayer, boolean radio, boolean remote, @NotNull String eggType) {
        this.trigger(serverPlayer, (triggerInstance) -> triggerInstance.matches(radio, remote, eggType));
    }

    @Override
    public @NotNull Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, boolean radio, boolean remote, String eggType) implements SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.BOOL.fieldOf("radio").forGetter(TriggerInstance::radio),
                        Codec.BOOL.fieldOf("remote").forGetter(TriggerInstance::remote),
                        Codec.STRING.fieldOf("eggType").forGetter(TriggerInstance::eggType))
                .apply(instance, TriggerInstance::new)
        );

        public boolean matches(boolean radio, boolean remote, String eggType) {
            if (this.radio && !radio) return false;
            if (this.remote && !remote) return false;
            if (this.eggType == null || this.eggType.isEmpty()) {
                return true;
            }
            return this.eggType.equals(eggType);
        }

        public static TriggerInstance listen(boolean radio, boolean remote, @NotNull String eggType) {
            return new TriggerInstance(Optional.empty(), radio, remote, eggType);
        }

        public static TriggerInstance listen(boolean radio, boolean remote) {
            return new TriggerInstance(Optional.empty(), radio, remote, "");
        }

        @Override
        public @NotNull Optional<ContextAwarePredicate> player() {
            return player;
        }
    }
}