package dev.felnull.imp.create.behaviour;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.create.network.OpenBoomboxMenuPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class BoomboxMovingInteractionBehaviour extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos, AbstractContraptionEntity contraptionEntity) {
        var actorAt = contraptionEntity.getContraption().getActorAt(localPos);
        if(actorAt == null)return false;
        var content = actorAt.getRight();
        if(player.level().isClientSide && content.state.is(IMPBlocks.BOOMBOX.get())){
            NetworkManager.sendToServer(new OpenBoomboxMenuPacket(contraptionEntity.getId(), localPos));
            return true;
        }
        return false;
    }
}
