package dev.felnull.imp.client.gui.config.proxy;

import dev.felnull.imp.IMPConfig;
import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class ProxyGuiProvider implements GuiProvider {
    @Override
    public List<AbstractConfigListEntry> get(String s, Field field, Object o, Object o1, GuiRegistryAccess guiRegistryAccess) {
        if(!(o instanceof IMPConfig nowConfig && o1 instanceof IMPConfig defaultConfig))return Collections.emptyList();
        var e = new ProxyEntry(Component.translatable(s), userProxy -> Utils.setUnsafely(field, o, userProxy), false, defaultConfig.proxy, nowConfig.proxy);
        return List.of(e);
    }
}
