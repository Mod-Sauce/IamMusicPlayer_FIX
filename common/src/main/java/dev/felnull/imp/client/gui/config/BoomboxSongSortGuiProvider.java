package dev.felnull.imp.client.gui.config;

import dev.felnull.imp.IMPConfig;
import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Rendering of the "boombox sort" entry ({@link IMPConfig.BoomboxSongSort}) in the config screen.
 */
public final class BoomboxSongSortGuiProvider implements GuiProvider {
    private static final String KEY_PREFIX = "imp.boomboxSongSort.";

    @Override
    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults,
                                            GuiRegistryAccess registry) {
        var current = (IMPConfig.BoomboxSongSort) read(field, config);
        var def = (IMPConfig.BoomboxSongSort) read(field, defaults);

        // CREATE_DATE is only written by "use the monitor's sort" and is not offered here; it is added temporarily when it really is the current value, or the selector could not find its entry
        var values = current == IMPConfig.BoomboxSongSort.CREATE_DATE
                ? new IMPConfig.BoomboxSongSort[]{IMPConfig.BoomboxSongSort.CREATE_DATE, IMPConfig.BoomboxSongSort.ADD_ORDER,
                IMPConfig.BoomboxSongSort.NAME, IMPConfig.BoomboxSongSort.PLAYER, IMPConfig.BoomboxSongSort.PUBLISH_DATE}
                : new IMPConfig.BoomboxSongSort[]{IMPConfig.BoomboxSongSort.ADD_ORDER, IMPConfig.BoomboxSongSort.NAME,
                IMPConfig.BoomboxSongSort.PLAYER, IMPConfig.BoomboxSongSort.PUBLISH_DATE};

        var entry = ConfigEntryBuilder.create()
                .startSelector(Component.translatable(i18n), values, current)
                .setDefaultValue(def)
                .setNameProvider(v -> Component.translatable(KEY_PREFIX + ((Enum<?>) v).name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(v -> write(field, config, v))
                .build();
        return List.of(entry);
    }

    private static Object read(Field field, Object holder) {
        try {
            field.setAccessible(true);
            return field.get(holder);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void write(Field field, Object holder, Object value) {
        try {
            field.setAccessible(true);
            field.set(holder, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
