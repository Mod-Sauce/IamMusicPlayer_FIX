package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.gui.IIMPSmartRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.client.gui.components.FixedListWidget;

import java.util.List;

/**
 * Category list (「全部」/「未分类」, the UP's 合集, then the parent zones) — a list rather than a cycling button because a UP can have 40+ 合集; each row carries its own {@code ticked} and reports via {@code onPick}, and the row records keep identity stable for the base widget's hover logic.
 */
public class SpaceCategoryListWidget extends IMPBaseFixedListWidget<SpaceCategoryListWidget.Row> {
    /** What a row means; {@link #HEADER} rows are pure separators and are not clickable. */
    public enum Kind {
        ALL, UNGROUPED, HEADER, SEASON, ZONE,
        /** 「多P」: 分P 数 > 1 as far as the **local part cache** knows (never asks Bilibili — see the playlist page). */
        MULTI_PART,
        /** 「非多P」: everything else, including videos whose part list was never looked up. */
        SINGLE_PART
    }

    /** One row. {@code count} is ignored by {@link Kind#HEADER}. */
    public record Row(Kind kind, Component label, String filterName, int count, boolean ticked) {
        public static Row all(int count, boolean ticked) {
            return new Row(Kind.ALL, Component.translatable("imp.text.spaceCategoryAll"), "", count, ticked);
        }

        public static Row ungrouped(int count, boolean ticked) {
            return new Row(Kind.UNGROUPED, Component.translatable("imp.text.spaceCategoryUngrouped"), "", count, ticked);
        }

        public static Row header(Component label) {
            return new Row(Kind.HEADER, label, "", 0, false);
        }

        public static Row season(String name, int count, boolean ticked) {
            return new Row(Kind.SEASON, Component.literal(name), name, count, ticked);
        }

        public static Row zone(String name, int count, boolean ticked) {
            return new Row(Kind.ZONE, Component.literal(name), name, count, ticked);
        }

        public static Row multiPart(int count, boolean ticked) {
            return new Row(Kind.MULTI_PART, Component.translatable("imp.text.spaceCategoryMultiPart"), "", count, ticked);
        }

        public static Row singlePart(int count, boolean ticked) {
            return new Row(Kind.SINGLE_PART, Component.translatable("imp.text.spaceCategorySinglePart"), "", count, ticked);
        }

        /** Same category as another row, ignoring the tick (used to find "the row that is currently applied"). */
        public boolean sameCategory(Row other) {
            return other != null && kind == other.kind() && filterName.equals(other.filterName);
        }
    }

    private final java.util.function.Consumer<Row> onPick;

    public SpaceCategoryListWidget(int x, int y, int width, int height, @NotNull Component message, int entryShowCount,
                                   @NotNull List<Row> entryList, @NotNull java.util.function.Consumer<Row> onPick,
                                   @Nullable FixedListWidget<Row> old) {
        super(x, y, width, height, message, entryShowCount, entryList, Row::label, null, false, old);
        this.onPick = onPick;
    }

    @Override
    protected void renderOneButton(GuiGraphics guiGraphics, Row item, int lnum, int bnum, int bX, int bY,
                                   int mx, int my, float parTick, boolean selectedFromList) {
        int rowWidth = getIndividualWidth();

        // Section separators ("合集" / "分区"): dim text, no box, no checkbox
        if (item.kind() == Kind.HEADER) {
            drawSmartText(guiGraphics, item.label(), bX + 3, bY + 3, 0xFF7A7A7A);
            return;
        }

        drawSmartButtonBox(guiGraphics, bX, bY, rowWidth, getIndividualHeight(),
                item.ticked() ? 2 : this.getYImage(this.isEntryHovered(bnum)));

        int color = item.ticked() ? 0xFFFFFFFF : 0xFF000000;
        // Same checkbox convention as the video list: bright yellow when ticked, dark grey when not
        drawSmartFixedWidthText(guiGraphics, Component.literal(item.ticked() ? "[x]" : "[ ]"), bX + 2, bY + 3, 14,
                item.ticked() ? 0xFFFFFF00 : 0xFF444444);

        var count = Component.literal(String.valueOf(item.count()));
        float countWidth = IIMPSmartRender.mc.font.width(count) + 3;
        drawSmartFixedWidthText(guiGraphics, item.label(), bX + 18, bY + 3, rowWidth - 21 - countWidth, color);
        drawSmartText(guiGraphics, count, bX + rowWidth - countWidth, bY + 3, color);
    }

    /**
     * A click reports the row; the base implementation is deliberately not called — it would keep the widget's own single selection next to the page's tick state.
     */
    @Override
    public void onPress(int num) {
        int index = getCurrentFirstEntryIndex() + num;
        var entries = getEntryList();
        if (index < 0 || index >= entries.size() || !isEntryHovered(num))
            return;
        var row = entries.get(index);
        if (row.kind() == Kind.HEADER)
            return;
        onPick.accept(row);
    }
}
