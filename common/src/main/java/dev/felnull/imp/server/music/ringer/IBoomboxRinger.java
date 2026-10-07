package dev.felnull.imp.server.music.ringer;

import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.music.resource.MusicSource;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.saveddata.EarphoneSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public interface IBoomboxRinger extends IMusicRinger {
    Random rand = new Random();

    @NotNull
    public BoomboxData getRingerBoomboxData();

    @Override
    default public boolean isRingerPlaying() {
        return getRingerBoomboxData().isPlaying();
    }

    @Override
    default public void setRingerPlaying(boolean playing) {
        getRingerBoomboxData().setPlaying(playing);
    }

    @Override
    default public @Nullable MusicSource getRingerMusicSource() {
        if (isRingerStream())
            return getRingerBoomboxData().getRadioSource();
        if (getRingerBoomboxData().isRadioRemote()) {
            var m = getRingerBoomboxData().getSelectedMusic();
            if (m != null)
                return m.getSource();
        } else {
            return getRingerBoomboxData().getMusicSource();
        }
        return null;
    }

    @Override
    default public boolean isRingerLoop() {
        var data = getRingerBoomboxData();
        // [Stop after this] Auto play off pauses at the track end in **every** state, repeat one included —
        // `isLoop()` alone must not decide here, or "pause when it ends" would keep replaying the same track.
        // With auto play on, "keep playing" = repeat one (loop) or sequential/shuffle (ringerEnd picks the next track).
        return data.isAutoPlay() && (data.isLoop() || shouldAutoContinue());
    }

    /** Sequential/shuffle states: the next track follows automatically (the actual picking happens in {@link #ringerEnd()}). */
    default public boolean shouldAutoContinue() {
        var data = getRingerBoomboxData();
        // Use the effective value: the field may hold the `(loop=false, NONE)` combination, which is outside the three states.
        return data.isRadioRemote() && data.isAutoPlay()
                && data.getEffectiveContinuousType() != BoomboxData.ContinuousType.NONE;
    }

    /**
     * Comparator for sequential playback; field and direction must match {@code SortButton.SortType} exactly,
     * or "sequential" would not match the order shown.
     */
    static java.util.Comparator<Music> musicComparator(String sortName, boolean descending) {
        java.util.Comparator<Music> c = switch (sortName == null ? "ADD_ORDER" : sortName) {
            case "NAME" -> java.util.Comparator.comparing(Music::getCompareName);
            case "PLAYER" -> java.util.Comparator.comparing(Music::getComparePlayerName);
            case "CREATE_DATE" -> java.util.Comparator.comparingLong(Music::getCompareDate);
            case "PUBLISH_DATE" -> java.util.Comparator.comparingLong(IBoomboxRinger::publishDate);
            default -> null;
        };
        return c == null ? null : (descending ? c.reversed() : c);
    }

    /** 曲子的 B站 投稿时间，靠客户端上报（没报过 = 0：升序排最前、降序排最后）。 */
    static long publishDate(Music music) {
        var source = music.getSource();
        return source == null ? 0L
                : dev.felnull.imp.server.music.BiliBiliPubdateCache.getInstance().pubdateOf(source.getIdentifier());
    }

    @Override
    default public long getRingerPosition() {
        return getRingerBoomboxData().getMusicPosition();
    }

    @Override
    default public void setRingerPosition(long position) {
        getRingerBoomboxData().setMusicPosition(position);
    }

    @Override
    default public float getRingerVolume() {
        if (isRingerMute())
            return 0;
        return getRingerBoomboxData().getRawVolume();
    }

    @Override
    default public float getRingerRange() {
        if (isRingerMute())
            return 0;

        return 90f * getRingerBoomboxData().getRawVolume();
    }

    @Override
    default public boolean isRingerStream() {
        return getRingerBoomboxData().isRadioStream();
    }

    @Override
    default public void ringerEnd() {
        // [Stop after this] With "auto play" off the next track must not even be picked, and it has to be blocked here:
        // `MusicRing` calls `ringerEnd()` unconditionally first and only then asks `isRingerLoop()`, so returning false there would stop it after `setSelectedMusic(next)` already ran.
        if (!shouldAutoContinue())
            return;

        if (getRingerBoomboxData().isRadioRemote()) {
            var con = getRingerBoomboxData().getEffectiveContinuousType();
            if (con != BoomboxData.ContinuousType.NONE) {
                var m = getRingerBoomboxData().getSelectedMusic();
                if (m == null) return;

                var mm = MusicManager.getInstance();
                var pl = mm.getPlaylistByMusic(getRingerLevel().getServer(), m.getUuid());
                if (pl == null) return;

                // [Multi-P counts as one] A B站 video is one playlist entry and its parts are not rows, so when the current part has a successor, play that instead of moving on.
                // Two guards: only in the sequential state, and only when that part is not already a playlist row — playlists built by the previous version (parts appended as rows) must not play every part twice.
                if (con == BoomboxData.ContinuousType.ORDER) {
                    var part = dev.felnull.imp.server.music.BiliBiliPartCache.getInstance()
                            .nextPartMusic(m, System.currentTimeMillis());
                    if (part != null && !hasMusicWithIdentifier(getRingerLevel().getServer(), mm, pl, part.getSource().getIdentifier())) {
                        if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("ringerEndPart:" + m.getUuid()))
                            dev.felnull.imp.client.music.link.LinkDebug.log("ringerEnd next-part",
                                    "cur", m.getSource() == null ? "?" : m.getSource().getIdentifier(),
                                    "next", part.getSource().getIdentifier(), "name", part.getName());
                        getRingerBoomboxData().setSelectedMusic(part);
                        return;
                    }
                }

                var ml = new ArrayList<>(pl.getMusicList());
                if (ml.isEmpty() || ml.size() == 1) return;

                // [Sequential playback convention] Sort by the sorting the UI uses, otherwise sequential would not match the UI.
                var boomboxData = getRingerBoomboxData();
                var orderCmp = musicComparator(boomboxData.getRemoteSongSort(), boomboxData.isRemoteSongSortDescending());
                if (orderCmp != null) {
                    var allMusics = mm.getSaveData(getRingerLevel().getServer()).getMusics();
                    ml.sort((a, b) -> {
                        var ma = allMusics.get(a);
                        var mb = allMusics.get(b);
                        return (ma == null || mb == null) ? 0 : orderCmp.compare(ma, mb);
                    });
                }

                Music nm = null;
                if (con == BoomboxData.ContinuousType.ORDER) {
                    var nmi = ml.get((ml.indexOf(m.getUuid()) + 1) % ml.size());
                    nm = mm.getSaveData(getRingerLevel().getServer()).getMusics().get(nmi);
                    if (dev.felnull.imp.client.music.link.LinkDebug.shouldLogThrottled("ringerEnd:" + m.getUuid()))
                        dev.felnull.imp.client.music.link.LinkDebug.log("ringerEnd pick-next",
                                "playlist", pl.getUuid() + "/" + pl.getName(), "size", ml.size(),
                                "indexOfCur", ml.indexOf(m.getUuid()), "cur", m.getUuid(),
                                "next", nmi, "nextName", nm == null ? "?" : nm.getName());
                } else if (con == BoomboxData.ContinuousType.RANDOM) {
                    ml.remove(m.getUuid());
                    var nmi = ml.get(rand.nextInt(ml.size()));
                    nm = mm.getSaveData(getRingerLevel().getServer()).getMusics().get(nmi);
                }
                if (nm != null)
                    getRingerBoomboxData().setSelectedMusic(nm);
            }
        }
    }

    @Override
    default boolean isRingerMute() {
        return getRingerBoomboxData().isMute();
    }

    @Override
    default boolean isRingerRemote() {
        return getRingerBoomboxData().isRadioRemote();
    }

    @Override
    @Nullable
    default String getRingerMusicAuthor() {
        var data = getRingerBoomboxData();
        if (data.isRadioRemote() && data.getSelectedMusic() != null)
            return data.getSelectedMusic().getAuthor();
        if (data.getCassetteTapeMusic() != null)
            return data.getCassetteTapeMusic().getAuthor();
        return null;
    }

    @Override
    default @NotNull ItemStack getRingerAntenna() {
        return getRingerBoomboxData().getAntenna();
    }

    @Override
    default boolean canListen(ServerPlayer player) {
        var boomboxData = getRingerBoomboxData();
        if(boomboxData.getEarphoneUUID() == null)return true;
        var data = EarphoneSaveData.getInstance(player.serverLevel());
        var location = data.get(player);
        if(location == null)return false;
        return Objects.equals(boomboxData.getEarphoneUUID(), location.earphoneUUID()) &&
                earphoneOnHead(player, location);
    }

    private boolean earphoneOnHead(ServerPlayer player, EarphoneSaveData.EarphoneLocation location){
        var item = EarphoneSaveData.findEarphone(player.serverLevel(), location);
        if(item == null)return false;
        var head = player.getItemBySlot(EquipmentSlot.HEAD);
        return ItemStack.isSameItemSameComponents(item, head);
    }
    /**
     * Whether a playlist already contains a song with this B站 identifier, used by the multi-P rule in
     * {@link #ringerEnd()}; the identifier is the only stable key — a synthesized part carries its entry's uuid.
     */
    private static boolean hasMusicWithIdentifier(MinecraftServer server, MusicManager mm, MusicPlayList pl, String identifier) {
        if (identifier == null || identifier.isBlank())
            return false;
        var all = mm.getSaveData(server).getMusics();
        for (var id : pl.getMusicList()) {
            var m = all.get(id);
            if (m != null && m.getSource() != null && identifier.equals(m.getSource().getIdentifier()))
                return true;
        }
        return false;
    }
}
