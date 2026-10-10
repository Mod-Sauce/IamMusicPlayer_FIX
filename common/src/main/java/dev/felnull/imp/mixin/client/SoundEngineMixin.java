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

    /** 音效引擎重载（F3+T / 换资源包 / 改声音设置）不再打断本模组的播放：那次 destroy→stopAll 由 reloadFlag 吃掉。 */
    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;destroy()V"))
    private void reloadFlag(CallbackInfo ci) {
        MusicEngine.getInstance().reloadFlag = true;
    }

    /** 重载结束（新上下文已建好）时置位：下一 tick 把在放的曲子按当前进度重起，否则只剩"无声续播"。 */
    @Inject(method = "reload", at = @At("RETURN"))
    private void reloadRebuildAudio(CallbackInfo ci) {
        MusicEngine.getInstance().requestAudioRebuild();
    }

    @Inject(method = "stopAll", at = @At("HEAD"))
    private void stopAll(CallbackInfo ci) {
        MusicEngine.getInstance().stopAll();
    }
}
