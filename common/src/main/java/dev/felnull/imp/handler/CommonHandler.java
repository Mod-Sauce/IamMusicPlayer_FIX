package dev.felnull.imp.handler;

import dev.architectury.event.events.common.TickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public class CommonHandler {

  public static List<UUID> itemBoomboxes = new ArrayList<>();

  public static void init() {
    TickEvent.SERVER_POST.register(CommonHandler::onTickEnd);
  }

  private static void onTickEnd(MinecraftServer minecraftServer) {
    itemBoomboxes.clear();
  }
}
