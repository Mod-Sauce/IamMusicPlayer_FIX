package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.gui.components.SearchMusicsFixedListWidget;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.media.MusicMedia;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.util.FlagThread;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class SearchMusicMMMonitor extends MusicManagerMonitor {
    private static final ResourceLocation SEARCH_MUSIC_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/music_manager/monitor/search_music.png");
    private static final Component SEARCHING_TEXT = Component.translatable("imp.text.searching");
    /**
     * Local copy of the result batch for rendering/scroll only: the authoritative list lives on the **screen**
     * ({@link MusicManagerScreen#getSearchResults()}), so going back restores the same results.
     */
    private final List<MusicMediaResult> searchMusics = new ArrayList<>();
    private SearchMusicsFixedListWidget searchMusicsFixedButtonsList;
    private EditBox searchNameEditBox;
    private SearchThread searchThread;
    /** Grab focus on the first tick after entering the screen (see {@link #tick()}). */
    private boolean focusPending;
    /** New results are pending "scroll back to top" (set by the worker thread, consumed by the render thread, see {@link #tick()}). */
    private boolean scrollToTopPending;

    public SearchMusicMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        // Move the screen's results into the local view first: when returning from "add music" the previous batch must show immediately (no re-search)
        this.searchMusics.clear();
        this.searchMusics.addAll(getScreen().getSearchResults());
        this.searchMusicsFixedButtonsList = this.addRenderWidget(new SearchMusicsFixedListWidget(getStartX() + 2, getStartY() + 25, 366, 172, Component.translatable("imp.fixedList.searchMusic"), 4, searchMusics, (widget, item) -> {
            if (item.directory()) {
                this.searchNameEditBox.setValue(item.source().getIdentifier());
                setMusicSearchName(item.source().getIdentifier());
            } else {
                setMusicSourceName(item.source().getIdentifier());
                getScreen().lastSearch = true;
                insMonitor(MusicManagerBlockEntity.MonitorType.ADD_MUSIC);
            }
        }, this.searchMusicsFixedButtonsList));

        this.searchNameEditBox = new EditBox(IIMPSmartRender.mc.font, getStartX() + 2, getStartY() + 11, 367, 12, Component.translatable("imp.editBox.musicSearchName"));
        this.searchNameEditBox.setMaxLength(300);
        this.searchNameEditBox.setValue(getMusicSearchName());
        this.searchNameEditBox.setResponder(this::setMusicSearchName);
        addRenderWidget(this.searchNameEditBox);

        // The search box does not focus itself, so you have to click once before typing; hence the focus is grabbed on the first tick.
        // Reason: the whole IMP project never sets an initial focus, and on this screen the result list widget is registered **first**,
        // while setting it in init would be overwritten by the screen's own initial focus logic.
        this.focusPending = true;

        // Only re-search when "the search term changed" (returning from elsewhere does not change it, so do not wipe the results)
        if (this.searchMusics.isEmpty() || !getMusicSearchName().equals(lastSearchedName))
            startMusicSearch(getMusicSearchName());
    }

    /**
     * Pressing ESC on this screen goes back to the "add music" page (instead of closing the whole interface).
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            insMonitor(MusicManagerBlockEntity.MonitorType.ADD_MUSIC);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        // The batch of results on the screen is authoritative: when something else changed it (e.g. another search) sync it over
        var screenResults = getScreen().getSearchResults();
        if (screenResults.size() != searchMusics.size() || !screenResults.equals(searchMusics)) {
            searchMusics.clear();
            searchMusics.addAll(screenResults);
            scrollToTopPending = true;
        }
        if (focusPending) {
            focusPending = false;
            // Go through the screen's setFocused: it also sets the focused flag on the widget;
            // calling the widget's own setFocused alone would not route keyboard events to it.
            getScreen().setFocused(this.searchNameEditBox);
        }
        // When new results arrive, scroll the list back to the top (otherwise the list stays at its old scroll position). Root cause:
        // the list widget is created in init and reused, and Otya's FixedListWidget inherits scrollAmount from the old widget.
        // ⚠️ The actual flag is set in the worker thread's setSearchMusics, the widget is touched here on the render thread.
        if (scrollToTopPending && searchMusicsFixedButtonsList != null) {
            scrollToTopPending = false;
            searchMusicsFixedButtonsList.setScrollAmount(0f);   // 0 = the first entry (clamped to [0,1] internally)
            LinkDebug.log("search scroll-to-top", "results", searchMusics.size());
        }
    }

    @Override
    public void depose() {
        super.depose();
        stopMusicSearch();
        // Note: **do not clear** the results —— they belong to the screen, and are still needed when leaving this screen
        // (e.g. to look at one entry's details) and coming back.
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        OERenderUtils.drawTexture(SEARCH_MUSIC_TEXTURE, guiGraphics.pose(), getStartX(), getStartY(), 0f, 0f, width, height, width, height);
        if (searchMusics.isEmpty() && searchThread != null && searchThread.isAlive())
            drawSmartText(guiGraphics, SEARCHING_TEXT, getStartX() + 3, getStartY() + 27);
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        OERenderUtils.renderTextureSprite(SEARCH_MUSIC_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 3, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);
        renderSmartEditBoxSprite(poseStack, multiBufferSource, 2, 11, OERenderUtils.MIN_BREADTH * 3, 367, 12, i, j, onPxW, onPxH, monitorHeight, getMusicSearchName(blockEntity));

        renderScrollbarSprite(poseStack, multiBufferSource, 359, 25, OERenderUtils.MIN_BREADTH * 3, 172, i, j, onPxW, onPxH, monitorHeight, 0, 6);
    }

    private void startMusicSearch(String name) {
        this.stopMusicSearch();
        this.lastSearchedName = name == null ? "" : name;
        // Clear the old results immediately, otherwise the list still shows the previous results for an instant after the term changed.
        // What is cleared is the copy on the **screen** (authoritative); the local view syncs to it in tick.
        getScreen().clearSearchResults();
        this.searchMusics.clear();
        this.searchThread = new SearchThread(name);
        this.searchThread.start();
    }

    private void stopMusicSearch() {
        if (this.searchThread != null) {
            this.searchThread.stopped();
            this.searchThread = null;
        }
    }

    public String getMusicSearchName() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity blockEntity)
            return getMusicSearchName(blockEntity);
        return "";
    }

    public String getMusicSearchName(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getMusicSearchName(mc.player);
    }

    private void setMusicSearchName(String name) {
        if (!getMusicSearchName().equals(name))
            startMusicSearch(name);
        getScreen().insMusicSearchName(name);
    }

    /** The term the last search was actually sent with (used to decide "whether to re-search when returning from elsewhere"). */
    private String lastSearchedName = "";
    private void setMusicSourceName(String name) {
        getScreen().insMusicSourceName(name);
    }

    public MusicMedia getRawMusicLoaderType() {
        return IMPMusicMedias.getAllMedia().get(getMusicLoaderType());
    }

    public String getMusicLoaderType() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity blockEntity)
            return getMusicLoaderType(blockEntity);
        return "auto";
    }

    public String getMusicLoaderType(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getMusicLoaderType(mc.player).isEmpty() ? "auto" : blockEntity.getMusicLoaderType(mc.player);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.ADD_MUSIC;
    }

    private class SearchThread extends FlagThread {
        private final String name;

        public SearchThread(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            if (isStopped())
                return;
            var lt = getRawMusicLoaderType();
            if (lt == null || !lt.isSearchable())
                return;

            if (isStopped())
                return;

            List<MusicMediaResult> slst;
            slst = lt.search(name);

            if (isStopped())
                return;

            setSearchMusics(slst);
        }

        private void setSearchMusics(List<MusicMediaResult> musics) {
            if (musics != null) {
                // Results go to the **screen** (the authoritative copy); the local view is synced by tick. The **channel**
                // is recorded with them, since it decides whether the "back to search results" button may appear.
                getScreen().setSearchResults(musics, getMusicLoaderType());
                scrollToTopPending = true;
            }
        }
    }
}
