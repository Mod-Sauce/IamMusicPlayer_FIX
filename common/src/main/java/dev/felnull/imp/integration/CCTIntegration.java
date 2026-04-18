package dev.felnull.imp.integration;

import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.blockentity.CassetteDeckBlockEntity;
import dev.felnull.imp.blockentity.MusicManagerBlockEntity;
import dev.felnull.imp.explatform.IMPCCTExpectPlatform;
import dev.felnull.imp.integration.cct.BoomboxPeripheral;
import dev.felnull.imp.integration.cct.CassetteDeckPeripheral;
import dev.felnull.imp.integration.cct.MusicManagerPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.modsauce.otyacraftenginerenewed.integration.BaseIntegration;

public class CCTIntegration extends BaseIntegration {
    public static final CCTIntegration INSTANCE = new CCTIntegration();
    @Override
    public String getModId() {
        return "computercraft";
    }

    @Override
    public boolean isConfigEnabled() {
        return IamMusicPlayer.getConfig().cctIntegration;
    }

    public void init(){
        if(isEnable())
            IMPCCTExpectPlatform.init();
    }

    public static Object lookup(ServerLevel var1, BlockPos var2, BlockState var3, BlockEntity blockEntity, Direction var5){
        if(blockEntity instanceof BoomboxBlockEntity boomboxBlockEntity)
            return new BoomboxPeripheral(boomboxBlockEntity);
        if(blockEntity instanceof CassetteDeckBlockEntity cassetteDeckBlockEntity)
            return new CassetteDeckPeripheral(cassetteDeckBlockEntity);
        if(blockEntity instanceof MusicManagerBlockEntity musicManagerBlockEntity)
            return new MusicManagerPeripheral(musicManagerBlockEntity);
        return null;
    }
}
