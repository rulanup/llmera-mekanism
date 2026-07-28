package com.example.llmera.mekanism.network;

import com.example.llmera.network.CodeNetworkMember;
import com.example.llmera.network.CodeNetworkScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class MekanismNetworkBridgeBlock extends Block implements EntityBlock, CodeNetworkMember {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public MekanismNetworkBridgeBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.5f));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction against = context.getClickedFace().getOpposite();
        if (MekanismNetworkBridgeBlockEntity.isSupportedTarget(context.getLevel().getBlockEntity(context.getClickedPos().relative(against)))) {
            return defaultBlockState().setValue(FACING, against);
        }
        for (Direction direction : Direction.values()) {
            if (MekanismNetworkBridgeBlockEntity.isSupportedTarget(context.getLevel().getBlockEntity(context.getClickedPos().relative(direction)))) {
                return defaultBlockState().setValue(FACING, direction);
            }
        }
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MekanismNetworkBridgeBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            boolean online = CodeNetworkScanner.scan(serverLevel, pos).memberHasChannel(pos);
            MekanismNetworkBridgeBlockEntity bridge = serverLevel.getBlockEntity(pos) instanceof MekanismNetworkBridgeBlockEntity be ? be : null;
            boolean connected = bridge != null && bridge.hasSupportedTarget();
            player.displayClientMessage(Component.literal("Mekanism Network Bridge: code "
                    + (online ? "1/1" : "0/1") + ", target " + (connected ? "connected" : "disconnected")
                    + ", type " + (bridge == null ? "none" : bridge.targetType())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
