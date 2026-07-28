package com.example.llmera.mekanism.jade;

import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlock;
import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlockEntity;
import com.example.llmera.network.CodeNetworkScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

public enum MekanismNetworkStatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("llmera_mekanism", "network_status");
    private static final String DATA_KEY = ID.toString();

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlockEntity() instanceof MekanismNetworkBridgeBlockEntity bridge)) {
            return;
        }

        CodeNetworkScanner.NetworkView view = CodeNetworkScanner.scan(level, accessor.getPosition());
        BlockPos targetPos = bridge.targetPos();
        boolean codeAssigned = view.memberHasChannel(accessor.getPosition());
        boolean targetConnected = bridge.hasSupportedTarget();
        CompoundTag status = new CompoundTag();
        status.putBoolean("CodeControllerPresent", view.controller() != null);
        status.putBoolean("CodePowered", view.powered());
        status.putInt("CodeChannelAssigned", codeAssigned
                ? MekanismNetworkBridgeBlockEntity.CHANNEL_DEMAND : 0);
        status.putInt("CodeChannelTotal", MekanismNetworkBridgeBlockEntity.CHANNEL_DEMAND);
        status.putBoolean("TargetConnected", targetConnected);
        status.putBoolean("Operational", view.powered() && codeAssigned && targetConnected);
        status.putString("TargetType", bridge.targetType());
        status.putString("Facing", bridge.getBlockState().getValue(MekanismNetworkBridgeBlock.FACING).getName());
        status.putLong("TargetPos", targetPos.asLong());
        status.putString("TargetBlockId", BuiltInRegistries.BLOCK.getKey(level.getBlockState(targetPos).getBlock()).toString());
        status.putString("TargetNameKey", level.getBlockState(targetPos).getBlock().getDescriptionId());
        status.putBoolean("ScanTruncated", view.truncated());
        data.put(DATA_KEY, status);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(DATA_KEY)) {
            return;
        }
        CompoundTag status = data.getCompound(DATA_KEY);

        IThemeHelper theme = IThemeHelper.get();
        Component codeState;
        if (!status.getBoolean("CodeControllerPresent")) {
            codeState = theme.danger(Component.translatable("tooltip.llmera_mekanism.jade.no_controller"));
        } else if (!status.getBoolean("CodePowered")) {
            codeState = theme.danger(Component.translatable("tooltip.llmera_mekanism.jade.no_power"));
        } else if (status.getInt("CodeChannelAssigned") == 0) {
            codeState = theme.danger(Component.translatable("tooltip.llmera_mekanism.jade.no_channel"));
        } else {
            codeState = theme.success(Component.translatable("tooltip.llmera_mekanism.jade.online"));
        }
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.code_network", codeState));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.code_channel",
                status.getInt("CodeChannelAssigned") > 0
                        ? theme.success(status.getInt("CodeChannelAssigned"))
                        : theme.danger(0),
                theme.info(status.getInt("CodeChannelTotal"))));

        Component connection = status.getBoolean("TargetConnected")
                ? theme.success(Component.translatable("tooltip.llmera_mekanism.jade.connected"))
                : theme.danger(Component.translatable("tooltip.llmera_mekanism.jade.disconnected"));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.target", connection));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.target_type",
                theme.info(Component.translatable("tooltip.llmera_mekanism.jade.target_type."
                        + status.getString("TargetType")))));
        if (status.getBoolean("TargetConnected") && status.contains("TargetNameKey")) {
            tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.target_block",
                    theme.info(Component.translatable(status.getString("TargetNameKey"))),
                    theme.info(status.getString("TargetBlockId"))));
        }
        BlockPos targetPos = BlockPos.of(status.getLong("TargetPos"));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.target_pos",
                theme.info(targetPos.getX()), theme.info(targetPos.getY()), theme.info(targetPos.getZ())));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.direction",
                theme.info(Component.translatable("direction.minecraft." + status.getString("Facing")))));

        Component operational = status.getBoolean("Operational")
                ? theme.success(Component.translatable("tooltip.llmera_mekanism.jade.ready"))
                : theme.danger(Component.translatable("tooltip.llmera_mekanism.jade.not_ready"));
        tooltip.add(Component.translatable("tooltip.llmera_mekanism.jade.bridge", operational));
        if (status.getBoolean("ScanTruncated")) {
            tooltip.add(theme.warning(Component.translatable("tooltip.llmera_mekanism.jade.scan_truncated")));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ID;
    }
}
