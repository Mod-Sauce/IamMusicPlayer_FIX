package dev.felnull.imp.client.gui.screen;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.client.gui.components.PowerButton;
import dev.felnull.imp.client.gui.screen.monitor.music_manager.MusicManagerMonitor;
import dev.felnull.imp.client.music.IMPMusicTrackerFactory;
import dev.felnull.imp.client.music.MusicEngine;
import dev.felnull.imp.client.music.MusicEntry;
import dev.felnull.imp.client.music.media.MusicMediaResult;
import dev.felnull.imp.inventory.MusicManagerMenu;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.music.tracker.IMPMusicTrackers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MusicManagerScreen extends IMPBaseContainerScreen<MusicManagerMenu> {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/music_manager/music_manager_base.png");
    /** [Diagnostic] Last logged "insSelectedPlayList" line: the log is written only when this line changes (one fixed key). */
    private static final Map<String, String> SEL_PLAYLIST_LAST_LOG = new HashMap<>();
    /** Same-key cap: a long session would otherwise pile up keys; 64 is far above the real key count. */
    private static final int SEL_PLAYLIST_MAX_KEYS = 64;
    private final Map<MusicManagerBlockEntity.MonitorType, MusicManagerMonitor> monitors = new HashMap<>();
    private final UUID musicPlayerId = UUID.randomUUID();
    public boolean lastSearch;
    protected MusicManagerMonitor monitor;
    public byte[] musicFileImage;

    public MusicManagerScreen(MusicManagerMenu abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
        this.imageWidth = 386;
        this.imageHeight = 227;
        this.bgTextureWidth = 512;
        this.bgTextureHeight = 512;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new PowerButton(this, leftPos + 368, topPos + 4, 12, 12, 386, 0, BG_TEXTURE, bgTextureWidth, bgTextureHeight));
        changeScreenMonitor(getBEMonitorType());
        insMonitor(getBEMonitorType());
    }

    public void insMonitor(MusicManagerBlockEntity.MonitorType type) {
        // A description-link visit only survives on the pages that can show that video
        if (type != MusicManagerBlockEntity.MonitorType.DETAIL_MUSIC
                && type != MusicManagerBlockEntity.MonitorType.BILIBILI_PARTS)
            guestMusic = null;
        if (monitor != null && monitor.getType() != type)
            stopMusic();

        // The back target of the import-related screens: IMP navigation relies on the server-side BE type sync, so a screen's own
        // hard-coded parent type is not the real origin (the import pages are pulled in by the clipboard auto-jump from any screen).
        // The real origin is recorded here for them to use: {@link #getLastMonitorType()}.
        if (monitor != null && monitor.getType() != type)
            lastMonitorType = monitor.getType();

        var tag = new CompoundTag();
        tag.putString("type", type.getName());
        instruction("set_monitor", tag);
    }

    /** Previous screen (only **client-initiated** jumps record it); null = no client jump yet in this session. */
    @Nullable
    public MusicManagerBlockEntity.MonitorType getLastMonitorType() {
        return lastMonitorType;
    }

    @Nullable
    private MusicManagerBlockEntity.MonitorType lastMonitorType;

    /**
     * The latest batch of "search music" results.
     */
    private final List<MusicMediaResult> searchResults = new ArrayList<>();

    /**
     * The **channel** that produced this batch (`MusicMedia` name, such as `kugou` / `netease`).
     */
    private volatile String searchResultsLoaderType = "auto";

    /** A **snapshot** of the search results (written by the worker thread, read by the render thread, hence the locked copy). */
    public List<MusicMediaResult> getSearchResults() {
        synchronized (searchResults) {
            return List.copyOf(searchResults);
        }
    }

    /** Whether any search results from **this channel** are left: empty **or** a different channel counts as "no". */
    public boolean hasSearchResultsFor(@Nullable String loaderType) {
        synchronized (searchResults) {
            return !searchResults.isEmpty() && loaderType != null
                    && loaderType.equals(searchResultsLoaderType);
        }
    }

    /** Replace with a new batch of results (called by the search thread). `loaderType` = which channel produced them. */
    public void setSearchResults(@Nullable List<MusicMediaResult> results, String loaderType) {
        synchronized (searchResults) {
            searchResults.clear();
            if (results != null)
                searchResults.addAll(results);
            if (loaderType != null)
                searchResultsLoaderType = loaderType;
        }
    }

    public void clearSearchResults() {
        synchronized (searchResults) {
            searchResults.clear();
        }
    }

    public void insAddPlayList(UUID playListId) {
        var tag = new CompoundTag();
        tag.putUUID("playlist", playListId);
        instruction("add_playlist", tag);
    }

    private void changeScreenMonitor(MusicManagerBlockEntity.MonitorType type) {
        if (monitor != null) {
            if (monitor.getType() != MusicManagerBlockEntity.MonitorType.UPLOAD_MUSIC || type != MusicManagerBlockEntity.MonitorType.ADD_MUSIC)
                musicFileImage = null;
            monitor.renderables.forEach(this::removeWidget);
            monitor.depose();
        }

        if (!monitors.containsKey(type))
            monitors.put(type, MusicManagerMonitor.createdMusicMonitor(type, this));

        monitor = monitors.get(type);
        monitor.init(leftPos, topPos);
        monitor.renderables.forEach(this::addRenderableWidget);
    }

    private MusicManagerBlockEntity.MonitorType getBEMonitorType() {
        if (getBlockEntity() instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return musicManagerBlockEntity.getMonitor(mc.player);
        return MusicManagerBlockEntity.MonitorType.OFF;
    }

    public void insImportPlayListMusicCount(int count) {
        var tag = new CompoundTag();
        tag.putInt("count", count);
        instruction("set_import_playlist_music_count", tag);
    }

    public void insImportPlayListAuthor(String author) {
        var tag = new CompoundTag();
        tag.putString("author", author);
        instruction("set_import_playlist_author", tag);
    }

    public void insImportPlayListName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_import_playlist_name", tag);
    }

    public void insImportIdentifier(String identifier) {
        var tag = new CompoundTag();
        tag.putString("id", identifier);
        instruction("set_import_identifier", tag);
    }

    public void insImageURL(String url) {
        var tag = new CompoundTag();
        tag.putString("url", url);
        instruction("set_image_url", tag);
    }

    public void insImage(ImageInfo image) {
        var tag = new CompoundTag();
        tag.put("image", image.createSavedTag());
        instruction("set_image", tag);
    }

    public void insCreateName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_create_name", tag);
    }

    public void insPublishing(String publishing) {
        var tag = new CompoundTag();
        tag.putString("publishing", publishing);
        instruction("set_publishing", tag);
    }

    public void insInitialAuthority(String initialAuthority) {
        var tag = new CompoundTag();
        tag.putString("initial_authority", initialAuthority);
        instruction("set_initial_authority", tag);
    }

    public void insInvitePlayerName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_invite_player_name", tag);
    }

    public void insInvitePlayers(List<UUID> players) {
        var tag = new CompoundTag();
        OENbtUtils.writeUUIDList(tag, "players", players);
        instruction("set_invite_players", tag);
    }

    public void insSelectedPlayList(UUID selectedPlayList) {
        // [Diagnostic] Log every **client-initiated** selection change (to tell "the client cleared it" from "the
        // server lost it"), and only when the value changes, so a repeat call does not log the same thing again.
        if (dev.felnull.imp.client.music.link.LinkDebug.isEnabled()) {
            var selNow = selectedPlayList == null ? "null" : selectedPlayList.toString();
            if (!selNow.equals(SEL_PLAYLIST_LAST_LOG.get("insSelectedPlayList"))) {
                if (SEL_PLAYLIST_LAST_LOG.size() > SEL_PLAYLIST_MAX_KEYS)
                    SEL_PLAYLIST_LAST_LOG.clear();
                SEL_PLAYLIST_LAST_LOG.put("insSelectedPlayList", selNow);
                dev.felnull.imp.client.music.link.LinkDebug.log("insSelectedPlayList",
                        "to", selNow,
                        "from", callerFrame());
            }
        }
        var tag = new CompoundTag();
        if (selectedPlayList != null)
            tag.putUUID("playlist", selectedPlayList);
        instruction("set_selected_playlist", tag);
    }

    /** For diagnostics: the first non-JDK frame in the current thread (who changed the selection). */
    private static String callerFrame() {
        try {
            for (var e : Thread.currentThread().getStackTrace()) {
                var cn = e.getClassName();
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                        || cn.equals(MusicManagerScreen.class.getName()))
                    continue;
                return cn + "#" + e.getMethodName() + ":" + e.getLineNumber();
            }
        } catch (Throwable ignored) {
        }
        return "(unknown)";
    }

    public void insSelectedMusic(@Nullable UUID selectedMusic) {
        // Picking another song ends a description-link visit
        guestMusic = null;
        var tag = new CompoundTag();
        if (selectedMusic != null)
            tag.putUUID("music", selectedMusic);
        instruction("set_selected_music", tag);
    }

    /** A video opened from a description link: the detail / video-info pages show it until the user picks another song. */
    @Nullable
    private Music guestMusic;

    @Nullable
    public Music getGuestMusic() {
        return guestMusic;
    }

    /** Opens a description link inside the mod (never added to the playlist). */
    public void openGuestMusic(Music music) {
        this.guestMusic = music;
        insMonitor(MusicManagerBlockEntity.MonitorType.DETAIL_MUSIC);
    }

    public void insSelectedPlayer(@Nullable UUID selectedPlayer) {
        var tag = new CompoundTag();
        if (selectedPlayer != null)
            tag.putUUID("player", selectedPlayer);
        instruction("set_selected_player", tag);
    }

    public void insMusicLoaderType(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_music_loader_type", tag);
    }

    public void insMusicSourceName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_music_source_name", tag);
    }

    public void insMusicSource(MusicSource source) {
        if (source == null)
            source = MusicSource.EMPTY;
        var tag = new CompoundTag();
        tag.put("MusicSource", source.createSavedTag());
        instruction("set_music_source", tag);
    }

    public void insMusicSearchName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_music_search_name", tag);
    }

    public void insMusicAuthor(String author) {
        var tag = new CompoundTag();
        tag.putString("author", author);
        instruction("set_music_author", tag);
    }

    public void insImportPlayListType(String type){
        var tag = new CompoundTag();
        tag.putString("type", type);
        instruction("set_import_playlist_type", tag);
    }

    @Override
    public void onInstructionReturn(String name, CompoundTag data) {
        super.onInstructionReturn(name, data);
        if ("add_playlist".equals(name)) {
            if (data.contains("playlist")) {
                insSelectedPlayList(data.getUUID("playlist"));
            }
        } else if ("set_selected_playlist".equals(name)) {
            if (data.contains("playlist")) {
                monitor.onUpdateSelectedPlayList(data.getUUID("playlist"));
            } else {
                monitor.onUpdateSelectedPlayList(null);
            }
        }
    }

    @Override
    protected ResourceLocation getBackGrandTexture() {
        return BG_TEXTURE;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
      guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752,false);
        //this.font.draw(poseStack, this.title, (float) this.titleLabelX, (float) this.titleLabelY, 4210752);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
        super.renderBg(guiGraphics, f, i, j);
        if (monitor != null)
            monitor.render(guiGraphics, f, i, j);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (monitor != null) {
            if (getBEMonitorType() != monitor.getType())
                changeScreenMonitor(getBEMonitorType());
            monitor.tick();
        }
    }

    /**
     * Key events are handed to the current monitor first ({@code MusicManagerMonitor}), then to the default handling.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (monitor != null && monitor.keyPressed(keyCode, scanCode, modifiers))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
        if (monitor != null)
            monitor.depose();

        stopMusic();
    }

    @Override
    public void onFilesDrop(List<Path> list) {
        if (monitor != null)
            monitor.onFilesDrop(list);
    }

    public void playMusic(MusicSource source, long postion) {
        stopMusic();
        getMusicEngine().loadAndPlay(musicPlayerId, source, postion, false);
        getMusicEngine().addSpeaker(musicPlayerId, musicPlayerId, IMPMusicTrackerFactory.linked(IMPMusicTrackers.createPlayerTracker(mc.player, 1, 10)));

        //getMusicEngine().loadAddMusicPlayer(musicPlayerId, new MusicPlaybackInfo(MusicRingManager.PLAYER_TRACKER, MusicRingManager.createPlayerTracker(mc.player), 1, 10), source, postion, (result, time, player, retry) -> getMusicEngine().playMusicPlayer(musicPlayerId, 0));
    }

    public void stopMusic() {
        getMusicEngine().stop(musicPlayerId);
    }

    public boolean isMusicPlaying() {
        return getMusicEngine().isPlaying(musicPlayerId);
    }

    public boolean isMusicLoading() {
        return getMusicEngine().isLoading(musicPlayerId);
    }

    private MusicEngine getMusicEngine() {
        return MusicEngine.getInstance();
    }

    public MusicEntry getMusicPlayer() {
        return getMusicEngine().getMusicEntry(musicPlayerId);
    }
}
