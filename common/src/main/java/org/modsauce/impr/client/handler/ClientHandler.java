package org.modsauce.impr.client.handler;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import dev.architectury.networking.NetworkManager;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
// SoundOptionsScreen import intentionally removed for 1.21+ compatibility.
// In Minecraft 1.21 the dedicated sound/options screen class used previously by older mappings
// may have been renamed or moved. Once a direct replacement or equivalent screen class is
// available in the mappings you're using, you can re-enable the import and the related
// integration code below. Until then the import is left out to avoid compile errors.
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.modsauce.impr.IMPConfig;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.block.IMPBlocks;
import org.modsauce.impr.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import org.modsauce.impr.client.music.MusicEngine;
import org.modsauce.impr.client.music.MusicSyncManager;
import org.modsauce.impr.client.renderer.item.IMPItemRenderers;
import org.modsauce.impr.client.renderer.item.hand.BoomboxHandRenderer;
import org.modsauce.impr.entity.IRingerPartyParrot;
import org.modsauce.impr.integration.PatchouliIntegration;
import org.modsauce.impr.item.BoomboxItem;
import org.modsauce.impr.networking.IMPPackets;
import org.modsauce.impr.server.music.ringer.MusicRingManager;
import org.modsauce.otyacraftenginerenewed.client.event.ClientEvent;
import org.modsauce.otyacraftenginerenewed.client.gui.TextureRegion;
import org.modsauce.otyacraftenginerenewed.client.gui.components.IconButton;
import org.modsauce.otyacraftenginerenewed.event.MoreEntityEvent;
import org.modsauce.otyacraftenginerenewed.item.location.HandItemLocation;

public class ClientHandler {

  private static final Minecraft mc = Minecraft.getInstance();
  private static final Component CONFIG_BUTTON = Component.translatable(
    "imp.button.config"
  );
  private static final TextureRegion CONFIG_BUTTON_REGION =
    TextureRegion.relative(MusicManagerMonitor.WIDGETS_TEXTURE, 36, 58, 14, 5);
  private static double LAST_MUSIC_VOLUME = IamMusicPlayer.getConfig().volume;

  public static void init() {
    ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register(
      ClientHandler::onClientLevelLoad
    );
    ClientEvent.CHANGE_HAND_HEIGHT.register(ClientHandler::changeHandHeight);
    AutoConfig.getConfigHolder(IMPConfig.class).registerSaveListener(
      ClientHandler::onConfigSave
    );
    ClientEvent.POSE_HUMANOID_ARM.register(ClientHandler::onPoseHumanoidArm);
    ClientEvent.INTEGRATED_SERVER_PAUSE.register(ClientHandler::onPauseChange);
    MoreEntityEvent.LIVING_ENTITY_TICK.register(
      ClientHandler::onLivingEntityTick
    );
    ClientTickEvent.CLIENT_POST.register(ClientHandler::ontClientTick);
    ClientEvent.HAND_ATTACK.register(ClientHandler::onHandAttack);
    ClientGuiEvent.SET_SCREEN.register(ClientHandler::onModifyScreen);
    ClientGuiEvent.INIT_POST.register(ClientHandler::onScreenInit);
  }

  private static void onScreenInit(Screen screen, ScreenAccess screenAccess) {
    // Note: Integration with the dedicated sound/options screen is disabled for 1.21+.
    // The concrete class that represented the sound options screen in older versions
    // (previously referenced as SoundOptionsScreen) is not available in current mappings,
    // so attempting to reference it causes compile-time errors.
    //
    // Guidance for future re-enablement:
    // - When a replacement/analogous screen class for sound/options is identified in your
    //   1.21 mappings, re-add the import at the top of this file and re-enable the code
    //   below. It previously did two things:
    //     1) Captured the last music volume from the mod config when the sound screen opened.
    //     2) Added an icon button to open the mod's config screen from the sound/options screen.
    //
    // Example of the original intent (left commented for reference):
    // if (screen instanceof SoundOptionsScreen) {
    //     LAST_MUSIC_VOLUME = IamMusicPlayer.getConfig().volume;
    //     screenAccess.addRenderableWidget(new IconButton(
    //         screen.width - 27, screen.height - 27, 20, 20, CONFIG_BUTTON,
    //         (button) -> mc.setScreen(AutoConfig.getConfigScreen(IMPConfig.class, screen).get()),
    //         CONFIG_BUTTON_REGION
    //     ));
    // }
    //
    // If you want a temporary fallback that does not depend on the missing screen class,
    // consider adding the config button for a broader set of screens or for specific screens
    // that are available in your environment. Be careful to avoid spamming the button on
    // every screen — gate it by whatever condition makes sense for your desired UX.
  }

