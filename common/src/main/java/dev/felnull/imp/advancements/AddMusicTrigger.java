package dev.felnull.imp.advancements;

import com.google.gson.JsonObject;
import dev.felnull.imp.IamMusicPlayer;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class AddMusicTrigger extends SimpleCriterionTrigger<AddMusicTrigger.TriggerInstance> {
  private static final ResourceLocation ID = new ResourceLocation(IamMusicPlayer.MODID, "add_music");

  /*
   * @Override
   * protected TriggerInstance createInstance(JsonObject jsonObject,
   * EntityPredicate.Composite composite, DeserializationContext
   * deserializationContext) {
   * return new TriggerInstance(composite);
   * }
   */

  @Override
  protected @NotNull TriggerInstance createInstance(JsonObject jsonObject, ContextAwarePredicate contextAwarePredicate,
                                                    DeserializationContext deserializationContext) {
    return new TriggerInstance(contextAwarePredicate);
  }

  public ResourceLocation getId() {
    return ID;
  }

  public void trigger(ServerPlayer serverPlayer) {
    this.trigger(serverPlayer, (triggerInstance) -> true);
  }

  public static class TriggerInstance extends AbstractCriterionTriggerInstance {

    public TriggerInstance(ContextAwarePredicate contextAwarePredicate) {
      super(Optional.of(contextAwarePredicate));

    }

    public static TriggerInstance addMusic() {
      return new TriggerInstance(ContextAwarePredicate.create());
    }
  }
}
