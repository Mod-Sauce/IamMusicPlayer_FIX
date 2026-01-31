package dev.felnull.imp.client.gui.components;

import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public abstract class IMPButton extends Button {

  protected static final CreateNarration DEFAULT_NARRATION =
    Supplier::get;

  protected IMPButton(
    int x,
    int y,
    int width,
    int height,
    Component msg,
    OnPress onPress
  ) {
    super(x, y, width, height, msg, onPress, DEFAULT_NARRATION);
  }

  protected IMPButton(
    int x,
    int y,
    int width,
    int height,
    Component msg,
    OnPress onPress,
    Tooltip tooltip
  ) {
    super(x, y, width, height, msg, onPress, DEFAULT_NARRATION);
    setTooltip(tooltip);
  }
}
