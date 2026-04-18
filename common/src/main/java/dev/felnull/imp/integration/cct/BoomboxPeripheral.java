package dev.felnull.imp.integration.cct;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.server.music.MusicManager;
import dev.felnull.imp.server.saveddata.MusicSaveData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class BoomboxPeripheral implements IPeripheral {
    private IComputerAccess computerAccess;
    private final BoomboxBlockEntity boomboxBlockEntity;

    public BoomboxPeripheral(BoomboxBlockEntity boomboxBlockEntity) {
        this.boomboxBlockEntity = boomboxBlockEntity;
    }

    @Override
    public @Nonnull String getType() {
        return "boombox";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other == this || (
                other instanceof BoomboxPeripheral boomboxPeripheral && boomboxPeripheral.boomboxBlockEntity == boomboxBlockEntity
                );
    }

    @Override
    public void attach(@Nonnull IComputerAccess computer) {
        IPeripheral.super.attach(computer);
        computerAccess = computer;
    }

    @Override
    public void detach(@Nonnull IComputerAccess computer) {
        IPeripheral.super.detach(computer);
        if(computerAccess == computer)
            computerAccess = null;
    }

    // lua start

    @LuaFunction(mainThread = true)
    public void pushItemsTo(String toName, int fromSlot, Optional<Integer> limit, Optional<Integer> toSlot) throws LuaException {
        PeripheralUtil.transferExact(boomboxBlockEntity, PeripheralUtil.findContainer(computerAccess, toName), fromSlot, toSlot.orElse(null), limit.orElse(null));
    }

    @LuaFunction(mainThread = true)
    public void pullItemsFrom(String fromName, int fromSlot, Optional<Integer> limit, Optional<Integer> toSlot) throws LuaException {
        PeripheralUtil.transferExact(PeripheralUtil.findContainer(computerAccess, fromName), boomboxBlockEntity, fromSlot, toSlot.orElse(null), limit.orElse(null));
    }

    @LuaFunction(mainThread = true)
    public void setPower(boolean power){
        boomboxBlockEntity.setPower(power);
    }

    @LuaFunction(mainThread = true)
    public void setPlaying(boolean playing) throws LuaException {
        if(playing && !boomboxBlockEntity.getBoomboxData().canPlay())
            throw new LuaException("Currently not playable");
        boomboxBlockEntity.getBoomboxData().setPlaying(playing);
    }

    @LuaFunction(mainThread = true)
    public boolean isPlaying(){
        return boomboxBlockEntity.isPlaying();
    }

    @LuaFunction(mainThread = true)
    public void startMusic() throws LuaException {
        setPlaying(true);
    }

    @LuaFunction(mainThread = true)
    public void stopMusic() throws LuaException {
        setPlaying(false);
        boomboxBlockEntity.getBoomboxData().setMusicPosition(0);
    }

    @LuaFunction(mainThread = true)
    public void pause() throws LuaException {
        setPlaying(false);
    }

    @LuaFunction(mainThread = true)
    public void setMusicPosition(long position){
        boomboxBlockEntity.getBoomboxData().setMusicPositionAndRestart(position);
    }

    @LuaFunction(mainThread = true)
    public void setVolume(int volume){
        boomboxBlockEntity.getBoomboxData().setVolume(volume);
    }

    @LuaFunction(mainThread = true)
    public void setMute(boolean mute){
        boomboxBlockEntity.getBoomboxData().setMute(mute);
    }

    @LuaFunction(mainThread = true)
    public void setLoop(boolean loop){
        boomboxBlockEntity.getBoomboxData().setLoop(loop);
    }

    @LuaFunction(mainThread = true)
    public void setContinuousType(String type){
        boomboxBlockEntity.getBoomboxData().setContinuousType(BoomboxData.ContinuousType.getByName(type));
    }

    @LuaFunction(mainThread = true)
    public void setSelectMusic(String musicUUID) throws LuaException {
        var data = getSavedData().getMusics();
        if(!data.containsKey(PeripheralUtil.getUUID(musicUUID)))
            throw new LuaException("Song not found.");
        var music = data.get(PeripheralUtil.getUUID(musicUUID));
        boomboxBlockEntity.getBoomboxData().setSelectedMusic(music);
        boomboxBlockEntity.getBoomboxData().setMonitorType(BoomboxData.MonitorType.REMOTE_PLAYBACK);
        stopMusic();
    }

    @LuaFunction(mainThread = true)
    public void clearSelectMusic(){
        boomboxBlockEntity.getBoomboxData().setSelectedMusic(null);
    }

    // lua end

    private MusicSaveData getSavedData(){
        return MusicManager.getInstance().getSaveData(Objects.requireNonNull(boomboxBlockEntity.getLevel()).getServer());
    }
}
