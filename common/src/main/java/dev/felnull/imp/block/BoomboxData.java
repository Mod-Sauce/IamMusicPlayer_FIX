package dev.felnull.imp.block;

import dev.felnull.imp.item.CassetteTapeItem;
import dev.felnull.imp.item.IMPItems;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.music.ringer.IMusicRinger;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.util.IMPItemUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;

import java.util.*;
import java.util.function.Function;

public class BoomboxData {
    private final Map<UUID, UUID> playerSelectPlaylists = new HashMap<>();
    private final DataAccess access;
    private MonitorType monitorType = MonitorType.OFF;
    private MonitorType lastMonitorType = MonitorType.OFF;
    private boolean handleRaising = true;
    private boolean lidOpen;
    private int handleRaisedProgressOld = getHandleRaisedMax();
    private int handleRaisedProgress = getHandleRaisedMax();
    private int lidOpenProgressOld;
    private int lidOpenProgress;
    private int parabolicAntennaProgressOld;
    private int parabolicAntennaProgress;
    private int antennaProgressOld;
    private int antennaProgress;
    private boolean oldCassetteTapeFlg;
    private boolean changeCassetteTape;
    private boolean playing;
    private int volume = 150;
    private boolean loop;
    /** [NEW] Auto play: true = the next track follows when the current one ends; false = pause when it ends. Independent of the loop state. */
    private boolean autoPlay = true;
    private boolean mute;
    private long musicPosition;
    private boolean loadingMusic;
    private String radioUrl = "";
    private MusicSource radioSource = MusicSource.EMPTY;
    private ImageInfo radioImage = ImageInfo.EMPTY;
    private String radioName = "";
    private String radioAuthor = "";
    private Music selectedMusic;
    private ContinuousType continuousType = ContinuousType.NONE;

    /** [Sequential playback convention] The UI's sorting is mirrored here (sent as `set_remote_song_sort` when the client renders that page) ⇒ sequential playback = the UI order. */
    private String remoteSongSort = "ADD_ORDER";
    private boolean remoteSongSortDescending = false;
    private boolean radioStartFlg;
    private boolean noChangeCassetteTape;

    @Nullable
    private UUID earphoneUUID;

    public BoomboxData(CompoundTag boomboxTag, @NotNull BoomboxData.DataAccess access) {
        this.access = access;
        if (boomboxTag != null) this.load(boomboxTag.getCompound("BoomBoxData"), true, true);
    }

    public void tick(Level level) {

        this.handleRaisedProgressOld = this.handleRaisedProgress;
        this.lidOpenProgressOld = this.lidOpenProgress;
        this.antennaProgressOld = this.antennaProgress;
        this.parabolicAntennaProgressOld = this.parabolicAntennaProgress;

        if (this.handleRaising)
            this.handleRaisedProgress = Mth.clamp(this.handleRaisedProgress + 1, 0, getHandleRaisedMax());
        else this.handleRaisedProgress = Mth.clamp(this.handleRaisedProgress - 1, 0, getHandleRaisedMax());

        if (this.lidOpen) this.lidOpenProgress = Mth.clamp(this.lidOpenProgress + 1, 0, getLidOpenProgressMax());
        else this.lidOpenProgress = Mth.clamp(this.lidOpenProgress - 1, 0, getLidOpenProgressMax());

        if (isUseAntenna() && isRadioStream()) this.antennaProgress = Mth.clamp(this.antennaProgress + 1, 0, 30);
        else this.antennaProgress = Mth.clamp(this.antennaProgress - 1, 0, 30);

        if (isPowered() && isUseAntenna() && isAntennaExist() && getAntenna().is(IMPItems.PARABOLIC_ANTENNA.get()))
            this.parabolicAntennaProgress += 2;


        if (!level.isClientSide()) {
            if (getRinger() != null) loadingMusic = getRinger().isRingerWait();

            if (isPowered() && monitorType == MonitorType.OFF) monitorType = MonitorType.PLAYBACK;

            if (!isPowered() && monitorType != MonitorType.OFF) monitorType = MonitorType.OFF;

            if(!isPowered() && earphoneUUID != null) setEarphoneUUID(null);

            // [Track change / page change] Song changed: rewind + force a ringer restart, playing untouched; page flip: governed by `keepPlayingOnRemoteSwitch`; anything else: reset.
            // The restart is mandatory — `MusicRing.tick()` only rebuilds its listeners when `isRingerPlaying()` changed, so a same-track source swap is never sent.
            // `setPlaying(false)` must not be called here: nothing would ever set it back for automatic continuation.
            var curSel = getSelectedMusic();
            var curSelUuid = curSel == null ? null : curSel.getUuid();
            boolean songChanged = !Objects.equals(curSelUuid, lastSelectedMusicUuid);
            lastSelectedMusicUuid = curSelUuid;
            boolean keepPlaying = remoteKeep() && !songChanged;

            if (songChanged) {
                setMusicPosition(0);
                if (getRinger() != null)
                    getRinger().ringerRestart();
            } else if (!keepPlaying) {
                if (monitorType != lastMonitorType) {
                    setMusicPosition(0);
                    setPlaying(false);
                }
                if (!canPlay()) {
                    setMusicPosition(0);
                    setPlaying(false);
                }
            }
            lastMonitorType = monitorType;

            if (!isRadioRemote()) {
                playerSelectPlaylists.clear();
                setSelectedMusic(null);
                setContinuousType(ContinuousType.NONE);
            }

            if (radioStartFlg) {
                setRadioStartFlg(false);
                setPlaying(true);
            }

            if (monitorType == MonitorType.REMOTE_PLAYBACK && getSelectedMusic() == null)
                monitorType = MonitorType.REMOTE_PLAYBACK_SELECT;

            if (monitorType == MonitorType.RADIO && getRadioSource().isEmpty()) monitorType = MonitorType.RADIO_SELECT;

            if ((isRadio() && !isAntennaExist()) || (isRadioRemote() && !IMPItemUtil.isRemotePlayBackAntenna(getAntenna())) || (isRadioStream() && IMPItemUtil.isRemotePlayBackAntenna(getAntenna())))

                if ((isRadioRemote() && !IMPItemUtil.isRemotePlayBackAntenna(getAntenna())) || (isRadioStream() && !IMPItemUtil.isRadioAntenna(getAntenna()))) {
                    monitorType = MonitorType.PLAYBACK;
                }

            if (!isRadioStream()) {
                setRadioImage(ImageInfo.EMPTY);
                setRadioSource(MusicSource.EMPTY);
                setRadioAuthor("");
                setRadioName("");
                setRadioUrl("");
            }

            if (monitorType != MonitorType.RADIO_SELECT) setRadioUrl("");

            if (this.changeCassetteTape) {
                if (!isLidOpen()) startLidOpen(true, level);

                if (getLidOpenProgress() >= getLidOpenProgressMax()) {
                    changeCassetteTape = false;
                    startLidOpen(false, level);
                }
            }

            if(earphoneUUID != null){
                if(!EarphoneSaveData.getInstance((ServerLevel) level).has(earphoneUUID)) {
                    setPlaying(false);
                    setEarphoneUUID(null);
                }
            }
        }
    }

