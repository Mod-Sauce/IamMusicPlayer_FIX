package dev.felnull.imp.create.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felnull.imp.create.IMPCreate;
import dev.felnull.imp.create.block_entity.BoomboxControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.block.IContainerEntityBlock;

public class BoomboxControllerBlock extends BaseEntityBlock implements IContainerEntityBlock, EntityBlock {
    private static final MapCodec<BoomboxControllerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec()
    ).apply(i, BoomboxControllerBlock::new));

    public BoomboxControllerBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack arg, BlockState arg2, Level arg3, BlockPos arg4, Player arg5, InteractionHand arg6, BlockHitResult arg7) {
        switch (useContainer(arg2, arg3, arg4, arg5, arg6, arg7)){
            case SUCCESS -> {
                return ItemInteractionResult.SUCCESS;
            }case CONSUME -> {
                return ItemInteractionResult.CONSUME;
            }case PASS -> {
                return super.useItemOn(arg, arg2, arg3, arg4, arg5, arg6, arg7);
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new BoomboxControllerBlockEntity(IMPCreate.BOOMBOX_CONTROLLER_BLOCK_ENTITY.get(), arg, arg2);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> entityType) {
        return createTickerHelper(entityType, IMPCreate.BOOMBOX_CONTROLLER_BLOCK_ENTITY.get(), BoomboxControllerBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState arg) {
        return RenderShape.MODEL;
    }
}
