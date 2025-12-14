package dev.felnull.imp.mixin;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

  @Inject(method = "tick()V", at = @At("HEAD"))
  private void tick(CallbackInfo ci) {
    var ths = (ItemEntity) (Object) this;
    if (
      IamMusicPlayer.getConfig().dropItemRing &&
      ths.getItem().getItem() instanceof BoomboxItem
    ) {
      BoomboxItem.tick(ths.level(), ths, ths.getItem(), true);
    }
  }
}
