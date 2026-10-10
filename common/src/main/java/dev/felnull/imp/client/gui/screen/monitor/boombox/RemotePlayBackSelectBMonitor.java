package dev.felnull.imp.client.gui.screen.monitor.boombox;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.IMPConfig;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.client.bilibili.BiliBiliArcTags;
import dev.felnull.imp.client.bilibili.BiliBiliUtil;
import dev.felnull.imp.client.gui.components.BoomboxIconButton;
import dev.felnull.imp.client.gui.components.RemoteMusicsFixedListWidget;
import dev.felnull.imp.client.gui.components.RemotePlayListFixedListWidget;
import dev.felnull.imp.client.gui.components.SortButton;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.client.music.link.LinkDebug;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.networking.IMPPackets;
import me.shedaniel.autoconfig.AutoConfig;
import org.modsauce.otyacraftenginerenewed.client.util.OEClientUtils;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RemotePlayBackSelectBMonitor extends BoomboxMonitor {
    protected static final ResourceLocation REMOTE_PLAYBACK_SELECT_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox/monitor/remote_playback_select.png");
    /** How many rows of the song list fit on screen (= one of the two 5s passed to the widget below; the "scroll to entry N" formula needs it). */
    private static final int LIST_ROWS = 5;
    private final List<MusicPlayList> musicPlayLists = new ArrayList<>();
    private final List<Music> musics = new ArrayList<>();
    private List<MusicPlayList> musicPlayListsCash;
    private List<Music> musicsCash;
    private RemoteMusicsFixedListWidget remoteMusicsFixedButtonsList;
    private RemotePlayListFixedListWidget remotePlayListFixedButtonsList;
    /** 「用监视器的排序」：紧挨音响耳机键（屏幕局部 190,77）左侧，即屏幕局部 (171,77) = 面板局部 (164,41)。 */
    private static final int SYNC_SORT_X = 164;
    private static final int SYNC_SORT_Y = 41;
    private static final Component SYNC_SORT_TEXT = Component.translatable("imp.button.useMonitorSort");
    private BoomboxIconButton syncSortButton;
    /** 本次会话已经报过投稿时间的歌单（同一个歌单不重复报）。 */
    private final Set<UUID> pubdatePushed = new HashSet<>();

    public RemotePlayBackSelectBMonitor(BoomboxData.MonitorType monitorType, BoomboxScreen screen) {
        super(monitorType, screen);
    }

    @Override
    public void init(int leftPos, int topPos) {
        super.init(leftPos, topPos);
        this.remotePlayListFixedButtonsList = this.addRenderWidget(new RemotePlayListFixedListWidget(getStartX() + 1, getStartY() + 1, musicPlayLists, (widget, item) -> setSelectPlaylist(item.getUuid()), this.remotePlayListFixedButtonsList, musicPlayList -> musicPlayList.getUuid().equals(getSelectPlaylist())));

        this.remoteMusicsFixedButtonsList = this.addRenderWidget(new RemoteMusicsFixedListWidget(getStartX() + 70, getStartY() + 1, musics, (widget, item) -> {
            setMusic(item.getUuid());
            setMonitor(BoomboxData.MonitorType.REMOTE_PLAYBACK);
        }, remoteMusicsFixedButtonsList));

        this.syncSortButton = addRenderWidget(new BoomboxIconButton(getStartX() + SYNC_SORT_X, getStartY() + SYNC_SORT_Y,
                SYNC_SORT_TEXT, n -> useMonitorSort(), BoomboxScreen.BG_TEXTURE, 99, 188, 7, 7));
    }

    /** 页面切换时这个监视器实例是复用的：置空，免得 tick() 写到已废弃的控件。 */
    @Override
    public void depose() {
        super.depose();
        syncSortButton = null;
    }

    /** 把播放列表页的歌曲排序交给**这台**音响当顺序播放口径（单向、显式）；「投稿日期」交不过去，它靠客户端本地缓存。 */
    private void useMonitorSort() {
        var sort = monitorSortOf();
        var data = getScreen().getBoomBoxData();
        var ui = IamMusicPlayer.getConfig().uiConfig;
        var cfg = IamMusicPlayer.getConfig().boomboxConfig;
        // 「投稿日期」服务端没有这个字段，先把时间报给它，再推排序
        if (sort == IMPConfig.BoomboxSongSort.PUBLISH_DATE) {
            var playlist = getSelectPlaylist();
            if (playlist != null) {
                pubdatePushed.add(playlist);
                pushPubdates(playlist);
            }
        }
        cfg.remoteSongSort = sort;
        cfg.remoteSongSortDescending = monitorSortDescending();
        AutoConfig.getConfigHolder(IMPConfig.class).save();
        // The page pushes this config to the block every tick; doing it here means the click takes effect at once.
        syncRemoteSongSortToServer();
        // …and the list here only re-sorts when the playlist's songs change, so do it now
        updateMusics();
        LinkDebug.log("boombox sync sort", "monitorSort", ui.musicSortType, "descending", cfg.remoteSongSortDescending,
                "blockBefore", data.getRemoteSongSort() + "/" + data.isRemoteSongSortDescending(),
                "pushed", sort.name() + "/" + cfg.remoteSongSortDescending, "playlist", getSelectPlaylist());
    }

    /** 播放列表页的升降序，按音响存的形式。 */
    private boolean monitorSortDescending() {
        return SortButton.OrderType.byName(IamMusicPlayer.getConfig().uiConfig.musicOrderType).isReverse();
    }

    /** 这台音响当前的顺序是否已与监视器一致（键的亮/暗底就是它）。 */
    private boolean syncSortMatches() {
        var data = getScreen().getBoomBoxData();
        return monitorSortOf().name().equals(data.getRemoteSongSort())
                && monitorSortDescending() == data.isRemoteSongSortDescending();
    }

    /** 播放列表页当前的排序，翻成音响那边的名字。 */
    private IMPConfig.BoomboxSongSort monitorSortOf() {
        return switch (SortButton.SortType.byName(IamMusicPlayer.getConfig().uiConfig.musicSortType)) {
            case NAME -> IMPConfig.BoomboxSongSort.NAME;
            case PLAYER -> IMPConfig.BoomboxSongSort.PLAYER;
            // 「加入时间」照原样同步（服务端比较器认这个名字；配置界面不列这一档当选项）
            case CREATE_DATE -> IMPConfig.BoomboxSongSort.CREATE_DATE;
            case PUBLISH_DATE -> IMPConfig.BoomboxSongSort.PUBLISH_DATE;
        };
    }

    /** 把这台歌单里 B站 曲子的投稿时间报给服务端（它只认 {@code Music} 上有的东西），「投稿日期」排序靠这张表。 */
    private void pushPubdates(UUID playlist) {
        var songs = getSyncManager().getMusics(playlist);
        if (songs == null || songs.isEmpty())
            return;
        var tags = BiliBiliArcTags.getInstance();
        var bvids = new ArrayList<String>(songs.size());
        var pubdates = new ArrayList<Long>(songs.size());
        for (var music : songs) {
            var source = music == null ? null : music.getSource();
            if (source == null)
                continue;
            var bvid = BiliBiliUtil.stripPage(source.getIdentifier());
            long ms = tags.pubdateOf(bvid);
            if (ms <= 0L)
                continue;
            bvids.add(bvid);
            pubdates.add(ms);
        }
        if (bvids.isEmpty())
            return;
        NetworkManager.sendToServer(IMPPackets.BILIBILI_PUBDATES,
                new IMPPackets.BiliBiliPubdatesMessage(bvids, pubdates).toRFBB());
        LinkDebug.log("boombox pubdates pushed", "playlist", playlist, "count", bvids.size());
    }

    @Override
    public void render(GuiGraphics guiGraphics, float f, int mouseX, int mouseY) {
        super.render(guiGraphics, f, mouseX, mouseY);
        OERenderUtils.drawTexture(REMOTE_PLAYBACK_SELECT_TEXTURE, guiGraphics.pose(), getStartX(), getStartY(), 0f, 0f, width, height, width, height);
    }

    @Override
    public void renderAppearance(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, float f, float monitorWidth, float monitorHeight, BoomboxData data) {
        super.renderAppearance(poseStack, multiBufferSource, i, j, f, monitorWidth, monitorHeight, data);
        OERenderUtils.renderTextureSprite(REMOTE_PLAYBACK_SELECT_TEXTURE, poseStack, multiBufferSource, 0, 0, OERenderUtils.MIN_BREADTH * 2, 0, 0, 0, monitorWidth, monitorHeight, 0, 0, width, height, width, height, i, j);
        float onPxW = monitorWidth / (float) width;
        float onPxH = monitorHeight / (float) height;

        renderFixedListSprite(poseStack, multiBufferSource, 1, 1, OERenderUtils.MIN_BREADTH * 2f, 68, 35, i, j, onPxW, onPxH, monitorHeight, getSyncManager().getMyPlayList(), 5, (poseStack1, multiBufferSource1, x, y, z, w, h, i1, j1, entry) -> {
            renderSmartButtonBoxSprite(poseStack1, multiBufferSource1, x, y, z + OERenderUtils.MIN_BREADTH, w, h, i1, j1, onPxW, onPxH, monitorHeight, entry.getUuid().equals(getSelectPlaylist(data)));
            float sx = 1;
            var img = entry.getImage();
            if (!img.isEmpty()) {
                sx += h - 2 + 1;
                renderPlayListImage(poseStack1, multiBufferSource1, img, x + 1f, y + 1f, z + OERenderUtils.MIN_BREADTH * 3, h - 2, i1, j1, onPxW, onPxH, monitorHeight);
            }
            renderSmartTextSprite(poseStack1, multiBufferSource1, Component.literal(OEClientUtils.getWidthOmitText(entry.getName(), w - sx - 2 + 20, "...")), x + sx, y + 0.25f, z + OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, getDefaultRenderTextScale() * 0.7f, i1);
        });

        renderFixedListSprite(poseStack, multiBufferSource, 70, 1, OERenderUtils.MIN_BREADTH * 2f, 129, 35, i, j, onPxW, onPxH, monitorHeight, getSyncManager().getMusics(getSelectPlaylist(data)), 5, (poseStack12, multiBufferSource12, x, y, z, w, h, i12, j12, entry) -> {
            renderSmartButtonBoxSprite(poseStack12, multiBufferSource12, x, y, z + OERenderUtils.MIN_BREADTH, w, h, i12, j12, onPxW, onPxH, monitorHeight, false);
            float sx = 1;
            var img = entry.getImage();
            if (!img.isEmpty()) {
                sx += h - 2 + 1;
                renderPlayListImage(poseStack12, multiBufferSource12, img, x + 1f, y + 1f, z + OERenderUtils.MIN_BREADTH * 3, h - 2, i12, j12, onPxW, onPxH, monitorHeight);
            }
            renderSmartTextSprite(poseStack12, multiBufferSource12, Component.literal(OEClientUtils.getWidthOmitText(RemoteMusicsFixedListWidget.rowLabel(entry, currentMusicId(data)), w - sx - 2 + 20, "...")), x + sx, y + 0.25f, z + OERenderUtils.MIN_BREADTH * 3, onPxW, onPxH, monitorHeight, getDefaultRenderTextScale() * 0.7f, i12);
        });
    }

    /** Id of the track currently playing (the panel path uses data, the GUI path uses the screen data; both decide on the same value). */
    @Nullable
    private static UUID currentMusicId(BoomboxData data) {
        var m = data.getSelectedMusic();
        return m == null ? null : m.getUuid();
    }

    @Override
    public void tick() {
        super.tick();

        if (musicPlayListsCash != getSyncManager().getMyPlayList()) {
            musicPlayListsCash = getSyncManager().getMyPlayList();
            updateList();
        }

        if (getSelectPlaylist() != null && musicsCash != getSyncManager().getMusics(getSelectPlaylist())) {
            musicsCash = getSyncManager().getMusics(getSelectPlaylist());
            updateMusics();
        }

        syncRemoteSongSortToServer();

        // 服务端重启后投稿时间表就没了：这台音响只要还按「投稿日期」排，进页面时补报一次
        var pubdatePlaylist = getSelectPlaylist();
        if (pubdatePlaylist != null
                && IMPConfig.BoomboxSongSort.PUBLISH_DATE.name().equals(getScreen().getBoomBoxData().getRemoteSongSort())
                && pubdatePushed.add(pubdatePlaylist))
            pushPubdates(pubdatePlaylist);

        // The key shows the state by its background; it stays clickable whenever a playlist is selected
        if (syncSortButton != null) {
            syncSortButton.active = getSelectPlaylist() != null;
            syncSortButton.setLit(syncSortMatches());
        }

        // [▶ marker of the track currently playing] Pushed to the list widget every tick (it only uses it when drawing that row)
        if (remoteMusicsFixedButtonsList != null)
            remoteMusicsFixedButtonsList.setCurrentMusic(currentMusicId(getScreen().getBoomBoxData()));

        // 双击歌名后的定位：切页与选歌单都是服务端指令、回包晚一帧，早滚没用还会把焦点吃掉，所以要等目标歌单真的同步到了再滚一次
        var focus = getScreen().getFocusMusic();
        if (focus != null) {
            if (getScreen().isFocusExpired(System.currentTimeMillis())) {
                getScreen().clearFocusMusic();   // waited too long (sync never succeeded) ⇒ drop it
            } else if (getScreen().getFocusPlayList() != null
                    && getScreen().getFocusPlayList().equals(getSelectPlaylist())
                    && musicsCash != null && musicsCash == getSyncManager().getMusics(getSelectPlaylist())
                    && !musics.isEmpty()) {
                int idx = indexOfMusic(focus);
                if (idx >= 0)
                    scrollToEntry(idx);
                getScreen().clearFocusMusic();   // consume it even when not found: this track is no longer in this playlist
            }
        }
    }

    /** Index of the focus track in the list (not present = -1). */
    private int indexOfMusic(UUID musicId) {
        for (int i = 0; i < musics.size(); i++)
            if (musicId.equals(musics.get(i).getUuid()))
                return i;
        return -1;
    }

    /**
     * Scrolls the song list to entry idx: {@code setScrollAmount} is a fraction clamped to [0,1] and the top entry is
     * {@code firstIndex = (n - rows) × scrollAmount}, so bringing idx to the first row is {@code idx / (n - rows)}
     * (one row of top margin, {@code idx - 1}), then one self-correction by "top row" so the click is not a no-op.
     */
    private void scrollToEntry(int idx) {
        if (remoteMusicsFixedButtonsList == null)
            return;
        int n = musics.size();
        int rows = LIST_ROWS;
        if (n <= rows) {
            remoteMusicsFixedButtonsList.setScrollAmount(0f);
            return;
        }
        int max = n - rows;
        float f = Math.max(0f, Math.min(1f, (idx - 1f) / max));
        remoteMusicsFixedButtonsList.setScrollAmount(f);
        int first = remoteMusicsFixedButtonsList.currentFirstEntryIndex();
        if (idx < first || idx >= first + rows)
            remoteMusicsFixedButtonsList.setScrollAmount(Math.max(0f, Math.min(1f, idx / (float) max)));
    }

    /**
     * [Sequential playback convention] **Mirror** the sorting the UI is using **to the server**.
     */
    private void syncRemoteSongSortToServer() {
        var cfg = IamMusicPlayer.getConfig().boomboxConfig;
        String want = cfg.remoteSongSort == null ? "ADD_ORDER" : cfg.remoteSongSort.name();
        boolean wantDesc = cfg.remoteSongSortDescending;

        var data = getScreen().getBoomBoxData();
        if (want.equals(data.getRemoteSongSort()) && wantDesc == data.isRemoteSongSortDescending())
            return;

        var tag = new CompoundTag();
        tag.putString("sort", want);
        tag.putBoolean("descending", wantDesc);
        getScreen().instruction("set_remote_song_sort", tag);
    }

    private void setMusic(UUID musicId) {
        getScreen().insSelectedMusic(musicId);
    }

    private Music getMusic() {
        return getScreen().getBoomBoxData().getSelectedMusic();
    }

    private UUID getSelectPlaylist(BoomboxData data) {
        return data.getSelectedPlayList(mc.player);
    }

    private UUID getSelectPlaylist() {
        return getScreen().getBoomBoxData().getSelectedPlayList(mc.player);
    }

    private void updateList() {
        musicPlayLists.clear();
        if (musicPlayListsCash != null) musicPlayLists.addAll(musicPlayListsCash);
    }

    private void updateMusics() {
        musics.clear();
        if (musicsCash != null) musics.addAll(musicsCash);

        // [Config boomboxConfig.remoteSongSort] A ClothConfig option (**no button added on the boombox**); `ADD_ORDER` = the playlist's stored order.
        var cfg = IamMusicPlayer.getConfig().boomboxConfig;
        var sort = cfg.remoteSongSort == null ? IMPConfig.BoomboxSongSort.ADD_ORDER : cfg.remoteSongSort;
        var type = switch (sort) {
            case ADD_ORDER -> null;
            case NAME -> SortButton.SortType.NAME;
            case PLAYER -> SortButton.SortType.PLAYER;
            case CREATE_DATE -> SortButton.SortType.CREATE_DATE;
            case PUBLISH_DATE -> SortButton.SortType.PUBLISH_DATE;
        };
        if (type == null)
            return;
        var order = cfg.remoteSongSortDescending ? SortButton.OrderType.DESCENDING : SortButton.OrderType.ASCENDING;
        var sorted = musics.stream().sorted(type.getComparator(order)).toList();
        musics.clear();
        musics.addAll(sorted);
    }

    private void setSelectPlaylist(UUID playlist) {
        getScreen().insSelectedPlayList(playlist);
    }
}
