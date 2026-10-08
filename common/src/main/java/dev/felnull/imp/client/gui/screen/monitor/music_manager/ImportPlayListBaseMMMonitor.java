package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceModel;
import dev.felnull.imp.client.bilibili.BiliBiliSpaceUtil;
import dev.felnull.imp.client.gui.components.PlayListMusicsFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.components.SpaceCategoryListWidget;
import dev.felnull.imp.client.gui.components.SpaceVideoListWidget;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.playlist.IMPPlaylistLoaders;
import dev.felnull.imp.client.music.playlist.IPlaylistLoader;
import dev.felnull.imp.client.music.playlist.IPlaylistResolver;
import dev.felnull.imp.client.music.playlist.PlaylistResult;
import dev.felnull.imp.client.renderer.PlayImageRenderer;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.networking.IMPPackets;
import dev.felnull.imp.util.IMPNbtUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ImportPlayListBaseMMMonitor extends MusicManagerMonitor {
    private static final ResourceLocation IMPORT_PLAY_LIST_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/music_manager/monitor/import_play_list.png");
    private static final Component BACK_TEXT = Component.translatable("gui.back");
    private static final Component LOADING_TEXT = Component.translatable("imp.text.playlistLoading");
    private List<PlayListEntry> playListEntries = new ArrayList<>();
    private List<Music> musics = new ArrayList<>();
    private SmartButton importButton;
    private EditBox playlistIdentifierEditBox;
    private PlayListMusicsFixedListWidget playListMusicsFixedButtonsList;
    private IPlaylistLoader playlistLoader;
    private IPlaylistResolver playlistLoaderThread;
    /** True during resetImport: blocks the recursion "clear the box → responder → resolve again". */
    private boolean resetting = false;

    private ImageInfo importImageInfo = ImageInfo.EMPTY;

    // ------------------------------------------------------------------ "UP 主页" (space) view
    // Shown in place of the ordinary playlist view for a Bilibili space id ("space:<mid>"). No new MonitorType on
    // purpose: the block entity persists the current page by name, so an unknown type would break existing saves.

    private static final Component SPACE_IMPORT_ALL_TEXT = Component.translatable("imp.button.spaceImportAll");
    private static final Component SPACE_IMPORT_SELECTED_TEXT = Component.translatable("imp.button.spaceImportSelected");
    private static final Component SPACE_HINT_TEXT = Component.translatable("imp.text.spaceHint");
    /** Left panel (categories), right panel (videos) and the two bottoms buttons; the page itself is 370 x 199. */
    private static final int SPACE_CATEGORY_X = 1;
    private static final int SPACE_CATEGORY_Y = 38;
    private static final int SPACE_CATEGORY_W = 126;
    private static final int SPACE_CATEGORY_H = 117;
    private static final int SPACE_CATEGORY_ROWS = 9;
    private static final int SPACE_VIDEO_X = 129;
    private static final int SPACE_VIDEO_Y = 10;
    private static final int SPACE_VIDEO_W = 240;
    private static final int SPACE_VIDEO_H = 148;
    private static final int SPACE_VIDEO_ROWS = 10;
    /** An identifier typed character by character only starts a request once typing pauses (Bilibili answers 412 to a burst). */
    private static final long SPACE_ID_DEBOUNCE_MS = 500;

    /** Short line of the two import pages' status slot when this page refused an UP 主页 link (see {@link #allowsSpaceImport()}). */
    private static final Component SPACE_MIX_REFUSED_TEXT = Component.translatable("imp.text.spaceImportRefused");

    /** **Static** on purpose: a re-init (window resize) or re-entering the page would otherwise restart a 20 s /
     * ~40-request load — slow, and a burst against Bilibili's risk control. Dropped when the page stops being an
     * "UP 主页" page. */
    private static final BiliBiliSpaceModel spaceModel = new BiliBiliSpaceModel();
    private SpaceCategoryListWidget spaceCategoryList;
    private SpaceVideoListWidget spaceVideoList;
    private SmartButton spaceImportAllButton;
    private SmartButton spaceImportSelectedButton;
    /** Whether the widgets currently show the space layout (the switch itself only runs when this changes). */
    private boolean spaceLayout;
    private String pendingSpaceId;
    private long pendingSpaceIdAt;
    /** UP whose finished load is already written into the block entity (each setter is a client→server instruction). */
    private long spacePushedFor = -1;
    /** Reason of the last local refusal (null = none); drawn where "what the import would create" is drawn. */
    private Component importRefusal;
    /** True while the box holds an UP 主页 id on a page that must not import one (see {@link #allowsSpaceImport()}). */
    private boolean spaceMixRefused;

    // ---- chunked import: one packet cannot carry an unbounded list ----
    // A packet NBT tag is capped at 2 MiB accounted (~1100 songs, IMPNbtUtil#PACKET_NBT_BUDGET): the first batch
    // **creates** the playlist, the rest append to the same id.

    /** Batches still to send (empty = no import running). */
    private final java.util.ArrayDeque<List<Music>> importBatches = new java.util.ArrayDeque<>();
    /** Id of the playlist this import creates / appends to. */
    private UUID importPlaylistId;
    /** True when the first batch creates the playlist (otherwise every batch appends to an existing one). */
    private boolean importCreates;
    /** Import source of the new playlist ({@code space:<mid>} / {@code fav:<fid>} / empty). */
    private String importSource = "";
    private int importBatchTotal;
    private int importBatchSent;
    private int importSongTotal;
    /** Last rows handed to the category widget; record equality keeps the row identity stable across ticks. */
    private List<SpaceCategoryListWidget.Row> spaceRows = List.of();
    private List<BiliBiliSpaceUtil.SpaceVideo> spaceVideoRows;
    /** Last ticked-category set (so the video list scrolls back to the top only when the ticks really changed). */
    private java.util.Set<String> spaceCategoryTicks = java.util.Set.of();

    public ImportPlayListBaseMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
        if(screen == null){
            playlistLoader = null;
            return;
        }
        this.playlistLoader = IMPPlaylistLoaders.getLoader(getLoaderType());
    }

    private String getLoaderType(){
        if(!(getScreen().getBlockEntity() instanceof MusicManagerBlockEntity blockEntity))return "";
        return blockEntity.getImportPlayListType(mc.player);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.playlistLoader = IMPPlaylistLoaders.getLoader(getLoaderType());

        addRenderWidget(new SmartButton(getStartX() + 5, getStartY() + 180, 87, 15, BACK_TEXT, n -> {
            if (getParentType() != null)
                insMonitor(getParentType());
            resetImport();
        }));

        this.importButton = addRenderWidget(new SmartButton(getStartX() + 95, getStartY() + 180, 87, 15, CreatePlayListMMMonitor.IMPORT_TEXT, n -> onImport()));
        this.importButton.active = canImport();

        this.playlistIdentifierEditBox = addRenderWidget(new EditBox(mc.font, getStartX() + 6, getStartY() + 164, 175, 12,
                playlistLoader == null ? Component.empty() : Component.translatable(String.format("imp.editBox.%sPlaylistIdentifier", playlistLoader.getID()))){
            @Override
            public boolean keyPressed(int i, int j, int k) {
                if (this.isActive() && this.isFocused() && Screen.isPaste(i)) {
                    insertText(extractPlayListId(Minecraft.getInstance().keyboardHandler.getClipboard()));
                    return true;
                }
                return super.keyPressed(i, j, k);
            }
        });
        this.playlistIdentifierEditBox.setMaxLength(300);
        this.playlistIdentifierEditBox.setResponder(this::startPlayListLoad);
        this.playlistIdentifierEditBox.setValue(getImportPlayList());

        this.playListMusicsFixedButtonsList = addRenderWidget(new PlayListMusicsFixedListWidget(getStartX() + 1, getStartY() + 10, 368, 148,
                Component.translatable(String.format("imp.fixedList.%sPlayListMusics", playlistLoader == null ? "" : playlistLoader.getID())),
                4, playListEntries, this.playListMusicsFixedButtonsList));

        // ---- "UP 主页" widgets: built unconditionally, switched by visibility (see applySpaceLayout) ----
        this.spaceCategoryList = addRenderWidget(new SpaceCategoryListWidget(getStartX() + SPACE_CATEGORY_X, getStartY() + SPACE_CATEGORY_Y,
                SPACE_CATEGORY_W, SPACE_CATEGORY_H, Component.translatable("imp.fixedList.spaceCategories"), SPACE_CATEGORY_ROWS,
                spaceRows, this::pickSpaceCategory, null));
        this.spaceVideoList = addRenderWidget(new SpaceVideoListWidget(getStartX() + SPACE_VIDEO_X, getStartY() + SPACE_VIDEO_Y,
                SPACE_VIDEO_W, SPACE_VIDEO_H, Component.translatable("imp.fixedList.spaceVideos"), SPACE_VIDEO_ROWS,
                List.of(), spaceModel, null));
        this.spaceImportAllButton = addRenderWidget(new SmartButton(getStartX() + 95, getStartY() + 180, 87, 15, SPACE_IMPORT_ALL_TEXT, n -> startSpaceImport(true)));
        this.spaceImportSelectedButton = addRenderWidget(new SmartButton(getStartX() + 185, getStartY() + 180, 87, 15, SPACE_IMPORT_SELECTED_TEXT, n -> startSpaceImport(false)));

        if(tryAutoFillID() && playlistLoader != null)
            startPlayListLoad(playlistIdentifierEditBox.getValue());

        // The layout follows what the box holds right now (an auto-filled space link is already in it), so the page
        // never shows the ordinary view for a frame before switching over.
        applySpaceLayout(allowsSpaceImport() && BiliBiliSpaceUtil.midOf(currentIdentifier()) >= 0);
    }

    private boolean tryAutoFillID(){
        if(playlistLoader == null)return false;
        var text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if(text.isEmpty())return false;
        var r = extractPlayListId(text);
        if(!r.equals(text)) {
            playlistIdentifierEditBox.setValue(r);
            // Reset the EditBox display window: scrollTo is a fixed point while "the cursor is at the end and the tail
            // fits", so the displayPos of the previous long content survives. moveCursorToEnd must not be used — it
            // would fire onValueChange and start the load a second time.
            playlistIdentifierEditBox.setCursorPosition(0);
            playlistIdentifierEditBox.setHighlightPos(0);
            return true;
        }
        return false;
    }

    /** On a Ctrl+V paste, leaves only the ID in the box: the loader's own recognition comes first, then
     * platform-independent share link recognition (album / radio / QQ Music playlist links also turn into an ID). */
    private String extractPlayListId(String text){
        if(playlistLoader == null)return text;
        return playlistLoader.autoPasteFromClipboard(text)
                // `convert=false`: this runs on the client thread, and converting a QQ single-track link would do a
                // synchronous songmid→songid HTTP request and freeze the screen.
                .or(() -> IPlaylistLoader.autoPasteFromMusicLink(playlistLoader, text, false))
                .orElse(text);
    }

    abstract protected void onImport();

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        OERenderUtils.drawTexture(IMPORT_PLAY_LIST_TEXTURE, guiGraphics.pose(), getStartX(), getStartY(), 0f, 0f, width, height, width, height);

        if (spaceLayout) {
            // The ordinary "loading playlist" line belongs to the preview resolver, which the space view never starts
            renderSpaceInfo(guiGraphics);
            return;
        }

        if (isPlayListLoading()) {
            drawSmartText(guiGraphics, LOADING_TEXT, getStartX() + 2, getStartY() + 11);
        }

        // A refusal takes the slot of "the playlist this import would create": it is the one thing to read, and it is
        // where the UP view draws a load failure too.
        if (importRefusal != null) {
            drawSmartFixedWidthText(guiGraphics, importRefusal, getStartX() + 200, getStartY() + 167, 168, 0xFFAA0000);
        } else {
            drawSmartText(guiGraphics, Component.literal(getImportPlayListName()), getStartX() + 200, getStartY() + 167);
        }
        drawSmartText(guiGraphics, Component.literal(getImportPlayListAuthor()), getStartX() + 200, getStartY() + 183);
    }

    /** The left panel (avatar, UP name, progress) and the two lines under the video list. The avatar comes from
     * {@code x/web-interface/card}, which answers {@code code=0} with no cookie; when unavailable (or covers are
     * switched off) the UP name's first character marks the box instead. */
    private void renderSpaceInfo(GuiGraphics guiGraphics) {
        int x = getStartX();
        int y = getStartY();

        var upName = spaceModel.upName();
        var face = spaceModel.face();
        if (showImages() && !face.isBlank()) {
            PlayImageRenderer.getInstance().draw(new ImageInfo(ImageInfo.ImageType.URL, face),
                    guiGraphics.pose(), x + 4, y + 13, 22, true);
        } else {
            drawSmartButtonBox(guiGraphics, x + 4, y + 13, 22, 22, 1);
            drawSmartCenterText(guiGraphics, Component.literal(upName.isBlank() ? "?" : upName.substring(0, 1)), x + 15, y + 20, 0xFF444444);
        }
        drawSmartFixedWidthText(guiGraphics, Component.literal(upName), x + 29, y + 14, 95);
        drawSmartFixedWidthText(guiGraphics, spaceInfoText(), x + 29, y + 26, 95, 0xFF333333);

        boolean failed = spaceModel.state() == BiliBiliSpaceModel.State.FAILED || importRefusal != null;
        drawSmartFixedWidthText(guiGraphics, spaceTitleText(), x + 200, y + 167, 168, failed ? 0xFFAA0000 : 0xFF000000);
        drawSmartFixedWidthText(guiGraphics, spaceSelectionText(), x + 200, y + 183, 80, 0xFF333333);
    }

    /** The "load image covers" switch; the avatar counts as an image too (one switch, predictable behaviour). */
    private static boolean showImages() {
        return IamMusicPlayer.getConfig().bilibiliConfig.loadVideoImage;
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;
        OERenderUtils.renderTextureSprite(IMPORT_PLAY_LIST_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 2, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);

        renderSmartButtonSprite(poseStack, multiBufferSource, 5, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, BACK_TEXT, true);

        if (BiliBiliSpaceUtil.midOf(getImportPlayList(blockEntity)) >= 0) {
            // The two lists are not drawn here: this path only receives the block entity (the ordinary import page
            // does not draw its list here either).
            renderSmartButtonSprite(poseStack, multiBufferSource, 95, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, SPACE_IMPORT_ALL_TEXT, true, !canImport(blockEntity));
            renderSmartButtonSprite(poseStack, multiBufferSource, 185, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, SPACE_IMPORT_SELECTED_TEXT, true, !canImport(blockEntity));
            renderSmartEditBoxSprite(poseStack, multiBufferSource, 6, 164, OERenderUtils.MIN_BREADTH * 4, 175, 12, i, j, onPxW, onPxH, monitorHeight, getImportPlayList(blockEntity));

            renderSmartButtonBoxSprite(poseStack, multiBufferSource, 4, 13, OERenderUtils.MIN_BREADTH * 2, 22, 22, i, j, onPxW, onPxH, monitorHeight);
            var upName = getImportPlayListAuthor(blockEntity);
            if (!upName.isBlank())
                renderSmartCenterTextSprite(poseStack, multiBufferSource, Component.literal(upName.substring(0, 1)), 15, 20, OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, i);
            renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(upName), 29, 14, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

            renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(getImportPlayListName(blockEntity)), 200, 167, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
            renderSmartTextSprite(poseStack, multiBufferSource, SPACE_HINT_TEXT, 200, 183, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

            renderScrollbarSprite(poseStack, multiBufferSource, 360, 10, OERenderUtils.MIN_BREADTH * 2, 148, i, j, onPxW, onPxH, monitorHeight, 1, 1);
            renderScrollbarSprite(poseStack, multiBufferSource, 117, SPACE_CATEGORY_Y, OERenderUtils.MIN_BREADTH * 2, SPACE_CATEGORY_H, i, j, onPxW, onPxH, monitorHeight, 1, 1);
            return;
        }

        renderSmartButtonSprite(poseStack, multiBufferSource, 95, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, CreatePlayListMMMonitor.IMPORT_TEXT, true, !canImport(blockEntity));

        renderSmartEditBoxSprite(poseStack, multiBufferSource, 6, 164, OERenderUtils.MIN_BREADTH * 4, 175, 12, i, j, onPxW, onPxH, monitorHeight, getImportPlayList(blockEntity));

        renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(getImportPlayListName(blockEntity)), 200, 167, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);
        renderSmartTextSprite(poseStack, multiBufferSource, Component.literal(getImportPlayListAuthor(blockEntity)), 200, 183, OERenderUtils.MIN_BREADTH * 2, onPxW, onPxH, monitorHeight, i);

        renderScrollbarSprite(poseStack, multiBufferSource, 360, 10, OERenderUtils.MIN_BREADTH * 2, 148, i, j, onPxW, onPxH, monitorHeight, 1, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if(playlistLoader == null) {
            insMonitor(getParentType() == null ? MusicManagerBlockEntity.MonitorType.PLAY_LIST : getParentType());
            return;
        }
        tickSpaceModel();
        // Batches started by a subclass in this or a previous tick (one packet per tick).
        tickImportBatches();
        if(playlistLoaderThread != null && !playlistLoaderThread.isAlive()){
            if(playlistLoaderThread.isFailure())
                resetImport();
            else{
                var r = playlistLoaderThread.getResult();
                if(r == null)resetImport();
                else {
                    setImportPlayList(r.id());
                    setImportPlayListAuthor(r.author());
                    setImportPlayListName(r.name());
                    setImportPlayListMusicCount(r.count());
                    setImportPlayListIco(r.ico());
                    playListEntries.clear();
                    playListEntries.addAll(r.musicEntries());
                    musics.clear();
                    musics.addAll(r.musics());
                }
            }
        }
        this.importButton.active = canImport();
    }

    protected int getImportPlayListMusicCount() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListMusicCount(musicManagerBlockEntity);
        return 0;
    }

    protected int getImportPlayListMusicCount(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListMusicCount(mc.player);
    }


    protected String getImportPlayListAuthor() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListAuthor(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayListAuthor(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListAuthor(mc.player);
    }

    protected String getImportPlayList() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayList(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayList(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportIdentifier(mc.player);
    }

    protected String getImportPlayListName() {
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return getImportPlayListName(musicManagerBlockEntity);
        return "";
    }

    protected String getImportPlayListName(MusicManagerBlockEntity blockEntity) {
        return blockEntity.getImportPlayListName(mc.player);
    }

    protected boolean canImport() {
        if (spaceImportForbidden())
            return false;
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return canImport(musicManagerBlockEntity);
        return false;
    }

    protected boolean canImport(MusicManagerBlockEntity blockEntity) {
        return !getImportPlayList(blockEntity).isEmpty() && getImportPlayListMusicCount(blockEntity) > 0;
    }

    protected boolean isPlayListLoading() {
        return playlistLoaderThread != null && playlistLoaderThread.isAlive();
    }

    protected void setImportPlayListAuthor(String author) {
        getScreen().insImportPlayListAuthor(author);
    }

    protected void setImportPlayListMusicCount(int count) {
        getScreen().insImportPlayListMusicCount(count);
    }

    protected void setImportPlayListName(String name) {
        getScreen().insImportPlayListName(name);
    }

    protected void setImportPlayList(String id) {
        getScreen().insImportIdentifier(id);
    }

    protected void setImportPlayListIco(ImageInfo imageInfo){
        this.importImageInfo = imageInfo;
    }

    public ImageInfo getImportImageInfo() {
        return importImageInfo;
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        // Target of the "back" button (used by the base class MusicManagerMonitor#init to build the button): prefer returning to the **page you actually came from**
        // —— this page is often entered automatically by clipboard recognition, and the hardcoded "import select page" is not where you came from.
        var screen = getScreen();
        var origin = screen == null ? null : screen.getLastMonitorType();
        if (origin != null && origin != getType() && origin != MusicManagerBlockEntity.MonitorType.OFF) {
            LinkDebug.log("import back target", "origin", origin.getName(), "self", getType().getName());
            return origin;
        }
        LinkDebug.log("import back target", "origin", "null/self", "self", getType().getName());
        return fallbackParentType();
    }

    /** The fixed target used when the origin cannot be obtained. Subclasses override **this method** (not
     * {@link #getParentType()}), otherwise "back to the real origin" is shadowed. */
    protected MusicManagerBlockEntity.MonitorType fallbackParentType() {
        return MusicManagerBlockEntity.MonitorType.ADD_ONLINE_PLAY_LIST;
    }

    @Override
    protected void onBackParent() {
        super.onBackParent();
        // Stop the resolver and clear the state on the way out: a result arriving a step late would keep refreshing
        // the page that was already left.
        resetImport();
    }

    protected void resetImport() {
        // Leaving the page (or a finished import) must not leave a half-sent job behind: the batches live in this
        // monitor instance, which survives a page change.
        if (!importBatches.isEmpty()) {
            LinkDebug.log("import cancelled", "sent", importBatchSent + "/" + importBatchTotal,
                    "playlist", importPlaylistId);
            importBatches.clear();
        }
        importPlaylistId = null;
        if (!spaceMixRefused)
            importRefusal = null;
        // Clearing the box re-fires the responder, and startPlayListLoad calls resetImport itself, so `resetting`
        // has to block that recursion; otherwise a resolver that yields nothing leaves the page stuck loading.
        resetting = true;
        try {
            setImportPlayList("");
            setImportPlayListAuthor("");
            setImportPlayListName("");
            setImportPlayListMusicCount(0);
            stopPlayListLoad();
        } finally {
            resetting = false;
        }
    }

    protected void startPlayListLoad(String id) {
        if (resetting) return;
        if (playlistLoader == null) return;
        // Empty text: the resolver would treat "" as an ID and throw "For input string: \"\""
        if (id == null || id.trim().isEmpty()) return;
        // "UP 主页": the responder fires on every keystroke, so wait for typing to pause before touching the API
        // (a burst makes Bilibili's risk control answer 412 / -352).
        if (BiliBiliSpaceUtil.midOf(id) >= 0) {
            pendingSpaceId = id;
            pendingSpaceIdAt = System.currentTimeMillis();
            return;
        }
        stopPlayListLoad();
        playListEntries.clear();
        musics.clear();
        resetImport();
        playlistLoaderThread = playlistLoader.getResolver(id);
        playlistLoaderThread.start();
    }

    protected void stopPlayListLoad() {
        if (playlistLoaderThread != null) {
            playlistLoaderThread.stopped();
            playlistLoaderThread = null;
        }
    }

    // ------------------------------------------------------------------ "UP 主页" (space) view

    /** The identifier being edited: the box wins over the block entity, because a paste lands in the box first and
     * only reaches the block entity after a client→server→client round trip. */
    private String currentIdentifier() {
        var box = playlistIdentifierEditBox;
        if (box != null && !box.getValue().isBlank())
            return box.getValue();
        return getImportPlayList();
    }

    /** True while this page shows the "UP 主页" view (subclasses use it to pick their import path). */
    protected boolean isSpaceMode() {
        return spaceLayout;
    }

    /** Whether this page may import an UP 主页 at all. The append page must not: an UP's uploads always get their
     * own playlist, since mixing two UPs makes 「刷新」/「绑定UP」 undecidable. */
    protected boolean allowsSpaceImport() {
        return true;
    }

    /** True when the identifier box holds an UP 主页 id on a page that must not import one. */
    protected boolean spaceImportForbidden() {
        return !allowsSpaceImport() && BiliBiliSpaceUtil.midOf(currentIdentifier()) >= 0;
    }

    /** Splits {@code musics} into batches that each fit one packet and starts sending them (one batch per tick, from
     * {@link #tickImportBatches()}). {@code create} = the first batch creates the playlist under {@code playListId};
     * otherwise every batch is appended to that existing playlist. */
    protected void startChunkedImport(List<Music> musics, UUID playListId, boolean create, String source) {
        if (!importBatches.isEmpty() || musics == null || musics.isEmpty() || playListId == null)
            return;
        int from = 0;
        while (from < musics.size()) {
            int count = IMPNbtUtil.fittingMusicCount("ImportMusics", musics, from);
            importBatches.add(List.copyOf(musics.subList(from, Math.min(from + count, musics.size()))));
            from += count;
        }
        importPlaylistId = playListId;
        importCreates = create;
        importSource = source == null ? "" : source;
        importBatchSent = 0;
        importBatchTotal = importBatches.size();
        importSongTotal = musics.size();
        importRefusal = null;
        LinkDebug.log("import batches", "songs", importSongTotal, "batches", importBatchTotal,
                "largest", importBatches.peek().size(), "create", create, "playlist", playListId);
    }

    /** True while batches are being sent (the page shows the progress instead of the song count). */
    protected boolean importSending() {
        return !importBatches.isEmpty();
    }

    protected int importBatchSent() {
        return importBatchSent;
    }

    protected int importBatchTotal() {
        return importBatchTotal;
    }

    /** One batch per tick: a burst of packets for a 1000-song import is neither needed nor polite. */
    private void tickImportBatches() {
        if (importBatches.isEmpty())
            return;
        var musics = importBatches.poll();
        var existence = BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity());
        if (importBatchSent == 0 && importCreates) {
            NetworkManager.sendToServer(IMPPackets.MUSIC_PLAYLIST_ADD, new IMPPackets.MusicPlayListMessage(importPlaylistId,
                    getImportPlayListName(), getImportImageInfo(), false, false, List.of(), existence, musics, importSource).toRFBB());
        } else {
            NetworkManager.sendToServer(IMPPackets.MULTIPLE_MUSIC_ADD,
                    new IMPPackets.MultipleMusicAddMessage(importPlaylistId, musics, existence).toRFBB());
        }
        importBatchSent++;
        LinkDebug.log("import batch sent", "n", importBatchSent + "/" + importBatchTotal, "songs", musics.size(),
                "playlist", importPlaylistId);
        if (!importBatches.isEmpty())
            return;
        var done = importPlaylistId;
        LinkDebug.log("import done", "songs", importSongTotal, "batches", importBatchTotal, "playlist", done);
        importPlaylistId = null;
        importSource = "";
        resetImport();
        importFinished(done);
    }

    /** Called once every batch has been sent (subclasses: leave the page / drop their caches). */
    protected void importFinished(UUID playListId) {
    }

    /** True while a local refusal is on screen (the page then shows why instead of the import count). */
    protected boolean importRefused() {
        return importRefusal != null;
    }

    /** The short refusal line of the two pages' status slot (only the append page refuses anything, see {@link #allowsSpaceImport()}). */
    protected Component importRefusedStatus() {
        return SPACE_MIX_REFUSED_TEXT;
    }

    /** Switches the page between the ordinary playlist view and the "UP 主页" view. Both widget sets are built up
     * front and switched by visibility: the layout can change while the page is open, and re-initialising mid-page
     * would need the screen to re-add every widget. */
    private void applySpaceLayout(boolean space) {
        spaceLayout = space;
        setWidgetShown(playListMusicsFixedButtonsList, !space);
        setWidgetShown(importButton, !space);
        setWidgetShown(spaceCategoryList, space);
        setWidgetShown(spaceVideoList, space);
        setWidgetShown(spaceImportAllButton, space);
        setWidgetShown(spaceImportSelectedButton, space);
        if (!space) {
            // No longer an "UP 主页" page: forget the load and drop what the widgets showed, so switching to a
            // favourites folder cannot briefly draw another UP's videos.
            spaceModel.reset();
            spacePushedFor = -1;
            spaceCategoryTicks = java.util.Set.of();
            spaceVideoRows = null;
            spaceRows = List.of();
            if (spaceCategoryList != null)
                spaceCategoryList.setEntryList(spaceRows);
            if (spaceVideoList != null)
                spaceVideoList.setEntryList(List.of());
        }
    }

    private static void setWidgetShown(AbstractWidget widget, boolean shown) {
        if (widget == null)
            return;
        widget.visible = shown;
        widget.active = shown;
    }

    /** Per-tick work of the "UP 主页" view: layout, load, list contents and the block entity write-back. */
    private void tickSpaceModel() {
        // 0) an UP 主页 id on a page that must not import one (the append page): refuse it and never touch the API —
        //    entering the UP view there is exactly how an UP's uploads would end up mixed into an existing playlist
        if (spaceImportForbidden()) {
            if (!spaceMixRefused) {
                spaceMixRefused = true;
                importRefusal = Component.translatable("imp.text.spaceImportOwnPlaylist");
                LinkDebug.log("space import refused", "why", "this page adds songs to an existing playlist",
                        "mid", BiliBiliSpaceUtil.midOf(currentIdentifier()), "monitor", getType().getName());
            }
            pendingSpaceId = null;
            if (spaceLayout)
                applySpaceLayout(false);
            return;
        }
        if (spaceMixRefused) {
            spaceMixRefused = false;
            importRefusal = null;
        }

        // 1) an identifier typed one character at a time only starts a load once typing pauses
        if (pendingSpaceId != null && System.currentTimeMillis() - pendingSpaceIdAt >= SPACE_ID_DEBOUNCE_MS) {
            var id = pendingSpaceId;
            pendingSpaceId = null;
            var mid = BiliBiliSpaceUtil.midOf(id);
            if (mid >= 0)
                spaceModel.start(mid, spaceMaxPages(), spacePageDelayMs(), spaceParallelism());
        }

        // 2) the layout follows the identifier
        var mid = BiliBiliSpaceUtil.midOf(currentIdentifier());
        if ((mid >= 0) != spaceLayout)
            applySpaceLayout(mid >= 0);
        if (mid < 0)
            return;

        // 3) a load is (re)started only when the model is for another UP; a failed one is retried through the
        //    explicit load above, never once per tick (that would hammer the API)
        if (spaceModel.mid() != mid)
            spaceModel.start(mid, spaceMaxPages(), spacePageDelayMs(), spaceParallelism());

        refreshSpaceWidgets();
        pushSpaceResult();
    }

    /** Request cap for one space load ({@code 0} = no cap) — read here so the model itself stays config-free. */
    private static int spaceMaxPages() {
        return IamMusicPlayer.getConfig().bilibiliConfig.spaceMaxPages;
    }

    /** Pause between space requests; Bilibili's risk control answers 412 / -352 to a burst. */
    private static int spacePageDelayMs() {
        return IamMusicPlayer.getConfig().bilibiliConfig.spacePageDelayMs;
    }

    /** How many zone / 合集 passes may be in flight at once ({@code 1} = strictly sequential). The pacer keeps the
     * request **rate** unchanged, so this only removes the waiting. */
    private static int spaceParallelism() {
        return IamMusicPlayer.getConfig().bilibiliConfig.spaceParallelism;
    }

    /** A category row was clicked in the "UP 主页" view. Ticking several rows unions their videos in the list **and**
     * in 「导入选中分类」; the 「全部」row clears the ticks instead ("no filter at all"). */
    private void pickSpaceCategory(SpaceCategoryListWidget.Row row) {
        // Another selection: a refusal that was about the previous one no longer describes anything.
        if (!spaceMixRefused)
            importRefusal = null;
        if (row.kind() == SpaceCategoryListWidget.Kind.ALL) {
            spaceModel.clearCategoryTicks();
            return;
        }
        spaceModel.toggleCategoryTick(categoryTickKey(row));
    }

    /** The model's key for one row (see {@link BiliBiliSpaceModel#tickKey}). */
    private static String categoryTickKey(SpaceCategoryListWidget.Row row) {
        return switch (row.kind()) {
            case UNGROUPED -> BiliBiliSpaceModel.TICK_UNGROUPED;
            case SEASON -> BiliBiliSpaceModel.tickKey(false, row.filterName());
            case ZONE -> BiliBiliSpaceModel.tickKey(true, row.filterName());
            // The 分P rows exist on the playlist page only (this view never builds them) — no key, like 全部/表头
            case ALL, HEADER, MULTI_PART, SINGLE_PART -> "";
        };
    }

    /** Keeps both lists and the two import buttons in step with the model. */
    private void refreshSpaceWidgets() {
        var ticks = spaceModel.categoryTicks();
        var rows = new ArrayList<SpaceCategoryListWidget.Row>();
        rows.add(SpaceCategoryListWidget.Row.all(spaceModel.videos().size(), ticks.isEmpty()));
        var seasons = spaceModel.seasons();
        var zones = spaceModel.zones();
        if (!seasons.isEmpty()) {
            rows.add(SpaceCategoryListWidget.Row.ungrouped(spaceModel.ungroupedCount(),
                    ticks.contains(BiliBiliSpaceModel.TICK_UNGROUPED)));
            rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.spaceCategorySeasons")));
            for (var s : seasons)
                rows.add(SpaceCategoryListWidget.Row.season(s.name(), s.count(),
                        ticks.contains(BiliBiliSpaceModel.tickKey(false, s.name()))));
        }
        if (!zones.isEmpty()) {
            rows.add(SpaceCategoryListWidget.Row.header(Component.translatable("imp.text.spaceCategoryZones")));
            for (var z : zones)
                rows.add(SpaceCategoryListWidget.Row.zone(z.name(), z.count(),
                        ticks.contains(BiliBiliSpaceModel.tickKey(true, z.name()))));
        }
        // Hand a new list over only when something actually changed (records compare by value, the tick included):
        // the base widget keeps hover state by row identity, so a new list every tick would fight it for nothing.
        if (!rows.equals(spaceRows)) {
            spaceRows = rows;
            spaceCategoryList.setEntryList(rows);
        }

        var visible = spaceModel.visibleVideos();
        if (visible != spaceVideoRows) {
            spaceVideoRows = visible;
            spaceVideoList.setEntryList(visible);
        }

        if (!ticks.equals(spaceCategoryTicks)) {
            spaceCategoryTicks = ticks;
            spaceVideoList.setScrollAmount(0);
        }

        var done = spaceModel.state() == BiliBiliSpaceModel.State.DONE;
        spaceImportAllButton.active = done && !visible.isEmpty();
        // No category ticked = the whole UP; otherwise the union of the ticked categories
        spaceImportAllButton.setMessage(ticks.isEmpty()
                ? Component.translatable("imp.text.spaceImportAllCount", visible.size())
                : Component.translatable("imp.text.spaceImportTickedCategories", visible.size()));
        spaceImportSelectedButton.active = done && spaceModel.selectedCount() > 0;
        spaceImportSelectedButton.setMessage(Component.translatable("imp.text.spaceImportSelectedCount", spaceModel.selectedCount()));
    }

    /** Writes a finished load into the block entity through the same setters the resolver path uses, so the in-world
     * appearance and {@code canImport} see an ordinary "resolved playlist". Each setter is a client→server
     * instruction, hence the "once per UP" guard. */
    private void pushSpaceResult() {
        if (spaceModel.state() != BiliBiliSpaceModel.State.DONE || spacePushedFor == spaceModel.mid())
            return;
        spacePushedFor = spaceModel.mid();
        setImportPlayList(BiliBiliSpaceUtil.ID_PREFIX + spaceModel.mid());
        // Remember the 合集/分区 of every loaded video: the imported `Music` only carries bvid/title/author/cover,
        // so without this the categories are gone the moment the import ends (the playlist page filters from here).
        // 投稿时间也一起记：只有这份列表里有它（每个视频单独查要发一次 view）。
        var arcTags = BiliBiliArcTags.getInstance();
        for (var v : spaceModel.videos()) {
            var tag = spaceModel.tagOf(v.bvid());
            arcTags.remember(v.bvid(), tag.zone(), tag.seasons(), v.createdMs());
        }
        arcTags.flush();
        setImportPlayListName(spaceModel.playlistName());
        setImportPlayListAuthor(spaceModel.upName());
        setImportPlayListMusicCount(spaceModel.videos().size());
        // Cover of the imported playlist: the UP's avatar when we have it, otherwise the newest video's cover as a
        // stand-in (the avatar comes from the card endpoint, which needs no login).
        var face = spaceModel.face();
        var cover = spaceModel.cover();
        var ico = !face.isBlank() ? face : cover;
        setImportPlayListIco(showImages() && !ico.isBlank()
                ? new ImageInfo(ImageInfo.ImageType.URL, ico)
                : ImageInfo.EMPTY);
        LinkDebug.log("BiliBili space view ready", "mid", spaceModel.mid(), "videos", spaceModel.videos().size(),
                "seasons", spaceModel.seasons().size(), "zones", spaceModel.zones().size());
    }

    /** The line under the UP name: what the load is doing (or that it stopped). */
    private Component spaceInfoText() {
        return switch (spaceModel.state()) {
            // The reason itself is long (risk control, "log in via QR", …) and is drawn where the playlist name goes
            case FAILED -> Component.translatable("imp.text.spaceLoadFailed");
            case LOADING -> spaceModel.stage() == BiliBiliSpaceModel.Stage.SEASONS
                    ? Component.translatable("imp.text.spaceLoadingSeasons")
                    : Component.translatable("imp.text.spaceLoading", spaceModel.loaded(),
                    Math.max(spaceModel.total(), spaceModel.loaded()));
            case DONE -> spaceModel.filterRelaxed()
                    // The video list came from the fallback endpoint: no 分区, and 充电/课堂/回放 were not excluded
                    ? Component.translatable("imp.text.spaceLoadedRelaxed", spaceModel.videos().size())
                    : Component.translatable("imp.text.spaceLoaded", spaceModel.videos().size());
            case IDLE -> Component.empty();
        };
    }

    /** Normally the name the import would create, on failure the reason; too long for the narrow left column, but it
     * is the one thing the user must read. */
    private Component spaceTitleText() {
        if (importRefusal != null)
            return importRefusal;
        return spaceModel.state() == BiliBiliSpaceModel.State.FAILED
                ? Component.literal(spaceModel.error())
                : Component.literal(spaceModel.playlistName());
    }

    /** The line under the video list: how much is ticked, and how much of that the filter is hiding. */
    private Component spaceSelectionText() {
        var selected = spaceModel.selectedCount();
        if (selected <= 0)
            return Component.translatable("imp.text.spaceSelectHint");
        var hidden = spaceModel.selectedHiddenByFilter();
        return hidden > 0
                ? Component.translatable("imp.text.spaceSelectedHidden", selected, hidden)
                : Component.translatable("imp.text.spaceSelected", selected);
    }

    /** "导入全部" takes **what the list currently shows** (a selected category relabels the button 导入本类, so a
     * subset is never a surprise); "只导选中" takes the ticked rows in submission order, **including** the ticks the
     * filter hides. */
    private void startSpaceImport(boolean all) {
        if (spaceModel.state() != BiliBiliSpaceModel.State.DONE)
            return;
        var videos = all ? spaceModel.visibleVideos() : spaceModel.selectedVideos();
        if (videos.isEmpty())
            return;
        var spaceMusics = buildSpaceMusics(videos);
        if (spaceMusics.isEmpty())
            return;
        // The subclass' import path reads all three: the count gates the packet, the list is what the packet
        // carries, and the finished resolver keeps the platform resolver (a second full page walk) out of it.
        this.musics.clear();
        this.musics.addAll(spaceMusics);
        setImportPlayListMusicCount(spaceMusics.size());
        onImport();
    }

    /** The videos of a space load as importable musics — built from the submission fields, no per-video request. */
    private List<Music> buildSpaceMusics(List<BiliBiliSpaceUtil.SpaceVideo> videos) {
        var loadImage = IamMusicPlayer.getConfig().bilibiliConfig.loadVideoImage;
        var owner = Minecraft.getInstance().getGameProfile().getId();
        var now = System.currentTimeMillis();
        var out = new ArrayList<Music>(videos.size());
        for (var v : videos) {
            out.add(new Music(
                    UUID.randomUUID(),
                    v.title(),
                    v.author(),
                    new MusicSource(IMPMusicMedias.BILIBILI.getName(), v.bvid(), v.durationMs()),
                    loadImage && !v.cover().isBlank()
                            ? new ImageInfo(ImageInfo.ImageType.URL, v.cover())
                            : ImageInfo.EMPTY,
                    owner,
                    now
            ));
        }
        return out;
    }

    /** In "UP 主页" mode the videos are already in memory, so the platform resolver would walk every submission page
     * a **second** time and ignore the selection — hence an already-finished resolver. */
    protected IPlaylistResolver createImportResolver(String id) {
        if (!isSpaceMode())
            return getPlaylistLoader() == null ? null : getPlaylistLoader().getResolver(id);
        PlaylistResult result = null;
        if (spaceModel.state() == BiliBiliSpaceModel.State.DONE) {
            var importMusics = List.copyOf(musics);
            result = new PlaylistResult(BiliBiliSpaceUtil.ID_PREFIX + spaceModel.mid(), importMusics.size(),
                    spaceModel.playlistName(), spaceModel.upName(), getImportImageInfo(),
                    IPlaylistLoader.createEntriesFromMusics(importMusics), importMusics);
        }
        return new ReadyResolver(id, result);
    }

    /** A resolver that already knows its answer: the import pages run "start a thread, then send a packet with
     * {@link #getMusics()} once it has finished", so this runs that path unchanged and without a single request. */
    private static final class ReadyResolver extends IPlaylistResolver {
        private ReadyResolver(String id, PlaylistResult result) {
            super(id);
            if (result != null)
                setResult(result);
        }

        @Override
        public void run() {
        }
    }

    public record PlayListEntry(String name, String artist, MusicSource source, ImageInfo imageInfo) {
    }

    public IPlaylistLoader getPlaylistLoader() {
        return playlistLoader;
    }

    public List<Music> getMusics() {
        return musics;
    }
}
