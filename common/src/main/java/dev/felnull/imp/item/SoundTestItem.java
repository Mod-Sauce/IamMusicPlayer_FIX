package dev.felnull.imp.item;

import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SoundTestItem extends Item {

  private static final UUID ID = UUID.randomUUID();

  public SoundTestItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(
    Level level,
    Player player,
    InteractionHand interactionHand
  ) {
    ItemStack itemStack = player.getItemInHand(interactionHand);
    if (level.isClientSide()) {
      System.out.println("Sound test");
    }
    return InteractionResultHolder.sidedSuccess(
      itemStack,
      level.isClientSide
    );
  }
}
