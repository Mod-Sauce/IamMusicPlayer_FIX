package dev.felnull.imp.util;

import dev.felnull.imp.music.resource.AuthorityInfo;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IMPNbtUtil {
    /**
     * vanilla 对**一个包内 NBT 标签**限 2 MiB 记账字节（{@code NbtAccounter.create(2097152L)}）；
     * 记账按对象开销算：一首歌载荷 ~400 字节却记 ~1.8 KB ⇒ 一个包约 1100 首封顶，凡「一个包发一个列表」的路径都要切批。
     */
    public static final int PACKET_NBT_LIMIT = 2097152;
    /** What one packet may hold, with a margin. */
    public static final int PACKET_NBT_BUDGET = PACKET_NBT_LIMIT - 131072;

    /** Accounted size of a music list exactly as a packet carries it (the titles' length decides, so it is measured). */
    public static int musicsSizeInBytes(String name, List<Music> musics) {
        return writeMusics(new CompoundTag(), name, musics).sizeInBytes();
    }

    /** How many songs from {@code from} fit {@link #PACKET_NBT_BUDGET}; never 0, so a chunked walk always progresses. */
    public static int fittingMusicCount(String name, List<Music> musics, int from) {
        int lo = 0, hi = musics.size() - from;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (musicsSizeInBytes(name, musics.subList(from, from + mid)) <= PACKET_NBT_BUDGET)
                lo = mid;
            else
                hi = mid - 1;
        }
        return Math.max(lo, 1);
    }

    public static CompoundTag writeAuthority(CompoundTag tag, String name, Map<UUID, AuthorityInfo.AuthorityType> authority) {
        return OENbtUtils.writeUUIDKeyMap(tag, name, authority, n -> {
            CompoundTag taaa = new CompoundTag();
            taaa.putString("n", n.getName());
            return taaa;
        });
    }

    public static void readAuthority(CompoundTag tag, String name, Map<UUID, AuthorityInfo.AuthorityType> authority) {
        OENbtUtils.readUUIDKeyMap(tag, name, authority, n -> AuthorityInfo.AuthorityType.getByName(((CompoundTag) n).getString("n")), Tag.TAG_COMPOUND);
    }

    public static CompoundTag writeMusics(CompoundTag tag, String name, List<Music> musics) {
        return OENbtUtils.writeList(tag, name, musics, n -> {
            var rettag = new CompoundTag();
            rettag.put("m", n.createSavedTag());
            return rettag;
        });
    }

    public static void readMusics(CompoundTag tag, String name, List<Music> musics) {
        OENbtUtils.readList(tag, name, musics, n -> TagSerializable.loadSavedTag(((CompoundTag) n).getCompound("m"), new Music()), Tag.TAG_COMPOUND);
    }

    public static CompoundTag writeMusicPlayLists(CompoundTag tag, String name, List<MusicPlayList> musics) {
        return OENbtUtils.writeList(tag, name, musics, n -> {
            var rettag = new CompoundTag();
            rettag.put("m", n.createSavedTag());
            return rettag;
        });
    }

    public static void readMusicPlayLists(CompoundTag tag, String name, List<MusicPlayList> musics) {
        OENbtUtils.readList(tag, name, musics, n -> TagSerializable.loadSavedTag(((CompoundTag) n).getCompound("m"), new MusicPlayList()), Tag.TAG_COMPOUND);
    }

    /**
     * The **transport stage** of server-sent names: a flat name map (UUID → display name), carried at the tail of the sync packet.
     */
    public static CompoundTag writePlayerNames(CompoundTag tag, String name, Map<UUID, String> names) {
        return OENbtUtils.writeUUIDKeyMap(tag, name, names, n -> {
            var rettag = new CompoundTag();
            rettag.putString("n", n == null ? "" : n);
            return rettag;
        });
    }

    public static void readPlayerNames(CompoundTag tag, String name, Map<UUID, String> names) {
        OENbtUtils.readUUIDKeyMap(tag, name, names, n -> {
            var n2 = ((CompoundTag) n).getString("n");
            return n2.isEmpty() ? null : n2;
        }, Tag.TAG_COMPOUND);
    }
}
