package com.example.llmera.mekanism.network;

import com.example.llmera.mekanism.LLMEraMekanism;
import com.example.llmera.network.CodeNetworkMember;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MekanismNetworkBridgeBlockEntity extends BlockEntity implements CodeNetworkMember {
    public static final int CHANNEL_DEMAND = 1;

    public MekanismNetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(LLMEraMekanism.NETWORK_BRIDGE.get(), pos, state);
    }

    public BlockPos targetPos() {
        return worldPosition.relative(getBlockState().getValue(MekanismNetworkBridgeBlock.FACING));
    }

    public BlockEntity target() {
        return level == null ? null : level.getBlockEntity(targetPos());
    }

    public boolean hasSupportedTarget() {
        return isSupportedTarget(target());
    }

    public static boolean isSupportedTarget(BlockEntity target) {
        return target instanceof TileEntityMekanism || target instanceof TileEntityLogisticalTransporterBase;
    }

    public String targetType() {
        BlockEntity target = target();
        if (target instanceof TileEntityLogisticalTransporterBase) {
            return "logistical_transporter";
        }
        if (target instanceof TileEntityMekanism) {
            return "machine";
        }
        return "none";
    }

    @Override
    public int channelDemand() {
        return CHANNEL_DEMAND;
    }
}
