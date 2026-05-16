package dev.felnull.imp.create.block_entity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record BoomboxControllerData(List<Signal> signals) {
    public static final Codec<BoomboxControllerData> CODEC = RecordCodecBuilder.create(i ->
            i.group(
                    Codec.list(Signal.CODEC).fieldOf("signals").forGetter(BoomboxControllerData::signals)
            ).apply(i, BoomboxControllerData::new));
    private static final Logger LOGGER = LogManager.getLogger(
            BoomboxControllerData.class
    );

    public record Signal(ItemStack left, ItemStack right, Mode mode){
        public static final Codec<Signal> CODEC = RecordCodecBuilder.create(i ->
            i.group(
                ItemStack.OPTIONAL_CODEC.fieldOf("left").forGetter(Signal::left),
                ItemStack.OPTIONAL_CODEC.fieldOf("right").forGetter(Signal::right),
                StringRepresentable.fromEnum(Mode::values).fieldOf("mode").forGetter(Signal::mode)
            ).apply(i, Signal::new)
        );

        public static void saveToNbt(CompoundTag tag, Signal data, String key) {
            if(data == null)return;
            Signal.CODEC.encodeStart(NbtOps.INSTANCE, data)
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(nbt -> tag.put(key, nbt));
        }

        public static Signal loadFromNbt(CompoundTag tag, String key) {
            if (!tag.contains(key)) return new Signal(ItemStack.EMPTY, ItemStack.EMPTY, Mode.NONE);
            Tag encoded = tag.get(key);
            DataResult<Signal> result = Signal.CODEC.parse(NbtOps.INSTANCE, encoded);
            return result.resultOrPartial(LOGGER::error).orElse(new Signal(ItemStack.EMPTY, ItemStack.EMPTY, Mode.NONE));
        }
    }

    public enum Mode implements StringRepresentable {
        NONE(false),
        PLAYING(false),
        PLAY(true),
        STOP(true),
        PAUSE(true),
        NEXT(true),
        SWITCH_PLAY_MODE(true);

        public final boolean input;
        Mode(boolean input){
            this.input = input;
        }
        @Override
        public @NotNull String getSerializedName() {
            return toString().toLowerCase(Locale.ROOT);
        }

        public Component getName(){
            return Component.translatable(String.format("imp.text.boombox_controller.mode.%s", getSerializedName()));
        }
    }

    public static void saveToNbt(CompoundTag tag, BoomboxControllerData data, String key) {
        if(data == null)return;
        BoomboxControllerData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                .resultOrPartial(LOGGER::error)
                .ifPresent(nbt -> tag.put(key, nbt));
    }

    public static BoomboxControllerData loadFromNbt(CompoundTag tag, String key) {
        if (!tag.contains(key)) return new BoomboxControllerData(List.of());
        Tag encoded = tag.get(key);
        DataResult<BoomboxControllerData> result = BoomboxControllerData.CODEC.parse(NbtOps.INSTANCE, encoded);
        return result.resultOrPartial(LOGGER::error).orElse(new BoomboxControllerData(List.of()));
    }

    public List<Signal> getEditableSignals(){
        return new ArrayList<>(signals);
    }
}
