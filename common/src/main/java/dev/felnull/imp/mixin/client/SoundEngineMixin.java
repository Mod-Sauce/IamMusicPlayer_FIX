package dev.felnull.imp.mixin.client;

import dev.felnull.imp.client.music.MusicEngine;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

  //FIXME/TODO!
  @Inject(method = "method_20185(Z)V", at = @At("HEAD"))
  private void tick(boolean bl, CallbackInfo ci) {
    MusicEngine.getInstance().tick();
  }

  //FIXME/TODO!

  @Inject(method = "method_4837()V", at = @At("HEAD"))
  private void reload(CallbackInfo ci) {
    MusicEngine.getInstance().destroy();
  }

  //FIXME/TODO!
  @Inject(
    method = "method_4837()V",
    at = @At(
      value = "INVOKE",
      target = "Lnet/minecraft/client/sounds/SoundEngine;method_4856()V"
    )
  )
  private void reloadFlag(CallbackInfo ci) {
    MusicEngine.getInstance().reloadFlag = true;
  }

  //FIXME/TODO!
  @Inject(method = "method_4843()V", at = @At("HEAD"))
  private void stopAll(CallbackInfo ci) {
    MusicEngine.getInstance().stopAll();
  }
}
