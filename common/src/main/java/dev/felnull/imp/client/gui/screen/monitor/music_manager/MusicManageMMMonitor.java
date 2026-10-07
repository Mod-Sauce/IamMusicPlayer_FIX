package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliPacer;
import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.gui.components.ManageOwnersFixedListWidget;
import dev.felnull.imp.client.gui.components.MusicsManageFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.components.TextButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.networking.IMPPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import dev.felnull.imp.client.gui.PlayerNameUtil;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;

import java.util.*;

/**
 * "Music Management" page: clicking a row checks it (several rows accumulate = multi-select).
 * Layout: list (1,20, 301×136 = 5 rows of 27), list ops (305, 60×14), actions (y=180), undo (3,169); rows stay 27 high,
 * so the uploader name is not drawn.
 */
public class MusicManageMMMonitor extends MusicManagerMonitor {
    private static final Component BACK_TEXT = Component.translatable("gui.back");
    private static final Component SELECT_ALL_TEXT = Component.translatable("imp.button.manageSelectAll");
    private static final Component INVERT_TEXT = Component.translatable("imp.button.manageInvert");
    private static final Component CLEAR_TEXT = Component.translatable("imp.button.manageClear");
    private static final Component DELETE_SELECTED_TEXT = Component.translatable("imp.button.manageDeleteSelected").withStyle(ChatFormatting.RED);
    /** The text of "delete by uploader" **while idle** (clicking it = enter "pick uploader" mode, it does not delete directly). */
    private static final Component PLAYER_PICK_TEXT = Component.translatable("imp.button.manageDeleteMine").withStyle(ChatFormatting.RED);
    /** The text of that button after entering "pick uploader" mode (clicking again = leave, back to the music list). */
    private static final Component BACK_TO_MUSICS_TEXT = Component.translatable("imp.button.manageBackToMusics");
    private static final Component LIST_TEXT = Component.translatable("imp.fixedList.manageMusics");
    /** Title of the uploader list (the same list area; the widget is swapped when the mode changes). */
    private static final Component OWNER_LIST_TEXT = Component.translatable("imp.fixedList.manageOwners");

    private static final Component TIP1 = Component.translatable("imp.text.manageTip1");
    private static final Component TIP2 = Component.translatable("imp.text.manageTip2");
    /** The hint on that line in "pick uploader" mode (TIP2's position): clicking a player only changes the selection, deleting still uses "delete selected". */
    private static final Component PICK_TIP = Component.translatable("imp.text.managePickTip");
    /** Rewritten in pick mode when nothing can be deleted, so a row of gray checkboxes does not look broken. */
    private static final Component PICK_NO_PERM_TEXT = Component.translatable("imp.text.managePickNoPerm");

    /** 「展开分P」: appends parts 2..n of the ticked B站 videos (see {@link #startPartsExpansion()}). */
    private static final Component EXPAND_PARTS_TEXT = Component.translatable("imp.button.manageExpandParts");
    /** 「选最近加入」: ticks the songs of the newest add-batch (see {@link #selectRecentBatch}). */
    private static final Component RECENT_TEXT = Component.translatable("imp.button.manageSelectRecent");
    /** 「扫失效」: ticks the B站 songs Bilibili reports as gone ({@code -404}); select-only, like the other clean-up keys. */
    private static final Component DEAD_SCAN_TEXT = Component.translatable("imp.button.manageScanDead");
    /** The same key while the scan runs (pressing it again stops the walk). */
    private static final Component DEAD_SCAN_RUNNING_TEXT = Component.translatable("imp.button.manageScanDeadRunning");
    /** How close in time two songs must be to count as **one** add batch (an add writes its own timestamp per song). */
    private static final long RECENT_BATCH_WINDOW_MS = 3000L;

    /**
     * The songs of the last add-batch: {@code createDate} within {@link #RECENT_BATCH_WINDOW_MS} of the newest one (one
     * add stamps every song with the same millisecond).
     */
    List<UUID> selectRecentBatch() {
        long newest = Long.MIN_VALUE;
        for (var m : allMusics) {
            if (m != null && m.getCreateDate() > newest)
                newest = m.getCreateDate();
        }
        var out = new ArrayList<UUID>();
        if (newest == Long.MIN_VALUE)
            return out;
        for (var m : allMusics) {
            if (m == null)
                continue;
            var age = newest - m.getCreateDate();
            if (age >= 0 && age <= RECENT_BATCH_WINDOW_MS)
                out.add(m.getUuid());
        }
        return out;
    }

    /** 「选最近加入」: clear the filters (the selection may only hold visible rows) and tick the newest add-batch — nothing is deleted. */
    private void requestSelectRecentBatch() {
        setPlayerPickMode(false);
        searchText = "";
        loaderFilter = null;
        ownerFilter = null;
        if (searchEditBox != null)
            searchEditBox.setValue("");
        applyFilter();
        var batch = selectRecentBatch();
        if (batch.isEmpty())
            return;
        selected.clear();
        selected.addAll(batch);
        cleanDupesResult = Component.translatable("imp.text.manageSelectRecentTicked", batch.size());
        cleanDupesResultUntil = System.currentTimeMillis() + CLEAN_DUPES_RESULT_MS;
    }

    /** 「清理重复」: no duplicates → plain label (inactive); with duplicates → 「清理(N)」, which still fits the 60 px key. */
    private static final Component DUPLICATE_TEXT = Component.translatable("imp.button.manageCleanDuplicates");
    /** Shown for a moment after the key ticked the duplicates: the user still has to press 「删除选中」. */
    private static final Component DUPLICATE_TICKED_TEXT = Component.translatable("imp.text.manageCleanDuplicatesTicked");

    /** Top search box: filters by **music name / author** (same spec as the playlist page: `SEARCH_BOX_WIDTH=140`, y=3, height 12). */
    private static final Component SEARCH_HINT_TEXT = Component.translatable("imp.editBox.manageMusicSearch");
    /** The name shown on the two filter buttons when there is "no filter". */
    private static final Component ALL_TEXT = Component.translatable("imp.text.manageFilterAll");

    /** Undo window: within this time after deleting, the **clickable undo text** appears on the line below the list. */
    private static final long UNDO_WINDOW_MS = 6000L;

    /** List widget: x=1, y=20, 301×136 = 5 rows of 27; the last 9px is Otya's scrollbar, so the right column starts at x=305. */
    private static final int LIST_X = 1;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 301;
    private static final int LIST_H = 136;
    private static final int LIST_ROWS = 5;

    /** The three list-action buttons of the right column: x=305, 60×14, gap 4 (the small right-side button spec of SavedPlayListBase / AddMusic). */
    private static final int GUTTER_X = 305;
    private static final int GUTTER_W = 60;
    private static final int GUTTER_H = 14;
    private static final int GUTTER_GAP = 4;
    private static final int GUTTER_Y1 = LIST_Y;
    private static final int GUTTER_Y2 = GUTTER_Y1 + GUTTER_H + GUTTER_GAP;
    private static final int GUTTER_Y3 = GUTTER_Y2 + GUTTER_H + GUTTER_GAP;
    /** The 4th and 5th buttons of the right column = the two **filter** cycle buttons (source / uploader). */
    private static final int GUTTER_Y4 = GUTTER_Y3 + GUTTER_H + GUTTER_GAP;
    private static final int GUTTER_Y5 = GUTTER_Y4 + GUTTER_H + GUTTER_GAP;
    /** The 6th slot: 「清理重复」 (still beside the list — the list ends at y=156, this one at 124). */
    private static final int GUTTER_Y6 = GUTTER_Y5 + GUTTER_H + GUTTER_GAP;
    /** The 7th slot: 「选最近加入」 (y=128..142, last one that still fits beside the list). */
    private static final int GUTTER_Y7 = GUTTER_Y6 + GUTTER_H + GUTTER_GAP;
    /**
     * 「扫失效」: the 8th slot, at y=146. ⚠️ Two pixels shorter on purpose ({@link #GUTTER_H8}): a full-height key would touch the tip line at y=159.
     */
    private static final int GUTTER_Y8 = GUTTER_Y7 + GUTTER_H + GUTTER_GAP;
    private static final int GUTTER_H8 = GUTTER_H - 2;
    /** Filter button text must be truncated to fit inside the button (4px margin in the 60 width): the label is built from a lang key + the current value, so overlong text is omitted. */
    private static final int FILTER_TEXT_WIDTH = GUTTER_W - 4;

