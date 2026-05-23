package dev.felnull.imp.create.client.screen;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.client.gui.screen.BoomboxScreen;
import dev.felnull.imp.create.behaviour.BoomboxMovementBehaviour;
import dev.felnull.imp.create.client.menu.MovingBoomboxMenu;
import dev.felnull.imp.create.network.MovingBoomboxInstructionMessage;
import dev.felnull.imp.inventory.BoomboxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MovingBoomboxScreen extends BoomboxScreen  {
    public MovingBoomboxScreen(BoomboxMenu abstractContainerMenu, Inventory inventory, Component component) {
        super(abstractContainerMenu, inventory, component);
    }

    @Override
    public BlockEntity getBlockEntity() {
        var level = Minecraft.getInstance().level;
        if(level == null)return null;
        var entity = level.getEntity(((MovingBoomboxMenu)menu).entityID);
        if(!(entity instanceof AbstractContraptionEntity contraptionEntity))return null;
        var actor = contraptionEntity.getContraption().getActorAt(getBlockPos());
        if(actor == null)return null;
        var content = actor.getRight();
        var be = new BoomboxBlockEntity(getBlockPos(), content.state){
            @Override
            public void setChanged() {
                BoomboxMovementBehaviour.saveData(content, getBlockState(),
                        saveWithoutMetadata(content.world.registryAccess()));
            }

            @Override
            public void updateLyric() {

            }
        };
        be.loadCustomOnly(content.blockEntityData, level.registryAccess());
        return be;
    }

    @Override
    public void instruction(String name, CompoundTag data) {
        NetworkManager.sendToServer(new MovingBoomboxInstructionMessage(
                getInstructionID(), getBlockPos(), ((MovingBoomboxMenu)menu).entityID, name, data
        ));
    }
}
