package dev.felnull.imp.advancements;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.advancements.critereon.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class WriteCassetteTapeTrigger extends SimpleCriterionTrigger<WriteCassetteTapeTrigger.TriggerInstance> {

    public void trigger(ServerPlayer serverPlayer, ItemStack itemStack) {
        this.trigger(serverPlayer, (triggerInstance) -> triggerInstance.matches(itemStack));
    }

    @Override
    public @NotNull Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }


    public record TriggerInstance(Optional<ContextAwarePredicate> player, @NotNull ItemPredicate itemPredicate) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(triggerInstanceInstance -> triggerInstanceInstance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ItemPredicate.CODEC.fieldOf("item").forGetter(TriggerInstance::itemPredicate))
                .apply(triggerInstanceInstance, TriggerInstance::new)
        );
        public boolean matches(ItemStack itemStack) {
            return itemPredicate.test(itemStack);
        }

        public static TriggerInstance writeCassetteTape() {
            return new TriggerInstance(Optional.empty(), ItemPredicate.Builder
                    .item().of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "cassette_tape")))
                    .build());
        }

        @Override
        public @NotNull Optional<ContextAwarePredicate> player() {
            return player;
        }
    }
}