    /** Search box (top center, same spec as the playlist page). */
    private static final int SEARCH_BOX_WIDTH = 140;
    private static final int SEARCH_BOX_Y = 3;
    private static final int SEARCH_BOX_H = 12;

    /** Bottom bar main buttons: y=180, 87×15. */
    private static final int ROW_Y = 180;
    private static final int BTN_W = 87;
    private static final int BTN_H = 15;
    /** The x of "back" on the left of the bottom bar (5, which is what every page uses). */
    private static final int ROW_LEFT_X = 5;
    /** The x of 「展开分P」, the bottom bar's only free slot: back ends at 92 and the right pair starts at width-95-87=188. */
    private static final int ROW_PARTS_X = 96;
    /** The x of the two buttons on the right of the bottom bar: width-95-87 / width-5-87 (same as DetailMusicMMMonitor). */
    private static final int ROW_RIGHT_A_MARGIN = 95;
    private static final int ROW_RIGHT_B_MARGIN = 5;

    /** Text lines: one above the list (selection count) and two below it (hints / undo countdown). */
    private static final int TEXT_X = 3;
    private static final int COUNT_Y = 11;
    private static final int TIP1_Y = 159;
    private static final int TIP2_Y = 169;

    /**
     * **All** music synced from the server. ⚠️ Everything the page acts on (widget, select all, delete copies) uses the filtered {@link #musics} view instead.
     */
    private final List<Music> allMusics = new ArrayList<>();
    /** **Visible** music (= {@link #allMusics} filtered by {@link #searchText}/{@link #loaderFilter}/{@link #ownerFilter}). */
    private final List<Music> musics = new ArrayList<>();
    /** Ids of the checked music (accumulated by clicking rows). ⚠️ Always a subset of the visible list, see {@link #applyFilter()}. */
    private final Set<UUID> selected = new LinkedHashSet<>();

    /** Search text (music name / author, substring, case-insensitive). */
    private String searchText = "";
    private EditBox searchEditBox;
    /** Source filter: null = all, otherwise = {@code MusicSource#getLoaderType()}. */
    @Nullable
    private String loaderFilter;
    /** Uploader filter: null = all, otherwise = the uploader uuid (double-clicking an uploader avatar switches here too). */
    @Nullable
    private UUID ownerFilter;
    private SmartButton sourceFilterButton;
    private SmartButton ownerFilterButton;
    /** Cached once: the in-world panel instance draws every frame and its filter is always "all", so the text never changes. */
    @Nullable
    private String panelSearchHint;
    @Nullable
    private Component panelSourceFilterText;
    @Nullable
    private Component panelOwnerFilterText;

    /** The copy left by the last delete (used by undo); null = nothing to undo right now. */
    @Nullable
    private List<Music> lastDeleted;
    private long undoDeadline;

    private List<Music> musicsCash;
    /** The playlist whose music list has **really been synced once** (null = never). Tells "not synced yet" from "synced and really empty". */
    @Nullable
    private UUID musicsSyncedPlayList;
    private MusicsManageFixedListWidget musicList;
    private SmartButton selectAllButton;
    private SmartButton invertButton;
    private SmartButton clearButton;
    private SmartButton deleteSelectedButton;
    private SmartButton playerPickButton;
    /** 「展开分P」: see {@link #startPartsExpansion()}. */
    private SmartButton expandPartsButton;
    /** 「清理重复」 (right column, 6th slot): ticks every repeated entry of this playlist. */
    private SmartButton cleanDupesButton;
    /** 「选最近加入」 (7th slot): ticks the songs of the newest add-batch. */
    private SmartButton recentBatchButton;
    /** 「扫失效」 (8th slot): ticks the songs whose submission Bilibili reports as gone. */
    private SmartButton deadScanButton;
    /** The running dead-link scan, or null (see {@link #startDeadScan()}). */
    @Nullable
    private DeadScan deadScan;
    /** What the last scan found (takes TIP1's line over for a few seconds, like the other clean-up keys). */
    @Nullable
    private Component deadScanResult;
    private long deadScanResultUntil;
    /** The running 分P job, or null. */
    @Nullable
    private PartsExpansion partsExpansion;
    /** What the last 分P run did (it takes TIP1's line over while it is set); null = nothing to report. */
    @Nullable
    private Component partsExpandResult;

    /** "Delete by uploader" pick mode: the list area shows the uploader list, and clicking a player only changes the selection. */
    private boolean playerPickMode;
    /** Data of the uploader list (computed on the fly from {@link #musics}, see {@link #rebuildOwners()}). */
    private final List<ManageOwnersFixedListWidget.Owner> owners = new ArrayList<>();
    private ManageOwnersFixedListWidget ownerList;
    /** Timestamp of the uploader-list rebuild in pick mode (player names resolve asynchronously, so it is refreshed once a second). */
    private long ownersRefreshAt;
    /** What the pick-mode line shows now ({@link #PICK_TIP} or {@link #PICK_NO_PERM_TEXT}); set in {@link #updateButtons()}, never in render. */
    private Component pickTipText = PICK_TIP;
    /** "Undo" = **clickable text** (see {@link TextButton}), visible only inside the undo window, where it takes over TIP2's line. */
    private TextButton undoText;

