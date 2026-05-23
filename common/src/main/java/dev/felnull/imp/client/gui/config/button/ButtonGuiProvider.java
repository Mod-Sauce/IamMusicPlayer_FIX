package dev.felnull.imp.client.gui.config.button;

import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;

import java.lang.reflect.Field;
import java.util.List;

public class ButtonGuiProvider implements GuiProvider {
    @Override
    public List<AbstractConfigListEntry> get(String s, Field field, Object o, Object o1, GuiRegistryAccess guiRegistryAccess) {
        return List.of(AllButtons.get(field.getAnnotation(Button.class).value()));
    }
}
