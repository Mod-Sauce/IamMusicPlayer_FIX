package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.IMPConfig;
import me.shedaniel.autoconfig.AutoConfig;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.gui.PlayerNameUtil;
import dev.felnull.imp.client.renderer.PlayerFaceRenderer;
import dev.felnull.imp.client.gui.components.MusicsFixedListWidget;
import dev.felnull.imp.client.gui.components.MyPlayListFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.components.SortButton;
import dev.felnull.imp.client.gui.components.SpaceCategoryListWidget;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.MusicSyncManager;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliParts;
import dev.felnull.imp.client.bilibili.BiliBiliPlaylistRefresh;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.networking.IMPPackets;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayListMMMonitor extends MusicManagerMonitor {
    private static final ResourceLocation PLAY_LIST_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/music_manager/monitor/play_list.png");
    private static final Component ADD_PLAYLIST_TEXT = Component.translatable("imp.button.addPlaylist");
    private static final Component ADD_MUSIC_TEXT = Component.translatable("imp.button.addMusic");
    private static final Component DETAIL_TEXT = Component.translatable("imp.button.detailPlaylist");
    /** Goes to the "music management" page (selection bulk delete / delete by player). */
    private static final Component MANAGE_TEXT = Component.translatable("imp.button.musicManage");
    /** 「刷新」: walk the import source again and append only what is missing. */
    private static final Component REFRESH_TEXT = Component.translatable("imp.button.playListRefresh");
    /** Shown while the playlist has no source yet: one click learns the UP from its first B站 song. */
    private static final Component BIND_UP_TEXT = Component.translatable("imp.button.playListBindUp");
    private static final Component REFRESH_RUNNING_TEXT = Component.translatable("imp.text.playListRefreshRunning");
    private static final Component REFRESH_FAILED_TEXT = Component.translatable("imp.text.playListRefreshFailed");
    /** How long the refresh key keeps showing its result ("+3 首" / "没有新的" / "刷新失败"). */
    private static final long REFRESH_RESULT_MS = 6000L;
    /**
     * 「刷新」 on the 「管理」 row (y 31..40), starting flush against the playlist list's 1 px bar at x = 101; must not overlap
     * 「管理」 (x 336) nor the song list (y ≥ 40). */
    private static final int REFRESH_X = 102;
    private static final int REFRESH_Y = 31;
    private static final int REFRESH_W = 48;
    private static final int REFRESH_H = 9;
    /** Grey hint while the search box is empty — must say it searches playlists, or it reads as "search music". */
    private static final Component SEARCH_HINT_TEXT = Component.translatable("imp.editBox.playlistSearchName");
    private final List<MusicPlayList> musicPlayLists = new ArrayList<>();
    private final List<Music> musics = new ArrayList<>();
    private List<MusicPlayList> musicPlayListsCash;
    /** Pending invites, kept only to detect a change and re-sort the display list once (see tick()). */
    private List<MusicPlayList> invitePlayListsCash;
    private List<Music> musicsCash;
    /** 上次给列表做过"回顶部"的歌单：控件的滚动是比例，切歌单不清零就会停在下半段。 */
    private UUID lastScrollPlaylist;
    private MusicSyncManager.PlayListInfo lastPlayListInfo;
    private Component INFO_TEXT;
    private SortButton.SortTypeButton playlistSortButton;
    private SortButton.OrderTypeButton playlistOrderButton;
    private SortButton.SortTypeButton musicSortButton;
    private SortButton.OrderTypeButton musicOrderButton;
    private SmartButton addPlaylistButton;
    private SmartButton addMusic;
    private SmartButton detailButton;
    /** The "manage" key (goes to the music management page). */
    private SmartButton manageButton;
    /** 「刷新」: only offered for a playlist that knows its import source. */
    private SmartButton refreshButton;
    private MusicsFixedListWidget musicsFixedButtonsList;
    private MyPlayListFixedListWidget myPlayListFixedButtonsList;

    // ------------------------------------------------------------------ 「分类」 (B站 合集 / 分区)
    // A **view** filter for the song list: `Music` carries no category, so it looks the 合集/父分区 up by bvid in
    // {@link BiliBiliArcTags}; a picker list rather than a cycling button, since a UP can have 40+ 合集.

    private static final int CATEGORY_BUTTON_X = 296;
    private static final int CATEGORY_BUTTON_W = 74;
    private static final Component CATEGORY_TEXT = Component.translatable("imp.button.musicCategory");

    private SpaceCategoryListWidget categoryPickList;
    private SmartButton categoryButton;
    private List<SpaceCategoryListWidget.Row> categoryRows = List.of();
    /** The picked row; {@code Row.all(0)} = no category filter. */
    private SpaceCategoryListWidget.Row categoryFilter = SpaceCategoryListWidget.Row.all(0, true);
    private boolean categoryPickMode;

    public PlayListMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.myPlayListFixedButtonsList = addRenderWidget(new MyPlayListFixedListWidget(getStartX() + 1, getStartY() + 20, musicPlayLists,
                // Non-invited row: clicking the row selects this playlist. An invited row never gets here — the widget
                // swallows those clicks, only the "accept" / "reject" texts trigger the callbacks below.
                (widget, item) -> setSelectedPlayList(item.getUuid()),
                this.myPlayListFixedButtonsList, n -> n.getUuid().equals(getSelectedPlayList()), n -> isInvited(n.getUuid()),
                this::acceptInvite, this::rejectInvite));

        this.addPlaylistButton = addRenderWidget(new SmartButton(getStartX() + 1, getStartY() + 189, 72 + 9, 9, ADD_PLAYLIST_TEXT, n -> {
            insMonitor(MusicManagerBlockEntity.MonitorType.ADD_PLAY_LIST);
        }));
        this.addPlaylistButton.setIcon(WIDGETS_TEXTURE, 73, 14, 5, 5);

        this.addMusic = addRenderWidget(new SmartButton(getStartX() + 102, getStartY() + 189, 72, 9, ADD_MUSIC_TEXT, n -> {
            insMonitor(MusicManagerBlockEntity.MonitorType.ADD_MUSIC);
        }));
        this.addMusic.setIcon(WIDGETS_TEXTURE, 73, 14, 5, 5);
        this.addMusic.active = getSelectedMusicPlayList() != null && getSelectedMusicPlayList().getAuthority().getAuthorityType(IIMPSmartRender.mc.player.getGameProfile().getId()).isMoreMember();

        // Music list sort buttons: they draw only the icon, so the click callback must refresh the labels itself —
        // otherwise icon and text drift apart.
        this.musicSortButton = addRenderWidget(new SortButton.SortTypeButton(getStartX() + MUSIC_SORT_X, getStartY() + 189, n -> {
            saveSortChoices();
            refreshMusicSortLabels();
            updateMusics();
        }, true, getScreen()));
        this.musicSortButton.setWidth(MUSIC_SORT_W);
        this.musicOrderButton = addRenderWidget(new SortButton.OrderTypeButton(getStartX() + MUSIC_ORDER_X, getStartY() + 189, n -> {
            saveSortChoices();
            refreshMusicSortLabels();
            updateMusics();
        }, true, getScreen()));
        this.musicOrderButton.setWidth(MUSIC_ORDER_W);
        refreshMusicSortLabels();

        this.musicsFixedButtonsList = this.addRenderWidget(new MusicsFixedListWidget(getStartX() + 102, getStartY() + 40, 267, 148, Component.translatable("imp.fixedList.musics"), 4, musics, (widget, item) -> {
            setSelectedMusic(item.getUuid());
            insMonitor(MusicManagerBlockEntity.MonitorType.DETAIL_MUSIC);
        }, false, this.musicsFixedButtonsList));

        this.detailButton = this.addRenderWidget(new SmartButton(getStartX() + 336, getStartY() + 20, 33, 9, DETAIL_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.DETAIL_PLAY_LIST)));
        this.detailButton.visible = getSelectedMusicPlayList() != null;

        // 「分类」: the category picker (hidden until the button opens it; it takes over the song list's place)
        this.categoryPickList = addRenderWidget(new SpaceCategoryListWidget(getStartX() + 102, getStartY() + 40, 267, 148,
                Component.translatable("imp.fixedList.spaceCategories"), 10, categoryRows,
                this::pickCategory, null));
        this.categoryButton = addRenderWidget(new SmartButton(getStartX() + CATEGORY_BUTTON_X, getStartY() + 189, CATEGORY_BUTTON_W, 9,
                CATEGORY_TEXT, n -> toggleCategoryPick()));
        applyCategoryPickMode();
        updateCategoryButton();

        // The "manage" key: goes to the music management page (selection bulk delete / delete by player). Top right corner, directly below the detail key.
        this.manageButton = this.addRenderWidget(new SmartButton(getStartX() + 336, getStartY() + 31, 33, 9, MANAGE_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.MUSIC_MANAGE)));
        this.manageButton.visible = getSelectedMusicPlayList() != null;

        // Playlist list sort buttons. ⚠️ Both fields must be assigned, otherwise updateList() NPEs as soon as it sorts.
        this.playlistOrderButton = addRenderWidget(new SortButton.OrderTypeButton(getStartX() + PLAYLIST_ORDER_X, getStartY() + 189, n -> {
            saveSortChoices();
            updateList();
        }, false, getScreen()));
        this.playlistSortButton = addRenderWidget(new SortButton.SortTypeButton(getStartX() + PLAYLIST_SORT_X, getStartY() + 189, n -> {
            saveSortChoices();
            updateList();
        }, false, getScreen()));

        // ⚠️ No tooltip on purpose: a long one is drawn as one unwrapped line across the whole page.
        this.refreshButton = addRenderWidget(new SmartButton(getStartX() + REFRESH_X, getStartY() + REFRESH_Y,
                REFRESH_W, REFRESH_H, REFRESH_TEXT, n -> onRefreshPressed()));
        this.refreshButton.visible = false;

        // The box at the top filters the **songs in the current playlist**, behind a config option: off ⇒ no widget at all.
        if (IamMusicPlayer.getConfig().uiConfig.enablePlayListMusicFilter) {
            this.playlistSearchEditBox = new EditBox(IIMPSmartRender.mc.font, getStartX() + (width - SEARCH_BOX_WIDTH) / 2, getStartY() + 3, SEARCH_BOX_WIDTH, 12, Component.translatable("imp.editBox.playlistSearchName"));
            this.playlistSearchEditBox.setMaxLength(128);
            this.playlistSearchEditBox.setValue(playlistSearchText);
            this.playlistSearchEditBox.setResponder(this::setPlaylistSearchText);
            addRenderWidget(this.playlistSearchEditBox);
        } else {
            this.playlistSearchEditBox = null;
        }

        // Apply the stored sort modes before the updateList() / updateMusics() below, so they sort by "the last choice".
        restoreSortChoices();

        // Fill the lists right away: tick() only refreshes on a changed reference and the first tick ran before init.
        widgetsReady = true;
        // …and with it the 「分类」 choice made last time (it used to reset to 「全部」 on every entry)
        restoreCategoryFilter();
        updateList();
        updateMusics();
    }

    /** Whether the widgets have been created (set at the end of {@link #init}, reset by {@link #depose}).
     *  `updateList` / `updateMusics` skip sorting while it is not set; {@link #init} refreshes once more, so no data is lost. */
    private boolean widgetsReady;

    @Override
    public void depose() {
        super.depose();
        // Reset the flag so tick() cannot use disposed buttons between destroy and the next init
        widgetsReady = false;
        refreshButton = null;
        pendingRefresh = null;
    }

    /** 刷新键此刻是「只补投稿时间」形态（见 updateRefreshButton）。 */
    private boolean refreshKeyPubdates;

    /** Filter box width (centered in init). */
    private static final int SEARCH_BOX_WIDTH = 140;
    /** Music list sort buttons; the widths are narrowed to just fit "icon + full label". */
    private static final int MUSIC_SORT_X = 174;
    private static final int MUSIC_SORT_W = 90;
    private static final int MUSIC_ORDER_X = 266;
    private static final int MUSIC_ORDER_W = 30;
    /** Position of the playlist sort icon; it must fall outside the "add playlist" button (x 1..81). */
    private static final int PLAYLIST_SORT_X = 73 + 9;

    /**
     * The x of the 9×9 playlist **order** icon — must stay outside the "add playlist" button (x 1..81), else its early
     * registration swallows the click and "descending" stops working. */
    private static final int PLAYLIST_ORDER_X = 82 + 9;

    /** Current text of the music sort buttons (the hand-drawn in-world path cannot reach SortButton's protected getters). */
    private Component musicSortText = Component.translatable("imp.sortType.name");
    private Component musicOrderText = Component.translatable("imp.orderType.descending");

    /** Write the current sort into the text of the two music buttons. */
    private void refreshMusicSortLabels() {
        if (musicSortButton != null)
            musicSortText = Component.translatable("imp.sortType." + musicSortButton.getSortType().getName());
        if (musicOrderButton != null)
            musicOrderText = Component.translatable("imp.orderType." + musicOrderButton.getOrderType().getName());
        if (musicSortButton != null)
            musicSortButton.setMessage(musicSortText);
        if (musicOrderButton != null)
            musicOrderButton.setMessage(musicOrderText);
    }

    /** Filter keyword for the songs in the playlist; kept across re-entries of the interface. */
    private String playlistSearchText = "";
    private EditBox playlistSearchEditBox;

    /** [Remember the last sort mode · read] Apply the four sort choices from the client config to the four buttons. */
    private void restoreSortChoices() {
        var ui = IamMusicPlayer.getConfig().uiConfig;
        if (playlistSortButton != null)
            playlistSortButton.setSortType(SortButton.SortType.byName(ui.playListSortType));
        if (playlistOrderButton != null)
            playlistOrderButton.setOrderType(SortButton.OrderType.byName(ui.playListOrderType));
        if (musicSortButton != null)
            musicSortButton.setSortType(SortButton.SortType.byName(ui.musicSortType));
        if (musicOrderButton != null)
            musicOrderButton.setOrderType(SortButton.OrderType.byName(ui.musicOrderType));
        refreshMusicSortLabels();
    }

    /** [Remember the last sort mode · write] Write the four buttons' modes back to the client config (one save per click, survives a restart). */
    private void saveSortChoices() {
        var ui = IamMusicPlayer.getConfig().uiConfig;
        if (playlistSortButton != null)
            ui.playListSortType = playlistSortButton.getSortType().getName();
        if (playlistOrderButton != null)
            ui.playListOrderType = playlistOrderButton.getOrderType().getName();
        if (musicSortButton != null)
            ui.musicSortType = musicSortButton.getSortType().getName();
        if (musicOrderButton != null)
            ui.musicOrderType = musicOrderButton.getOrderType().getName();
        AutoConfig.getConfigHolder(IMPConfig.class).save();
    }

    /** [Remember the last category filter · write] Client config, restored in {@link #init}; encoded as {@code all} /
     * {@code ungrouped} / {@code multi} / {@code single} / {@code season:<name>} / {@code zone:<name>}. */
    private void saveCategoryFilter() {
        try {
            IamMusicPlayer.getConfig().uiConfig.playListCategoryFilter = encodeCategory(categoryFilter);
            AutoConfig.getConfigHolder(IMPConfig.class).save();
        } catch (Throwable t) {
            LinkDebug.log("category filter save FAIL", "ex", t.toString());
        }
    }

    /** Reads the remembered filter back ({@code all} when nothing usable is stored). */
    private void restoreCategoryFilter() {
        try {
            categoryFilter = decodeCategory(IamMusicPlayer.getConfig().uiConfig.playListCategoryFilter);
        } catch (Throwable t) {
            categoryFilter = SpaceCategoryListWidget.Row.all(0, true);
        }
        applyCategoryPickMode();
        updateCategoryButton();
    }

    /** One row → its config form. */
    static String encodeCategory(@Nullable SpaceCategoryListWidget.Row row) {
        if (row == null)
            return "all";
        return switch (row.kind()) {
            case UNGROUPED -> "ungrouped";
            case MULTI_PART -> "multi";
            case SINGLE_PART -> "single";
            case SEASON -> "season:" + row.filterName();
            case ZONE -> "zone:" + row.filterName();
            case ALL, HEADER -> "all";
        };
    }

    /** The config form → a row; anything unknown reads as 「全部」, so a hand-edited config cannot break the page. */
    static SpaceCategoryListWidget.Row decodeCategory(@Nullable String encoded) {
        var v = encoded == null ? "" : encoded.trim();
        if (v.isEmpty() || "all".equals(v))
            return SpaceCategoryListWidget.Row.all(0, true);
        if ("ungrouped".equals(v))
            return SpaceCategoryListWidget.Row.ungrouped(0, true);
        if ("multi".equals(v))
            return SpaceCategoryListWidget.Row.multiPart(0, true);
        if ("single".equals(v))
            return SpaceCategoryListWidget.Row.singlePart(0, true);
        if (v.startsWith("season:") && v.length() > "season:".length())
            return SpaceCategoryListWidget.Row.season(v.substring("season:".length()), 0, true);
        if (v.startsWith("zone:") && v.length() > "zone:".length())
            return SpaceCategoryListWidget.Row.zone(v.substring("zone:".length()), 0, true);
        return SpaceCategoryListWidget.Row.all(0, true);
    }

    private void setPlaylistSearchText(String text) {
        var t = text == null ? "" : text;
        if (t.equals(playlistSearchText))
            return;
        playlistSearchText = t;
        // This box filters the **songs in the current playlist** (not playlist names)
        updateMusics();
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        PoseStack poseStack = guiGraphics.pose();

        OERenderUtils.drawTexture(PLAY_LIST_TEXTURE, poseStack, getStartX(), getStartY(), 0f, 0f, width, height, width, height);
        if (INFO_TEXT != null)
            drawSmartText(guiGraphics, INFO_TEXT, getStartX() + width - IIMPSmartRender.mc.font.width(INFO_TEXT) - 3, getStartY() + 11);

        // Grey hint when the box is empty: state clearly "search playlists", otherwise it looks like it searches music
        if (playlistSearchEditBox != null && playlistSearchText.isEmpty() && !playlistSearchEditBox.isFocused())
            drawSmartText(guiGraphics, SEARCH_HINT_TEXT, getStartX() + (width - SEARCH_BOX_WIDTH) / 2 + 4, getStartY() + 5, 0xFF808080);

        var pl = getSelectedMusicPlayList();
        if (pl != null) {
            int plsty;
            var au = pl.getAuthority().getPlayersAuthority().entrySet().stream().sorted(Comparator.comparingInt(o -> o.getValue().getLevel())).map(Map.Entry::getKey).filter(n -> !n.equals(pl.getAuthority().getOwner())).toList();
            if (au.size() == 0) {
                PlayerFaceRenderer.drawFace(poseStack, pl.getAuthority().getOwner(), getStartX() + 328, getStartY() + 21, 7);
                plsty = 14;
            } else if (au.size() == 1) {
                PlayerFaceRenderer.drawFace(poseStack, au.get(0), getStartX() + 328, getStartY() + 21, 7);
                PlayerFaceRenderer.drawFace(poseStack, pl.getAuthority().getOwner(), getStartX() + 319, getStartY() + 21, 7);
                plsty = 20;
            } else {
                var tx = Component.literal("+" + au.size());
                int txw = mc.font.width(tx);
                drawSmartText(guiGraphics, tx, getStartX() + 336 - txw - 2, getStartY() + 21);
                PlayerFaceRenderer.drawFace(poseStack, pl.getAuthority().getOwner(), getStartX() + 336 - txw - 2 - 8, getStartY() + 21, 7);
                plsty = 18 + txw;
            }

            drawSmartText(guiGraphics, Component.literal(OEClientUtils.getWidthOmitText(pl.getName(), 240 - plsty, "...")), getStartX() + 103, getStartY() + 21);
        }
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        OERenderUtils.renderTextureSprite(PLAY_LIST_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 2, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);
        renderSmartButtonSprite(poseStack, multiBufferSource, 1, 189, OERenderUtils.MIN_BREADTH * 2, 72 + 9, 9, i, j, onPxW, onPxH, monitorHeight, ADD_PLAYLIST_TEXT, WIDGETS_TEXTURE, 73, 14, 5, 5, 256, 256);
        renderSmartButtonSprite(poseStack, multiBufferSource, 102, 189, OERenderUtils.MIN_BREADTH * 2, 72, 9, i, j, onPxW, onPxH, monitorHeight, ADD_MUSIC_TEXT, WIDGETS_TEXTURE, 73, 14, 5, 5, 256, 256, getSelectedPlayList(blockEntity) == null);

        // Playlist sort: two 9×9 small icons (positions 82+9 / 73+9), icons only, no behavior change.
        // ⚠️ Null check: the hand-drawn path may run before init (otherwise another `renderAppearance` null pointer).
        if (playlistSortButton != null && playlistOrderButton != null) {
            renderSmartButtonSprite(poseStack, multiBufferSource, 73 + 9, 189, OERenderUtils.MIN_BREADTH * 2, 9, 9, i, j, onPxW, onPxH, monitorHeight, WIDGETS_TEXTURE, 73 + playlistSortButton.getSortType().ordinal() * 7, 0, 7, 7, 256, 256);
            renderSmartButtonSprite(poseStack, multiBufferSource, 82 + 9, 189, OERenderUtils.MIN_BREADTH * 2, 9, 9, i, j, onPxW, onPxH, monitorHeight, WIDGETS_TEXTURE, 73 + playlistOrderButton.getOrderType().ordinal() * 7, 7, 7, 7, 256, 256);
        }

        // Music sort: the longed version (text = current sort, refreshed on click) —— same coordinates/text as the in-world display.
        // ⚠️ The text uses this class's own constants (`getToolTipComponent()` is protected in SortButton and not reachable across
        // packages); when the widgets are not created yet a fallback text is used, to avoid a null pointer before `init`.
        var sortText = musicSortButton != null ? musicSortText : Component.translatable("imp.sortType." + SortButton.SortType.NAME.getName());
        var orderText = musicOrderButton != null ? musicOrderText : Component.translatable("imp.orderType." + SortButton.OrderType.DESCENDING.getName());
        var sortIconIdx = musicSortButton != null ? musicSortButton.getSortType().ordinal() : 0;
        var orderIconIdx = musicOrderButton != null ? musicOrderButton.getOrderType().ordinal() : 0;
        renderSmartButtonSprite(poseStack, multiBufferSource, MUSIC_SORT_X, 189, OERenderUtils.MIN_BREADTH * 2, MUSIC_SORT_W, 9, i, j, onPxW, onPxH, monitorHeight, sortText, WIDGETS_TEXTURE, 73 + sortIconIdx * 7, 0, 7, 7, 256, 256);
        renderSmartButtonSprite(poseStack, multiBufferSource, MUSIC_ORDER_X, 189, OERenderUtils.MIN_BREADTH * 2, MUSIC_ORDER_W, 9, i, j, onPxW, onPxH, monitorHeight, orderText, WIDGETS_TEXTURE, 73 + orderIconIdx * 7, 7, 7, 7, 256, 256);
        // 「分类」: drawn on the world screen too (the monitor instance holds the current category, so both paths agree)
        renderSmartButtonSprite(poseStack, multiBufferSource, CATEGORY_BUTTON_X, 189, OERenderUtils.MIN_BREADTH * 2, CATEGORY_BUTTON_W, 9, i, j, onPxW, onPxH, monitorHeight, categoryButton == null ? CATEGORY_TEXT : categoryButton.getMessage(), true);

        // The filter box at the top, hand-drawn: the in-world display path must draw it explicitly or it is invisible on the panel.
        // Not drawn when the config option is off.
        if (IamMusicPlayer.getConfig().uiConfig.enablePlayListMusicFilter)
            renderSmartEditBoxSprite(poseStack, multiBufferSource, (width - SEARCH_BOX_WIDTH) / 2, 3, OERenderUtils.MIN_BREADTH * 2, SEARCH_BOX_WIDTH, 12, i, j, onPxW, onPxH, monitorHeight, playlistSearchText.isEmpty() ? SEARCH_HINT_TEXT.getString() : playlistSearchText);

        // The in-world display path must merge the "pending invites" in itself and mark them "invite".
        var pls = new ArrayList<MusicPlayList>();
        var myPls = getSyncManager().getMyPlayList();
        if (myPls != null)
            pls.addAll(myPls);
        var invPls = getSyncManager().getInvitedPlayList();
        if (invPls != null) {
            for (var iv : invPls)
                if (iv != null && pls.stream().noneMatch(n -> n.getUuid().equals(iv.getUuid())))
                    pls.add(iv);
        }
        int plsc = 0;
        if (pls != null) {
            plsc = pls.size();
            for (int k = 0; k < Math.min(8, pls.size()); k++) {
                var playList = pls.get(k);
                renderSmartButtonBoxSprite(poseStack, multiBufferSource, 1, 20 + (k * 21), OERenderUtils.MIN_BREADTH * 2, 90, 21, i, j, onPxW, onPxH, monitorHeight, playList.getUuid().equals(getSelectedPlayList(blockEntity)));
                float sx = 1;
                if (!playList.getImage().isEmpty()) {
                    sx += 18 + 2;
                    PlayImageRenderer.getInstance().renderSprite(playList.getImage(), poseStack, multiBufferSource, 3 * onPxW, monitorHeight - (20 + (k * 21) + 2 + 17) * onPxH, OERenderUtils.MIN_BREADTH * 4, 17 * onPxH, i, j);
                }

                renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(OEClientUtils.getWidthOmitText(playList.getName(), 80 - sx, "...")), sx + 3, 20 + (k * 21) + 3, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);
                boolean invitedRow = invPls != null && invPls.stream().anyMatch(n -> n.getUuid().equals(playList.getUuid()));
                if (invitedRow) {
                    // [Invited row] second row = the two actions "accept" / "reject" (**the same coordinates and colors as the GUI**, see
                    // MyPlayListFixedListWidget's public constants/static methods).
                    // ⚠️ The row box is drawn at monitor x=1 with width 90 ⇒ in-row local coordinates need +1 to become monitor coordinates.
                    int rowW = MyPlayListFixedListWidget.INVITE_ROW_W;
                    int actY = 20 + (k * 21) + MyPlayListFixedListWidget.INVITE_ACT_Y;
                    renderSmartTextSpriteColorSprite(poseStack, multiBufferSource, Component.translatable("imp.text.inviteAccept"),
                            MyPlayListFixedListWidget.inviteAcceptX(rowW) + 1, actY,
                            OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, MyPlayListFixedListWidget.INVITE_ACCEPT_COLOR, i);
                    renderSmartTextSpriteColorSprite(poseStack, multiBufferSource, Component.translatable("imp.text.inviteReject"),
                            MyPlayListFixedListWidget.inviteRejectX(rowW) + 1, actY,
                            OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, MyPlayListFixedListWidget.INVITE_REJECT_COLOR, i);
                } else {
                    renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(MyPlayListFixedListWidget.dateFormat.format(new Date(playList.getCreateDate()))), sx + 3, 20 + (k * 21) + 12, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, 0.7f, i);
                }
            }
        }
        renderScrollbarSprite(poseStack, multiBufferSource, 92, 20, OERenderUtils.MIN_BREADTH * 2, 168, i, j, onPxW, onPxH, monitorHeight, plsc, 8);

        updateInfoText();
        if (INFO_TEXT != null)
            renderSmartTextSprite(poseStack, multiBufferSource, INFO_TEXT, width - IIMPSmartRender.mc.font.width(INFO_TEXT) - 3, 11, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

        var ms = getSyncManager().getMusics(getSelectedPlayList(blockEntity));
        boolean favRowDate = isFavSource(getSelectedMusicPlayList(blockEntity));
        int msc = 0;
        if (ms != null) {
            msc = ms.size();
            for (int k = 0; k < Math.min(4, ms.size()); k++) {
                var music = ms.get(k);
                if (k == 0)
                    dev.felnull.imp.client.music.link.LinkDebug.log("playlist row image",
                            "playlist", String.valueOf(getSelectedPlayList(blockEntity)),
                            "songCount", ms.size(),
                            "name", music.getName(),
                            "imageType", String.valueOf(music.getImage().getImageType()),
                            "isEmpty", music.getImage().isEmpty(),
                            "imageId", music.getImage().isEmpty() ? "(EMPTY)" : music.getImage().getIdentifier());
                renderSmartButtonBoxSprite(poseStack, multiBufferSource, 102, 40 + (k * 37), OERenderUtils.MIN_BREADTH * 2, 267 - 10, 37, i, j, onPxW, onPxH, monitorHeight, false);
                float sx = 2;
                if (!music.getImage().isEmpty()) {
                    sx += 37 + 2;
                    PlayImageRenderer.getInstance().renderSprite(music.getImage(), poseStack, multiBufferSource, 103 * onPxW, monitorHeight - (40 + (k * 37) + 1 + 35) * onPxH, OERenderUtils.MIN_BREADTH * 4, 35 * onPxH, i, j);
                }
                var nt = Component.literal(music.getName());
                renderSmartTextSprite(poseStack, multiBufferSource, nt, sx + 3 + 102, 40 + (k * 37) + 3, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, Math.min(1, (267f - 9f - sx - 15f) / (float) IIMPSmartRender.mc.font.width(nt)), i);

                renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(music.getAuthor()), sx + 3 + 102, 40 + (k * 37) + 14, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);

                var pname = PlayerNameUtil.serverNameComponent(music.getOwnerName(), music.getOwner(), MusicsFixedListWidget.UNKNOWN_PLAYER_TEXT);

                poseStack.pushPose();
                poseStack.translate((sx + 102 + 2) * onPxW, monitorHeight - (40 + (k * 37) + 23 + 9) * onPxH, OERenderUtils.MIN_BREADTH * 4);
                PlayerFaceRenderer.drawFaceSprite(poseStack, multiBufferSource, music.getOwner(), onPxH * 9, i, j);
                poseStack.popPose();

                renderSmartTextSprite(poseStack, multiBufferSource, pname, sx + 3 + 102 + 12, 40 + (k * 37) + 26, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);

                renderSmartTextSprite(poseStack, multiBufferSource, MusicsFixedListWidget.rowDate(music, favRowDate
                        || (musicSortButton != null && musicSortButton.getSortType() == SortButton.SortType.PUBLISH_DATE)), sx + 3 + 102 + 12 + 88, 40 + (k * 37) + 26, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);
            }
        }
        renderScrollbarSprite(poseStack, multiBufferSource, 360, 40, OERenderUtils.MIN_BREADTH * 2, 148, i, j, onPxW, onPxH, monitorHeight, msc, 4);

        var pl = getSelectedMusicPlayList(blockEntity);
        if (pl != null) {
            // 「刷新」 on the panel too (same place: the header band, right of the title). Drawn from the block entity
            // only: this instance has screen == null, so the widget's own visibility fields are not usable here.
            if (BiliBiliSpaceUtil.isRefreshable(pl.getSource()) || BiliBiliPlaylistRefresh.isRunning())
                renderSmartButtonSprite(poseStack, multiBufferSource, REFRESH_X, REFRESH_Y, OERenderUtils.MIN_BREADTH * 2,
                        REFRESH_W, REFRESH_H, i, j, onPxW, onPxH, monitorHeight,
                        BiliBiliPlaylistRefresh.isRunning() ? REFRESH_RUNNING_TEXT : REFRESH_TEXT, true);
            renderSmartButtonSprite(poseStack, multiBufferSource, 336, 20, OERenderUtils.MIN_BREADTH * 2, 33, 9, i, j, onPxW, onPxH, monitorHeight, DETAIL_TEXT, true);
            // The "manage" key must be drawn here too: the widget exists only on the GUI path (the in-world display uses another
            // monitor instance with screen=null that is never init'd), so otherwise the panel has only "info" and no "manage".
            renderSmartButtonSprite(poseStack, multiBufferSource, 336, 31, OERenderUtils.MIN_BREADTH * 2, 33, 9, i, j, onPxW, onPxH, monitorHeight, MANAGE_TEXT, true);
            int plsty;
            var au = pl.getAuthority().getPlayersAuthority().entrySet().stream().sorted(Comparator.comparingInt(o -> o.getValue().getLevel())).map(Map.Entry::getKey).filter(n -> !n.equals(pl.getAuthority().getOwner())).toList();
            if (au.size() == 0) {
                renderPlayerFaceSprite(poseStack, multiBufferSource, pl.getAuthority().getOwner(), 328, 20.5f, OERenderUtils.MIN_BREADTH * 2, 7, i, j, onPxW, onPxH, monitorHeight);
                plsty = 14;
            } else if (au.size() == 1) {
                renderPlayerFaceSprite(poseStack, multiBufferSource, au.get(0), 328, 20.5f, OERenderUtils.MIN_BREADTH * 2, 7, i, j, onPxW, onPxH, monitorHeight);
                renderPlayerFaceSprite(poseStack, multiBufferSource, pl.getAuthority().getOwner(), 319, 20.5f, OERenderUtils.MIN_BREADTH * 2, 7, i, j, onPxW, onPxH, monitorHeight);
                plsty = 20;
            } else {
                var tx = Component.literal("+" + au.size());
                int txw = mc.font.width(tx);
                renderPlayerFaceSprite(poseStack, multiBufferSource, au.get(0), 336 - txw - 2 - 8, 20.5f, OERenderUtils.MIN_BREADTH * 2, 7, i, j, onPxW, onPxH, monitorHeight);
                renderSmartTextSprite(poseStack, multiBufferSource, tx, 336 - txw - 2, 22, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
                plsty = 18 + txw;
            }
            renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(OEClientUtils.getWidthOmitText(pl.getName(), 235 - plsty, "...")), 103, 22, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (musicPlayListsCash != getSyncManager().getMyPlayList()) {
            musicPlayListsCash = getSyncManager().getMyPlayList();
            updateList();
        }

        // The invited list must be re-sorted too when it changes (it is merged into the same display list and marked "invite")
        if (invitePlayListsCash != getSyncManager().getInvitedPlayList()) {
            invitePlayListsCash = getSyncManager().getInvitedPlayList();
            updateList();
        }

        if (musicsCash != getSyncManager().getMusics(getSelectedPlayList())) {
            musicsCash = getSyncManager().getMusics(getSelectedPlayList());
            var curPl = getSelectedPlayList();
            if (musicsFixedButtonsList != null && !java.util.Objects.equals(curPl, lastScrollPlaylist)) {
                musicsFixedButtonsList.setScrollAmount(0f);
                lastScrollPlaylist = curPl;
            }
            updateMusics();
        }

        updateInfoText();

        if (addMusic != null)
            addMusic.active = getSelectedMusicPlayList() != null && getSelectedMusicPlayList().getAuthority().getAuthorityType(IIMPSmartRender.mc.player.getGameProfile().getId()).isMoreMember();

        if (detailButton != null)
            detailButton.visible = getSelectedMusicPlayList() != null;
        // The "manage" key, like the "info" key, is only usable when a playlist is selected —— **it must be refreshed every tick together**.
        if (manageButton != null)
            manageButton.visible = getSelectedMusicPlayList() != null;

        updateCategoryButton();
        updateRefreshButton();
    }

    // ------------------------------------------------------------------ 「刷新」 (import source walk)

    /**
     * Keeps the refresh key in step with the selected playlist: visible only for a playlist that knows where it was
     * imported from — or that looks like an UP import and can still be claimed (see {@link #claimIfNeeded}). */
    private void updateRefreshButton() {
        if (refreshButton == null)
            return;
        var pl = getSelectedMusicPlayList();
        var source = pl == null ? "" : pl.getSource();
        // A claim/bind that a click kicked off: as soon as the source is known the walk continues by itself
        if (pendingRefresh != null) {
            if (pl == null || !pendingRefresh.equals(pl.getUuid()) || !BiliBiliPlaylistRefresh.isRunning()) {
                if (pl != null && pendingRefresh.equals(pl.getUuid()) && BiliBiliSpaceUtil.isRefreshable(pl.getSource())
                        && !BiliBiliPlaylistRefresh.needsRebind()) {
                    // ⚠️ Never start the walk before this playlist's songs are here: the bind above drops the song cache
                    // (`MusicSyncManager#reset()`), so starting right away walks with `known = 0` and appends everything
                    // again. The playlist itself carries its song count — wait for that many.
                    var songs = getSyncManager().getMusics(pl.getUuid());
                    var want = pl.getMusicList() == null ? 0 : pl.getMusicList().size();
                    if (songs == null || songs.size() < want) {
                        var waited = System.currentTimeMillis() - pendingSince;
                        if (waited < PENDING_SONGS_TIMEOUT_MS)
                            return;                       // still loading: try again next tick
                        LinkDebug.log("playlist refresh deferred", "playlist", pl.getUuid(),
                                "have", songs == null ? -1 : songs.size(), "want", want, "waitedMs", waited);
                        pendingRefresh = null;
                    } else {
                        pendingRefresh = null;
                        BiliBiliPlaylistRefresh.clearResult();
                        BiliBiliPlaylistRefresh.setExistence(existence());
                        BiliBiliPlaylistRefresh.start(pl.getUuid(), pl.getSource(), songs, pl.getName());
                        source = pl.getSource();
                    }
                } else if (pl == null || !pendingRefresh.equals(pl.getUuid())
                        || BiliBiliPlaylistRefresh.state() == BiliBiliPlaylistRefresh.State.FAILED) {
                    // The claim failed (or the page moved on): stop waiting, the key reports the failure itself
                    pendingRefresh = null;
                }
            }
        }
        var running = BiliBiliPlaylistRefresh.isRunning();
        // A playlist bound by an earlier version still carries the legacy "<UP 名> 的投稿" name: fix it once per session
        // as soon as its page is shown, so the name does not depend on the user pressing 「刷新」.
        if (pl != null && BiliBiliSpaceUtil.isRefreshable(pl.getSource()) && nameFixTried.add(pl.getUuid())) {
            BiliBiliPlaylistRefresh.setExistence(existence());
            BiliBiliPlaylistRefresh.fixLegacyName(pl.getUuid(), pl.getName());
        }
        // The key shows when the playlist has a source (**「刷新」**), or when it could be given one (**「绑定UP」**).
        var bindable = pl != null && !BiliBiliSpaceUtil.isRefreshable(pl.getSource()) && bindableBySongs(pl);
        // A playlist bound to the **wrong UP** (the refresh refuses with `needsRebind`) gets the bind key back:
        // re-binding infers the mid from the songs again.
        var rebind = pl != null && BiliBiliPlaylistRefresh.needsRebind();
        // The server rejects both silently: adding songs needs `canAddMusic`, binding rewrites the playlist source (`canEdit`).
        var auth = pl == null ? null : pl.getAuthority().getAuthorityType(IIMPSmartRender.mc.player.getGameProfile().getId());
        var canAdd = auth != null && auth.canAddMusic();
        // 「刷新」要能用：有来源、能加歌、绑的 UP 也没错。用不上时退成「只补投稿时间」——
        // 它只写本地标签、不碰歌单，所以「只是成员」或歌单没有来源时也点得动。
        var canRefresh = pl != null && BiliBiliSpaceUtil.isRefreshable(pl.getSource()) && canAdd && !rebind;
        // 只补时间只服务**收藏夹来源**的歌单（那类歌单的内容本该只由收藏夹决定）；别的歌单不给这个键
        var pubdatesKey = pl != null && BiliBiliSpaceUtil.favIdOf(pl.getSource()) >= 0 && !canRefresh && !bindable && !rebind;
        refreshKeyPubdates = pubdatesKey;
        refreshButton.visible = pl != null && (BiliBiliSpaceUtil.isRefreshable(pl.getSource())
                || claimTried.contains(pl.getUuid()) || claimIfNeeded(pl) || bindable || rebind || pubdatesKey);
        var songs = pl == null ? null : getSyncManager().getMusics(pl.getUuid());
        refreshButton.active = refreshButton.visible && !running && (pubdatesKey
                ? songs != null && !songs.isEmpty()
                : auth != null && (bindable || rebind ? auth.canEdit() : auth.canAddMusic()));
        refreshButton.setMessage(bindable || rebind ? BIND_UP_TEXT : refreshLabel(pl == null ? null : pl.getUuid(), pubdatesKey));
    }

    /**
     * Whether this playlist looks like an **UP import** and may be offered 「绑定UP」: ≥ 2 of its B站 entries carry a local
     * 分区/合集 tag, which only the import path writes. */
    private boolean bindableBySongs(MusicPlayList pl) {
        var list = getSyncManager().getMusics(pl.getUuid());
        if (list == null)
            return false;
        int tagged = 0;
        for (var m : list) {
            var src = m == null ? null : m.getSource();
            if (src == null || src.getLoaderType() == null
                    || !src.getLoaderType().equals(IMPMusicMedias.BILIBILI.getName()))
                continue;
            var tag = BiliBiliArcTags.getInstance().get(BiliBiliUtil.stripPage(src.getIdentifier()));
            if (tag != null && ((tag.zone() != null && !tag.zone().isBlank())
                    || (tag.seasons() != null && !tag.seasons().isEmpty())) && ++tagged >= 2)
                return true;
        }
        return false;
    }

    /**
     * The key's own text is the progress/result line: 「刷新」 → 「刷新中…」 → 「+3 首」 / 「没有新的」 / 「刷新失败」; the
     * client-wide state is shown only for its own playlist. */
    private Component refreshLabel(@Nullable UUID selected, boolean pubdatesKey) {
        var state = BiliBiliPlaylistRefresh.state();
        var belongs = selected != null && selected.equals(BiliBiliPlaylistRefresh.playlist())
                && BiliBiliPlaylistRefresh.pubdateOnly() == pubdatesKey;
        if (state == BiliBiliPlaylistRefresh.State.RUNNING)
            return belongs ? REFRESH_RUNNING_TEXT : REFRESH_TEXT;
        var finished = BiliBiliPlaylistRefresh.finishedAt();
        if (!belongs || finished <= 0L || System.currentTimeMillis() - finished >= REFRESH_RESULT_MS)
            return REFRESH_TEXT;
        return switch (state) {
            case FAILED -> REFRESH_FAILED_TEXT;
            case DONE -> pubdatesKey
                    ? (BiliBiliPlaylistRefresh.added() > 0
                    ? Component.translatable("imp.text.playListPubdatesFilled", BiliBiliPlaylistRefresh.added())
                    : Component.translatable("imp.text.playListPubdatesNone"))
                    : (BiliBiliPlaylistRefresh.added() > 0
                    ? Component.translatable("imp.text.playListRefreshAdded", BiliBiliPlaylistRefresh.added())
                    : (BiliBiliPlaylistRefresh.pubdatesFilled() > 0
                    ? Component.translatable("imp.text.playListPubdatesFilled", BiliBiliPlaylistRefresh.pubdatesFilled())
                    : Component.translatable("imp.text.playListRefreshNone")));
            default -> REFRESH_TEXT;
        };
    }

    /** A click: walk the source, or claim it first for a playlist imported before the source field existed. */
    private void onRefreshPressed() {
        var pl = getSelectedMusicPlayList();
        if (pl == null || BiliBiliPlaylistRefresh.isRunning())
            return;
        var source = pl.getSource();
        // 键是「只补投稿时间」形态时只走补时间（不需要权限，也不碰歌单）
        if (refreshKeyPubdates) {
            var own = getSyncManager().getMusics(pl.getUuid());
            if (own == null || own.isEmpty())
                return;
            BiliBiliPlaylistRefresh.startPubdates(pl.getUuid(), source, own);
            return;
        }
        // 「重绑UP」: no usable source, or the refresh refused because the bound UP is not the songs' author.
        // The UP is inferred from the playlist's own songs — sampled, so a mixed playlist is refused rather than bound.
        if (!BiliBiliSpaceUtil.isRefreshable(source) || BiliBiliPlaylistRefresh.needsRebind()) {
            if (!claimTried.contains(pl.getUuid())) {
                var list = getSyncManager().getMusics(pl.getUuid());
                if (list == null || list.isEmpty())
                    return;
                claimTried.add(pl.getUuid());
                BiliBiliPlaylistRefresh.claim(pl.getUuid(), pl.getName(), list, existence());
            }
            pendingRefresh = pl.getUuid();
            pendingSince = System.currentTimeMillis();
            return;
        }
        // ⚠️ A plain refresh needs this playlist's songs as the "already there" set — with an empty/unknown set every
        // returned video counts as new, so wait for them (see the deferred-start guard in updateRefreshButton).
        var songs = getSyncManager().getMusics(pl.getUuid());
        var want = pl.getMusicList() == null ? 0 : pl.getMusicList().size();
        if (songs == null || songs.size() < want) {
            pendingRefresh = pl.getUuid();
            pendingSince = System.currentTimeMillis();
            return;
        }
        BiliBiliPlaylistRefresh.setExistence(existence());
        BiliBiliPlaylistRefresh.start(pl.getUuid(), source, songs, pl.getName());
    }

    /** Set when a click kicked off a claim: the refresh follows once the claimed source is known. */
    @Nullable
    private UUID pendingRefresh;
    /** When {@link #pendingRefresh} was set — a follow-up refresh gives up instead of waiting forever. */
    private long pendingSince;
    /** How long a follow-up refresh waits for this playlist's songs to arrive before it gives up. */
    private static final long PENDING_SONGS_TIMEOUT_MS = 15000L;

    /** Playlists whose one-shot claim was already started this session. */
    private final Set<UUID> claimTried = new HashSet<>();
    /** Playlists whose legacy name was already repaired this session. */
    private final Set<UUID> nameFixTried = new HashSet<>();

    /**
     * One-time source claim, started only when the name "&lt;UP 名&gt;的投稿" and every B站 song's author match; else
     * the playlist gets 「绑定UP」. */
    private boolean claimIfNeeded(MusicPlayList pl) {
        if (pl == null || !pl.getSource().isBlank() || claimTried.contains(pl.getUuid()))
            return false;
        var list = getSyncManager().getMusics(pl.getUuid());
        if (!BiliBiliPlaylistRefresh.looksLikeUpImport(pl.getName(), list))
            return false;
        claimTried.add(pl.getUuid());
        BiliBiliPlaylistRefresh.claim(pl.getUuid(), pl.getName(), list, existence());
        return true;
    }

    private BlockEntityExistence existence() {
        return BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity());
    }

    /** Refresh of the playlist list (left): the selection is cleared only when the entry is really gone (sorting never
     * removes it). The null check must come first — {@code tick()} can run before {@code init()} (instances are reused)
     * and a bare use would NPE; {@link #widgetsReady} blocks that. */
    private void updateList() {
        musicPlayLists.clear();
        // Do not sort before the widgets exist (a tick before init), but never leave the list empty either:
        // init calls this again at the end, when `widgetsReady` is set.
        if (musicPlayListsCash != null && widgetsReady
                && playlistSortButton != null && playlistOrderButton != null)
            musicPlayLists.addAll(playlistSortButton.sort(musicPlayListsCash, playlistOrderButton));

        // Pending invites must be shown here too (marked "invite", clicking one = accept): the server keeps private
        // playlists out of "joinable playlists", so this is the invitee's only entry point.
        var invites = getSyncManager().getInvitedPlayList();
        if (invites != null && widgetsReady && playlistSortButton != null && playlistOrderButton != null) {
            for (var iv : playlistSortButton.sort(invites, playlistOrderButton)) {
                if (iv != null && musicPlayLists.stream().noneMatch(n -> n.getUuid().equals(iv.getUuid())))
                    musicPlayLists.add(iv);
            }
        }

        // Clear the selection only when the playlist data has really arrived; `getMyPlayList() != null` belongs to that
        // test: a server broadcast empties the sync cache (null) while `cash` still holds the previous round.
        var dataReady = getSyncManager().getMyPlayList() != null
                && musicPlayListsCash != null && !musicPlayLists.isEmpty();
        if (dataReady && getSelectedPlayList() != null
                && musicPlayLists.stream().noneMatch(n -> n.getUuid().equals(getSelectedPlayList()))) {
            if (LinkDebug.isEnabled())
                LinkDebug.log("PlayList clear-selection", "sel", getSelectedPlayList(),
                        "list", musicPlayLists.size(),
                        "cash", String.valueOf(musicPlayListsCash.size()),
                        "widgetsReady", widgetsReady,
                        "btns", (playlistSortButton != null) + "/" + (playlistOrderButton != null));
            setSelectedPlayList(null);
        }
    }

    /** [Diagnostic] Last logged "music filter" line — logged only when it changes. */
    private static final Map<String, String> MUSIC_FILTER_LAST_LOG = new HashMap<>();
    /** Cap on remembered keys; 64 is far above the real key count. */
    private static final int MUSIC_FILTER_MAX_KEYS = 64;

    /** Songs in the playlist: sorted, then filtered by the keyword (song name or artist). */
    private void updateMusics() {
        musics.clear();
        updateMusicSortTypes();
        if (musicsCash == null)
            return;
        if (!widgetsReady)
            return;
        var keyword = playlistSearchText == null ? "" : playlistSearchText.trim().toLowerCase(Locale.ROOT);
        for (var m : musicSortButton.sort(musicsCash, musicOrderButton)) {
            if (m == null)
                continue;
            if (!keyword.isEmpty()) {
                var name = m.getName() == null ? "" : m.getName().toLowerCase(Locale.ROOT);
                var author = m.getAuthor() == null ? "" : m.getAuthor().toLowerCase(Locale.ROOT);
                if (!name.contains(keyword) && !author.contains(keyword))
                    continue;
            }
            // 「分类」: a category filter is a **view** filter — it never decides what a delete or a play touches,
            // only what the list displays (songs whose category we never learned are not claimed by any category)
            if (!matchesCategory(m))
                continue;
            musics.add(m);
        }
        // [Diagnostic] updateMusics runs every tick, so log the filter line only when it changes.
        if (LinkDebug.isEnabled()) {
            var filterLine = "keyword=" + playlistSearchText + " | cash=" + musicsCash.size() + " | shown=" + musics.size();
            if (!filterLine.equals(MUSIC_FILTER_LAST_LOG.get("music filter"))) {
                if (MUSIC_FILTER_LAST_LOG.size() > MUSIC_FILTER_MAX_KEYS)
                    MUSIC_FILTER_LAST_LOG.clear();
                MUSIC_FILTER_LAST_LOG.put("music filter", filterLine);
                LinkDebug.log("music filter", "keyword", playlistSearchText,
                        "cash", musicsCash.size(), "shown", musics.size());
            }
        }
    }

    /** 收藏夹来源的歌单（`fav:<fid>`）：它的"加入歌单时间"就是导入时刻，所以行上直接显示投稿时间。 */
    private static boolean isFavSource(@Nullable MusicPlayList pl) {
        return pl != null && BiliBiliSpaceUtil.favIdOf(pl.getSource()) >= 0;
    }

    /** 「投稿日期」只对 B站 UP 投稿歌单有意义（投稿时间只在 UP 主页导入时写过）。 */
    private void updateMusicSortTypes() {
        if (musicSortButton == null)
            return;
        var pl = getSelectedMusicPlayList();
        musicSortButton.setPublishDateEnabled(pl != null && BiliBiliSpaceUtil.midOf(pl.getSource()) >= 0);
        // 档位表变了要重刷文字，否则标签停在旧档
        refreshMusicSortLabels();
        // 行上的时间跟着排序走；收藏夹歌单无论如何都显示投稿时间
        if (musicsFixedButtonsList != null)
            musicsFixedButtonsList.setByPublishDate(isFavSource(pl)
                    || musicSortButton.getSortType() == SortButton.SortType.PUBLISH_DATE);
    }

    private void updateInfoText() {
        var pls = getSyncManager();
        if (pls.getMyPlayListInfo() != lastPlayListInfo) {
            lastPlayListInfo = pls.getMyPlayListInfo();
            if (lastPlayListInfo != null) {
                INFO_TEXT = Component.translatable("imp.text.playlistInfo", lastPlayListInfo.playListCount(), lastPlayListInfo.musicCount());
            } else {
                INFO_TEXT = null;
            }
        }
    }

    public void setSelectedMusic(@Nullable UUID uuid) {
        getScreen().insSelectedMusic(uuid);
    }

    /**
     * Whether this playlist is one "I was invited to and have not handled yet" — sent by the server as an INVITATION
     * entry. */
    private boolean isInvited(@Nullable UUID playListId) {
        if (playListId == null)
            return false;
        var invites = getSyncManager().getInvitedPlayList();
        return invites != null && invites.stream().anyMatch(n -> n.getUuid().equals(playListId));
    }

    /**
     * [Accept invite] Goes through the existing {@code add_playlist} instruction (the server takes the player in via
     * {@code canJoin}); the reset that follows makes the two action texts disappear. */
    private void acceptInvite(MusicPlayList playList) {
        getScreen().insAddPlayList(playList.getUuid());
    }

    /**
     * [Reject invite] Reuses the "exit" key's channel: an invitee has no {@code canDelete()}, so the delete falls
     * through to {@code exitPlayList}. */
    private void rejectInvite(MusicPlayList playList) {
        if (!(getScreen().getBlockEntity() instanceof MusicManagerBlockEntity be))
            return;
        NetworkManager.sendToServer(IMPPackets.MUSIC_OR_PLAYLIST_DELETE,
                new IMPPackets.MusicOrPlayListDeleteMessage(playList.getUuid(), UUID.randomUUID(),
                        BlockEntityExistence.getByBlockEntity(be), false).toRFBB());
    }

    @Nullable
    public UUID getSelectedMusicRaw() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedMusicRaw(musicManagerBlockEntity);
        return null;
    }

    @Nullable
    public UUID getSelectedMusicRaw(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedMusic(mc.player);
    }

    public MusicPlayList getSelectedMusicPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        var pls = getSyncManager().getMyPlayList();
        if (pls == null) return null;
        return getSyncManager().getMyPlayList().stream().filter(n -> n.getUuid().equals(getSelectedPlayList(musicManagerBlockEntity))).findFirst().orElse(null);
    }

    public MusicPlayList getSelectedMusicPlayList() {
        return musicPlayLists.stream().filter(n -> n.getUuid().equals(getSelectedPlayList())).findFirst().orElse(null);
    }

    public UUID getSelectedPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getSelectedPlayList(musicManagerBlockEntity);
        return null;
    }

    public UUID getSelectedPlayList(MusicManagerBlockEntity musicManagerBlockEntity) {
        return musicManagerBlockEntity.getSelectedPlayList(mc.player);
    }

    private void setSelectedPlayList(UUID selectedPlayList) {
        getScreen().insSelectedPlayList(selectedPlayList);
    }

    @Override
    public void onUpdateSelectedPlayList(UUID playListId) {
        // ⚠️ Only **another** playlist means "other categories, start from 「全部」 again": the screen re-notifies with
        // the same id on a sync round, and a blind reset would also wipe the filter init just restored from the config.
        var samePlayList = playListId != null && playListId.equals(categoryPlayListId);
        categoryPlayListId = playListId;
        if (!samePlayList) {
            categoryFilter = SpaceCategoryListWidget.Row.all(0, true);
            categoryPickMode = false;
            // The config remembers the *current* playlist's filter: switching playlists must not resurrect the old one
            saveCategoryFilter();
        }
        applyCategoryPickMode();
        updateMusics();
        updateCategoryButton();
    }

    /** Which playlist the current {@link #categoryFilter} belongs to. */
    @Nullable
    private UUID categoryPlayListId;

    // ------------------------------------------------------------------ 「分类」 helpers

    private void toggleCategoryPick() {
        categoryPickMode = !categoryPickMode;
        if (categoryPickMode)
            refreshCategoryRows();
        applyCategoryPickMode();
    }

    /** The picker takes the song list's place while it is open (the song list is hidden, not resized). */
    private void applyCategoryPickMode() {
        if (musicsFixedButtonsList != null) {
            musicsFixedButtonsList.visible = !categoryPickMode;
            musicsFixedButtonsList.active = !categoryPickMode;
        }
        if (categoryPickList != null) {
            categoryPickList.visible = categoryPickMode;
            categoryPickList.active = categoryPickMode;
        }
    }

    /** A row was clicked: apply it, remember it, close the picker and refresh the list. */
    private void pickCategory(SpaceCategoryListWidget.Row row) {
        categoryFilter = row;
        categoryPickMode = false;
        saveCategoryFilter();
        applyCategoryPickMode();
        updateMusics();
        updateCategoryButton();
    }

    /**
     * Builds the rows from the **selected playlist's songs** and the local tag cache — purely local, no request.
     */
    private void refreshCategoryRows() {
        var all = musicsCash == null ? List.<Music>of() : musicsCash;
        var seasonCounts = new LinkedHashMap<String, Integer>();
        var zoneCounts = new LinkedHashMap<String, Integer>();
        var ungrouped = 0;
        var tagged = 0;
        for (var m : all) {
            var tag = arcTagOf(m);
            if (tag == null)
                continue;
            tagged++;
            if (tag.seasons().isEmpty())
                ungrouped++;
            for (var s : tag.seasons())
                seasonCounts.merge(s, 1, Integer::sum);
            if (!tag.zone().isBlank())
                zoneCounts.merge(tag.zone(), 1, Integer::sum);
        }

        var rows = new ArrayList<SpaceCategoryListWidget.Row>();
        rows.add(SpaceCategoryListWidget.Row.all(all.size(), isNoCategoryFilter()));

        // 「多P / 非多P」: counted from the **local part cache** alone — a video whose parts were never looked up counts
        // as 非多P (the submission list carries no part count, so each one would cost a `view`), and it moves over by
        // itself once the video is played or its parts are queried.
        var multiPart = 0;
        for (var m : all) {
            if (partCountOf(m) > 1)
                multiPart++;
        }
        rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.spaceCategoryParts")));
        rows.add(SpaceCategoryListWidget.Row.multiPart(multiPart,
                isActiveCategory(SpaceCategoryListWidget.Kind.MULTI_PART, "")));
        rows.add(SpaceCategoryListWidget.Row.singlePart(all.size() - multiPart,
                isActiveCategory(SpaceCategoryListWidget.Kind.SINGLE_PART, "")));

        if (tagged == 0) {
            rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.musicCategoryNone")));
        } else {
            rows.add(SpaceCategoryListWidget.Row.ungrouped(ungrouped,
                    categoryFilter.kind() == SpaceCategoryListWidget.Kind.UNGROUPED));
            if (!seasonCounts.isEmpty()) {
                rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.spaceCategorySeasons")));
                seasonCounts.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .forEach(e -> rows.add(SpaceCategoryListWidget.Row.season(e.getKey(), e.getValue(),
                                isActiveCategory(SpaceCategoryListWidget.Kind.SEASON, e.getKey()))));
            }
            if (!zoneCounts.isEmpty()) {
                rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.spaceCategoryZones")));
                zoneCounts.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .forEach(e -> rows.add(SpaceCategoryListWidget.Row.zone(e.getKey(), e.getValue(),
                                isActiveCategory(SpaceCategoryListWidget.Kind.ZONE, e.getKey()))));
            }
        }
        categoryRows = rows;
        categoryPickList.setEntryList(rows);
        categoryPickList.setScrollAmount(0);
    }

    /** Whether the given row is the one currently applied (drives the tick drawn in the picker). */
    private boolean isActiveCategory(SpaceCategoryListWidget.Kind kind, String name) {
        return categoryFilter != null && categoryFilter.kind() == kind
                && (name == null ? categoryFilter.filterName().isEmpty() : name.equals(categoryFilter.filterName()));
    }

    /** The 「全部」row is ticked while no category filter is applied (picking it clears the filter). */
    private boolean isNoCategoryFilter() {
        return categoryFilter == null || categoryFilter.kind() == SpaceCategoryListWidget.Kind.ALL;
    }

    /** Whether one song passes the current category filter. Songs with no remembered tag never pass a tag category. */
    private boolean matchesCategory(Music m) {
        var kind = categoryFilter == null ? SpaceCategoryListWidget.Kind.ALL : categoryFilter.kind();
        // 分P rows are answered from the local part cache alone (no tags, no request) and are therefore decided
        // **before** the "no tag ⇒ no match" rule below
        if (kind == SpaceCategoryListWidget.Kind.MULTI_PART)
            return partCountOf(m) > 1;
        if (kind == SpaceCategoryListWidget.Kind.SINGLE_PART)
            return partCountOf(m) <= 1;
        if (kind == SpaceCategoryListWidget.Kind.ALL)
            return true;
        var tag = arcTagOf(m);
        if (tag == null)
            return false;
        return switch (kind) {
            case UNGROUPED -> tag.seasons().isEmpty();
            case SEASON -> tag.seasons().contains(categoryFilter.filterName());
            case ZONE -> categoryFilter.filterName().equals(tag.zone());
            case ALL, HEADER, MULTI_PART, SINGLE_PART -> true;
        };
    }

    /** [Local-only part count] {@code 0} and {@code 1} both mean "not a multi-part video"; asks nobody. */
    private static int partCountOf(Music m) {
        var source = m == null ? null : m.getSource();
        if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
            return 0;
        var parts = BiliBiliParts.known(source.getIdentifier());
        return parts == null ? 0 : parts.size();
    }

    /** [Remembered 合集/分区 of one song] Looked up by the **bare video number** (an expanded part carries `BV…-p2`, with
     * which every part would drop out of its category); {@code null} when there are none. */
    @Nullable
    private static BiliBiliArcTags.Tag arcTagOf(Music m) {
        var source = m.getSource();
        if (source == null || !IMPMusicMedias.BILIBILI.getName().equals(source.getLoaderType()))
            return null;
        return BiliBiliArcTags.getInstance().get(BiliBiliUtil.stripPage(source.getIdentifier()));
    }

    /** The button names the active category (shortened to fit 74 px), so the filter is never invisible. */
    private void updateCategoryButton() {
        if (categoryButton == null)
            return;
        if (categoryFilter == null || categoryFilter.kind() == SpaceCategoryListWidget.Kind.ALL) {
            categoryButton.setMessage(CATEGORY_TEXT);
        } else {
            var name = categoryFilter.label().getString();
            if (name.length() > 5)
                name = name.substring(0, 5) + "…";
            categoryButton.setMessage(Component.translatable("imp.button.musicCategoryOn", name));
        }
        categoryButton.active = musicsCash != null && !musicsCash.isEmpty();
    }
}
