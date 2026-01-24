package dev.felnull.imp.server.saveddata;

import com.google.common.collect.Lists;
import dev.felnull.imp.music.resource.Music;
import dev.felnull.imp.music.resource.MusicPlayList;
import dev.felnull.imp.server.handler.ServerMessageHandler;
import dev.felnull.imp.util.IMPNbtUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
import org.modsauce.otyacraftenginerenewed.server.level.saveddata.OEBaseSavedData;
import org.modsauce.otyacraftenginerenewed.server.util.OESaveDataUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.*;

public class MusicSaveData extends OEBaseSavedData {
    private final Map<UUID, MusicPlayList> playLists = new HashMap<>();
    private final Map<UUID, Music> musics = new HashMap<>();

    public MusicSaveData() {
        setDirty();
    }

    public static MusicSaveData get(MinecraftServer server) {
        return OESaveDataUtils.getSaveData(server, "imp_music_data", factory());
    }

    public static Factory<MusicSaveData> factory(){
        return createFactory(MusicSaveData::new, MusicSaveData::load, DataFixTypes.LEVEL);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        IMPNbtUtil.writeMusicPlayLists(tag, "PlayLists", Lists.newArrayList(playLists.values()));
        IMPNbtUtil.writeMusics(tag, "Musics", Lists.newArrayList(musics.values()));
        return tag;
    }

    public static MusicSaveData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new MusicSaveData();
        data.playLists.clear();
        List<MusicPlayList> pls = new ArrayList<>();
        IMPNbtUtil.readMusicPlayLists(tag, "PlayLists", pls);
        pls.forEach(pl -> data.playLists.put(pl.getUuid(), pl));

        data.musics.clear();
        List<Music> ms = new ArrayList<>();
        IMPNbtUtil.readMusics(tag, "Musics", ms);
        ms.forEach(m -> data.musics.put(m.getUuid(), m));
        return data;
    }

    public Map<UUID, Music> getMusics() {
        return musics;
    }

    public Map<UUID, MusicPlayList> getPlayLists() {
        return playLists;
    }

    @Override
    public void setDirty() {
        super.setDirty();
        ServerMessageHandler.onMusicDataUpdate();
    }
}
