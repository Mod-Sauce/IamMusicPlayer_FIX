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

/** Rendering of the Bilibili subtitle-translation entry ({@link IMPConfig.SubtitleTrans}) in the config screen — same pattern
 * as {@link BoomboxSongSortGuiProvider} / {@link HudBottomStyleGuiProvider}: Cloth shows the raw enum constant name
 * otherwise, so the translatable names live under {@code imp.subtitleTrans.*}. */
public final class SubtitleTransGuiProvider implements GuiProvider {
    private static final String KEY_PREFIX = "imp.subtitleTrans.";

    @Override
    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults,
                                            GuiRegistryAccess registry) {
        var current = (IMPConfig.SubtitleTrans) read(field, config);
        var def = (IMPConfig.SubtitleTrans) read(field, defaults);

        var entry = ConfigEntryBuilder.create()
                .startEnumSelector(Component.translatable(i18n), IMPConfig.SubtitleTrans.class, current)
                .setDefaultValue(def)
                .setEnumNameProvider(v -> Component.translatable(KEY_PREFIX + ((Enum<?>) v).name().toLowerCase(Locale.ROOT)))
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