    public MusicManageMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);

        this.musicList = addRenderWidget(new MusicsManageFixedListWidget(
                getStartX() + LIST_X, getStartY() + LIST_Y, LIST_W, LIST_H, LIST_TEXT, LIST_ROWS, musics,
                (widget, item) -> onMusicRowPressed(item),
                // The `selectable` flag does not affect the callback: Otya's FixedListWidget#onPress calls `onPressEntry`
                // outside `if (selectable)`. The checkbox is drawn from this page's own selected set (MusicsManageFixedListWidget#selectedIds).
                false, this.musicList,
                (java.util.function.Function<Music, Boolean>) this::canSelect,
                selected));

        // The **uploader list** of "delete by uploader": **the same list area** as the music list (same coordinates and size).
        // The in-world monitor path (renderAppearance) never enters this mode, so it keeps drawing only the music list.
        this.ownerList = addRenderWidget(new ManageOwnersFixedListWidget(
                getStartX() + LIST_X, getStartY() + LIST_Y, LIST_W, LIST_H, OWNER_LIST_TEXT, LIST_ROWS, owners,
                (widget, item) -> onOwnerRowPressed(item), false, this.ownerList, selected,
                // Double-clicking the **avatar** on a row = drill into "only this uploader's music"
                this::enterOwnerView));
        this.ownerList.visible = false;

        // Right column: three list-action buttons (stacked vertically, kept apart from the bottom bar action buttons) — select all / invert / clear selection
        this.selectAllButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y1,
                GUTTER_W, GUTTER_H, SELECT_ALL_TEXT, n -> selectAll()));
        this.invertButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y2,
                GUTTER_W, GUTTER_H, INVERT_TEXT, n -> invertSelection()));
        this.clearButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y3,
                GUTTER_W, GUTTER_H, CLEAR_TEXT, n -> selected.clear()));
        // The two below in the right column = **filter** cycle buttons (click to switch to the next one; the label is built from a lang key + the current value, truncated when too wide)
        this.sourceFilterButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y4,
                GUTTER_W, GUTTER_H, sourceFilterText(), n -> cycleSourceFilter()));
        this.ownerFilterButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y5,
                GUTTER_W, GUTTER_H, ownerFilterText(), n -> cycleOwnerFilter()));
        // 「清理重复」: the 6th slot of the right column (y=110..124, still beside the list, which ends at y=156).
        // Select-only (see requestCleanDuplicates) — the actual deletion stays on 「删除选中」.
        this.cleanDupesButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y6,
                GUTTER_W, GUTTER_H, DUPLICATE_TEXT, n -> requestCleanDuplicates()));
        this.cleanDupesButton.active = false;
        // 「选最近加入」: the 7th slot, ticks the songs of the **last add-batch**. ⚠️ Select-only, like 「清理重复」 — nothing is deleted here.
        this.recentBatchButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y7,
                GUTTER_W, GUTTER_H, RECENT_TEXT, n -> requestSelectRecentBatch()));
        this.recentBatchButton.active = false;
        // 「扫失效」: the 8th slot (y=146, 2 px shorter). It ticks the B站 songs whose submission is **gone**; pressing it
        // again stops the walk. ⚠️ Select-only, like the other clean-up keys — the deletion stays on 「删除选中」.
        this.deadScanButton = addRenderWidget(new SmartButton(getStartX() + GUTTER_X, getStartY() + GUTTER_Y8,
                GUTTER_W, GUTTER_H8, DEAD_SCAN_TEXT, n -> toggleDeadScan()));
        this.deadScanButton.active = false;

        // Top search box: filters by music name/author (same spec as the playlist page). The hint text is drawn in render/renderAppearance.
        this.searchEditBox = new EditBox(IIMPSmartRender.mc.font,
                getStartX() + (width - SEARCH_BOX_WIDTH) / 2, getStartY() + SEARCH_BOX_Y,
                SEARCH_BOX_WIDTH, SEARCH_BOX_H, SEARCH_HINT_TEXT);
        this.searchEditBox.setMaxLength(128);
        this.searchEditBox.setValue(searchText);
        this.searchEditBox.setResponder(this::setSearchText);
        addRenderWidget(this.searchEditBox);

        // "Undo" is not a button but **clickable text** (see {@link TextButton}), on the line below the list (TIP2's position),
        // visible only inside the undo window.
        this.undoText = addRenderWidget(new TextButton(getStartX() + TEXT_X, getStartY() + TIP2_Y,
                Component.empty(), n -> doUndo()));
        this.undoText.visible = false;

        // Bottom bar: back + two delete actions (same line, same width and height)
        addRenderWidget(new SmartButton(getStartX() + ROW_LEFT_X, getStartY() + ROW_Y, BTN_W, BTN_H, BACK_TEXT, n -> {
            if (getParentType() != null) insMonitor(getParentType());
        }));
        this.deleteSelectedButton = addRenderWidget(new SmartButton(
                getStartX() + width - ROW_RIGHT_A_MARGIN - BTN_W, getStartY() + ROW_Y, BTN_W, BTN_H,
                DELETE_SELECTED_TEXT, n -> requestDeleteSelected()));
        this.playerPickButton = addRenderWidget(new SmartButton(
                getStartX() + width - ROW_RIGHT_B_MARGIN - BTN_W, getStartY() + ROW_Y, BTN_W, BTN_H,
                PLAYER_PICK_TEXT, n -> setPlayerPickMode(!playerPickMode)));

        // 「展开分P」: the submission list carries **no part count**, so learning it costs one `view` request per video —
        // hence an explicit action on the ticked rows instead of something the import does for every song.
        this.expandPartsButton = addRenderWidget(new SmartButton(
                getStartX() + ROW_PARTS_X, getStartY() + ROW_Y, BTN_W, BTN_H,
                EXPAND_PARTS_TEXT, n -> togglePartsExpansion()));
    }

    @Override
    public void tick() {
        super.tick();
        refreshMusics();
        // Pick mode: the uploader list follows the music data (already rebuilt in refreshMusics); player names resolve **asynchronously**
        // (`getPlayerNameByUUID` may return only a UUID string the first time), so it is rebuilt at most once per second in this mode.
        if (playerPickMode && System.currentTimeMillis() - ownersRefreshAt >= 1000L) {
            ownersRefreshAt = System.currentTimeMillis();
            rebuildOwners();
        }
        // Check expiry before refreshing widgets/text: the very tick that just crosses 6 seconds is already collapsed (no need to wait for the next tick)
        if (lastDeleted != null && System.currentTimeMillis() > undoDeadline) {
            lastDeleted = null;
            undoDeadline = 0L;
            hideUndoTextNow();
        }
        // The 分P result line belongs to the selection it was produced for: drop it as soon as that changes
        int selectionSig = selectionSignature();
        if (selectionSig != lastSelectionSignature) {
            lastSelectionSignature = selectionSig;
            partsExpandResult = null;
        }
        updateButtons();
        updateUndoText();
        tickPartsExpansion();
        tickDeadScan();
    }

    /**
     * Leaving the page stops the 分P job: each part list went to the cache and the server as soon as it was learned, so stopping only means "no further lookups".
     */
    @Override
    public void depose() {
        super.depose();
        var exp = partsExpansion;
        partsExpansion = null;
        if (exp != null)
            exp.cancelled = true;
        // The dead-link scan stops with the page as well: whatever it already found stays (it is collected, not applied
        // per request), so leaving early only means "no further requests".
        var scan = deadScan;
        deadScan = null;
        if (scan != null)
            scan.cancelled = true;
    }

    /**
     * The text the GUI draws; {@link #renderAppearance} (the in-world monitor) must draw the same lines, or one of the
     * two paths goes blank.
     */
    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);

        drawSmartText(guiGraphics, Component.translatable("imp.text.manageSelected", selected.size(), musics.size()),
                getStartX() + TEXT_X, getStartY() + COUNT_Y);
        // Draw a gray hint when the search box is empty (EditBox does not draw a placeholder itself; the playlist page patches it in the same way)
        if (searchText.isEmpty())
            drawSmartText(guiGraphics, SEARCH_HINT_TEXT, getStartX() + (width - SEARCH_BOX_WIDTH) / 2 + 4,
                    getStartY() + SEARCH_BOX_Y + 2, 0xFF808080);
        drawSmartText(guiGraphics, tipText(), getStartX() + TEXT_X, getStartY() + TIP1_Y);
        if (!isUndoAvailable())
            drawSmartText(guiGraphics, playerPickMode ? pickTipText : TIP2, getStartX() + TEXT_X, getStartY() + TIP2_Y);
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource,
                                 int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;

        // Note: this in-world monitor uses **another monitor instance** (MusicManagerBlockEntityRenderer
        // caches one per type, with screen=null, never init/tick). So it must not touch any widget field or getScreen(),
        // data always comes from getSyncManager() on the fly; the selection set on this instance is always empty (the panel only "looks like" it, it cannot be clicked).
        var pl = getSelectedMusicPlayList(blockEntity);
        var ms = getSyncManager().getMusics(getSelectedPlayList(blockEntity));
        int total = ms == null ? 0 : ms.size();

        // Selection count
        renderSmartTextSprite(poseStack, multiBufferSource,
                Component.translatable("imp.text.manageSelected", selected.size(), total),
                TEXT_X, COUNT_Y, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

        // Top search box: the panel only draws it (no input). ⚠️ The hint is fetched once as a string — the panel draws every frame.
        if (panelSearchHint == null)
            panelSearchHint = SEARCH_HINT_TEXT.getString();
        renderSmartEditBoxSprite(poseStack, multiBufferSource, (width - SEARCH_BOX_WIDTH) / 2, SEARCH_BOX_Y,
                OERenderUtils.MIN_BREADTH * 2, SEARCH_BOX_WIDTH, SEARCH_BOX_H, i, j, onPxW, onPxH, monitorHeight,
                panelSearchHint);

        // Hints / undo tip (the same line: while the window is open it shows "N deleted")
        // The panel cannot be clicked, so **another text** is used here (`…Panel`), not a copy of the GUI line's "click here to undo".
        if (isUndoAvailable()) {
            long left = Math.max(0L, (undoDeadline - System.currentTimeMillis() + 999L) / 1000L);
            renderSmartTextSpriteColorSprite(poseStack, multiBufferSource,
                    Component.translatable("imp.text.manageUndoHintPanel", left, lastDeleted.size()),
                    TEXT_X, TIP2_Y, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, 0xFFFF5555, i);
        } else {
            renderSmartTextSprite(poseStack, multiBufferSource, TIP2, TEXT_X, TIP2_Y,
                    OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
        }
        renderSmartTextSprite(poseStack, multiBufferSource, tipText(), TEXT_X, TIP1_Y,
                OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

        // List: rows + left column (checkbox above, uploader avatar below) — the same layout as MusicsManageFixedListWidget#renderOneButton
        renderFixedListSprite(poseStack, multiBufferSource, LIST_X, LIST_Y, OERenderUtils.MIN_BREADTH * 3,
                LIST_W, LIST_H, i, j, onPxW, onPxH, monitorHeight, ms, LIST_ROWS,
                (poseStack1, multiBufferSource1, x, y, z, w, h, i1, j1, entry) -> {
                    renderSmartButtonBoxSprite(poseStack1, multiBufferSource1, x, y, z + OERenderUtils.MIN_BREADTH,
                            w, h, i1, j1, onPxW, onPxH, monitorHeight, false);
                    boolean allowed = canDeleteMusic(entry, pl);
                    boolean checked = selected.contains(entry.getUuid());
                    int boxColor = !allowed ? 0xFF7A7A7A : (checked ? 0xFFFFFF00 : 0xFF444444);
                    renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1,
                            Component.literal(checked ? "[x]" : "[ ]"), x + 2, y + 2, z + OERenderUtils.MIN_BREADTH * 2,
                            onPxW, onPxH, monitorHeight, boxColor, i1);
                    renderPlayerFaceSprite(poseStack1, multiBufferSource1, entry.getOwner(), x + 2, y + 13,
                            z + OERenderUtils.MIN_BREADTH * 2, 9, i1, j1, onPxW, onPxH, monitorHeight);

                    var img = entry.getImage();
                    float sx = 18;
                    if (!img.isEmpty()) {
                        sx += h - 2 + 1;
                        renderPlayListImage(poseStack1, multiBufferSource1, img, x + 18, y + 1,
                                z + OERenderUtils.MIN_BREADTH * 2, h - 2, i1, j1, onPxW, onPxH, monitorHeight);
                    }

                    int textColor = allowed ? 0xFFFFFFFF : 0xFF888888;
                    renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1,
                            Component.literal(OEClientUtils.getWidthOmitText(entry.getName(), (int) (w - sx - 2), "...")),
                            x + sx, y + 4, z + OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, textColor, i1);
                    renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1,
                            Component.literal(entry.getAuthor()), x + sx, y + 15, z + OERenderUtils.MIN_BREADTH * 2,
                            onPxW, onPxH, monitorHeight, textColor, i1);
                    // 分P badge of the row (same as the GUI path — see MusicsManageFixedListWidget#partBadge)
                    var badge = MusicsManageFixedListWidget.partBadge(entry);
                    if (!badge.isEmpty())
                        renderSmartTextSpriteColorSprite(poseStack1, multiBufferSource1, Component.literal(badge),
                                x + w - 17, y + 15, z + OERenderUtils.MIN_BREADTH * 2,
                                onPxW, onPxH, monitorHeight, 0xFF55FFFF, i1);
                });

        // Buttons (the panel must draw them itself: widgets exist only on the GUI path)
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y1, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, SELECT_ALL_TEXT, true, total <= 0);
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y2, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, INVERT_TEXT, true, total <= 0);
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y3, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, CLEAR_TEXT, true, selected.isEmpty());
        // The two filter buttons (the panel instance's filter is always "all"; the text is cached the same way, see panelSearchHint)
        if (panelSourceFilterText == null)
            panelSourceFilterText = sourceFilterText();
        if (panelOwnerFilterText == null)
            panelOwnerFilterText = ownerFilterText();
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y4, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, panelSourceFilterText, true, total <= 0);
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y5, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, panelOwnerFilterText, true, total <= 0);
        // 「清理重复」: the panel instance has no widget, so the label has to be derived here (count from the same set)
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y6, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight,
                cleanDupesButton != null ? cleanDupesButton.getMessage() : DUPLICATE_TEXT, true, duplicateIds.isEmpty());
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y7, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H, i, j, onPxW, onPxH, monitorHeight, RECENT_TEXT, true, total <= 0);
        renderSmartButtonSprite(poseStack, multiBufferSource, GUTTER_X, GUTTER_Y8, OERenderUtils.MIN_BREADTH * 2,
                GUTTER_W, GUTTER_H8, i, j, onPxW, onPxH, monitorHeight, DEAD_SCAN_TEXT, true, total <= 0);

        renderSmartButtonSprite(poseStack, multiBufferSource, ROW_LEFT_X, ROW_Y, OERenderUtils.MIN_BREADTH * 2,
                BTN_W, BTN_H, i, j, onPxW, onPxH, monitorHeight, BACK_TEXT, true);
        renderSmartButtonSprite(poseStack, multiBufferSource, width - ROW_RIGHT_A_MARGIN - BTN_W, ROW_Y,
                OERenderUtils.MIN_BREADTH * 2, BTN_W, BTN_H, i, j, onPxW, onPxH, monitorHeight,
                DELETE_SELECTED_TEXT, true, selected.isEmpty());
        renderSmartButtonSprite(poseStack, multiBufferSource, width - ROW_RIGHT_B_MARGIN - BTN_W, ROW_Y,
                OERenderUtils.MIN_BREADTH * 2, BTN_W, BTN_H, i, j, onPxW, onPxH, monitorHeight,
                PLAYER_PICK_TEXT, true, total <= 0);
        // 「展开分P」 is drawn enabled: its own state comes from the selection (this panel instance cannot be clicked),
        // so there is no data-derived condition to show here.
        renderSmartButtonSprite(poseStack, multiBufferSource, ROW_PARTS_X, ROW_Y, OERenderUtils.MIN_BREADTH * 2,
                BTN_W, BTN_H, i, j, onPxW, onPxH, monitorHeight, EXPAND_PARTS_TEXT, true);
    }

    // ---------------- Data ----------------

    private void refreshMusics() {
        var pl = getSelectedPlayList();
        if (pl == null) {
            musicsSyncedPlayList = null;
            clearMusicData();
            return;
        }
        var ms = getSyncManager().getMusics(pl);
        // ⚠️ getMusics is @Nullable: null means "not synced yet" **or** "a broadcast just reset the cache", so data of an
        // already synced playlist must be kept — dropping it wipes the checks and the undo copy too. Only the cache reference goes.
        if (ms == null) {
            // ⚠️ A playlist switch must still clear: the kept data belongs to `musicsSyncedPlayList` only.
            if (!pl.equals(musicsSyncedPlayList)) {
                musicsSyncedPlayList = null;
                clearMusicData();
            }
            musicsCash = null;
            return;
        }
        if (ms == musicsCash)
            return;
        musicsCash = ms;
        // Data really arrived for this playlist (an empty list here is a **really empty playlist**, so it does clear below).
        musicsSyncedPlayList = pl;
        allMusics.clear();
        allMusics.addAll(ms);
        // Data changed: recompute the visible list (also trimming the selection to the visible range) + rebuild the uploader list
        applyFilter();
        rebuildOwners();
    }

    /** Really no data to show (no playlist selected, or the selected playlist changed): clear the music list, the visible list, the selection and the uploader list. */
    private void clearMusicData() {
        duplicateIds.clear();
        if (!allMusics.isEmpty()) {
            allMusics.clear();
            selected.clear();
        }
        if (!musics.isEmpty())
            musics.clear();
        if (!owners.isEmpty())
            owners.clear();
    }

    // ---------------- Filters (search / source / uploader) ----------------

    /**
     * Recomputes {@link #musics} and trims the selection to it: "delete selected" sends every id in it, so a
     * filtered-out but still ticked row would be deleted too.
     */
    private void applyFilter() {
        musics.clear();
        for (Music m : allMusics)
            if (matches(m))
                musics.add(m);
        var visible = new HashSet<UUID>();
        for (Music m : musics)
            visible.add(m.getUuid());
        selected.removeIf(id -> !visible.contains(id));
        rebuildDuplicates();
    }

    // ---------------- Duplicates (「清理重复」) ----------------

    /**
     * Ids of every repeated entry, oldest kept: same source ({@code loaderType} + identifier; a trailing {@code -p1}
     * counts as the bare video). Computed from {@link #allMusics}, not the filtered view.
     */
    private final List<UUID> duplicateIds = new ArrayList<>();

    private void rebuildDuplicates() {
        duplicateIds.clear();
        var seen = new HashSet<String>();
        for (Music m : allMusics) {
            var src = m == null ? null : m.getSource();
            if (src == null || src.getLoaderType() == null)
                continue;
            var id = normalizeIdentifier(src);
            // ⚠️ Without this guard every entry with no identifier would share the key "loaderType|" and all but the first would be offered for deletion.
            if (id.isBlank())
                continue;
            if (!seen.add(src.getLoaderType() + "|" + id))
                duplicateIds.add(m.getUuid());   // a repeat: the first one stays
        }
    }

    /** The identity used for the duplicate check (see {@link #rebuildDuplicates}); B站-specific normalisation only. */
    private static String normalizeIdentifier(dev.felnull.imp.music.resource.MusicSource src) {
        var id = src.getIdentifier() == null ? "" : src.getIdentifier().strip();
        // Part 1 is spelled as the bare video number elsewhere in the mod, so `BV…-p1` == `BV…`. Only B站 uses the
        // `-pN` suffix — applying it to every source could silently merge two unrelated ids that happen to end in -p1.
        if ("bilibili".equals(src.getLoaderType()) && id.endsWith("-p1"))
            id = id.substring(0, id.length() - 3);
        return id;
    }

    /**
     * 「清理重复」 = **tick them, do not delete**: every repeated entry except the oldest is checked, and the deletion stays on 「删除选中」.
     */
    private void requestCleanDuplicates() {
        if (duplicateIds.isEmpty())
            return;
        setPlayerPickMode(false);
        // ⚠️ The filters have to go first: `selected` may only hold **visible** rows, so ticking under a search/source/
        // uploader filter would silently clean only part of the duplicates (and the count line would lie).
        searchText = "";
        loaderFilter = null;
        ownerFilter = null;
        if (searchEditBox != null)
            searchEditBox.setValue("");
        applyFilter();
        selected.clear();
        selected.addAll(duplicateIds);
        cleanDupesResult = Component.translatable("imp.text.manageCleanDuplicatesTicked", duplicateIds.size());
        cleanDupesResultUntil = System.currentTimeMillis() + CLEAN_DUPES_RESULT_MS;
    }

    /** Whether the current filter (all three together) lets this music through. */
    private boolean matches(Music m) {
        return matchesSearchAndSource(m) && (ownerFilter == null || ownerFilter.equals(m.getOwner()));
    }

    /**
     * Applies only "search + source" (**not the uploader**), so the uploader list still shows everyone present under the current filter.
     */
    private boolean matchesSearchAndSource(Music m) {
        if (loaderFilter != null) {
            var lt = m.getSource() == null ? null : m.getSource().getLoaderType();
            if (!loaderFilter.equals(lt))
                return false;
        }
        if (searchText.isEmpty())
            return true;
        var q = searchText.toLowerCase(Locale.ROOT);
        return m.getName().toLowerCase(Locale.ROOT).contains(q)
                || m.getAuthor().toLowerCase(Locale.ROOT).contains(q);
    }

    private void setSearchText(String text) {
        if (Objects.equals(searchText, text))
            return;
        searchText = text;
        applyFilter();
        rebuildOwners();
    }

    /** Source filter: all → each source that actually occurs in this playlist → back to all. */
    private void cycleSourceFilter() {
        var choices = new ArrayList<String>();
        choices.add(null);
        for (Music m : allMusics) {
            var lt = m.getSource() == null ? null : m.getSource().getLoaderType();
            if (lt != null && !choices.contains(lt))
                choices.add(lt);
        }
        loaderFilter = choices.get((choices.indexOf(loaderFilter) + 1) % choices.size());
        sourceFilterButton.setMessage(sourceFilterText());
        applyFilter();
        rebuildOwners();
    }

    /** Uploader filter: all → each uploader occurring in this playlist (in music order) → back to all. */
    private void cycleOwnerFilter() {
        var choices = new ArrayList<UUID>();
        choices.add(null);
        for (Music m : allMusics)
            if (!choices.contains(m.getOwner()))
                choices.add(m.getOwner());
        ownerFilter = choices.get((choices.indexOf(ownerFilter) + 1) % choices.size());
        ownerFilterButton.setMessage(ownerFilterText());
        applyFilter();
    }

    /** Drill into "only this uploader's music" (double-clicking an avatar): set the uploader filter and go back to the music list. */
    private void enterOwnerView(ManageOwnersFixedListWidget.Owner o) {
        ownerFilter = o.id();
        if (ownerFilterButton != null)
            ownerFilterButton.setMessage(ownerFilterText());
        applyFilter();
        setPlayerPickMode(false);
    }

    /** Label of the source filter button: "Source: <current value>", truncated to the button width when too wide (60px cannot fit "Source: NetEase Cloud Music"). */
    private Component sourceFilterText() {
        var value = loaderFilter == null ? ALL_TEXT.getString() : loaderDisplayName(loaderFilter);
        var full = Component.translatable("imp.button.manageSourceFilter", value).getString();
        return Component.literal(OEClientUtils.getWidthOmitText(full, FILTER_TEXT_WIDTH, "..."));
    }

    /** Label of the uploader filter button: "Uploader: <name>", truncated the same way. */
    private Component ownerFilterText() {
        var value = ownerFilter == null ? ALL_TEXT.getString() : playerName(ownerFilter);
        var full = Component.translatable("imp.button.manageOwnerFilter", value).getString();
        return Component.literal(OEClientUtils.getWidthOmitText(full, FILTER_TEXT_WIDTH, "..."));
    }

    /** Source display name: prefer the media's own name (NetEase's key is netease, so its display name must be taken elsewhere); when unavailable, fall back to the lang key. */
    private static String loaderDisplayName(String loaderType) {
        var media = dev.felnull.imp.client.music.media.IMPMusicMedias.getAllMedia().get(loaderType);
        return media == null ? Component.translatable("imp.loaderType." + loaderType).getString()
                : media.getMediaName().getString();
    }

    private static String playerName(UUID id) {
        // Goes through the unified entry: the local name cache comes first (recognizable if the player has been online), then it falls back to the original lookup chain.
        return PlayerNameUtil.playerName(id);
    }

    /**
     * Uploader list in playlist order: each uploader plus his deletable music, filtered only by search + source, so
     * filtering by one person does not leave only his own row.
     */
    private void rebuildOwners() {
        owners.clear();
        var pl = getSelectedMusicPlayList();
        var total = new LinkedHashMap<UUID, Integer>();
        var deletable = new LinkedHashMap<UUID, List<UUID>>();
        for (Music m : allMusics) {
            if (!matchesSearchAndSource(m))
                continue;
            total.merge(m.getOwner(), 1, Integer::sum);
            if (canDeleteMusic(m, pl))
                deletable.computeIfAbsent(m.getOwner(), k -> new ArrayList<>()).add(m.getUuid());
        }
        for (var e : total.entrySet()) {
            var id = e.getKey();
            owners.add(new ManageOwnersFixedListWidget.Owner(id, playerName(id), e.getValue(),
                    List.copyOf(deletable.getOrDefault(id, List.of()))));
        }
    }

    private void updateButtons() {
        var pl = getSelectedMusicPlayList();
        boolean hasPlayList = pl != null;
        boolean any = hasPlayList && !musics.isEmpty();

        if (selectAllButton != null) selectAllButton.active = any;
        // "Invert" is like "select all": clickable as soon as there are rows (rows that cannot be checked do not take part anyway)
        if (invertButton != null) invertButton.active = any;
        if (clearButton != null) clearButton.active = !selected.isEmpty();
        if (deleteSelectedButton != null) deleteSelectedButton.active = hasPlayList && !selected.isEmpty();
        // "Delete by uploader" = enter pick mode (**only changes the selection**). ⚠️ Enabled whenever the playlist has
        // music, not only when something is deletable — the list then shows each uploader with his "N (0 deletable)".
        boolean anyMusic = hasPlayList && !musics.isEmpty();
        int deletable = deletableCount(musics, pl);
        if (playerPickButton != null) {
            playerPickButton.active = anyMusic;
            var wantText = playerPickMode ? BACK_TO_MUSICS_TEXT : PLAYER_PICK_TEXT;
            if (!wantText.equals(playerPickButton.getMessage()))
                playerPickButton.setMessage(wantText);
        }
        if (anyMusic && deletable <= 0)
            pickTipText = PICK_NO_PERM_TEXT;
        else
            pickTipText = PICK_TIP;
        // 「展开分P」: clickable while something expandable is ticked — or while its own job runs, because then the
        // button is the stop button (the label says so, see below).
        if (expandPartsButton != null) {
            var exp = partsExpansion;
            expandPartsButton.active = exp != null || partsSelection().bili() > 0;
            var wantPartsText = exp != null
                    ? Component.translatable("imp.button.manageExpandPartsRunning", exp.done(), exp.total())
                    : EXPAND_PARTS_TEXT;
            if (!wantPartsText.equals(expandPartsButton.getMessage()))
                expandPartsButton.setMessage(wantPartsText);
        }
        // The two filter buttons: clickable as long as the playlist has music. ⚠️ The text is only rebuilt when the value
        // really changed — otherwise a new Component every tick.
        if (sourceFilterButton != null) sourceFilterButton.active = !allMusics.isEmpty();
        if (ownerFilterButton != null) ownerFilterButton.active = !allMusics.isEmpty();
        // 「清理重复」: active only when the playlist really has repeated entries. ⚠️ The label stays 「清理重复」, never a
        // shortened 「清理(N)」 — the count belongs on the tip line, not on the key.
        if (cleanDupesButton != null) {
            cleanDupesButton.active = hasPlayList && !duplicateIds.isEmpty();
            if (!DUPLICATE_TEXT.equals(cleanDupesButton.getMessage()))
                cleanDupesButton.setMessage(DUPLICATE_TEXT);
        }
        // 「选最近加入」 is usable as soon as the playlist has music (it ticks the newest add-batch, see selectRecentBatch)
        if (recentBatchButton != null)
            recentBatchButton.active = hasPlayList && !allMusics.isEmpty();
        // 「扫失效」: usable while the playlist has music; while the walk runs the label says so and pressing it stops it
        if (deadScanButton != null) {
            deadScanButton.active = hasPlayList && !allMusics.isEmpty();
            var wantScanText = deadScan != null ? DEAD_SCAN_RUNNING_TEXT : DEAD_SCAN_TEXT;
            if (!wantScanText.equals(deadScanButton.getMessage()))
                deadScanButton.setMessage(wantScanText);
        }
        // The two lists occupy the same rectangle: visibility is aligned with the mode every tick (and also immediately on the click frame, see setPlayerPickMode)
        syncPickModeWidgets();
    }

    /** Whether there is a delete to undo right now (the undo text appears/disappears based on this). */
    private boolean isUndoAvailable() {
        return lastDeleted != null && !lastDeleted.isEmpty();
    }

    /**
     * The clickable undo line: shown inside the window, hidden outside it. Deleting and undoing happen inside click
     * events, so those two places call {@link #showUndoTextNow()} / {@link #hideUndoTextNow()} to stay in one frame.
     */
    private void updateUndoText() {
        if (undoText == null)
            return;
        boolean available = isUndoAvailable();
        undoText.visible = available;
        undoText.active = available;
        if (!available) {
            lastUndoSecondsShown = -1;
            return;
        }
        long left = Math.max(0L, (undoDeadline - System.currentTimeMillis() + 999L) / 1000L);
        if (left == lastUndoSecondsShown && lastUndoCountShown == lastDeleted.size())
            return;
        lastUndoSecondsShown = left;
        lastUndoCountShown = lastDeleted.size();
        undoText.setMessage(Component.translatable("imp.text.manageUndoHint", left, lastDeleted.size()));
    }

    /** Make that line appear **immediately** after the delete is sent; the widget hides itself at {@code undoDeadline}. */
    private void showUndoTextNow() {
        if (undoText == null)
            return;
        lastUndoSecondsShown = -1;
        lastUndoCountShown = -1;
        updateUndoText();
        undoText.setHideAt(undoDeadline);
    }

    /** Collapse that line **immediately** on undo / window expiry (without waiting for the next tick), avoiding an overlapping frame with the static hint. */
    private void hideUndoTextNow() {
        if (undoText == null)
            return;
        undoText.setHideAt(0L);
        undoText.visible = false;
        undoText.active = false;
        lastUndoSecondsShown = -1;
        lastUndoCountShown = -1;
    }

    /** The "remaining seconds / count" last set on the undo line (avoids rebuilding the text every tick), see {@link #updateUndoText()}. */
    private long lastUndoSecondsShown = -1L;
    private int lastUndoCountShown = -1;

    private static int deletableCount(@Nullable List<Music> list, @Nullable MusicPlayList pl) {
        if (list == null)
            return 0;
        int n = 0;
        for (Music m : list)
            if (canDeleteMusic(m, pl))
                n++;
        return n;
    }

    /** Same criterion as {@code MusicManager#deleteMusic}: delete permission in that playlist, or you uploaded it. */
    private static boolean canDeleteMusic(Music m, @Nullable MusicPlayList pl) {
        if (pl == null || m == null)
            return false;
        var me = IIMPSmartRender.mc.player;
        if (me == null)
            return false;
        var id = me.getGameProfile().getId();
        return pl.getAuthority().getAuthorityType(id).canMusicDelete() || Objects.equals(m.getOwner(), id);
    }

    /** The criterion for the GUI path (the list widget asks it per row whether "this row can be checked"). */
    private boolean canSelect(Music m) {
        return canDeleteMusic(m, getSelectedMusicPlayList());
    }

    // ---------------- Selection ----------------

    /** Row callback of the music list: in pick mode this widget is hidden and **must not receive clicks** (the two lists share one rectangle). */
    private void onMusicRowPressed(Music m) {
        if (playerPickMode)
            return;
        toggle(m);
    }

    /** Clicking a row = toggle the checkbox of this music (**accumulative**; multi-select works by clicking several rows). */
    private void toggle(Music m) {
        if (!canSelect(m))
            return;
        if (!selected.remove(m.getUuid()))
            selected.add(m.getUuid());
    }

    // ---------------- Check by player (no delete) ----------------

    /** Clicking an uploader row = check the music he uploaded (uncheck all if everything is checked). **Only changes the selection**; the delete is still performed by "delete selected". */
    private void onOwnerRowPressed(ManageOwnersFixedListWidget.Owner o) {
        if (!playerPickMode || o.deletable().isEmpty())
            return;
        boolean all = selected.containsAll(o.deletable());
        if (all)
            selected.removeAll(o.deletable());
        else
            selected.addAll(o.deletable());
    }

    /** Enter/leave "pick uploader" mode: only swaps what the list area shows, **leaves the selection alone and deletes nothing**. */
    private void setPlayerPickMode(boolean pick) {
        if (playerPickMode == pick)
            return;
        playerPickMode = pick;
        if (pick) {
            rebuildOwners();
            ownersRefreshAt = System.currentTimeMillis();
        }
        syncPickModeWidgets();
        updateButtons();
    }

    /** Make the visibility of "music list / uploader list" match {@link #playerPickMode} (the two occupy the same rectangle). */
    private void syncPickModeWidgets() {
        if (musicList != null) {
            musicList.visible = !playerPickMode;
            musicList.active = !playerPickMode;
        }
        if (ownerList != null) {
            ownerList.visible = playerPickMode;
            ownerList.active = playerPickMode;
        }
    }

    private void selectAll() {
        selected.clear();
        for (Music m : musics)
            if (canSelect(m))
                selected.add(m.getUuid());
    }

    /** Invert every **checkable row**; rows you may not delete do not take part, or invert would check music you cannot remove. */
    private void invertSelection() {
        for (Music m : musics) {
            if (!canSelect(m))
                continue;
            if (!selected.remove(m.getUuid()))
                selected.add(m.getUuid());
        }
    }

    // ---------------- Delete / undo ----------------

    private void requestDeleteSelected() {
        if (selected.isEmpty())
            return;
        sendDelete(new ArrayList<>(selected));
        // Go back to the music list after deleting to see the result (the selection was already cleared in sendDelete; "pick mode" is for picking music, so leave it once done)
        setPlayerPickMode(false);
    }

    private void sendDelete(List<UUID> ids) {
        sendDelete(ids, musics);
    }

    /**
     * The batch delete; {@code from} is where the undo copies come from — 「清理重复」 passes {@link #allMusics} because it deletes rows the filter hides.
     */
    private void sendDelete(List<UUID> ids, List<Music> from) {
        var pl = getSelectedPlayList();
        if (pl == null || ids.isEmpty())
            return;

        // Keep a full copy before deleting (undo sends the objects back as they are; once deleted the server drops them from its global table)
        var copies = new ArrayList<Music>();
        for (Music m : from)
            if (ids.contains(m.getUuid()))
                copies.add(m);

        var be = BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity());
        NetworkManager.sendToServer(IMPPackets.MUSIC_DELETE_BATCH,
                new IMPPackets.MusicDeleteBatchMessage(pl, ids, be).toRFBB());

        lastDeleted = copies;
        undoDeadline = System.currentTimeMillis() + UNDO_WINDOW_MS;
        selected.clear();
        // Make that line appear in the same frame (without waiting for the next tick), see {@link #updateUndoText()}.
        showUndoTextNow();
    }

    /** Undo the last delete: send the whole batch of copies back to the server to add them to the playlist again (the server requires music-add permission). */
    private void doUndo() {
        var pl = getSelectedPlayList();
        if (pl == null || lastDeleted == null || lastDeleted.isEmpty())
            return;
        var be = BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity());
        NetworkManager.sendToServer(IMPPackets.MUSIC_DELETE_BATCH_UNDO,
                new IMPPackets.MusicDeleteUndoMessage(pl, new ArrayList<>(lastDeleted), be).toRFBB());
        lastDeleted = null;
        undoDeadline = 0L;
        hideUndoTextNow();
    }

    // ---------------- Accessors ----------------

    @Nullable
    public UUID getSelectedPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedPlayList(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    public UUID getSelectedPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedPlayList(mc.player);
    }

    @Nullable
    public MusicPlayList getSelectedMusicPlayList() {
        var pls = getSyncManager().getMyPlayList();
        var sel = getSelectedPlayList();
        if (pls == null || sel == null)
            return null;
        return pls.stream().filter(n -> Objects.equals(n.getUuid(), sel)).findFirst().orElse(null);
    }

    /** The version used by the in-world monitor path (does not touch screen). */
    @Nullable
    public MusicPlayList getSelectedMusicPlayList(MusicManagerBlockEntity blockEntity) {
        var pls = getSyncManager().getMyPlayList();
        var sel = getSelectedPlayList(blockEntity);
        if (pls == null || sel == null)
            return null;
        return pls.stream().filter(n -> Objects.equals(n.getUuid(), sel)).findFirst().orElse(null);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.PLAY_LIST;
    }

    // ---------------- 分P (B站 multi-part videos) ----------------

    /**
     * The line above TIP2: the static hint, taken over while a job or report has something to say; returns {@link #TIP1}
     * when idle, since the in-world panel draws every frame and must not build Components.
     */
    private Component tipText() {
        var scan = deadScan;
        if (scan != null)
            return Component.translatable("imp.text.manageDeadScanRunning", scan.done(), scan.total());
        var exp = partsExpansion;
        if (exp != null)
            return Component.translatable("imp.text.managePartsRunning", exp.done(), exp.total());
        if (deadScanResult != null && System.currentTimeMillis() < deadScanResultUntil)
            return deadScanResult;
        deadScanResult = null;
        if (partsExpandResult != null)
            return partsExpandResult;
        // 「清理重复」 reported what it ticked (it deletes nothing — the user still presses 「删除选中」)
        if (cleanDupesResult != null && System.currentTimeMillis() < cleanDupesResultUntil)
            return cleanDupesResult;
        cleanDupesResult = null;
        var sel = partsSelection();
        if (sel.bili() > 0)
            return Component.translatable("imp.text.managePartsTip", sel.bili(), sel.known(), sel.unknown());
        return TIP1;
    }

    /** What 「清理重复」 last ticked (takes TIP1's line over for a few seconds, like the 分P result does). */
    @Nullable
    private Component cleanDupesResult;
    private long cleanDupesResultUntil;
    /** How long that report stays on the line. */
    private static final long CLEAN_DUPES_RESULT_MS = 6000L;

    /**
     * What the ticked rows mean for the 分P action.
     *
     * @param bili    ticked B站 **videos** (an expanded part is not counted)
     * @param known   those whose part list is cached: pressing the button costs no request
     * @param unknown the rest: one {@code view} request each just to learn the part count
     */
    private record PartsSelection(int bili, int known, int unknown) {
    }

    private PartsSelection partsSelection() {
        int bili = 0;
        int known = 0;
        int unknown = 0;
        for (Music m : musics) {
            if (!selected.contains(m.getUuid()) || !isExpandableVideo(m))
                continue;
            bili++;
            if (BiliBiliParts.known(m.getSource().getIdentifier()).size() > 1)
                known++;
            else
                unknown++;
        }
        return new PartsSelection(bili, known, unknown);
    }

    /** A ticked row that can be expanded: a B站 **video**, i.e. not an already expanded part ({@code BV…-p2}). */
    private static boolean isExpandableVideo(@Nullable Music m) {
        var source = m == null ? null : m.getSource();
        if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
            return false;
        return BiliBiliUtil.pageOf(source.getIdentifier()) <= 1;
    }

    /** 「展开分P」: start the job for the ticked videos, or stop it while it runs — what was already collected is kept. */
    private void togglePartsExpansion() {
        if (partsExpansion != null) {
            partsExpansion.cancelled = true;
            return;
        }
        startPartsExpansion();
    }

    /**
     * 「查询选中分P」: looks up the part lists of the ticked B站 videos; adds nothing to the playlist — a multi-part video
     * stays one entry and the boombox walks its parts (the client reports the part list to the server).
     */
    private void startPartsExpansion() {
        var videos = new ArrayList<Music>();
        for (Music m : musics) {
            if (selected.contains(m.getUuid()) && isExpandableVideo(m))
                videos.add(m);
        }
        if (videos.isEmpty())
            return;

        partsExpandResult = null;
        var job = new PartsExpansion(videos, IamMusicPlayer.getConfig().bilibiliConfig.spaceParallelism);
        job.setName("IMP-BiliBili-Parts");
        job.setDaemon(true);
        partsExpansion = job;
        job.start();
    }

    /**
     * Runs every tick while the job lives: progress, then the outcome. Each lookup reports its own part
     * list, so a cancel or leaving the page only stops the remaining lookups.
     */
    private void tickPartsExpansion() {
        var exp = partsExpansion;
        if (exp == null)
            return;
        if (exp.isAlive())
            return;
        partsExpansion = null;
        // 分P表在走查期间累积，一次性落盘（逐视频写会把 65 KB 的缓存文件反复重写）。
        BiliBiliArcTags.getInstance().flush();
        LinkDebug.log("BiliBili parts lookup done", "videos", exp.total(), "multi", exp.multi(),
                "parts", exp.parts(), "single", exp.single(), "failed", exp.failed(), "cancelled", exp.cancelled);
        partsExpandResult = Component.translatable("imp.text.managePartsDone", exp.multi(), exp.parts(),
                exp.single(), exp.failed());
    }

    // ---------------- 扫失效 (songs whose submission is gone) ----------------

    /** 「扫失效」: start the walk, or — while it runs — stop it (everything found so far is kept and ticked). */
    private void toggleDeadScan() {
        if (deadScan != null) {
            deadScan.cancelled = true;
            return;
        }
        startDeadScan();
    }

    /**
     * Ticks the B站 songs Bilibili reports as gone ({@code -404}); known-gone rows cost no request and videos checked
     * within {@link #DEAD_SCAN_TTL_MS} are skipped, so only the first press is a full walk. Never deletes.
     */
    private void startDeadScan() {
        var now = System.currentTimeMillis();
        // distinct bvid → every playlist row pointing at it (an expanded part `BV…-p2` is just as dead as its video)
        var byBvid = new java.util.LinkedHashMap<String, List<UUID>>();
        var known = new LinkedHashSet<UUID>();
        int skipped = 0;
        for (Music m : allMusics) {
            var source = m == null ? null : m.getSource();
            if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
                continue;
            var bvid = BiliBiliUtil.stripPage(source.getIdentifier());
            if (bvid == null || bvid.isBlank())
                continue;
            if (BiliBiliParts.knownGone(bvid)) {
                // Bilibili already said this one is gone: tick it now, no request
                known.add(m.getUuid());
                continue;
            }
            var at = BiliBiliParts.checkedAt(bvid);
            if (at > 0L && now - at < DEAD_SCAN_TTL_MS) {
                // Asked about recently: skipping it is what makes a repeat press cheap
                skipped++;
                continue;
            }
            byBvid.computeIfAbsent(bvid, k -> new ArrayList<>()).add(m.getUuid());
        }

        deadScanResult = null;
        deadScanKnown.clear();
        deadScanKnown.addAll(known);
        deadScanSkipped = skipped;
        // Everything already known is ticked **at once**: pressing the key must always do something visible.
        claimDeadRows(known);

        if (byBvid.isEmpty()) {
            reportDeadScan(0, known.size(), 0, skipped);
            return;
        }
        var job = new DeadScan(byBvid,
                Math.max(DEAD_SCAN_MIN_PARALLELISM, IamMusicPlayer.getConfig().bilibiliConfig.spaceParallelism));
        job.setName("IMP-BiliBili-DeadScan");
        job.setDaemon(true);
        deadScan = job;
        job.start();
    }

    /** Rows found dead by the running sweep, plus the ones that were already known when it started. */
    private final Set<UUID> deadScanKnown = new LinkedHashSet<>();
    private int deadScanSkipped;

    /** Ticks these rows after clearing the filters (⚠️ `selected` may only hold **visible** rows — see applyFilter). */
    private void claimDeadRows(Set<UUID> ids) {
        if (ids.isEmpty())
            return;
        setPlayerPickMode(false);
        searchText = "";
        loaderFilter = null;
        ownerFilter = null;
        if (searchEditBox != null)
            searchEditBox.setValue("");
        applyFilter();
        selected.clear();
        selected.addAll(ids);
    }

    /** The 「扫失效」 report line: how many rows it ticked, how many were newly found, what it could not check. */
    private void reportDeadScan(int foundNow, int rows, int failed, int skipped) {
        deadScanResult = rows <= 0
                ? Component.translatable("imp.text.manageDeadScanNone", failed, skipped)
                : Component.translatable("imp.text.manageDeadScanTicked", rows, foundNow, failed, skipped);
        deadScanResultUntil = System.currentTimeMillis() + CLEAN_DUPES_RESULT_MS + 2000L;
    }

    /** 「扫失效」 request gap; tighter than the 分P walk's, see {@link #DEAD_SCAN_MIN_PARALLELISM}. */
    private static final long DEAD_SCAN_GAP_MS = 20L;
    /** Workers for the sweep; the floor of 8 matters — with the 分P walk's 3 workers the sweep managed under 7 videos per second. */
    private static final int DEAD_SCAN_MIN_PARALLELISM = 8;
    /** Videos answered within this window are skipped, so a repeat press costs (almost) nothing. */
    private static final long DEAD_SCAN_TTL_MS = 12L * 60 * 60 * 1000L;

    /** Runs every tick while the scan lives: progress on the tip line, and the ticked rows once the walk ends. */
    private void tickDeadScan() {
        var scan = deadScan;
        if (scan == null)
            return;
        if (scan.isAlive())
            return;
        deadScan = null;
        // The scan's `view` calls also warmed the info/parts cache: write it once, not once per video.
        BiliBiliArcTags.getInstance().flush();
        var deadIds = new LinkedHashSet<>(deadScanKnown);
        deadIds.addAll(scan.deadIds());
        LinkDebug.log("BiliBili dead scan done", "scanned", scan.total(), "dead", scan.dead(),
                "failed", scan.failed(), "rows", deadIds.size(), "skipped", deadScanSkipped,
                "cancelled", scan.cancelled);
        claimDeadRows(deadIds);
        reportDeadScan(scan.dead(), deadIds.size(), scan.failed(), deadScanSkipped);
    }

    /**
     * The 「扫失效」 job: one paced {@code view} per distinct video, collecting ids only. Only {@code -404} counts as gone.
     */
    private static final class DeadScan extends Thread {
        /** distinct bvid → the playlist rows that point at it. */
        private final java.util.Map<String, List<UUID>> targets;
        private final int parallelism;
        private final BiliBiliPacer pacer = new BiliBiliPacer();
        private final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger gone = new java.util.concurrent.atomic.AtomicInteger();
        /** Ids of the playlist rows whose video is gone. */
        private final Set<UUID> deadIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private volatile boolean cancelled;

        private DeadScan(java.util.Map<String, List<UUID>> targets, int parallelism) {
            this.targets = targets;
            this.parallelism = Math.max(1, Math.min(8, parallelism));
        }

        private int done() {
            return done.get();
        }

        private int total() {
            return targets.size();
        }

        private int failed() {
            return failed.get();
        }

        private int dead() {
            return gone.get();
        }

        private Set<UUID> deadIds() {
            return deadIds;
        }

        @Override
        public void run() {
            var pool = java.util.concurrent.Executors.newFixedThreadPool(parallelism, r -> {
                var t = new Thread(r, "IMP-BiliBili-DeadScan-worker");
                t.setDaemon(true);
                return t;
            });
            try {
                var futures = new ArrayList<java.util.concurrent.Future<?>>(targets.size());
                for (var entry : targets.entrySet())
                    futures.add(pool.submit(() -> one(entry.getKey(), entry.getValue())));
                for (var f : futures) {
                    try {
                        f.get();
                    } catch (Exception ignored) {
                        // "the walk is over" is all the caller needs; a worker's failure was counted in one()
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }

        /** One video: a paced {@code view}; a gone video marks **all** of its rows, anything else is just counted. */
        private void one(String bvid, List<UUID> rows) {
            try {
                if (cancelled)
                    return;
                // Quiet and on the tight gap: the only caller that fires this hundreds of times per press.
                BiliBiliPacer.await(pacer.reserve(DEAD_SCAN_GAP_MS));
                if (cancelled)
                    return;
                if (BiliBiliParts.fetch(bvid, true) == null) {
                    if (BiliBiliParts.failureOf(bvid) == BiliBiliParts.Failure.GONE) {
                        gone.incrementAndGet();
                        deadIds.addAll(rows);
                    } else {
                        failed.incrementAndGet();
                    }
                }
            } catch (Throwable t) {
                failed.incrementAndGet();
            } finally {
                done.incrementAndGet();
            }
        }
    }

    /** Cheap signature of the selection, so the result line is dropped as soon as it changes (no hook in the five places that mutate it). */
    private int selectionSignature() {
        int sig = selected.size() * 31;
        for (var id : selected)
            sig = sig * 31 + id.hashCode();
        return sig;
    }

    private int lastSelectionSignature;

    /**
     * The 分P job: learns the part lists of the ticked B站 videos (cache + server report), adding nothing to the
     * playlist; a part count only comes from a per-video {@code view}, so the workers share one {@link BiliBiliPacer}.
     */
    private static final class PartsExpansion extends Thread {
        /** The ticked videos, in list order. */
        private final List<Music> videos;
        private final int parallelism;
        private final BiliBiliPacer pacer = new BiliBiliPacer();
        private final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger single = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger();
        /** Videos that turned out to have more than one part, and how many parts those hold together. */
        private final java.util.concurrent.atomic.AtomicInteger multi = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger parts = new java.util.concurrent.atomic.AtomicInteger();
        private volatile boolean cancelled;

        private PartsExpansion(List<Music> videos, int parallelism) {
            this.videos = videos;
            this.parallelism = Math.max(1, Math.min(8, parallelism));
        }

        /** How many videos the pass has finished with (progress for the button and the tip line). */
        private int done() {
            return done.get();
        }

        private int total() {
            return videos.size();
        }

        private int single() {
            return single.get();
        }

        private int failed() {
            return failed.get();
        }

        private int multi() {
            return multi.get();
        }

        private int parts() {
            return parts.get();
        }

        @Override
        public void run() {
            var pool = java.util.concurrent.Executors.newFixedThreadPool(parallelism, r -> {
                var t = new Thread(r, "IMP-BiliBili-Parts-worker");
                t.setDaemon(true);
                return t;
            });
            try {
                var futures = new ArrayList<java.util.concurrent.Future<?>>(videos.size());
                for (Music video : videos)
                    futures.add(pool.submit(() -> one(video)));
                for (var f : futures) {
                    try {
                        f.get();
                    } catch (Exception ignored) {
                        // "the pass is over" is all the caller needs; a worker's failure was counted in one()
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }

        /** One video: cache hit or one request, then just count what was learned. Never throws. */
        private void one(Music video) {
            try {
                if (cancelled)
                    return;
                var identifier = video.getSource().getIdentifier();
                var list = BiliBiliParts.known(identifier);
                if (list.isEmpty()) {
                    // Never looked at: one request through the pacer (it reserves the slot **before** sending, so N
                    // workers share one bounded rate instead of N times that rate). `fetch` also hands the result to
                    // the server, which is what makes 「按一条算」 work.
                    BiliBiliPacer.await(pacer.reserve(BiliBiliParts.REQUEST_GAP_MS));
                    if (cancelled)
                        return;
                    list = BiliBiliParts.fetch(identifier);
                } else {
                    // Already known (played before, or looked up earlier): make sure the server has it too — it may
                    // have restarted since, and a rejected report is simply retried here.
                    BiliBiliParts.report(identifier, list);
                }
                if (list == null) {
                    failed.incrementAndGet();
                } else if (list.size() <= 1) {
                    single.incrementAndGet();
                } else {
                    multi.incrementAndGet();
                    parts.addAndGet(list.size());
                }
            } catch (Throwable t) {
                failed.incrementAndGet();
            } finally {
                done.incrementAndGet();
            }
        }
    }
}
