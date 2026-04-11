package dev.felnull.imp.create.container;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.felnull.imp.create.ringer.MovingBoomboxRinger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class MovingBoomboxContainer extends SimpleContainer {
    private final AbstractContraptionEntity entity;
    private final BlockPos pos;
    public MovingBoomboxContainer(int count, AbstractContraptionEntity entity, BlockPos localPos){
        super(count);
        this.entity = entity;
        this.pos = localPos;
    }

    @Override
    public @NotNull ItemStack getItem(int i) {
        var be = MovingBoomboxRinger.getBoomboxEntity(pos, entity);
        if(be == null)return ItemStack.EMPTY;
        return be.getItem(i);
    }

    @Override
    public void setItem(int i, ItemStack arg) {
        super.setItem(i, arg);
        var be = MovingBoomboxRinger.getBoomboxEntity(pos, entity);
        if(be == null)return;
        be.setItem(i, arg);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        var be = MovingBoomboxRinger.getBoomboxEntity(pos, entity);
        if(be == null)return;
        be.setChanged();
    }

    public static SimpleContainer create(int count, Level level, BlockPos localPos, int entityID){
        if(level.getEntity(entityID) instanceof AbstractContraptionEntity abstractContraptionEntity)
            return new MovingBoomboxContainer(count, abstractContraptionEntity, localPos);
        return new SimpleContainer(count);
    }
}