    public boolean canPlay() {
        boolean canPlayFlg = monitorType == MonitorType.PLAYBACK && isMusicCassetteTapeExist();
        boolean canPlayFlg2 = monitorType == MonitorType.RADIO && !getRadioSource().isEmpty();
        boolean canPlayFlg3 = monitorType == MonitorType.REMOTE_PLAYBACK;
        return canPlayFlg || canPlayFlg2 || canPlayFlg3;
    }

    public CompoundTag onInstruction(ServerPlayer player, String name, CompoundTag data) {
        // [Trace] For "it stopped without anyone pressing a key": log every instruction received (button type included)
        if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("ins:" + name))
            dev.felnull.imp.client.music.link.LinkDebug.log("ins recv", "name", name,
                    "type", data.contains("Type") ? data.getString("Type") : "-",
                    "keys", String.join(",", data.getAllKeys()),
                    "player", player.getGameProfile().getName());
        if ("buttons_press".equals(name)) {
            ButtonType type = ButtonType.getByName(data.getString("Type"));
            switch (type) {
                case POWER -> setPower(!isPowered());
                case LOOP -> {
                    dev.felnull.imp.client.music.link.LinkDebug.log("ins button LOOP",
                            "stateBefore", getLoopState(), "powered", isPowered());
                    setLoopState((getLoopState() + 1) % 3);
                }
                case VOL_DOWN -> {
                    if (isPowered()) setVolume(Mth.clamp(volume - 10, 0, 300));
                }
                case VOL_UP -> {
                    if (isPowered()) setVolume(Mth.clamp(volume + 10, 0, 300));
                    setMute(false);
                }
                case VOL_MUTE -> setMute(!isMute());
                case VOL_MAX -> {
                    if (isPowered()) setVolume(300);
                    setMute(false);
                }
                case RADIO -> {
                    // [Antenna button] Satellite/parabolic antenna cycles inside remote mode; broadcast antenna opens the radio URL page.
                    // The remote case must not reach the `isRadio()` branch below — `isRadio()` includes `isRadioRemote()`, so one press would read as "leaving radio" and drop to PLAYBACK.
                    var satellite = IMPItemUtil.isRemotePlayBackAntenna(getAntenna());
                    if (satellite) {
                        if (getMonitorType() == MonitorType.PLAYBACK)
                            setPower(true);
                        // Round trip: select page → playback page; anything else (PLAYBACK / not in remote) → select page
                        setMonitorType(getMonitorType() == MonitorType.REMOTE_PLAYBACK
                                ? MonitorType.REMOTE_PLAYBACK_SELECT
                                : MonitorType.REMOTE_PLAYBACK);
                    } else if (isRadio()) {
                        setMonitorType(MonitorType.PLAYBACK);
                    } else {
                        setPower(true);
                        setRadioMode();
                    }
                }
                case START -> {
                    if (isMusicCassetteTapeExist() || isRadio()) {
                        setPower(true);
                        setPlaying(true);
                    }
                }
                case STOP -> {
                    if (isPowered()) {
                        setPlaying(false);
                        setMusicPosition(0);
                    }
                }
                case PAUSE -> {
                    if (isPowered()) {
                        setPlaying(false);
                        if (isRadioStream()) setMusicPosition(0);
                    }
                }
                case EARPHONE -> {
                    setMonitorType(MonitorType.EARPHONE);
                }
            }
            return null;
        } else if ("set_volume".equals(name)) {
            if (isPowered()) setVolume(data.getInt("volume"));
            return null;
        } else if ("set_playing".equals(name)) {
            if (isPowered()) {
                boolean pl = data.getBoolean("playing");
                setPlaying(pl);
                if (!pl) setMusicPosition(0);
            }
            return null;
        } else if ("set_pause".equals(name)) {
            if (isPowered()) setPlaying(false);
            return null;
        } else if ("set_loop".equals(name)) {
            dev.felnull.imp.client.music.link.LinkDebug.log("ins set_loop", "loop", data.getBoolean("loop"),
                    "powered", isPowered(), "applied", isPowered());
            if (isPowered()) setLoop(data.getBoolean("loop"));
            return null;
        } else if ("set_auto_play".equals(name)) {
            if (isPowered()) setAutoPlay(data.getBoolean("autoPlay"));
            return null;
        } else if ("restat_and_set_position".equals(name)) {
            if (isPowered()) setMusicPositionAndRestart(data.getLong("position"));
            return null;
        } else if ("set_radio_url".equals(name)) {
            if (isPowered()) setRadioUrl(data.getString("url"));
            return null;
        } else if ("set_monitor".equals(name)) {
            if (isPowered()) {
                var m = MonitorType.getByName(data.getString("name"));
                setMonitorType(m);
                if ((m == MonitorType.RADIO && getRadioSource() != null && !getRadioSource().isEmpty()) || (m == MonitorType.REMOTE_PLAYBACK && getSelectedMusic() != null && !getSelectedMusic().getSource().isEmpty()))
                    setRadioStartFlg(true);
            }
            return null;
        } else if ("set_radio_source".equals(name)) {
            if (isPowered())
                setRadioSource(TagSerializable.loadSavedTag(data.getCompound("source"), new MusicSource()));
            return null;
        } else if ("set_radio_image".equals(name)) {
            if (isPowered()) setRadioImage(TagSerializable.loadSavedTag(data.getCompound("image"), new ImageInfo()));
            return null;
        } else if ("set_radio_name".equals(name)) {
            if (isPowered()) setRadioName(data.getString("name"));
            return null;
        } else if ("set_radio_author".equals(name)) {
            if (isPowered()) setRadioAuthor(data.getString("author"));
            return null;
        } else if ("set_selected_play_list".equals(name)) {
            if (isPowered() && isRadioRemote()) {
                if (data.contains("pl")) {
                    var uuid = data.getUUID("pl");
                    var pl = MusicManager.getInstance().getSaveData(player.server).getPlayLists().get(uuid);
                    if (pl != null && pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).isMoreReadOnly())
                        setSelectedPlayList(player, uuid);
                } else {
                    setSelectedPlayList(player, null);
                }
            }
            return null;
        } else if ("set_selected_music".equals(name)) {
            if (isPowered() && isRadioRemote()) {
                if (data.contains("m")) {
                    var uuid = data.getUUID("m");
                    var m = MusicManager.getInstance().getSaveData(player.server).getMusics().get(uuid);
                    if (m != null) {
                        var pl = MusicManager.getInstance().getPlaylistByMusic(player.server, m.getUuid());
                        if (pl != null && pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).isMoreReadOnly()) {
                            setSelectedMusic(m);
                        }
                    }
                } else {
                    setSelectedMusic(null);
                }
            }
            return null;
        } else if ("bilibili_part_jump".equals(name)) {
            // [Part jump] Sent from the boombox's own screen (see BoomboxScreen), whose instruction channel already routes to that exact boombox,
            // so the same lines cover both the block entity and the handheld item.
            jumpBiliBiliPart(player, data.getInt("page"));
            return null;
        } else if ("set_continuous_type".equals(name)) {
            // `set_loop` and `set_continuous_type` (see `BoomboxScreen.insLoopState`) must require the same power condition:
            // if this one also required `isRadioRemote()`, the data could stay (loop=false, NONE) with the UI showing "sequential", and nothing would follow when the track ends.
            if (isPowered()) {
                setContinuousType(ContinuousType.getByName(data.getString("type")));
            }
            dev.felnull.imp.client.music.link.LinkDebug.log("ins set_continuous_type",
                    "recv", data.getString("type"), "powered", isPowered(),
                    "applied", isPowered(), "loopNow", isLoop(),
                    "stateNow", getLoopState());
            return null;
        } else if ("set_remote_song_sort".equals(name)) {
            // The client mirrors the config's sorting when rendering the select page (sent only when different);
            // `update()` persists it (the item form relies on that to write back to NBT), playback state is untouched.
            this.remoteSongSort = data.getString("sort");
            this.remoteSongSortDescending = data.getBoolean("descending");
            update();
            return null;
        } else if ("earphone".equals(name)){
            var savedData = EarphoneSaveData.getInstance(player.serverLevel());
            return OENbtUtils.writeList(new CompoundTag(), "earphone", savedData.getEarphones().stream().filter(
                    l -> {
                        var item = EarphoneSaveData.findEarphone(player.serverLevel(), l);
                        return item != null && Objects.equals(l.ownerUUID(), player.getUUID());
                    }
            ).toList(), EarphoneSaveData.EarphoneLocation::save);
        } else if ("connect_earphone".equals(name)) {
            if(data.contains("uuid"))
                setEarphoneUUID(data.getUUID("uuid"));
            else
                setEarphoneUUID(null);
            return null;
        }
        return null;
    }

    public void onCassetteTapeChange(ItemStack newItem, ItemStack oldItem) {
        oldCassetteTapeFlg = false;

        if (!isRadio()) {
            setMusicPosition(0);
            setPlaying(false);
        }
        if (!(newItem.isEmpty() && isLidOpen())) this.changeCassetteTape = true;
        update();
    }

    public void setOldCassetteTape(ItemStack oldCassetteTape) {
        this.oldCassetteTapeFlg = true;
        update();
    }

    public CompoundTag save(CompoundTag tag, boolean absolutely, boolean sync) {
        tag.putString("MonitorType", this.monitorType.getName());
        tag.putBoolean("HandleRaising", this.handleRaising);
        tag.putBoolean("LidOpen", this.lidOpen);
        tag.putBoolean("Playing", playing);
        tag.putInt("Volume", this.volume);
        tag.putBoolean("Loop", this.loop);
        tag.putBoolean("AutoPlay", this.autoPlay);
        tag.putBoolean("Mute", this.mute);
        tag.putLong("RingerPosition", this.musicPosition);
        tag.putString("RadioUrl", this.radioUrl);
        tag.put("RadioSource", this.radioSource.createSavedTag());
        tag.put("RadioImage", this.radioImage.createSavedTag());
        tag.putString("RadioName", this.radioName);
        tag.putString("RadioAuthor", this.radioAuthor);
        OENbtUtils.writeUUIDMap(tag, "PlayerSelectPlaylists", playerSelectPlaylists);
        if (this.selectedMusic != null) tag.put("SelectedMusic", this.selectedMusic.createSavedTag());
        tag.putString("ContinuousType", this.continuousType.getName());
        // [Sequential playback convention] Must be saved: the item form of the boombox is rebuilt from NBT every tick (see the note on lastSelectedMusicUuid).
        tag.putString("RemoteSongSort", this.remoteSongSort);
        tag.putBoolean("RemoteSongSortDescending", this.remoteSongSortDescending);
        tag.putString("LastMonitorType", this.lastMonitorType.getName());
        // `BoomboxItem.getData()` always builds a fresh BoomboxData, so in-memory fields do not survive one read: this uuid must be saved or every tick sees null
        // ⇒ `songChanged` always true ⇒ `setMusicPosition(0)` + `ringerRestart()` every tick ⇒ server STOP/LOAD every tick, which looks like a load-stop loop on the client.
        if (lastSelectedMusicUuid != null) tag.putUUID("LastSelectedMusicUuid", lastSelectedMusicUuid);
        if(earphoneUUID != null)
            tag.putUUID("earphone", earphoneUUID);


        if (absolutely) {
            tag.putInt("HandleRaisedProgressOld", this.handleRaisedProgressOld);
            tag.putInt("HandleRaisedProgress", this.handleRaisedProgress);
            tag.putInt("LidOpenProgressOld", this.lidOpenProgressOld);
            tag.putInt("LidOpenProgress", this.lidOpenProgress);
            tag.putInt("ParabolicAntennaProgressOld", this.parabolicAntennaProgressOld);
            tag.putInt("ParabolicAntennaProgress", this.parabolicAntennaProgress);
            tag.putInt("AntennaProgressOld", this.antennaProgressOld);
            tag.putInt("AntennaProgress", this.antennaProgress);
            tag.putBoolean("NoChangeCassetteTape", this.noChangeCassetteTape);
        }

        if (absolutely || sync) {
            tag.putBoolean("ChangeCassetteTape", this.changeCassetteTape);
//            tag.put("OldCassetteTape", this.oldCassetteTape.saveWithParent(provider));
            tag.putBoolean("OldCassetteTapeFlg", oldCassetteTapeFlg);
            tag.putBoolean("LoadingMusic", this.loadingMusic);
            tag.putBoolean("RadioStartFlg", this.radioStartFlg);
        }

        return tag;
    }

    public void load(CompoundTag tag, boolean absolutely, boolean sync) {
        this.monitorType = MonitorType.getByName(tag.getString("MonitorType"));
        this.handleRaising = tag.getBoolean("HandleRaising");
        this.lidOpen = tag.getBoolean("LidOpen");
        this.playing = tag.getBoolean("Playing");
        if (tag.contains("Volume")) this.volume = tag.getInt("Volume");
        this.loop = tag.getBoolean("Loop");
        this.mute = tag.getBoolean("Mute");
        this.musicPosition = tag.getLong("RingerPosition");
        this.radioUrl = tag.getString("RadioUrl");
        this.radioSource = TagSerializable.loadSavedTag(tag.getCompound("RadioSource"), new MusicSource());
        this.radioImage = TagSerializable.loadSavedTag(tag.getCompound("RadioImage"), new ImageInfo());
        this.radioName = tag.getString("RadioName");
        this.radioAuthor = tag.getString("RadioAuthor");
        OENbtUtils.readUUIDMap(tag, "PlayerSelectPlaylists", playerSelectPlaylists);
        this.continuousType = ContinuousType.getByName(tag.getString("ContinuousType"));
        // [Sequential playback convention] Falls back to "no sorting" when the field is missing (= the default behavior, old saves unaffected).
        this.remoteSongSort = tag.contains("RemoteSongSort") ? tag.getString("RemoteSongSort") : "ADD_ORDER";
        this.remoteSongSortDescending = tag.getBoolean("RemoteSongSortDescending");
        // Keep the stored value faithful: `(loop=false, NONE)` is a legal input and must not be rewritten into ORDER here, or "what was stored" would differ from "what is read back".
        // The combination is resolved at the consumption point by {@link #getEffectiveContinuousType()}.
        // A missing AutoPlay key falls back to false, so old saves do not suddenly continue to the next track.
        this.autoPlay = tag.contains("AutoPlay") && tag.getBoolean("AutoPlay");
        this.lastMonitorType = MonitorType.getByName(tag.getString("LastMonitorType"));
        lastSelectedMusicUuid = tag.hasUUID("LastSelectedMusicUuid") ? tag.getUUID("LastSelectedMusicUuid") : null;
        if(tag.contains("earphone"))
            earphoneUUID = tag.getUUID("earphone");

        if (tag.contains("SelectedMusic"))
            this.selectedMusic = TagSerializable.loadSavedTag(tag.getCompound("SelectedMusic"), new Music());

        if (absolutely) {
            this.handleRaisedProgressOld = tag.getInt("HandleRaisedProgressOld");
            this.handleRaisedProgress = tag.getInt("HandleRaisedProgress");
            this.lidOpenProgressOld = tag.getInt("LidOpenProgressOld");
            this.lidOpenProgress = tag.getInt("LidOpenProgress");
            this.parabolicAntennaProgressOld = tag.getInt("ParabolicAntennaProgressOld");
            this.parabolicAntennaProgress = tag.getInt("ParabolicAntennaProgress");
            this.antennaProgressOld = tag.getInt("AntennaProgressOld");
            this.antennaProgress = tag.getInt("AntennaProgress");
            this.noChangeCassetteTape = tag.getBoolean("NoChangeCassetteTape");
        }

        if (absolutely || sync) {
            this.changeCassetteTape = tag.getBoolean("ChangeCassetteTape");
//            this.oldCassetteTape = ItemStack.parse(provider, tag.getCompound("OldCassetteTape")).orElse(ItemStack.EMPTY);
            this.oldCassetteTapeFlg = tag.getBoolean("OldCassetteTapeFlg");
            this.loadingMusic = tag.getBoolean("LoadingMusic");
            this.radioStartFlg = tag.getBoolean("RadioStartFlg");
        }

        if (!sync) {
            if (this.handleRaising) this.handleRaisedProgress = getHandleRaisedMax();
            if (this.lidOpen) this.lidOpenProgress = getLidOpenProgressMax();
        }
    }

    public void setNoChangeCassetteTape(boolean noChangeCassetteTape) {
        this.noChangeCassetteTape = noChangeCassetteTape;
    }

    public boolean isNoChangeCassetteTape() {
        return noChangeCassetteTape;
    }

    public void setRadioSource(MusicSource radioSource) {
        this.radioSource = radioSource;
        update();
    }

    public void setRadioImage(ImageInfo radioImage) {
        this.radioImage = radioImage;
        update();
    }

    public MusicSource getRadioSource() {
        return radioSource;
    }

    public ImageInfo getRadioImage() {
        return radioImage;
    }

    public void setRadioUrl(String radioUrl) {
        this.radioUrl = radioUrl;
        update();
    }

    public void setRadioName(String radioName) {
        this.radioName = radioName;
        update();
    }

    public void setRadioAuthor(String radioAuthor) {
        this.radioAuthor = radioAuthor;
        update();
    }

    public String getRadioName() {
        return radioName;
    }

    public String getRadioAuthor() {
        return radioAuthor;
    }

    public String getRadioUrl() {
        return radioUrl;
    }

    public boolean isLoadingMusic() {
        return loadingMusic;
    }

    public long getMusicPosition() {
        return musicPosition;
    }

    public void setMusicPosition(long position) {
        this.musicPosition = position;
        if (isMusicCassetteTapeExist() && !isRadioRemote()) {
            var m = getMusicSource();
            if (m != null) {
                var nc = CassetteTapeItem.setTapePercentage(getCassetteTape().copy(), (float) position / (float) m.getDuration());
                if (!ItemStack.matches(nc, getCassetteTape())) {
                    setCassetteTape(nc);
                }
            }
        }
        update();
    }

    public void setCassetteTape(ItemStack stack) {
        access.setCassetteTape(stack);
    }

    public MusicSource getMusicSource() {
        if (isMusicCassetteTapeExist()) {
            var m = CassetteTapeItem.getMusic(getCassetteTape());
            if (m != null) return m.getSource();
        }
        return null;
    }

    public void setMusicPositionAndRestart(long position) {
        setMusicPosition(position);
        if (getRinger() != null) {
            getRinger().ringerRestart();
            update();
        }
    }

    public float getRawVolume() {
        return (float) getVolume() / 300f;
    }

    public void setRadioMode() {
        if (isAntennaExist()) {
            if (IMPItemUtil.isRemotePlayBackAntenna(getAntenna())) {
                setMonitorType(MonitorType.REMOTE_PLAYBACK_SELECT);
            } else {
                setMonitorType(MonitorType.RADIO_SELECT);
            }
        }
        update();
    }

    public boolean isRadio() {
        return isRadioStream() || isRadioRemote();
    }

    public boolean isRadioStream() {
        return monitorType == MonitorType.RADIO_SELECT || monitorType == MonitorType.RADIO;
    }

    public boolean isRadioRemote() {
        return monitorType == MonitorType.REMOTE_PLAYBACK || monitorType == MonitorType.REMOTE_PLAYBACK_SELECT;
    }

    /** These two pages share one audio source within the "remote" set (the track selected remotely) — see the page-change-keeps-playing part in update(). */
    private static boolean isRemotePage(MonitorType type) {
        return type == MonitorType.REMOTE_PLAYBACK || type == MonitorType.REMOTE_PLAYBACK_SELECT;
    }

    /** [boomboxConfig.keepPlayingOnRemoteSwitch] Switching between the two remote pages
     * (playback page ⇄ select page) must not cut off the track being played. */
    private boolean remoteKeep() {
        return IamMusicPlayer.getConfig().boomboxConfig.keepPlayingOnRemoteSwitch
                && (isRemotePage(monitorType) || isRemotePage(lastMonitorType));
    }

    /**
     * The "remote-selected track" uuid seen by the last {@code update()}.
     */
    private UUID lastSelectedMusicUuid;

    public void setPower(boolean power) {
        access.setPower(power);
    }

    public boolean isChangeCassetteTape() {
        return changeCassetteTape;
    }

    private boolean isMusicCassetteTapeExist() {
        return isCassetteTapeExist() && CassetteTapeItem.getMusic(getCassetteTape()) != null;
    }

    private boolean isCassetteTapeExist() {
        return IMPItemUtil.isCassetteTape(getCassetteTape());
    }

    public boolean isPlaying() {
        return playing;
    }


    public void setPlaying(boolean playing) {
        // [Trace, log only] `playing` is the only basis for "whether the server sends LOAD/STOP" (`MusicRing.tick` samples `isRingerPlaying()` once per tick).
        if (this.playing != playing
                && dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("playing:" + System.identityHashCode(this)))
            dev.felnull.imp.client.music.link.LinkDebug.log("Boombox setPlaying",
                    "playing", this.playing + "->" + playing,
                    "caller", callerFrame(),
                    "thread", Thread.currentThread().getName(),
                    "monitor", monitorType, "canPlay", canPlay(),
                    "sel", getSelectedMusic() == null ? "null" : getSelectedMusic().getSource().getLoaderType() + ":" + getSelectedMusic().getSource().getIdentifier(),
                    "radioRemote", isRadioRemote(), "powered", isPowered(),
                    "autoPlay", isAutoPlay(), "continuous", getContinuousType());

        if (canPlay() || !playing) {
            this.playing = playing;
            update();
        }
    }

    /** The frame that called `setPlaying` (which method, which line). */
    private static String callerFrame() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().equals(BoomboxData.class.getName()))
                .findFirst()
                .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "#" + f.getMethodName() + ":" + f.getLineNumber())
                .orElse("?"));
    }

    public void setEarphoneUUID(UUID uuid){
        this.earphoneUUID = uuid;
        update();
    }

    public @Nullable UUID getEarphoneUUID() {
        return earphoneUUID;
    }

    public void setMonitorType(MonitorType monitorType) {
        this.monitorType = monitorType;
        update();
    }

    public MonitorType getMonitorType() {
        return monitorType;
    }

    public boolean isUseAntenna() {
        return isRadio();
    }

    public boolean isAntennaExist() {
        return IMPItemUtil.isAntenna(getAntenna());
    }

    @Nullable
    public IMusicRinger getRinger() {
        return access.getRinger();
    }

    public boolean cycleLidOpen(Level level) {
        boolean flg = lidOpenProgress >= getLidOpenProgressMax();
        boolean flg2 = lidOpenProgress <= 0;
        if (!flg && !flg2) return false;
        if (flg) {
            startLidOpen(false, level);
        }
        if (flg2) {
            startLidOpen(true, level);
        }
        return true;
    }

    public void setContinuousType(ContinuousType continuousType) {
        this.continuousType = continuousType;
        update();
    }

    /** Sorting convention used for sequential playback (mirrored from the UI), values {@code ADD_ORDER/NAME/PLAYER/CREATE_DATE}. */
    public String getRemoteSongSort() {
        return remoteSongSort;
    }

    public boolean isRemoteSongSortDescending() {
        return remoteSongSortDescending;
    }

    /** Raw field value (faithfully reflects the state: save/packet round trips reproduce it). **Use {@link #getEffectiveContinuousType()} for behavior decisions**. */
    public ContinuousType getContinuousType() {
        return continuousType;
    }

    /** Behavior decisions ({@code shouldAutoContinue()} / {@code ringerEnd()}) must use this, not the raw field. */
    public ContinuousType getEffectiveContinuousType() {
        if (!loop && continuousType == ContinuousType.NONE)
            return ContinuousType.ORDER;
        return continuousType;
    }

    public void setSelectedMusic(Music selectedMusic) {
        this.selectedMusic = selectedMusic;
        update();
    }

    public Music getSelectedMusic() {
        return selectedMusic;
    }

    /** The part jump ({@code IMPPackets.BoomboxPartJumpMessage}): jump to part {@code page} of the Bilibili video playing now via
     * {@code BiliBiliPartCache#partMusic}; needs {@code isPowered()} and {@code isRadioRemote()}; keeps the source uuid
     * (a video stays one row) and its playlist order after the last part; no restart if it is already on. */
    public boolean jumpBiliBiliPart(@NotNull ServerPlayer player, int page) {
        if (!isPowered() || !isRadioRemote())
            return false;
        var current = getSelectedMusic();
        if (current == null || current.getSource() == null)
            return false;
        var pl = MusicManager.getInstance().getPlaylistByMusic(player.server, current.getUuid());
        if (pl == null || !pl.getAuthority().getAuthorityType(player.getGameProfile().getId()).isMoreReadOnly())
            return false;
        var target = dev.felnull.imp.server.music.BiliBiliPartCache.getInstance()
                .partMusic(current, page, System.currentTimeMillis());
        if (target == null || target.getSource() == null)
            return false;
        if (target.getSource().getIdentifier().equals(current.getSource().getIdentifier()))
            return false;
        if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("partJump:" + player.getUUID()))
            dev.felnull.imp.client.music.link.LinkDebug.log("boombox part-jump",
                    "by", player.getGameProfile().getName(),
                    "from", current.getSource().getIdentifier(),
                    "to", target.getSource().getIdentifier(),
                    "name", target.getName(), "dur", target.getSource().getDuration());
        setSelectedMusic(target);
        // Restarting the ringer is mandatory here and is the one thing automatic continuation does not need: the track-change check in {@link #tick()} compares uuids,
        // and a synthesized part deliberately keeps the uuid of the entry it came from, so a part-to-part jump looks like "the same track" ⇒ no restart ⇒ the client keeps playing the old part.
        setMusicPositionAndRestart(0);
        return true;
    }

    @Nullable
    public Music getCassetteTapeMusic() {
        if (IMPItemUtil.isCassetteTape(getCassetteTape())) return CassetteTapeItem.getMusic(getCassetteTape());
        return null;
    }

    public void startLidOpen(boolean open, Level level) {
        setLidOpen(open);
        var pos = getPosition();
        level.playSound(null, pos, isLidOpen() ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    public boolean isLidOpen() {
        return lidOpen;
    }

    public void setLidOpen(boolean lidOpen) {
        this.lidOpen = lidOpen;
        update();
    }

    public void setSelectedPlayList(@NotNull ServerPlayer player, UUID selectedPlayList) {
        if (selectedPlayList != null) {
            playerSelectPlaylists.put(player.getGameProfile().getId(), selectedPlayList);
        } else {
            playerSelectPlaylists.remove(player.getGameProfile().getId());
        }
        update();
    }

    @Nullable
    public UUID getSelectedPlayList(@NotNull Player player) {
        return playerSelectPlaylists.get(player.getGameProfile().getId());
    }

    public ItemStack getCassetteTape() {
        return access.getCassetteTape();
    }

    public ItemStack getAntenna() {
        return access.getAntenna();
    }

    public int getLidOpenProgressMax() {
        return 10;
    }

    public int getHandleRaisedProgress() {
        return handleRaisedProgress;
    }

    public float getHandleRaisedProgress(float partialTicks) {
        return Mth.lerp(partialTicks, handleRaisedProgressOld, handleRaisedProgress);
    }

    public int getParabolicAntennaProgress() {
        return parabolicAntennaProgress;
    }

    public float getParabolicAntennaProgress(float partialTicks) {
        return Mth.lerp(partialTicks, parabolicAntennaProgressOld, parabolicAntennaProgress);
    }

    public int getLidOpenProgress() {
        return lidOpenProgress;
    }

    public float getLidOpenProgress(float partialTicks) {
        return Mth.lerp(partialTicks, lidOpenProgressOld, lidOpenProgress);
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean isMute() {
        return mute;
    }

    /** Auto play toggle: true = continue after the track ends, false = pause when it ends. */
    public boolean isAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
        update();
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        update();
    }

    /**
     * Three loop states (0 repeat one / 1 sequential / 2 shuffle).
     */
    public int getLoopState() {
        // state0 = repeat one (replay this track); state1 = sequential; state2 = shuffle
        if (loop)
            return 0;   // loop only means "repeat one"
        return continuousType == ContinuousType.RANDOM ? 2 : 1;
    }

    /** Sets the three states and refreshes. */
    public void setLoopState(int state) {
        int s = Mth.clamp(state, 0, 2);
        this.loop = s == 0;                 // only the "repeat one" state sets loop
        this.continuousType = switch (s) {
            case 0 -> ContinuousType.NONE;   // repeat one: loop handles the replay, no next track is picked
            case 2 -> ContinuousType.RANDOM; // shuffle
            default -> ContinuousType.ORDER; // sequential
        };
        update();
    }

    public void setMute(boolean mute) {
        this.mute = mute;
        update();
    }

    public void setVolume(int volume) {
        this.volume = volume;
        update();
    }

    public int getVolume() {
        return volume;
    }

    public int getAntennaProgress() {
        return antennaProgress;
    }

    public float getAntennaProgress(float partialTicks) {
        return Mth.lerp(partialTicks, antennaProgressOld, antennaProgress);
    }

    public boolean isHandleRaising() {
        return handleRaising;
    }

    public int getHandleRaisedMax() {
        return 10;
    }

    public boolean cycleRaisedHandle() {
        boolean flg = getHandleRaisedProgress() >= getHandleRaisedMax();
        boolean flg2 = getHandleRaisedProgress() <= 0;
        if (!flg && !flg2) return false;
        if (flg) {
            setHandleRaising(false);
        }
        if (flg2) {
            setHandleRaising(true);
        }
        return true;
    }

    public void setHandleRaising(boolean handleRaising) {
        this.handleRaising = handleRaising;
        update();
    }

    public void setHandleRaisedProgressOld(int handleRaisedProgressOld) {
        this.handleRaisedProgressOld = handleRaisedProgressOld;
    }

    public int getHandleRaisedProgressOld() {
        return handleRaisedProgressOld;
    }

    public void setHandleRaisedProgress(int handleRaisedProgress) {
        this.handleRaisedProgress = handleRaisedProgress;
    }

    public void setAntennaProgressOld(int antennaProgressOld) {
        this.antennaProgressOld = antennaProgressOld;
    }

    public boolean isPowered() {
        return access.isPowered();
    }

    public BlockPos getPosition() {
        return BlockPos.containing(access.getPosition());
    }

    public void update() {
        access.dataUpdate(this);
    }

    public static enum MonitorType {
        OFF("off"), PLAYBACK("playback"), REMOTE_PLAYBACK("remote_playback"), REMOTE_PLAYBACK_SELECT("remote_playback_select"), RADIO("radio"), RADIO_SELECT("radio_select"), EARPHONE("earphone");
        private final String name;

        private MonitorType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static MonitorType getByName(String name) {
            for (MonitorType value : values()) {
                if (value.getName().equals(name)) return value;
            }
            return MonitorType.OFF;
        }
    }

    public void setRadioStartFlg(boolean radioStartFlg) {
        this.radioStartFlg = radioStartFlg;
        update();
    }

    public static interface DataAccess {
        ItemStack getCassetteTape();

        ItemStack getAntenna();

        boolean isPowered();

        void setPower(boolean power);

        IMusicRinger getRinger();

        Vec3 getPosition();

        void setCassetteTape(ItemStack stack);

        void dataUpdate(BoomboxData data);
    }

    public Buttons getButtons() {
        return new Buttons(isRadio(), isPlaying(), !isPlaying() && getMusicPosition() > 0, isLoop(), isMute(), !isMute() && volume >= 300, getLoopState());
    }

    public static record Buttons(boolean radio, boolean start, boolean pause, boolean loop, boolean volMute,
                                 boolean volMax, int loopState) {
        public static final Buttons EMPTY = new Buttons(false, false, false, false, false, false, 0);
    }

    public static enum ButtonType {
        NONE("none", n -> false), POWER("power", n -> false), RADIO("radio", n -> n.radio()), START("start", n -> n.start()), PAUSE("pause", n -> n.pause()), STOP("stop", n -> false), LOOP("loop", n -> n.loop()), VOL_DOWN("volDown", n -> false), VOL_UP("volUp", n -> false), VOL_MUTE("volMute", n -> n.volMute()), VOL_MAX("volMax", n -> n.volMax()), EARPHONE("earphone", n -> false);
        private final String name;
        private final Component component;
        private final Function<Buttons, Boolean> getter;

        private ButtonType(String name, Function<Buttons, Boolean> getter) {
            this.name = name;
            this.component = Component.translatable("imp.button.boombox." + name);
            this.getter = getter;
        }

        public String getName() {
            return name;
        }

        public Component getComponent() {
            return component;
        }

        public boolean getState(Buttons buttons) {
            return getter.apply(buttons);
        }

        public static ButtonType getByName(String name) {
            for (ButtonType value : values()) {
                if (value.getName().equals(name)) return value;
            }
            return NONE;
        }
    }

    public static enum ContinuousType {
        NONE("none"), ORDER("order"), RANDOM("random");
        private final String name;
        private final Component component;

        private ContinuousType(String name) {
            this.name = name;
            this.component = Component.translatable("imp.text.continuous." + name);
        }

        public Component getComponent() {
            return component;
        }

        public String getName() {
            return name;
        }

        public static ContinuousType getByName(String name) {
            for (ContinuousType value : values()) {
                if (value.getName().equals(name)) return value;
            }
            return NONE;
        }
    }
}
