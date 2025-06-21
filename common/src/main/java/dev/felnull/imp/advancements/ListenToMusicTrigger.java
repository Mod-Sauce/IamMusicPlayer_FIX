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

public class ListenToMusicTrigger extends SimpleCriterionTrigger<ListenToMusicTrigger.TriggerInstance> {
  private static final ResourceLocation ID = new ResourceLocation(IamMusicPlayer.MODID, "listen_to_music");

  /*
   * @Override
   * protected TriggerInstance createInstance(JsonObject jo,
   * EntityPredicate.Composite composite, DeserializationContext
   * deserializationContext) {
   * boolean radio = jo.has("radio") && jo.get("radio").getAsBoolean();
   * boolean remote = jo.has("remote") && jo.get("remote").getAsBoolean();
   * boolean kamesuta = jo.has("kamesuta") && jo.get("kamesuta").getAsBoolean();
   * return new TriggerInstance(composite, radio, remote, kamesuta);
   * }
   */

  @Override
  protected @NotNull TriggerInstance createInstance(JsonObject jo,
                                                    Optional<ContextAwarePredicate> contextAwarePredicate,
                                                    @NotNull DeserializationContext deserializationContext) {
    boolean radio = false;
    boolean remote = false;
    boolean kamesuta = false;

    if (jo != null) {
      try {
        if (jo.has("radio")) {
          radio = jo.get("radio").getAsBoolean();
        }
        if (jo.has("remote")) {
          remote = jo.get("remote").getAsBoolean();
        }
        if (jo.has("kamesuta")) {
          kamesuta = jo.get("kamesuta").getAsBoolean();
        }
      } catch (Exception e) {
        // Optionally log or handle error, fallback to false
      }
    }

    return new TriggerInstance(contextAwarePredicate.orElse(null), radio, remote, kamesuta);
  }

  public void trigger(ServerPlayer serverPlayer, boolean radio, boolean remote, boolean kamesuta) {
    this.trigger(serverPlayer, (triggerInstance) -> triggerInstance.matches(radio, remote, kamesuta));
  }

  public ResourceLocation getId() {
    return ID;
  }

  public static class TriggerInstance extends AbstractCriterionTriggerInstance {
    private final boolean radio;
    private final boolean remote;
    private final boolean kamesuta;

    public TriggerInstance(ContextAwarePredicate contextAwarePredicate, boolean radio, boolean remote,
                           boolean kamesuta) {
      super(ID, contextAwarePredicate);
      this.radio = radio;
      this.remote = remote;
      this.kamesuta = kamesuta;
    }

    /*
     * public TriggerInstance(EntityPredicate.Composite composite, boolean radio,
     * boolean remote, boolean kamesuta) {
     * super(ID, composite);
     * this.radio = radio;
     * this.remote = remote;
     * this.kamesuta = kamesuta;
     * }
     */

    public boolean matches(boolean radio, boolean remote, boolean kamesuta) {
      if (this.radio && !radio)
        return false;
      if (this.remote && !remote)
        return false;
      return !this.kamesuta || kamesuta;
    }

    @Override
    public JsonObject serializeToJson() {
      JsonObject jo = new JsonObject();
      jo.addProperty("radio", this.radio);
      jo.addProperty("remote", this.remote);
      jo.addProperty("kamesuta", this.kamesuta);
      return jo;
    }

    public static TriggerInstance listen(boolean radio, boolean remote, boolean kamesuta) {
      return new TriggerInstance(ContextAwarePredicate.ANY, radio, remote, kamesuta);
    }
  }
}
