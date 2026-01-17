package dev.felnull.imp.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.blockentity.BoomboxBlockEntity;
import dev.felnull.imp.blockentity.IMPBlockEntities;
import dev.felnull.imp.item.BoomboxItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.modsauce.otyacraftenginerenewed.shape.bundle.DirectionVoxelShapesBundle;
import org.modsauce.otyacraftenginerenewed.util.OEVoxelShapeUtils;

public class BoomboxBlock extends IMPBaseEntityBlock {
    private static final DirectionVoxelShapesBundle SHAPE = OEVoxelShapeUtils.makeAllDirection(OEVoxelShapeUtils.getShapeFromResource(ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "boombox"), BoomboxBlock.class));
    private static final DirectionVoxelShapesBundle SHAPE_NO_RAISED = OEVoxelShapeUtils.makeAllDirection(OEVoxelShapeUtils.getShapeFromResource(ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "boombox_no_raised"), BoomboxBlock.class));
    private static final MapCodec<BoomboxBlock> CODEC = RecordCodecBuilder.mapCodec(boomboxBlockInstance -> boomboxBlockInstance.group(
            propertiesCodec()
    ).apply(boomboxBlockInstance, BoomboxBlock::new));
    public static final BooleanProperty RAISED = IMPBlockStateProperties.RAISE;

    protected BoomboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(RAISED, true));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (player.isCrouching()) {
            var be = level.getBlockEntity(blockPos);
            if (be instanceof BoomboxBlockEntity boombox) {
                if (blockHitResult.getDirection() == Direction.UP) {
                    if (boombox.getBoomboxData().cycleRaisedHandle()) {
                        level.playSound(null, blockPos, boombox.getBoomboxData().isLidOpen() ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
                        return ItemInteractionResult.sidedSuccess(level.isClientSide());
                    }
                } else if (blockHitResult.getDirection() == blockState.getValue(FACING)) {
                    if (boombox.getBoomboxData().cycleLidOpen(level))
                        return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
        } else {
            return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new BoomboxBlockEntity(blockPos, blockState);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        var shp = blockState.getValue(RAISED) ? SHAPE : SHAPE_NO_RAISED;
        return shp.getShape(blockState.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RAISED);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, IMPBlockEntities.BOOMBOX.get(), BoomboxBlockEntity::tick);
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
        var be = levelReader.getBlockEntity(blockPos);
        if (be instanceof BoomboxBlockEntity boomboxBlockEntity)
            return BoomboxItem.createByBE(boomboxBlockEntity, true);
        return super.getCloneItemStack(levelReader, blockPos, blockState);
    }
}
