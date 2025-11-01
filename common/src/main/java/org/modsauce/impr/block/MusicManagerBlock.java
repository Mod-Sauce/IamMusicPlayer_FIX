package org.modsauce.impr.block;

import com.mojang.serialization.MapCodec;
import org.modsauce.impr.IamMusicPlayer;
import org.modsauce.impr.blockentity.IMPBlockEntities;
import org.modsauce.impr.blockentity.MusicManagerBlockEntity;
import org.modsauce.otyacraftenginerenewed.shape.bundle.DirectionVoxelShapesBundle;
import org.modsauce.otyacraftenginerenewed.util.OEVoxelShapeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MusicManagerBlock extends IMPBaseEntityBlock {
    public static final MapCodec<MusicManagerBlock> CODEC = simpleCodec(MusicManagerBlock::new);
    private static final DirectionVoxelShapesBundle SHAPE = OEVoxelShapeUtils.makeAllDirection(OEVoxelShapeUtils.getShapeFromResource(ResourceLocation.fromNamespaceAndPath(IamMusicPlayer.MODID, "music_manager"), MusicManagerBlock.class));

    protected MusicManagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MusicManagerBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new MusicManagerBlockEntity(blockPos, blockState);
    }

    @Override
    public VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPE.getShape(blockState.getValue(FACING));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, IMPBlockEntities.MUSIC_MANAGER.get(), MusicManagerBlockEntity::tick);
    }
}
