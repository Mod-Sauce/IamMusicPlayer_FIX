package dev.felnull.imp.client.gui.components;

import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.IIMPComparable;
import dev.felnull.imp.music.resource.Music;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class SortButton extends IMPButton implements IIMPSmartRender {
    protected final boolean longed;

    private SortButton(int x, int y, int w, int tx, Component component, OnPress onPress, boolean longed, Screen screen) {
        super(x, y, w, 9, component, onPress);
        // super(x, y, w, 9, component, onPress, longed ? NO_TOOLTIP : (button, poseStack, px, py) -> screen.renderTooltip(poseStack, getText(button), px, py));
        // super(x, y, w, 9, tx, 30, 9, MusicManagerMonitor.WIDGETS_TEXTURE, 256, 256, onPress, longed ? NO_TOOLTIP : (button, poseStack, px, py) -> screen.renderTooltip(poseStack, getText(button), px, py), Component.translatable("imp.button.sort"));
        this.longed = longed;
    }

    @Override
    public void onPress() {
        cycle();
        setTooltip(Tooltip.create(getToolTipComponent()));
        super.onPress();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
        drawSmartButtonBox(guiGraphics, getX(), getY(), width, height, isHoveredOrFocused());
      /*  if (this.isHoveredOrFocused())
            this.renderToolTip(poseStack, mx, my);*/
    }

    abstract public void cycle();

    abstract public Component getToolTipComponent();

    public static enum SortType {
        NAME("name", (o1, o2) -> {
            return o1.getCompareName().compareTo(o2.getCompareName());
        }), PLAYER("player", (o1, o2) -> {
            return o1.getComparePlayerName().compareTo(o2.getComparePlayerName());
        }), CREATE_DATE("create_date", (o1, o2) ->
                // Long.compare, not `(int) (a - b)`: the difference of two millisecond timestamps overflows int and scrambles the order
                // (the server sorts the same way with comparingLong, see IBoomboxRinger).
                Long.compare(o1.getCompareDate(), o2.getCompareDate())),
        /** Bilibili upload date (not "when it was added to the playlist"); the playlist page enables it per source. */
        PUBLISH_DATE("publish_date", (o1, o2) -> Long.compare(pubdateOf(o1), pubdateOf(o2)));
        private final String name;
        private final Comparator<IIMPComparable> comparator;
        private final Comparator<IIMPComparable> rcomparator;
        private final Component componentName;

        SortType(String name, Comparator<IIMPComparable> comparator) {
            this.name = name;
            this.comparator = comparator;
            this.rcomparator = comparator.reversed();
            this.componentName = Component.translatable("imp.sortType." + name);
        }

        /** {@code 0} = never recorded in the cache; those songs sort together at the end. */
        private static long pubdateOf(IIMPComparable o) {
            if (!(o instanceof Music music))
                return 0L;
            var source = music.getSource();
            if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
                return 0L;
            return BiliBiliArcTags.getInstance().pubdateOf(BiliBiliUtil.stripPage(source.getIdentifier()));
        }

        public String getName() {
            return name;
        }

        public Comparator<IIMPComparable> getComparator(OrderType orderType) {
            return orderType.isReverse() ? rcomparator : comparator;
        }

        public Component getComponentName() {
            return componentName;
        }

        /** Restore the state from the name stored in config; on mismatch (config edited by hand) fall back to NAME. */
        public static SortType byName(String name) {
            for (SortType v : values()) {
                if (v.name.equals(name))
                    return v;
            }
            return NAME;
        }
    }

    public static enum OrderType {
        ASCENDING("ascending", false), DESCENDING("descending", true);
        private final String name;
        private final boolean reverse;
        private final Component componentName;

        OrderType(String name, boolean reverse) {
            this.name = name;
            this.reverse = reverse;
            this.componentName = Component.translatable("imp.orderType." + name);
        }

        public boolean isReverse() {
            return reverse;
        }

        public String getName() {
            return name;
        }

        public Component getComponentName() {
            return componentName;
        }

        /** Restore the state from the name stored in config; on mismatch (config edited by hand) fall back to DESCENDING. */
        public static OrderType byName(String name) {
            for (OrderType v : values()) {
                if (v.name.equals(name))
                    return v;
            }
            return DESCENDING;
        }
    }

    public static class SortTypeButton extends SortButton {
        /** Three modes by default; the upload-date mode only means something for UP-imported playlists and is enabled on demand by the playlist page. */
        private static final List<SortType> BASE_TYPES = List.of(SortType.NAME, SortType.PLAYER, SortType.CREATE_DATE);
        /** The mode the user picked (including one the current playlist cannot use): switching playlists and back must keep it. */
        private SortType preference = SortType.NAME;
        private List<SortType> types = BASE_TYPES;
        private boolean publishDateEnabled;

        public SortTypeButton(int x, int y, OnPress onPress, boolean longed, Screen screen) {
            super(x, y, longed ? 97 : 9, longed ? 61 : 52, Component.translatable("imp.button.sort"), onPress, longed, screen);
            if (!longed)
                setTooltip(Tooltip.create(getToolTipComponent()));
        }

        @Override
        public void cycle() {
            var i = types.indexOf(getSortType());
            preference = types.get((i + 1) % types.size());
        }

        /** The switch for the upload-date mode (playlist page only): it changes the cycle-able list and leaves the user's choice alone. */
        public void setPublishDateEnabled(boolean enabled) {
            if (enabled == publishDateEnabled)
                return;
            publishDateEnabled = enabled;
            var list = new ArrayList<SortType>(BASE_TYPES);
            if (enabled)
                list.add(SortType.PUBLISH_DATE);
            types = List.copyOf(list);
            if (!longed)
                setTooltip(Tooltip.create(getToolTipComponent()));
        }

        @Override
        public Component getToolTipComponent() {
            return getSortType().getComponentName();
        }

        /** The effective mode: the preferred one when it is available, otherwise the first one (the preference is kept). */
        public SortType getSortType() {
            return types.contains(preference) ? preference : types.get(0);
        }

        /** Restore the state from the value stored in config (not editable in the config screen, applied only when the page opens). */
        public void setSortType(SortType sortType) {
            if (sortType == null)
                return;
            this.preference = sortType;
            if (!longed)
                setTooltip(Tooltip.create(getToolTipComponent()));
        }

        public <T extends IIMPComparable> List<T> sort(List<T> target, OrderTypeButton orderTypeButton) {
            return target.stream().sorted(getSortType().getComparator(orderTypeButton.getOrderType())).toList();
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            super.renderWidget(guiGraphics, i, j, f);
            OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(), getX() + 1, getY() + 1, 73 + getSortType().ordinal() * 7, 0, 7, 7);
            if (longed) {
                drawSmartText(guiGraphics, getToolTipComponent(), getX() + 9, getY() + 1);
            }
        }
    }

    public static class OrderTypeButton extends SortButton {
        private OrderType type = OrderType.DESCENDING;

        public OrderTypeButton(int x, int y, OnPress onPress, boolean longed, Screen screen) {
            super(x, y, longed ? 88 : 9, longed ? 158 : 52, Component.translatable("imp.button.order"), onPress, longed, screen);
            if (!longed)
                setTooltip(Tooltip.create(getToolTipComponent()));
        }

        @Override
        public void cycle() {
            type = OrderType.values()[(type.ordinal() + 1) % OrderType.values().length];
        }

        @Override
        public Component getToolTipComponent() {
            return type.getComponentName();
        }

        public OrderType getOrderType() {
            return type;
        }

        /** Restore the state from the value stored in config. */
        public void setOrderType(OrderType orderType) {
            if (orderType == null)
                return;
            this.type = orderType;
            if (!longed)
                setTooltip(Tooltip.create(getToolTipComponent()));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            super.renderWidget(guiGraphics, i, j, f);
            OERenderUtils.drawTexture(MusicManagerMonitor.WIDGETS_TEXTURE, guiGraphics.pose(), getX() + 1, getY() + 1, 73 + getOrderType().ordinal() * 7, 7, 7, 7);
            if (longed) {
                drawSmartText(guiGraphics, getToolTipComponent(), getX() + 9, getY() + 1);
            }
        }
    }
}
