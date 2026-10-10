package dev.felnull.imp.mixin.client;

import dev.felnull.imp.client.music.MusicEngine;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(boolean bl, CallbackInfo ci) {
        MusicEngine.getInstance().tick();
    }

    /** A sound-engine reload (F3+T, a resource-pack change, a sound-setting change) no longer interrupts this mod's playback: reloadFlag swallows that destroy→stopAll. */
    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;destroy()V"))
    private void reloadFlag(CallbackInfo ci) {
        MusicEngine.getInstance().reloadFlag = true;
    }

    /** Set when the reload finished (the new context exists): the next tick restarts the playing track at its current position, otherwise only a silent "still playing" remains. */
    @Inject(method = "reload", at = @At("RETURN"))
    private void reloadRebuildAudio(CallbackInfo ci) {
        MusicEngine.getInstance().requestAudioRebuild();
    }

    @Inject(method = "stopAll", at = @At("HEAD"))
    private void stopAll(CallbackInfo ci) {
        MusicEngine.getInstance().stopAll();
    }
}
