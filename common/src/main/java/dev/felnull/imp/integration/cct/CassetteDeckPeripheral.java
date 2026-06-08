package dev.felnull.imp.integration.cct;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.felnull.imp.blockentity.CassetteDeckBlockEntity;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.saveddata.MusicSaveData;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class CassetteDeckPeripheral implements IPeripheral {
    private IComputerAccess computerAccess;
    private final CassetteDeckBlockEntity cassetteDeckBlockEntity;

    public CassetteDeckPeripheral(CassetteDeckBlockEntity cassetteDeckBlockEntity) {
        this.cassetteDeckBlockEntity = cassetteDeckBlockEntity;
    }

    @Override
    public @Nonnull String getType() {
        return "cassette_deck";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other || (
                other instanceof CassetteDeckPeripheral peripheral && peripheral.cassetteDeckBlockEntity == cassetteDeckBlockEntity
                );
    }

    // lua start

    @LuaFunction(mainThread = true)
    public void setPower(boolean power){
        cassetteDeckBlockEntity.setPower(power);
    }

    @LuaFunction(mainThread = true)
    public void setSelectMusic(String musicUUID) throws LuaException {
        var data = getSavedData().getMusics();
        if(!data.containsKey(PeripheralUtil.getUUID(musicUUID)))
            throw new LuaException("Song not found.");
        var music = data.get(PeripheralUtil.getUUID(musicUUID));
        cassetteDeckBlockEntity.setMusic(music);
    }

    @LuaFunction(mainThread = true)
    public void write() throws LuaException {
        if(!cassetteDeckBlockEntity.canWriteCassetteTape())
            throw new LuaException("No music selected or tape not found.");
        cassetteDeckBlockEntity.writeCassetteTape();
    }

    // lua end

    private MusicSaveData getSavedData(){
        return MusicManager.getInstance().getSaveData(Objects.requireNonNull(cassetteDeckBlockEntity.getLevel()).getServer());
    }
}
