package com.example.llmera.mekanism.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import com.example.llmera.network.CodeNetworkMember;
import com.example.llmera.mekanism.LLMEraMekanism;

/** A powered endpoint that can keep up to sixteen Mekanism machines linked. */
public final class MekanismMachineConnectorBlock extends Block implements EntityBlock, CodeNetworkMember {
    public MekanismMachineConnectorBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.5f));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MekanismMachineConnectorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide || type != LLMEraMekanism.MACHINE_CONNECTOR.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) ->
                MekanismMachineConnectorBlockEntity.serverTick(
                        tickLevel, tickPos, tickState, (MekanismMachineConnectorBlockEntity) blockEntity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MekanismMachineConnectorBlockEntity connector) {
            player.displayClientMessage(connector.statusMessage(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
