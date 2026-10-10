package dev.felnull.imp.client.gui.config;

import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Renders a config enum as a **click-to-cycle** row with labelled values. The enum passed in must be the backing enum
 * itself: Cloth indexes {@code clazz.getEnumConstants()}, so a mismatched one throws while rendering.
 *
 * @param <E> the config enum (declared at the top level of the config so its type can be registered)
 */
abstract class EnumCycleGuiProvider<E extends Enum<E>> implements GuiProvider {
    private final Class<E> type;
    private final String labelKey;
    private final String optionKeyPrefix;

    EnumCycleGuiProvider(Class<E> type, String labelKey, String optionKeyPrefix) {
        this.type = type;
        this.labelKey = labelKey;
        this.optionKeyPrefix = optionKeyPrefix;
    }

    @Override
    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults,
                                             GuiRegistryAccess registry) {
        var current = type.cast(read(field, config));
        var fallback = type.cast(read(field, defaults));
        var entry = ConfigEntryBuilder.create()
                .startEnumSelector(Component.translatable(labelKey), type, current)
                .setDefaultValue(fallback)
                .setEnumNameProvider(value -> Component.translatable(
                        optionKeyPrefix + ((Enum<?>) value).name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(value -> write(field, config, value))
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
