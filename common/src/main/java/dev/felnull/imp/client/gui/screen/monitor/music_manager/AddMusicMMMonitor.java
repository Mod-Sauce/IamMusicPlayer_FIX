package dev.felnull.imp.client.gui.screen.monitor.music_manager;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.EditBoxUtil;
import dev.felnull.imp.client.gui.IIMPSmartRender;
import dev.felnull.imp.client.gui.components.MusicLoaderTypesFixedListWidget;
import dev.felnull.imp.client.gui.components.SmartButton;
import dev.felnull.imp.client.gui.screen.MusicManagerScreen;
import dev.felnull.imp.client.lava.LavaPlayerManager;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.client.music.MusicSyncManager;
import dev.felnull.imp.client.music.link.MusicLink;
import dev.felnull.imp.client.music.link.MusicLinks;
import dev.felnull.imp.client.music.media.IMPMusicMedias;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.client.kugou.KuGouUtil;
import dev.felnull.imp.client.music.netmusic.NetMusicUtil;
import dev.felnull.imp.client.qqMusic.QQMusicUtil;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.networking.IMPPackets;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import org.modsauce.otyacraftenginerenewed.networking.existence.BlockEntityExistence;
import org.modsauce.otyacraftenginerenewed.util.FlagThread;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class AddMusicMMMonitor extends SavedMusicBaseMMMonitor {
    private static final Component MUSIC_CHECKING_TEXT = Component.translatable("imp.text.musicChecking");
    private static final Component MUSIC_GUESSING_TEXT = Component.translatable("imp.text.musicGuessing");
    private static final Component MUSIC_LOAD_FAILURE_TEXT = Component.translatable("imp.text.loadFailure");
    private static final Component AUTO_ENTER_TEXT = Component.translatable("imp.text.enterText.auto");
    private static final Component AUTO_INFO_TEXT = Component.translatable("imp.text.loaderTypeInfo.auto");
    private static final Component AUTO_FAILURE_TEXT = Component.translatable("imp.text.loadFailure.auto");
    private static final Component UPLOAD_MUSIC_TEXT = Component.translatable("imp.button.uploadMusic");
    /** Hint for restricted NetEase tracks: no Cookie is configured. */
    private static final Component NETMUSIC_RESTRICTED_TEXT = Component.translatable("imp.text.neteaseRestricted");
    /** It is still unavailable even with a Cookie configured (delisted / region restricted). */
    private static final Component NETMUSIC_RESTRICTED_LOGGED_IN_TEXT = Component.translatable("imp.text.neteaseRestrictedLoggedIn");
    /** QQ Music: the audio is unavailable and no Cookie is configured —— only say it "may" be needed (anonymously some songs are still fetchable). */
    private static final Component QQ_COOKIE_MAYBE_NEEDED_TEXT = Component.translatable("imp.text.qqCookieMaybeNeeded");
    /** QQ Music: the audio is unavailable and a Cookie **is** configured —— the Cookie is stale/incomplete, or the track is restricted. */
    private static final Component QQ_UNAVAILABLE_TEXT = Component.translatable("imp.text.qqUnavailable");
    /** Kugou: the audio is unavailable and **no** login token is configured (paid/VIP tracks need a signed-in session). */
    private static final Component KUGOU_UNAVAILABLE_TEXT = Component.translatable("imp.text.kugouUnavailable");
    /** Kugou: it is unavailable **with** a logged-in session ⇒ the track itself is the problem (must be bought / needs VIP), not the login. */
    private static final Component KUGOU_UNAVAILABLE_LOGGED_IN_TEXT = Component.translatable("imp.text.kugouUnavailableLoggedIn");
    /** Kugou: what is in the box is not a hash (typically a playlist's plain numeric id typed into the single-track channel). */
    private static final Component KUGOU_NOT_A_HASH_TEXT = Component.translatable("imp.text.kugouNotAHash");
    /** "Back to search results" (shown mutually exclusively with the magnifier search button). */
    private static final Component BACK_TO_SEARCH_TEXT = Component.translatable("imp.button.backToSearchResults");
    /** Hover tooltip of the magnifier: searches **music** (not the playlist page's filter box). */
    private static final Component SEARCH_SONG_TEXT = Component.translatable("imp.text.searchSongHint");
    /** Bilibili AV number alone (see {@link #backfillResolvedIdentifier}). */
    private static final Pattern AV_ID_ONLY = Pattern.compile("(?i:av)\\d+");
    /** Bilibili BV number alone: prefix + 10 characters, no "-pN" suffix. */
    private static final Pattern BV_ID_ONLY = Pattern.compile("(?i:BV)[0-9A-Za-z]{10}");
    protected static final Component IMPORT_TEXT = Component.translatable("imp.button.import");
    private final List<String> musicLoaderTypes = new ArrayList<>();
    private EditBox musicSourceNameEditBox;
    private SmartButton searchButton;
    private SmartButton backToSearchButton;
    private SmartButton uploadButton;
    private MusicLoadThread musicLoadThread;
    private boolean loadFailure;
    private MusicLoaderTypesFixedListWidget musicLoaderTypesFixedButtonsList;
    /** auto routing latch: which platform the loader type was last switched for automatically. Not read back from the BE — the loader
     * type syncs late there, so during that gap the box would be cleared and the responder fired again and again (infinite loop). */
    private String autoRoutedPlatform;

    public AddMusicMMMonitor(MusicManagerBlockEntity.MonitorType type, MusicManagerScreen screen) {
        super(type, screen);
        this.playBackX = 189;
        this.playBackY = 140;
        this.isLoaderSelect = true;
    }

    @Override
    public void init(int leftPos, int topPos) {
        // Invalidate the name loaded last time on every entry: `super.init()` uses `getName()` for the initial value of the "music name" box.
        loadedName = null;
        super.init(leftPos, topPos);
        // Clear the previous probe conclusion when re-entering this screen: otherwise the old "needs login / restricted" is still hanging there.
        clearWarn();
        loadFailure = false;
        refreshMusicLoaderTypes();

        this.musicLoaderTypesFixedButtonsList = this.addRenderWidget(new MusicLoaderTypesFixedListWidget(getStartX() + 189, getStartY() + 23, 175, 65, Component.translatable("imp.fixedList.musicLoaderTypes"), 5, musicLoaderTypes, (widget, item) -> {
            // Loader type chosen manually: clear the auto routing latch so a pasted link can auto-switch again next time
            autoRoutedPlatform = null;
            setMusicLoaderType(item);
            loadFailure = false;
        }, this.musicLoaderTypesFixedButtonsList, n -> getMusicLoaderType().equals(n)));

        this.musicSourceNameEditBox = new EditBox(IIMPSmartRender.mc.font, getStartX() + 189, getStartY() + 112, isMSNShortWidth() ? 141 : 177, 12, Component.translatable("imp.editBox.musicSourceName"));
        this.musicSourceNameEditBox.setMaxLength(114514);
        this.musicSourceNameEditBox.visible = isMSNVisible();
        this.musicSourceNameEditBox.setValue(getMusicSourceName());
        this.musicSourceNameEditBox.setResponder(this::setMusicSourceName);
        addRenderWidget(this.musicSourceNameEditBox);

        startMusicLoad(getMusicSourceName(), getScreen().lastSearch);
        getScreen().lastSearch = false;

        this.searchButton = this.addRenderWidget(new SmartButton(getStartX() + 331, getStartY() + 111, 35, 14, Component.translatable("imp.button.search"), n -> {
            insMonitor(MusicManagerBlockEntity.MonitorType.SEARCH_MUSIC);
        }));
        this.searchButton.setHideText(true);
        this.searchButton.setIcon(WIDGETS_TEXTURE, 24, 107, 12, 12);
        this.searchButton.visible = isMSNVisible() && isMSNShortWidth();
        // `SmartButton` renders the button's own text as the tooltip — do **not** setTooltip, it would be drawn twice.

        // "Back to search results" shares the search button slot (x=331,y=111,35×14) — the two are mutually exclusive;
        // the magnifier means **search again** (BACK_TO_SEARCH_TEXT is itself the tooltip — no setTooltip needed).
        this.backToSearchButton = this.addRenderWidget(new SmartButton(getStartX() + 331, getStartY() + 111, 35, 14, BACK_TO_SEARCH_TEXT, n -> {
            insMonitor(MusicManagerBlockEntity.MonitorType.SEARCH_MUSIC);
        }));
        this.backToSearchButton.setHideText(true);
        this.backToSearchButton.setIcon(WIDGETS_TEXTURE, 24, 107, 12, 12);
        this.backToSearchButton.visible = false;

        this.uploadButton = this.addRenderWidget(new SmartButton(getStartX() + 189, getStartY() + 111, 177, 14, UPLOAD_MUSIC_TEXT, n -> {
            insMonitor(MusicManagerBlockEntity.MonitorType.UPLOAD_MUSIC);
        }));
        this.uploadButton.setIcon(WIDGETS_TEXTURE, 36, 107, 12, 12);
        this.uploadButton.visible = "upload".equals(getMusicLoaderType());

        addRenderWidget(new SmartButton(getStartX() + width - 95 - 87, getStartY() + 180, 87, 15, IMPORT_TEXT, n -> insMonitor(MusicManagerBlockEntity.MonitorType.IMPORT_MUSICS_SELECT)));
    }

    @Override
    public void depose() {
        super.depose();
        stopMusicLoad();
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);

        if (musicLoadThread != null && !getMusicSourceName().isEmpty())
            drawSmartText(guiGraphics, "auto".equals(getMusicLoaderType()) ? MUSIC_GUESSING_TEXT : MUSIC_CHECKING_TEXT, getStartX() + 189, getStartY() + 128);

        if (getRawMusicLoaderType() != null) {
            drawSmartText(guiGraphics, getRawMusicLoaderType().getEnterText(), getStartX() + 189, getStartY() + 102);
        } else if ("auto".equals(getMusicLoaderType())) {
            drawSmartText(guiGraphics, AUTO_ENTER_TEXT, getStartX() + 189, getStartY() + 102);
            drawSmartText(guiGraphics, AUTO_INFO_TEXT, getStartX() + 189, getStartY() + 90);
        }

        // When there is a more specific probe conclusion, yield to it —— both are drawn at the same position and would overlap.
        boolean hasWarn = isWarnValid(musicSourceNameEditBox.getValue());

        if (loadFailure && !hasWarn && !getMusicSourceName().isEmpty())
            drawSmartText(guiGraphics, "auto".equals(getMusicLoaderType()) ? AUTO_FAILURE_TEXT : MUSIC_LOAD_FAILURE_TEXT, getStartX() + 189, getStartY() + 128);

        // A problem hint yields to the "cannot load" text, which is drawn at the same position and would overlap. The criterion is
        // local: the box holds exactly the probed identifier and the current channel is the one that produced this conclusion.
        if (hasWarn)
            drawSmartText(guiGraphics, warnText, getStartX() + 189, getStartY() + 128, 0xFFFF5555);
    }

    /**
     * Whether this hint is still valid.
     */
    private boolean isWarnValid(String boxValue) {
        if (warnText == null || warnSourceId == null || warnLoaderId == null)
            return false;
        if (!warnSourceId.equals(boxValue))
            return false;
        return warnLoaderId.equals(currentLoaderId());
    }

    /** The channel id actually in effect right now (the auto-routed one wins if there is one, otherwise the type selected in the interface). */
    private String currentLoaderId() {
        if (autoRoutedPlatform != null)
            return autoRoutedPlatform;
        return getMusicLoaderType();
    }

    /**
     * Rebuild the "music loader type" list (needed by both init and renderAppearance).
     */
    private void refreshMusicLoaderTypes() {
        this.musicLoaderTypes.clear();
        List<String> medias = new ArrayList<>(IMPMusicMedias.getAllMedia().keySet());
        Collections.reverse(medias);
        medias.removeIf(n -> !IMPMusicMedias.showInLoaderList(n));

        // Display order: **configurable** (mod config → the "music source" category → source display order musicLoaderOrder).
        // Empty config (the default) = no sorting at all, i.e. the registry's TreeMap key order;
        // when an order is given, sort by it, with auto/upload still fixed at the front.
        var order = configuredLoaderOrder();
        if (!order.isEmpty()) {
            var idx = new HashMap<String, Integer>();
            for (int i = 0; i < order.size(); i++)
                idx.putIfAbsent(order.get(i), i);
            // Stable sort: sources not present in the config keep the registry's relative order and all move to the back
            medias.sort(Comparator.comparingInt(n -> idx.getOrDefault(n, 1000)));
        }

        // auto / upload are fixed at the **front** (they are not "platform sources"): they are listed in the default config too,
        // but their actual position is decided here, so "empty = default order" still gives auto→upload→platform sources.
        medias.removeIf(n -> "auto".equals(n) || "upload".equals(n));
        this.musicLoaderTypes.add("auto");
        if (IMPMusicMedias.showInLoaderList("upload"))
            this.musicLoaderTypes.add("upload");
        this.musicLoaderTypes.addAll(medias);
    }

    /** Parses the source order from the config (comma separated); empty = default order, the caller sorts nothing. */
    private static List<String> configuredLoaderOrder() {
        try {
            var raw = IamMusicPlayer.getConfig().musicLoaderOrder;
            if (raw == null || raw.isBlank())
                return List.of();
            var out = new ArrayList<String>();
            for (var part : raw.split(",")) {
                var n = part.trim();
                if (!n.isEmpty())
                    out.add(n);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void renderAppearance(MusicManagerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight) {
        super.renderAppearance(blockEntity, poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight);

        refreshMusicLoaderTypes();

        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;


        var ml = getRawMusicLoaderType(blockEntity);

        if (ml != null) {
            renderSmartTextSprite(poseStack, multiBufferSource, ml.getEnterText(), 189, 102, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);
        } else if ("auto".equals(getMusicLoaderType(blockEntity))) {
            renderSmartTextSprite(poseStack, multiBufferSource, AUTO_ENTER_TEXT, 189, 102, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);
            renderSmartTextSprite(poseStack, multiBufferSource, AUTO_INFO_TEXT, 189, 91, OERenderUtils.MIN_BREADTH * 4, onPxW, onPxH, monitorHeight, i);
        }

        if (!"upload".equals(getMusicLoaderType(blockEntity))) {
            var rtp = getRawMusicLoaderType(blockEntity);
            renderSmartEditBoxSprite(poseStack, multiBufferSource, 189, 112, OERenderUtils.MIN_BREADTH * 4, (rtp != null && rtp.isSearchable()) ? 141 : 177, 12, i, j, onPxW, onPxH, monitorHeight, getName(blockEntity));
            if (rtp != null && rtp.isSearchable())
                renderSmartButtonSprite(poseStack, multiBufferSource, 333, 111, OERenderUtils.MIN_BREADTH * 4, 33, 14, i, j, onPxW, onPxH, monitorHeight, MusicManagerMonitor.WIDGETS_TEXTURE, 24, 107, 11, 11, 256, 256);
        } else {
            renderSmartButtonSprite(poseStack, multiBufferSource, 189, 111, OERenderUtils.MIN_BREADTH * 4, 177, 14, i, j, onPxW, onPxH, monitorHeight, UPLOAD_MUSIC_TEXT, WIDGETS_TEXTURE, 36, 107, 12, 12, 256, 256);
        }

        renderScrollbarSprite(poseStack, multiBufferSource, 355, 23, OERenderUtils.MIN_BREADTH * 3, 65, i, j, onPxW, onPxH, monitorHeight, musicLoaderTypes.size(), 5);

        for (int k = 0; k < Math.min(5, musicLoaderTypes.size()); k++) {
            var lt = musicLoaderTypes.get(k);
            renderSmartButtonBoxSprite(poseStack, multiBufferSource, 189, 23 + (k * 13), OERenderUtils.MIN_BREADTH * 3, 165, 13, i, j, onPxW, onPxH, monitorHeight, lt.equals(getMusicLoaderType(blockEntity)));
            var type = IMPMusicMedias.getMedia(lt);
            int tx = 189 + 2;
            if ((type != null && type.getIcon() != null) || "upload".equals(lt)) {
                var icon = type != null ? type.getIcon() : MusicLoaderTypesFixedListWidget.UPLOAD_ICON;
                OERenderUtils.renderTextureSprite(icon, poseStack, multiBufferSource, (189f + 1f) * onPxW, monitorHeight - (23f + ((float) k * 13f) + 1f + 11f) * onPxH, OERenderUtils.MIN_BREADTH * 5, 0, 0, 0, 11f * onPxW, 11f * onPxH, 0, 0, 11f * onPxW, 11f * onPxH, 11f * onPxW, 11f * onPxH, i, j);
                tx += 13 - 1;
            }
            renderSmartTextSprite(poseStack, multiBufferSource, type == null ? Component.translatable("imp.loaderType." + lt) : type.getMediaName(), tx, 23 + (k * 13) + (13f - 6.5f) / 2f + 1f, OERenderUtils.MIN_BREADTH * 5, onPxW, onPxH, monitorHeight, i);
        }

        renderSmartButtonSprite(poseStack, multiBufferSource, width - 95 - 87, 180, OERenderUtils.MIN_BREADTH * 4, 87, 15, i, j, onPxW, onPxH, monitorHeight, IMPORT_TEXT, true);
    }

    @Override
    public boolean done(ImageInfo imageInfo, String name) {
        var ms = getMusicSource();
        // Diagnostic: whether the name handed over was already a hash (interface value problem) or written wrong server side.
        LinkDebug.log("add-music done", "name", name, "loadedName", loadedName,
                "box", musicSourceNameEditBox == null ? "null" : musicSourceNameEditBox.getValue(),
                "source", ms.getLoaderType() + ":" + ms.getIdentifier(), "author", getMusicAuthor());
        if (getScreen().getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlock && musicManagerBlock.getSelectedPlayList(mc.player) != null) {
            var playList = musicManagerBlock.getSelectedPlayList(mc.player);
            NetworkManager.sendToServer(IMPPackets.MUSIC_ADD, new IMPPackets.MusicMessage(playList, name, getMusicAuthor(), imageInfo, ms, BlockEntityExistence.getByBlockEntity(getScreen().getBlockEntity())).toRFBB());
            // Nothing is pushed back after an add, so expire this playlist's song cache (see MusicSyncManager#invalidateMusics)
            MusicSyncManager.getInstance().invalidateMusics(playList);
        }
        // After adding, clear the "name loaded back": it is a client-side temporary conclusion and the music source name is cleared
        // on the server side too after adding, so keeping this field would leave the previous song's name in the box next time.
        loadedName = null;
        if (nameEditBox != null)
            nameEditBox.setValue("");
        return true;
    }

    @Override
    public boolean canDone(MusicManagerBlockEntity blockEntity) {
        return super.canDone(blockEntity) && !getMusicSource(blockEntity).isEmpty();
    }

    @Override
    public List<Component> getNotEntered(List<Component> names, MusicManagerBlockEntity blockEntity) {
        if (getMusicSource(blockEntity).isEmpty())
            names.add(MUSIC_SOURCE_TEXT);
        return super.getNotEntered(names, blockEntity);
    }

    @Override
    protected MusicManagerBlockEntity.MonitorType getParentType() {
        return MusicManagerBlockEntity.MonitorType.PLAY_LIST;
    }

    // getDoneBackMonitor() is deliberately not overridden: it defaults to PLAY_LIST

    @Override
    public void tick() {
        super.tick();
        this.musicSourceNameEditBox.visible = isMSNVisible();
        this.musicSourceNameEditBox.setWidth(isMSNShortWidth() ? 141 : 177);

        // The search and "back to search results" buttons share one position (x=331,y=111,35×14), so they are mutually exclusive.
        // Set visibility here in tick only: `super.render()` draws the widgets, so doing it in both places draws both.
        // The channel in the criterion: it must also be "this batch of results on screen was searched on this channel".
        if (searchButton != null && backToSearchButton != null) {
            boolean showable = isMSNVisible() && isMSNShortWidth();
            boolean hasResults = getScreen().hasSearchResultsFor(getMusicLoaderType());
            searchButton.visible = showable && !hasResults;
            backToSearchButton.visible = showable && hasResults;
        }

        this.uploadButton.visible = "upload".equals(getMusicLoaderType());

        if ((getScreen().isMusicPlaying() && !getMusicSource().equals(getScreen().getMusicPlayer().getSource())) || (getScreen().isMusicLoading() && !getMusicSource().equals(getScreen().getMusicPlayer().getSource())))
            getScreen().stopMusic();
    }

    private boolean isMSNVisible() {
        return !"upload".equals(getMusicLoaderType());
    }

    @Override
    protected DoneType getDoneType() {
        return DoneType.ADD;
    }

    private boolean isMSNShortWidth() {
        var rtp = getRawMusicLoaderType();
        return rtp != null && rtp.isSearchable();
    }


    private void setMusicLoaderType(String name) {
        // Note: autoRoutedPlatform must not be reset here — auto routing calls this method too, and resetting the latch there
        // would bring back the infinite loop. Reset only on a manual click (see init).
        if (name.equals(getMusicLoaderType())) {
            LinkDebug.log("setMusicLoaderType SKIP-SAME", "name", name,
                    "boxNow", musicSourceNameEditBox.getValue());
            return;
        }

        var willClear = !"auto".equals(getMusicLoaderType());
        if (willClear) {
            musicSourceNameEditBox.setValue("");
            // The channel changed, so the name loaded back last time no longer counts as "the name of this song" (see getName)
            loadedName = null;
        }
        // Changing channel voids the previous hint, and it must be cleared here: the box text is often identical across channels
        // (same link / same hash), so judging only by "the identifier is unchanged" would keep the old channel's conclusion.
        clearWarn(warnLoaderId, warnSourceId);
        loadFailure = false;
        LinkDebug.log("setMusicLoaderType", "name", name, "clearedBox", willClear,
                "boxNow", musicSourceNameEditBox.getValue(), "beTypeBefore", getMusicLoaderType());
        getScreen().insMusicLoaderType(name);
    }

    /** Clear the probe conclusion (written by {@link #probePlayable}, read while rendering). */
    private void clearWarn() {
        warnSourceId = null;
        warnLoaderId = null;
        warnText = null;
    }

    /** Clear the hint on a channel switch: only when "this conclusion was made on the channel being switched away from". */
    private void clearWarn(@Nullable String loaderId, @Nullable String sourceId) {
        if (warnText == null || warnLoaderId == null)
            return;
        if (warnLoaderId.equals(loaderId) && (warnSourceId == null || warnSourceId.equals(sourceId)))
            clearWarn();
    }

    private void setMusicSourceName(String name) {
        // The input was cleared = back to the clean state of "no link selected": preview, song name, hint and failure flag are all cleared together.
        // Switching music source or editing library contents does **not** clear.
        if (name == null || name.isEmpty()) {
            setImage(ImageInfo.EMPTY);
            loadedName = null;
            clearWarn();
            loadFailure = false;
            lastLoggedBox = null;
            // The preview bar reads the backend music source (duration) and the author, so both must be cleared too, otherwise the previous entry's info stays
            setMusicSource(dev.felnull.imp.music.resource.MusicSource.EMPTY, "");
            if (nameEditBox != null)
                nameEditBox.setValue("");
        }
        // Diagnostic: who changes the box to what value.
        LinkDebug.log("setMusicSourceName", "newValue", name,
                "oldValue", getMusicSourceName(),
                "beMusicSource", getMusicSource().getLoaderType() + ":" + getMusicSource().getIdentifier(),
                "quiet", quietBoxWrite,
                "boxBefore", musicSourceNameEditBox.getValue());
        // Programmatic writes (route / link-conversion backfill) must not start a load: it would land on the old type that has not
        // synced yet. The writer starts the load it wants itself.
        if (!quietBoxWrite && !getMusicSourceName().equals(name))
            startMusicLoad(name, true);
        // A write by hand ⇒ the name loaded back last time is void (otherwise getName() would keep using it).
        // A programmatic write (quietBoxWrite) does not void it.
        if (!quietBoxWrite)
            loadedName = null;
        getScreen().insMusicSourceName(name);
    }


    private void setLoadResult(MusicMediaResult result, boolean autoIn) {
        if (result != null) {
            setMusicSource(result.source(), result.author());
            // Bilibili AV input: the box keeps "av<digits>" (link parsing is offline by design) while the load result
            // already holds the real BV number. Backfill that BV number, but only when the box still holds exactly the
            // identifier this load was started with -- a hand edit meanwhile made the result stale and must never be overwritten.
            backfillResolvedIdentifier(result);
            // Clear the failure flag on success: otherwise an earlier failed load thread finishing a step later would pin the interface on "cannot load".
            loadFailure = false;
            if (autoIn && result.imageInfo() != null)
                setImage(result.imageInfo());
            // The name is filled in **no matter how it was loaded**: the box holds the "music name" and must show the song name,
            // not the identifier (the identifier really loaded lives in the music source, see getName).
            if (result.name() != null && !result.name().isEmpty()) {
                loadedName = result.name();
                // The left "music name" box was only set at init, so it must be refreshed together once a new name is recognized
                if (nameEditBox != null)
                    nameEditBox.setValue(loadedName);
                EditBoxUtil.setValueShowHead(this.nameEditBox, result.name());
            }
        } else {
            // On load failure / empty input do not clear an existing valid source, otherwise the play button could never be clicked again.
            boolean hasValidSource = !getMusicSource().isEmpty();
            if (!hasValidSource)
                setMusicSource(MusicSource.EMPTY, "");
            LinkDebug.log("setLoadResult SKIP-EMPTY", "keepExistingSource", hasValidSource,
                    "existing", getMusicSource().getLoaderType() + ":" + getMusicSource().getIdentifier());
        }
        LinkDebug.log("setLoadResult", "autoIn", autoIn,
                "source", result == null ? "EMPTY" : (result.source().getLoaderType() + ":"
                        + result.source().getIdentifier() + " dur=" + result.source().getDuration()),
                "author", result == null ? "" : result.author());
    }

    /** Silent backfill of the resolved identifier (AV → BV), written via {@link #setBoxValueForNewIdentifier}
     * ({@code quietBoxWrite}) so it starts no load. */
    private void backfillResolvedIdentifier(MusicMediaResult result) {
        var loadInput = this.loadInput;
        // Consume it: this backfill belongs to the load that is finishing right now, never to a later one
        this.loadInput = null;
        if (loadInput == null || result.source() == null)
            return;
        var resultId = result.source().getIdentifier();
        if (resultId == null || resultId.equals(loadInput))
            return;
        // AV → BV only
        if (!AV_ID_ONLY.matcher(loadInput).matches() || !BV_ID_ONLY.matcher(resultId).matches())
            return;
        // Widget write: back to the render thread. No `musicLoadThread == self` check here — the load thread clears that
        // field when it finishes and this runnable runs after that. What guards the user's input is the value check below.
        IIMPSmartRender.mc.submit(() -> {
            if (musicSourceNameEditBox == null)
                return;
            if (!loadInput.equals(musicSourceNameEditBox.getValue()))
                return;
            LinkDebug.log("identifier-backfill", "box", loadInput, "resultIdentifier", resultId);
            setBoxValueForNewIdentifier(resultId);
        });
    }

    /** Writing an identifier into the box programmatically also voids the "loaded back name" (see {@link #getName}). */
    private void setBoxValueForNewIdentifier(String value) {
        loadedName = null;
        // The cover is not cleared here: the new link's cover comes from the load result.
        setBoxValue(musicSourceNameEditBox, value);
    }

    /**
     * What this box displays is the **music source name** (the pasted identifier), not the music name.
     */
    @Override
    protected String getName(MusicManagerBlockEntity musicManagerBlockEntity) {
        var sourceName = getMusicSourceName(musicManagerBlockEntity);
        var ui = musicSourceNameEditBox == null ? null : musicSourceNameEditBox.getValue();
        // Diagnostic: print the server-side value and the UI value side by side.
        if (ui != null && !ui.equals(lastLoggedBox)) {
            lastLoggedBox = ui;
            LinkDebug.log("render-source-name", "beValue", sourceName, "uiValue", ui);
        }
        // The widget value wins: the server-side name syncs back late, so before it arrives `getMusicSourceName()` still
        // reads the previous song's value and the box (and the name stored by "add") would show stale content.
        var shown = (ui != null && !ui.isEmpty())
                ? ui
                : (sourceName == null ? "" : sourceName);

        // The string shown in this box is **also** used as "what this song is called" (the "add" button of {@code ImageNameBaseMMMonitor}
        // calls `done(getImage(), getName())`, and that is what ends up stored in the playlist).
        // So "what is displayed" and "what is stored" must be the same value.
        if (loadedName != null && !loadedName.isEmpty())
            return loadedName;
        return shown;
    }

    /** The **name** loaded back successfully last time, so "shown in the box" can be taken as "the name to save"
     * (see {@link #getName}). */
    private String loadedName;

    /** The last recorded UI value (suppresses duplicate log lines). */
    private String lastLoggedBox;
    /** Hinted track's identifier (restricted / missing Cookie), null when none; an id rather than a boolean, because
     * rendering tests "is the box holding exactly this track right now" — a local check, independent of server sync. */
    private String warnSourceId;
    /**
     * Which **channel** this hint was probed on (loader id, e.g. {@code kugou} / {@code netease}).
     */
    private String warnLoaderId;
    /** The hint text paired with {@link #warnSourceId} (different platform and different cause mean different text). */
    private Component warnText;
    /**
     * True while the box is written programmatically: makes the responder {@link #setMusicSourceName} **not** trigger a load.
     */
    private boolean quietBoxWrite;
    /** Load sequence number, bumped on every load started; the backfill one frame later uses it to tell whether
     * a new load began in the meantime — if so the backfill is abandoned, so a stale result cannot overwrite it. */
    private int loadSeq;
    /** The identifier the running / last finished load was actually started with (already normalized); written on the
     * render thread when the load starts, consumed by {@link #backfillResolvedIdentifier}. */
    private volatile String loadInput;

    /**
     * After a successful load, run one lightweight probe to tell "whether this track can really fetch audio right now"; if not, explain the reason in the interface.
     */
    private void probePlayable(String sourceLoaderType, String sourceIdentifier) {
        if (sourceIdentifier == null)
            return;

        // Numeric parsing only inside each branch: the identifier formats differ per platform, and Kugou's is a 32-hex hash
        // that would throw in a `Long.parseLong` done up front.
        if ("netease".equals(sourceLoaderType)) {
            long id;
            try {
                id = Long.parseLong(sourceIdentifier.trim());
            } catch (Exception e) {
                return;
            }
            var unavailable = NetMusicUtil.isAudioUnavailable(id);
            LinkDebug.log("netease-playable-probe", "id", id, "unavailable", unavailable,
                    "hasCookie", NetMusicUtil.hasCookie());
            if (unavailable) {
                warnSourceId = sourceIdentifier;
                warnLoaderId = "netease";
                // No Cookie configured → hint to log in; configured but still unavailable → login does not help either (copyright/region)
                warnText = NetMusicUtil.hasCookie() ? NETMUSIC_RESTRICTED_LOGGED_IN_TEXT : NETMUSIC_RESTRICTED_TEXT;
            }
        } else if ("qq_music".equals(sourceLoaderType)) {
            long id;
            try {
                id = Long.parseLong(sourceIdentifier.trim());
            } catch (Exception e) {
                return;
            }
            // QQ is not "Cookie required": anonymously some songs still return a real purl, so probe first and only then pick the
            // text by Cookie presence — "no Cookie = dead" would put a fake hint on playable songs.
            var status = QQMusicUtil.probeAudioStatus(id);
            LinkDebug.log("QQ-playable-probe", "id", id, "status", status,
                    "hasCookie", QQMusicUtil.hasCookie());
            if (status == QQMusicUtil.AudioStatus.UNAVAILABLE) {
                warnSourceId = sourceIdentifier;
                warnLoaderId = "qq_music";
                warnText = QQMusicUtil.hasCookie() ? QQ_UNAVAILABLE_TEXT : QQ_COOKIE_MAYBE_NEEDED_TEXT;
            }
        } else if ("kugou".equals(sourceLoaderType)) {
            // Shape first: a Kugou single track is a 32-hex hash while a playlist id is plain numeric, and a playlist id here
            // would get a misleading "needs login or is restricted".
            if (!KuGouUtil.isHash(sourceIdentifier)) {
                LinkDebug.log("KuGou-playable-probe SKIP", "identifier", sourceIdentifier, "reason", "not a 32-hex hash");
                warnSourceId = sourceIdentifier;
                warnLoaderId = "kugou";
                warnText = KUGOU_NOT_A_HASH_TEXT;
                return;
            }
            // Kugou: the public API used for single tracks **plays free tracks anonymously**; what cannot be fetched is paid/VIP tracks,
            // which need a **logged-in session** (device registration + signed URL fetch + QR login) before an address can be obtained.
            var unavailable = KuGouUtil.isAudioUnavailable(sourceIdentifier);
            LinkDebug.log("KuGou-playable-probe", "hash", sourceIdentifier, "unavailable", unavailable);
            if (unavailable) {
                warnSourceId = sourceIdentifier;
                warnLoaderId = "kugou";
            // Split by login state (as for NetEase / QQ above): with a token configured the login is not the problem.
                warnText = KuGouUtil.hasLogin() ? KUGOU_UNAVAILABLE_LOGGED_IN_TEXT : KUGOU_UNAVAILABLE_TEXT;
            }
        }
    }

    /** Load with the loader type currently selected in the interface (input / manual conversion backfill go through this). */
    private void startMusicLoad(String name, boolean autoIn) {
        startMusicLoad(getMusicLoaderType(), name, autoIn, false);
    }

    /**
     * Load with the **specified** loader, ignoring the type recorded on the server.
     */
    private void startMusicLoad(String loaderType, String name, boolean autoIn, boolean explicit) {
        stopMusicLoad();
        // Clear the previous track's hint conclusion on every reload, so one track's conclusion is not applied to another
        clearWarn();
        // A new load voids the name loaded back last time, otherwise it would be kept and shown for the next song.
        loadedName = null;
        if (nameEditBox != null)
            nameEditBox.setValue("");
        this.loadSeq++;
        if (IMPMusicMedias.getMedia(loaderType) != null || "auto".equals(loaderType) || "upload".equals(loaderType)) {
            this.loadFailure = false;
            this.musicLoadThread = new MusicLoadThread(loaderType, name, autoIn);
            this.musicLoadThread.start();
            LinkDebug.log("startMusicLoad", "loaderType", loaderType, "name", name, "autoIn", autoIn,
                    "explicit", explicit, "rawLoaderKnown", IMPMusicMedias.getMedia(loaderType) != null,
                    "box", IIMPSmartRender.mc.isSameThread() ? musicSourceNameEditBox.getValue() : "(off-thread)");
        } else {
            LinkDebug.log("startMusicLoad SKIPPED", "loaderType", loaderType, "name", name);
        }
    }

    private void stopMusicLoad() {
        if (this.musicLoadThread != null) {
            this.musicLoadThread.stopped();
            this.musicLoadThread = null;
        }
    }

    /**
     * Rewrite the box content programmatically and fix the EditBox display window (otherwise only the last few characters of the new content are rendered).
     */
    private void setBoxValue(EditBox box, String value) {
        if (value.equals(box.getValue()))
            return;
        quietBoxWrite = true;
        try {
            // Details of "reset the view + move the cursor to the end" are in EditBoxUtil (the order must not be reversed)
            EditBoxUtil.setValueCursorEnd(box, value);
        } finally {
            quietBoxWrite = false;
        }
    }

    /** Converts the selected loader + input into the identifier that loader really consumes; only a share link of the
     * currently selected platform is converted, everything else is returned as is (behavior unchanged). */
    private static String normalizeSourceName(String loaderType, String name) {
        if (name == null || name.isEmpty())
            return name;

        // Resolve with the "current channel": a bare number (BV/AV without a domain) is only recognized when the current channel is that platform itself,
        // and is always skipped in auto mode (otherwise an `av…` inside someone else's link would be grabbed as this channel's id and the load would certainly fail).
        var link = MusicLinks.resolveFor(name, loaderType);
        // Playlist-like links belong to the import channel: converting one to its bare id here would load whatever single track happens to carry that id.
        if (link != null && link.platform().equals(loaderType) && !link.isPlaylistLike())
            return link.identifier();

        return name;
    }

    private class MusicLoadThread extends FlagThread {
        private final String name;
        private final String loaderType;
        private boolean autoIn;

        private MusicLoadThread(String loaderType, String name, boolean autoIn) {
            setName("Music Load Thread");
            this.loaderType = loaderType;
            this.name = name;
            this.autoIn = autoIn;
        }

        @Override
        public void run() {
            try {
                if (isStopped()) return;
                var loader = IMPMusicMedias.getMedia(loaderType);
                if ("upload".equals(loaderType))
                    loader = IMPMusicMedias.HTTP;
                if (isStopped()) return;

                if (loader != null) {
                    if (isStopped()) return;
                    // When a loader is selected manually a share link may still be pasted directly: if the platform matches, convert the link into the identifier that platform wants
                    var normalized = normalizeSourceName(loaderType, name);
                    if (!normalized.equals(name) && !"upload".equals(loaderType)) {
                        // Write the conversion result back (paste a link → only the identifier stays in the box). Goes through
                        // setBoxValue, which also pulls the display window back to the start.
                        IIMPSmartRender.mc.submit(() -> {
                            if (musicLoadThread == this)
                                setBoxValueForNewIdentifier(normalized);
                        });
                    }
                    LinkDebug.log("direct-load begin", "loaderType", loaderType, "input", name, "normalized", normalized);
                    // Remember what this load was really started with, so the finished result can backfill the box later (AV → BV)
                    loadInput = normalized;
                    var r = loader.load(normalized);
                    LinkDebug.log("direct-load result", "loaderType", loaderType, "normalized", normalized,
                            "result", r == null ? "null" : (r.source().getLoaderType() + ":" + r.source().getIdentifier()
                                    + " dur=" + r.source().getDuration() + " name=" + r.name()));
                    // If this load was already superseded (another link pasted / platform switched), leave the interface state alone:
                    // a slow old thread would overwrite the new result or pin the interface on "cannot load".
                    if (isStopped() || musicLoadThread != this) return;
                    setLoadResult(r, autoIn);
                    // After a successful load, probe once more "whether the audio can really be fetched"; on failure skip the probe.
                    // The platform and identifier **come from the result**, not from the loaderType this thread requested.
                    if (r != null) {
                        probePlayable(r.source().getLoaderType(), r.source().getIdentifier());
                        if (isStopped() || musicLoadThread != this) return;
                    }
                    if (r == null) {
                        // The load itself failed, so "can it play" cannot be probed, but one kind of input deserves its reason spelled out:
                        // a Kugou box holding something that is not a hash (e.g. a playlist id) → point to "import music".
                        if ("kugou".equals(loaderType) && !KuGouUtil.isHash(normalized)) {
                            warnSourceId = normalized;
                            warnLoaderId = "kugou";
                            warnText = KUGOU_NOT_A_HASH_TEXT;
                        }
                        loadFailure = true;
                    }
                    if (isStopped()) return;
                } else if ("auto".equals(loaderType)) {
                    if (isStopped()) return;

                    // Share links are recognized once by each platform's link shape; a recognized platform goes straight to the matching loader,
                    // not down LavaPlayer's generic load path.
                    // Resolved with the channel: here loaderType=="auto" ⇒ **bare numbers do not take part**; domain forms are recognized as usual.
                    var link = MusicLinks.resolveFor(name, loaderType);
                    LinkDebug.log("auto-route probe", "input", name,
                            "resolved", link == null ? "null" : link.toString());
                    // Both Bilibili videos (VIDEO) and single tracks (SONG) go through the "single track" channel.
                    // A source switched off in the config is not in the list, so routing to it would only switch the UI to a type the user cannot see.
                    if (link != null && (link.kind() == MusicLink.Kind.SONG || link.kind() == MusicLink.Kind.VIDEO)
                            && IMPMusicMedias.showInLoaderList(link.platform())) {
                        var linkLoader = IMPMusicMedias.getMedia(link.platform());
                        if (linkLoader != null) {
                            var identifier = link.identifier();
                            // Client-side latch: route for a platform only once. Reading the loader type back from the server-side BE
                            // does not work — it syncs late, and during the gap the result is the infinite loop
                            // setLoaderType → clear box → setValue → responder → load → route again.
                            var mySeq = loadSeq;
                            IIMPSmartRender.mc.submit(() -> {
                                // If a new load was started in this frame, abandon the backfill of this stale route
                                if (mySeq != loadSeq) {
                                    LinkDebug.log("auto-route STALE", "platform", link.platform(),
                                            "identifier", identifier, "mySeq", mySeq, "nowSeq", loadSeq);
                                    return;
                                }
                                var platform = link.platform();
                                boolean switchType = !platform.equals(autoRoutedPlatform);

                                if (switchType) {
                                    autoRoutedPlatform = platform;
                                    // Send only the "switch type" instruction, not setMusicLoaderType: that one empties the box
                                    // internally (clear → empty load → wipes out the valid source). setBoxValue writes it below.
                                    getScreen().insMusicLoaderType(platform);
                                }

                                // Write the clean identifier back (silent write: does not fire a responder load, see quietBoxWrite)
                                boolean setBox = !identifier.equals(musicSourceNameEditBox.getValue());
                                if (setBox)
                                    setBoxValueForNewIdentifier(identifier);

                                // Load explicitly with the resolved platform instead of waiting for the server to sync the type back:
                                // the responder route cannot work here, since getMusicLoaderType() is still auto and that load would fail.
                                startMusicLoad(platform, identifier, true, true);

                                LinkDebug.log("auto-route apply", "platform", platform,
                                        "identifier", identifier, "switchType", switchType, "setBox", setBox,
                                        "explicitLoad", true,
                                        "boxNow", musicSourceNameEditBox.getValue(),
                                        "beType", getMusicLoaderType());
                            });
                            // This (auto) thread's job ends here: the real load has already been handed to the explicit load above.
                            // Clear only while "the current thread is still this load thread".
                            if (musicLoadThread == this)
                                musicLoadThread = null;
                            return;
                        }
                    }

                    var ar = LavaPlayerManager.getInstance().autoLoad(name);
                    LinkDebug.log("auto-generic result", "input", name,
                            "result", ar == null ? "null" : (ar.getKey().getName() + ":"
                                    + ar.getValue().source().getIdentifier()));
                    if (isStopped() || musicLoadThread != this) return;
                    if (ar != null) {
                        var arName = ar.getKey().getName();
                        var arIdentifier = ar.getValue().source().getIdentifier();
                        // The whole block goes back to the render thread: calling setMusicLoaderType / setValue directly in the load
                        // thread would be a cross-thread write to MC widgets.
                        IIMPSmartRender.mc.submit(() -> {
                            if (musicLoadThread != this)
                                return;
                            if (!arName.equals(getMusicLoaderType()))
                                setMusicLoaderType(arName);
                            setBoxValueForNewIdentifier(arIdentifier);
                        });
                        if (isStopped()) return;
                    } else {
                        loadFailure = true;
                    }
                    if (isStopped()) return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (musicLoadThread == this)
                musicLoadThread = null;
        }
    }
}
