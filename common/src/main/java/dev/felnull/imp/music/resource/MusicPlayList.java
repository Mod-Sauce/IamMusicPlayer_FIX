package dev.felnull.imp.music.resource;

import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;
import org.modsauce.otyacraftenginerenewed.util.OENbtUtils;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MusicPlayList implements TagSerializable, IIMPComparable {
    private UUID uuid;
    private String name;
    private ImageInfo image;
    private AuthorityInfo authority;
    private List<UUID> musicList = new ArrayList<>();
    private long createDate;
    /**
     * Where this playlist was imported from — empty for a non-import ({@code space:<mid>} = an UP's submissions,
     * {@code fav:<fid>} = a favourites folder); this is what the 「刷新」 key works from, and it is NBT only, so an old
     * save reads it as empty and a jar without the field ignores it.
     */
    private String source = "";

    public MusicPlayList() {

    }

    public MusicPlayList(UUID uuid, String name, ImageInfo image, AuthorityInfo authority, List<UUID> musicList, long createDate) {
        this(uuid, name, image, authority, musicList, createDate, "");
    }

    /** Full form: only the import paths know a {@link #getSource() source}, everything else uses the 6-argument one. */
    public MusicPlayList(UUID uuid, String name, ImageInfo image, AuthorityInfo authority, List<UUID> musicList, long createDate, String source) {
        this.uuid = uuid;
        this.name = name;
        this.image = image;
        this.authority = authority;
        this.musicList = musicList;
        this.createDate = createDate;
        this.source = source == null ? "" : source;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putUUID("UUID", uuid);
        tag.putString("Name", name);
        tag.put("Image", image.createSavedTag());
        tag.put("Authority", authority.createSavedTag());
        OENbtUtils.writeUUIDList(tag, "MusicList", musicList);
        tag.putLong("CreateDate", createDate);
        tag.putString("Source", source == null ? "" : source);
    }

    @Override
    public void load(CompoundTag tag) {
        this.uuid = tag.getUUID("UUID");
        this.name = tag.getString("Name");
        this.image = TagSerializable.loadSavedTag(tag.getCompound("Image"), new ImageInfo());
        this.authority = TagSerializable.loadSavedTag(tag.getCompound("Authority"), new AuthorityInfo());
        OENbtUtils.readUUIDList(tag, "MusicList", musicList);
        this.createDate = tag.getLong("CreateDate");
        // Missing key (an older save) = "no import source" — never null
        this.source = tag.contains("Source") ? tag.getString("Source") : "";
    }

    /** The import source (see the field); never {@code null}. */
    public String getSource() {
        return source == null ? "" : source;
    }

    /** Used by the import (on creation) and by the 「刷新」 claim path (see {@code MusicManager#setPlayListSource}). */
    public void setSource(String source) {
        this.source = source == null ? "" : source;
    }

    /** Cover setter for the narrow refresh write path (see {@code MusicManager#setPlayListSource}). */
    public void setImage(ImageInfo image) {
        this.image = image;
    }

    /** Name setter for the same narrow write path (a legacy "&lt;UP 名&gt; 的投稿" gets its space dropped). */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public AuthorityInfo getAuthority() {
        return authority;
    }

    public ImageInfo getImage() {
        return image;
    }

    public List<UUID> getMusicList() {
        return musicList;
    }

    public long getCreateDate() {
        return createDate;
    }

    public int getPlayerCount() {
        return (int) authority.getPlayersAuthority().values().stream().filter(AuthorityInfo.AuthorityType::isMoreReadOnly).count();
    }

    @Override
    public String toString() {
        return "MusicPlayList{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", image=" + image +
                ", authority=" + authority +
                ", musicList=" + musicList +
                ", createDate=" + createDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicPlayList that = (MusicPlayList) o;
        return createDate == that.createDate && Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name) && Objects.equals(image, that.image) && Objects.equals(authority, that.authority) && Objects.equals(musicList, that.musicList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, image, authority, musicList, createDate);
    }

    @Override
    public String getCompareName() {
        return name;
    }

    @Override
    public String getComparePlayerName() {
        return authority.getOwner().toString();
    }

    @Override
    public long getCompareDate() {
        return createDate;
    }
}
