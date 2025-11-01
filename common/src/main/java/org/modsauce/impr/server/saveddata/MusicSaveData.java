package org.modsauce.impr.server.saveddata;

import com.google.common.collect.Lists;
import org.modsauce.impr.music.resource.Music;
import org.modsauce.impr.music.resource.MusicPlayList;
import org.modsauce.impr.server.handler.ServerMessageHandler;
import org.modsauce.impr.util.IMPNbtUtil;
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
        return OESaveDataUtils.getSaveData(server, "imp_music_data", MusicSaveData::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        IMPNbtUtil.writeMusicPlayLists(tag, "PlayLists", Lists.newArrayList(playLists.values()));
        IMPNbtUtil.writeMusics(tag, "Musics", Lists.newArrayList(musics.values()));
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        playLists.clear();
        List<MusicPlayList> pls = new ArrayList<>();
        IMPNbtUtil.readMusicPlayLists(tag, "PlayLists", pls);
        pls.forEach(pl -> playLists.put(pl.getUuid(), pl));

        musics.clear();
        List<Music> ms = new ArrayList<>();
        IMPNbtUtil.readMusics(tag, "Musics", ms);
        ms.forEach(m -> musics.put(m.getUuid(), m));
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
