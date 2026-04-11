package dev.felnull.imp.explatform.neoforge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.felnull.imp.block.BoomboxData;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.MutablePair;

public class IMPCreateHUDExpectPlatformImpl {
    public static BoomboxData getDataFromPlayer(Player player) {
        var c = player.getVehicle();
        if(!(c instanceof AbstractContraptionEntity ce))return null;
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair: ce.getContraption().getActors()){
            if(pair.right.state.is(IMPBlocks.BOOMBOX.get())){
                var be = new BoomboxBlockEntity(BlockPos.ZERO, pair.right.state);
                be.loadCustomOnly(pair.right.blockEntityData, player.level().registryAccess());
                return be.getBoomboxData();
            }
        }
        return null;
    }
}