  private static CompoundEventResult<Screen> onModifyScreen(Screen screen) {
    // Note: The legacy hook that saved the config when the (old) SoundOptionsScreen closed
    // is disabled because the referenced screen class is not present in 1.21 mappings.
    //
    // Original intent (left commented for reference):
    // if (mc.screen instanceof SoundOptionsScreen && LAST_MUSIC_VOLUME != IamMusicPlayer.getConfig().volume)
    //     AutoConfig.getConfigHolder(IMPConfig.class).save();
    //
    // Guidance:
    // - When you identify the current equivalent of the sound/options screen in your
    //   mappings, restore the conditional above (and the import) to re-enable automatic
    //   saving behavior tied to that screen.
    // - Alternatively, if you add a config button to other screens as a fallback, ensure
    //   this saving logic is executed from the appropriate place (for example, when your
    //   mod-config screen is closed).
    return CompoundEventResult.pass();
  }

  private static EventResult onHandAttack(@NotNull ItemStack itemStack) {
    if (
      itemStack.getItem() instanceof BoomboxItem &&
      BoomboxItem.isPowered(itemStack)
    ) {
      if (mc.player.isCrouching()) {
        var bu = BoomboxItem.getRingerUUID(itemStack);
        if (bu != null) NetworkManager.sendToServer(
          IMPPackets.HAND_LID_CYCLE,
          new IMPPackets.LidCycleMessage(
            bu,
            new HandItemLocation(InteractionHand.MAIN_HAND)
          ).toRFBB()
        );
      }
      return EventResult.interruptFalse();
    }
    return EventResult.pass();
  }

  private static EventResult onLivingEntityTick(
    @NotNull LivingEntity livingEntity
  ) {
    if (!livingEntity.level().isClientSide()) return EventResult.pass();

    if (livingEntity instanceof IRingerPartyParrot ringerPartyParrot) {
      var mm = MusicEngine.getInstance();
      var id = ringerPartyParrot.getRingerUUID();
      if (id == null || !mm.isPlaying(id)) ringerPartyParrot.setRingerUUID(
        null
      );
    }

    return EventResult.pass();
  }

  private static void onPauseChange(boolean paused) {
    var rm = MusicRingManager.getInstance();
    var nmm = MusicEngine.getInstance();
    if (paused) {
      rm.pause();
      nmm.pause();
    } else {
      rm.resume();
      nmm.resume();
    }
  }

  private static InteractionResult onConfigSave(
    ConfigHolder<IMPConfig> configHolder,
    IMPConfig impConfig
  ) {
    MusicEngine.getInstance().destroy();
    return InteractionResult.PASS;
  }

  private static void onClientLevelLoad(ClientLevel clientLevel) {
    MusicSyncManager.getInstance().reset();
  }

  private static EventResult changeHandHeight(
    InteractionHand hand,
    ItemStack oldStack,
    ItemStack newStack
  ) {
    if (
      oldStack.getItem() instanceof BoomboxItem &&
      newStack.getItem() instanceof BoomboxItem &&
      BoomboxItem.matches(oldStack, newStack)
    ) return EventResult.interruptFalse();
    return EventResult.pass();
  }

  private static EventResult onPoseHumanoidArm(
    HumanoidArm arm,
    InteractionHand hand,
    HumanoidModel<? extends LivingEntity> model,
    LivingEntity livingEntity
  ) {
    var item = livingEntity.getItemInHand(hand);
    if (
      item.is(IMPBlocks.BOOMBOX.get().asItem()) &&
      BoomboxItem.getTransferProgress(item) >= 1f
    ) {
      BoomboxHandRenderer.pose(arm, model, item);
      return EventResult.interruptFalse();
    }
    return EventResult.pass();
  }

  private static void ontClientTick(Minecraft instance) {
    if (
      PatchouliIntegration.INSTANCE.isEnable()
    ) IMPItemRenderers.manualItemRenderer.tick();
  }
}
