package dev.felnull.imp.create.pointType;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.block.IMPBlocks;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class IMPPointTypes {
    static {
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE, ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "boombox"),
                new BoomboxType());
    }

    public static class BoomboxType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos blockPos, BlockState blockState) {
            return blockState.is(IMPBlocks.BOOMBOX);
        }

        @Override
        public @Nullable ArmInteractionPoint createPoint(Level level, BlockPos blockPos, BlockState blockState) {
            return new BoomboxArmInteractionPoint(this, level, blockPos, blockState);
        }

        public static class BoomboxArmInteractionPoint extends ArmInteractionPoint{
            public BoomboxArmInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
                super(type, level, pos, state);
            }

            @Override
            public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
                if(!(level.getBlockEntity(pos) instanceof BoomboxBlockEntity be))return stack;
                if(!be.canPlaceItem(0, stack))return stack;
                if(simulate)return ItemStack.EMPTY;

                be.setChanged();
                be.setItem(0, stack);
                if(!stack.isEmpty()){
                    be.getBoomboxData().setMusicPosition(0);
                    be.getBoomboxData().setPlaying(true);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
                if(!(level.getBlockEntity(pos) instanceof BoomboxBlockEntity be))return ItemStack.EMPTY;
                if(slot != 0)return ItemStack.EMPTY;
                if(be.getItem(0).isEmpty())return ItemStack.EMPTY;
                if(simulate)return be.getItem(0);
                be.setChanged();
                return be.removeItem(0, amount);
            }

            @Override
            public int getSlotCount(ArmBlockEntity armBlockEntity) {
                return 1;
            }
        }
    }

    public static void init(){}
}
