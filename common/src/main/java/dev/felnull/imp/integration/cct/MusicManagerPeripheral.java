package dev.felnull.imp.integration.cct;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.ObjectLuaTable;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.saveddata.MusicSaveData;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MusicManagerPeripheral implements IPeripheral {
    private IComputerAccess computerAccess;
    private final MusicManagerBlockEntity musicManagerBlockEntity;

    public MusicManagerPeripheral(MusicManagerBlockEntity musicManagerBlockEntity) {
        this.musicManagerBlockEntity = musicManagerBlockEntity;
    }

    @Override
    public @Nonnull String getType() {
        return "music_manager";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other == this || (
                other instanceof MusicManagerPeripheral musicManagerPeripheral && musicManagerPeripheral.musicManagerBlockEntity == musicManagerBlockEntity);
    }

    // lua start

    @LuaFunction(mainThread = true)
    public void setPower(boolean power){
        musicManagerBlockEntity.setPower(power);
    }

    @LuaFunction(mainThread = true)
    public List<String> getAllMusicUUID(){
        return getSavedData().getMusics().keySet().stream().map(UUID::toString).toList();
    }

    @LuaFunction(mainThread = true)
    public List<String> getAllPlayListUUID(){
        return getSavedData().getPlayLists().keySet().stream().map(UUID::toString).toList();
    }

    @LuaFunction(mainThread = true)
    public ObjectLuaTable getMusic(String uuid) throws LuaException {
        var table = new HashMap<>();
        var music = getSavedData().getMusics().get(PeripheralUtil.getUUID(uuid));
        if(music == null)
            throw new LuaException("Song not found.");
        table.put("uuid", music.getUuid());
        table.put("name", music.getName());
        table.put("author", music.getAuthor());
        table.put("duration", music.getSource().getDuration());
        table.put("loaderType", music.getSource().getLoaderType());
        table.put("musicIdentifier", music.getSource().getIdentifier());
        table.put("imageType", music.getImage().getImageType().getNmae());
        table.put("imageIdentifier", music.getImage().getIdentifier());
        table.put("owner", music.getOwner());
        table.put("createDate", music.getCreateDate());
        return new ObjectLuaTable(table);
    }

    @LuaFunction(mainThread = true)
    public ObjectLuaTable getPlayList(String uuid) throws LuaException {
        var table = new HashMap<>();
        var playList = getSavedData().getPlayLists().get(PeripheralUtil.getUUID(uuid));
        if(playList == null)
            throw new LuaException("Playlist not found.");

        table.put("uuid", playList.getUuid());
        table.put("name", playList.getName());
        table.put("imageType", playList.getImage().getImageType().getNmae());
        table.put("imageIdentifier", playList.getImage().getIdentifier());
        table.put("createDate", playList.getCreateDate());
        table.put("musicList", playList.getMusicList().stream().map(UUID::toString));
        return new ObjectLuaTable(table);
    }

    @LuaFunction(mainThread = true)
    public List<String> getMusicsFromPlayList(String uuid) throws LuaException {
        var playList = getSavedData().getPlayLists().get(PeripheralUtil.getUUID(uuid));
        if(playList == null)
            throw new LuaException("Playlist not found.");
        return playList.getMusicList().stream().map(UUID::toString).toList();
    }

    @LuaFunction(mainThread = true)
    public boolean hasMusic(String playListUUID, String musicUUID) throws LuaException {
        var playList = getSavedData().getPlayLists().get(PeripheralUtil.getUUID(playListUUID));
        if(playList == null)
            return false;
        return playList.getMusicList().contains(PeripheralUtil.getUUID(musicUUID));
    }

    // lua end

    private MusicSaveData getSavedData(){
        return MusicManager.getInstance().getSaveData(Objects.requireNonNull(musicManagerBlockEntity.getLevel()).getServer());
    }
}
