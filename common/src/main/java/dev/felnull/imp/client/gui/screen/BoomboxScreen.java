package dev.felnull.imp.client.gui.screen;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.client.IMPKeyMappings;
import dev.felnull.imp.client.gui.components.BoomboxButton;
import dev.felnull.imp.client.gui.screen.monitor.boombox.BoomboxMonitor;
import dev.felnull.imp.client.gui.screen.monitor.boombox.EarphoneMonitor;
import dev.felnull.imp.inventory.BoomboxMenu;
import dev.felnull.imp.item.BoomboxItem;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import dev.felnull.imp.util.IMPItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import org.modsauce.otyacraftenginerenewed.client.gui.screen.OEItemBEContainerBasedScreen;
import org.modsauce.otyacraftenginerenewed.client.util.OERenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;

import java.nio.file.Path;
import java.util.*;

public class BoomboxScreen extends OEItemBEContainerBasedScreen<BoomboxMenu> {
    public static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/boombox/boombox_base.png");
    public static final ResourceLocation EMPTY_CASSETTE_TAPE_SLOT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/slot/cassette_tape_slot.png");
    public static final ResourceLocation EMPTY_ANTENNA_SLOT = ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "textures/gui/container/slot/antenna_slot.png");
    private final Map<BoomboxData.MonitorType, BoomboxMonitor> monitors = new HashMap<>();
    protected BoomboxMonitor monitor;
    public long lastNoAntenna;

    public BoomboxScreen(BoomboxMenu abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
        this.imageWidth = 214;
        this.imageHeight = 175;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new BoomboxButton(leftPos + 5, topPos + 17, BoomboxData.ButtonType.POWER, n -> {
            insPressButton(BoomboxData.ButtonType.POWER);
        }, this::getButtons, this::isPowered));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19, topPos + 17, BoomboxData.ButtonType.RADIO, n -> {
            if (!getAntenna().isEmpty() && IMPItemUtil.isAntenna(getAntenna())) {
                insPressButton(BoomboxData.ButtonType.RADIO);
            } else {
                lastNoAntenna = System.currentTimeMillis();
            }
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 2, topPos + 17, BoomboxData.ButtonType.START, n -> {
            insPressButton(BoomboxData.ButtonType.START);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 3, topPos + 17, BoomboxData.ButtonType.PAUSE, n -> {
            insPressButton(BoomboxData.ButtonType.PAUSE);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 4, topPos + 17, BoomboxData.ButtonType.STOP, n -> {
            insPressButton(BoomboxData.ButtonType.STOP);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 5, topPos + 17, BoomboxData.ButtonType.LOOP, n -> {
            insPressButton(BoomboxData.ButtonType.LOOP);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 6 + 14, topPos + 17, BoomboxData.ButtonType.VOL_DOWN, n -> {
            insPressButton(BoomboxData.ButtonType.VOL_DOWN);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 7 + 14, topPos + 17, BoomboxData.ButtonType.VOL_UP, n -> {
            insPressButton(BoomboxData.ButtonType.VOL_UP);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 8 + 14, topPos + 17, BoomboxData.ButtonType.VOL_MUTE, n -> {
            insPressButton(BoomboxData.ButtonType.VOL_MUTE);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 9 + 14, topPos + 17, BoomboxData.ButtonType.VOL_MAX, n -> {
            insPressButton(BoomboxData.ButtonType.VOL_MAX);
        }, this::getButtons));

        this.addRenderableWidget(new BoomboxButton(leftPos + 5 + 19 * 9 + 14, topPos + 17 + 60, BoomboxData.ButtonType.EARPHONE, n -> {
            insPressButton(BoomboxData.ButtonType.EARPHONE);
        }, this::getButtons));

        changeScreenMonitor(getRawMonitorType());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        // Part-jump line: one line drawn inside this screen's own layout, in the free band the "物品栏" label sits on.
        // The on-screen HUD position must not be used — it lands on the hotbar row at the bottom of the container screen.
        if (partJumpInput != null)
            PartJumpScreen.drawScreenPrompt(guiGraphics, partJumpMusic(), partJumpInput,
                    leftPos + imageWidth / 2, topPos + inventoryLabelY, imageWidth - 8);
    }

    /**
     * Part-jump digits typed so far, or {@code null} while the input line is closed. Living on the screen is the point:
     * a screen belongs to exactly one boombox, so no "which ringer is this player hearing" lookup is needed.
     */
    @Nullable
    private String partJumpInput;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (partJumpInput != null) {
            // While the line is up it owns the keyboard (same rule as the in-world line): digits are not hotkeys,
            // Esc cancels the **line** instead of closing the screen, and the text keys do not reach the GUI.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                partJumpInput = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitPartJump();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!partJumpInput.isEmpty())
                    partJumpInput = partJumpInput.substring(0, partJumpInput.length() - 1);
                return true;
            }
            int digit = PartJumpScreen.digitOf(keyCode);
            if (digit >= 0)
                partJumpInput = PartJumpScreen.append(partJumpInput, digit);
            return true;
        }
        // Open only on the **song info page** (remote playback): that is where a B站 video is being played from a
        // playlist, and therefore the only page where "which part" means anything. Everywhere else the key stays free.
        if (getRawMonitorType() == BoomboxData.MonitorType.REMOTE_PLAYBACK
                && IMPKeyMappings.PART_JUMP.matches(keyCode, scanCode)) {
            partJumpInput = "";
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitPartJump() {
        int page = PartJumpScreen.parsePage(partJumpInput);
        partJumpInput = null;
        if (page >= 1)
            insBiliBiliPartJump(page);
    }

    /** The music this screen's boombox is on (cassette included), i.e. what the input line talks about. */
    @Nullable
    private Music partJumpMusic() {
        var data = getBoomBoxData();
        return data.getSelectedMusic() == null ? data.getCassetteTapeMusic() : data.getSelectedMusic();
    }

    /**
     * Asks this screen's boombox to jump to part {@code page} of the B站 video it is playing: the base screen routes the instruction to the boombox this screen was opened on, and {@code BoomboxData#onInstruction} reuses the same {@code jumpBiliBiliPart} as the in-world hotkey.
     */
    public void insBiliBiliPartJump(int page) {
        var tag = new CompoundTag();
        tag.putInt("page", page);
        instruction("bilibili_part_jump", tag);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
        super.renderBg(guiGraphics, f, i, j);
        if (getCassetteTape().isEmpty())
            OERenderUtils.drawTexture(EMPTY_CASSETTE_TAPE_SLOT, guiGraphics.pose(), leftPos + 183, topPos + 98, 0, 0, 16, 16, 16, 16);
        if (getAntenna().isEmpty())
            OERenderUtils.drawTexture(EMPTY_ANTENNA_SLOT, guiGraphics.pose(), leftPos + 183, topPos + 124, 0, 0, 16, 16, 16, 16);

        if (monitor != null)
            monitor.render(guiGraphics, f, i, j);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (monitor != null) {
            if (getRawMonitorType() != monitor.getType())
                changeScreenMonitor(getRawMonitorType());
            monitor.tick();
        }
    }

    public void insSelectedPlayList(@Nullable UUID uuid) {
        var tag = new CompoundTag();
        if (uuid != null)
            tag.putUUID("pl", uuid);
        instruction("set_selected_play_list", tag);
    }

    /**
     * [double click the track name] The "focus track" passed across pages, together with its target playlist: the
     * consumer may only scroll once that playlist is selected, else the old list consumes the focus.
     */
    private static final long FOCUS_TIMEOUT_MS = 5000L;
    @Nullable
    private UUID focusMusicId;
    @Nullable
    private UUID focusPlayListId;
    private long focusAt;

    public void setFocusMusic(@Nullable UUID musicId, @Nullable UUID playListId) {
        this.focusMusicId = musicId;
        this.focusPlayListId = playListId;
        this.focusAt = System.currentTimeMillis();
    }

    /** The target page takes the focus away (the page itself decides when it counts as "consumed"). */
    @Nullable
    public UUID getFocusMusic() {
        return focusMusicId;
    }

    /** The playlist the focus belongs to (the consumer must first confirm "the current selection is this one"). */
    @Nullable
    public UUID getFocusPlayList() {
        return focusPlayListId;
    }

    /** Whether the focus has waited too long (discarded when the page switch / sync never arrives). */
    public boolean isFocusExpired(long now) {
        return focusMusicId != null && now - focusAt > FOCUS_TIMEOUT_MS;
    }

    public void clearFocusMusic() {
        this.focusMusicId = null;
        this.focusPlayListId = null;
    }

    public void insRadioUrl(String url) {
        var tag = new CompoundTag();
        tag.putString("url", url);
        instruction("set_radio_url", tag);
    }

    public void insSelectedMusic(@Nullable UUID musicId) {
        var tag = new CompoundTag();
        if (musicId != null)
            tag.putUUID("m", musicId);
        instruction("set_selected_music", tag);
    }

    public boolean isMute() {
        return getBoomBoxData().isMute();
    }

    public boolean isPlaying() {
        return getBoomBoxData().isPlaying();
    }

    public int getVolume() {
        return getBoomBoxData().getVolume();
    }

    public long getMusicPosition() {
        return getBoomBoxData().getMusicPosition();
    }

    private boolean isPowered() {
        return getBoomBoxData().isPowered();
    }

    public boolean isLoop() {
        return getBoomBoxData().isLoop();
    }

    public BoomboxData getBoomBoxData() {
        if (isBlock()) {
            if (getBlockEntity() instanceof BoomboxBlockEntity boomboxBlockEntity)
                return boomboxBlockEntity.getBoomboxData();
        }
        return BoomboxItem.getData(getItem(), Minecraft.getInstance().level.registryAccess());
    }

    public void insMonitor(BoomboxData.MonitorType monitorType) {
        var tag = new CompoundTag();
        tag.putString("name", monitorType.getName());
        instruction("set_monitor", tag);
    }

    public boolean isMusicLoading() {
        return getBoomBoxData().isLoadingMusic();
    }

    public ItemStack getCassetteTape() {
        return getMenu().getItems().get(0);
    }

    public ItemStack getAntenna() {
        return getMenu().getItems().get(1);
    }

    public void insVolume(int volume) {
        var tag = new CompoundTag();
        tag.putInt("volume", volume);
        instruction("set_volume", tag);
    }

    public void insPositionAndRestart(long position) {
        var tag = new CompoundTag();
        tag.putLong("position", position);
        instruction("restat_and_set_position", tag);
    }

    public void insContinuousType(@NotNull BoomboxData.ContinuousType continuousType) {
        var tag = new CompoundTag();
        tag.putString("type", continuousType.getName());
        instruction("set_continuous_type", tag);
    }

    public boolean isAutoPlay() {
        return getBoomBoxData().isAutoPlay();
    }

    public void insAutoPlay(boolean autoPlay) {
        var tag = new CompoundTag();
        tag.putBoolean("autoPlay", autoPlay);
        instruction("set_auto_play", tag);
    }

    public void insLoop(boolean loop) {
        var tag = new CompoundTag();
        tag.putBoolean("loop", loop);
        instruction("set_loop", tag);
    }

    /** Three loop states — **the state order matches the server's `setLoopState` exactly**: 0 repeat one / 1 sequential / 2 shuffle. */
    public int getLoopState() {
        return getBoomBoxData().getLoopState();
    }

    /**
     * Sets the three loop states by reusing two packets ({@code set_loop} + {@code set_continuous_type}, no new
     * protocol); the mapping must match the server's {@code BoomboxData.setLoopState(int)} or it stays on state0.
     */
    public void insLoopState(int state) {
        dev.felnull.imp.client.music.link.LinkDebug.log("insLoopState(send)", "state", state,
                "localState", getBoomBoxData().getLoopState(),
                "localLoop", getBoomBoxData().isLoop(),
                "localContinuous", getBoomBoxData().getContinuousType());
        insLoop(state == 0);   // only state0 (repeat one) sets loop — consistent with the server's setLoopState
        insContinuousType(switch (state) {
            case 2 -> BoomboxData.ContinuousType.RANDOM;   // shuffle
            case 0 -> BoomboxData.ContinuousType.NONE;     // repeat one: no next track is picked
            default -> BoomboxData.ContinuousType.ORDER;   // sequential: next track automatically
        });
    }

    public void insPause() {
        instruction("set_pause", new CompoundTag());
    }

    public void insPlaying(boolean playing) {
        var tag = new CompoundTag();
        tag.putBoolean("playing", playing);
        instruction("set_playing", tag);
    }

    public void insRadioSource(MusicSource source) {
        var tag = new CompoundTag();
        tag.put("source", source.createSavedTag());
        instruction("set_radio_source", tag);
    }

    public void insRadioImage(ImageInfo imageInfo) {
        var tag = new CompoundTag();
        tag.put("image", imageInfo.createSavedTag());
        instruction("set_radio_image", tag);
    }

    public void insRadioName(String name) {
        var tag = new CompoundTag();
        tag.putString("name", name);
        instruction("set_radio_name", tag);
    }

    public void insRadioAuthor(String author) {
        var tag = new CompoundTag();
        tag.putString("author", author);
        instruction("set_radio_author", tag);
    }

    @Override
    protected ResourceLocation getBackGrandTexture() {
        return BG_TEXTURE;
    }

    private BoomboxData.Buttons getButtons() {
        return getBoomBoxData().getButtons();
    }

    private void insPressButton(BoomboxData.ButtonType type) {
        var tag = new CompoundTag();
        tag.putString("Type", type.getName());
        instruction("buttons_press", tag);
    }

    private void changeScreenMonitor(BoomboxData.MonitorType type) {
        if (monitor != null) {
            monitor.renderables.forEach(this::removeWidget);
            monitor.depose();
        }

        if (!monitors.containsKey(type))
            monitors.put(type, BoomboxMonitor.createdBoomBoxMonitor(type, this));

        monitor = monitors.get(type);
        monitor.init(leftPos, topPos);
        monitor.renderables.forEach(this::addRenderableWidget);
    }

    private BoomboxData.MonitorType getRawMonitorType() {
        return getBoomBoxData().getMonitorType();
    }

    @Override
    public void onClose() {
        super.onClose();
        if (monitor != null)
            monitor.depose();
    }

    @Override
    public void onFilesDrop(List<Path> list) {
        if (monitor != null)
            monitor.onFilesDrop(list);
    }

    @Override
    public void onInstructionReturn(String name, CompoundTag data) {
        super.onInstructionReturn(name, data);
        if(Objects.equals(name, "earphone") && getRawMonitorType() == BoomboxData.MonitorType.EARPHONE && monitor instanceof EarphoneMonitor earphoneMonitor){
            var earphones = OENbtUtils.readList(data, "earphone", null, EarphoneSaveData.EarphoneLocation::load, Tag.TAG_COMPOUND);
            earphoneMonitor.setEarphoneLocationList(earphones);
        }
    }
}
