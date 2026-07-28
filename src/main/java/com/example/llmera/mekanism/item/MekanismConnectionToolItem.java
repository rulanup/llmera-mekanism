package com.example.llmera.mekanism.item;

import com.example.llmera.mekanism.network.MekanismMachineConnectorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Binds a connector and adds/removes Mekanism machine targets. */
public final class MekanismConnectionToolItem extends Item {
    public static final String CONNECTOR_X = "ConnectorX";
    public static final String CONNECTOR_Y = "ConnectorY";
    public static final String CONNECTOR_Z = "ConnectorZ";
    public static final String CONNECTOR_DIMENSION = "ConnectorDimension";

    public MekanismConnectionToolItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockEntity target = level.getBlockEntity(clicked);

        if (target instanceof MekanismMachineConnectorBlockEntity connector) {
            if (!context.getPlayer().isShiftKeyDown()) {
                context.getPlayer().displayClientMessage(Component.translatable(
                        "message.llmera_mekanism.connection_tool.bind_hint"), true);
                return InteractionResult.CONSUME;
            }
            bind(stack, level, clicked);
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.llmera_mekanism.connection_tool.bound", format(clicked)), true);
            return InteractionResult.CONSUME;
        }

        if (!MekanismMachineConnectorBlockEntity.isSupportedMachine(target)) {
            return InteractionResult.PASS;
        }

        BoundConnector bound = readBound(stack);
        if (bound == null) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.llmera_mekanism.connection_tool.bind_first"), true);
            return InteractionResult.CONSUME;
        }
        if (!level.dimension().location().equals(bound.dimension())) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.llmera_mekanism.connection_tool.other_dimension"), true);
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(bound.pos()) instanceof MekanismMachineConnectorBlockEntity connector)) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.llmera_mekanism.connection_tool.missing_connector"), true);
            return InteractionResult.CONSUME;
        }

        boolean changed;
        if (context.getPlayer().isShiftKeyDown()) {
            changed = connector.removeConnection(clicked);
            context.getPlayer().displayClientMessage(Component.translatable(changed
                    ? "message.llmera_mekanism.connection_tool.removed"
                    : "message.llmera_mekanism.connection_tool.not_connected"), true);
        } else {
            changed = connector.addConnection(clicked);
            Component message = changed
                    ? Component.translatable("message.llmera_mekanism.connection_tool.added",
                            connector.connectionCount(), MekanismMachineConnectorBlockEntity.MAX_CONNECTIONS)
                    : Component.translatable(connector.hasConnection(clicked)
                            ? "message.llmera_mekanism.connection_tool.already_connected"
                            : "message.llmera_mekanism.connection_tool.full",
                            MekanismMachineConnectorBlockEntity.MAX_CONNECTIONS);
            context.getPlayer().displayClientMessage(message, true);
        }
        return InteractionResult.CONSUME;
    }

    public static boolean isBoundTo(ItemStack stack, Level level, BlockPos connectorPos) {
        BoundConnector bound = readBound(stack);
        return bound != null && level != null
                && level.dimension().location().equals(bound.dimension())
                && bound.pos().equals(connectorPos);
    }

    private static void bind(ItemStack stack, Level level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(CONNECTOR_X, pos.getX());
            tag.putInt(CONNECTOR_Y, pos.getY());
            tag.putInt(CONNECTOR_Z, pos.getZ());
            tag.putString(CONNECTOR_DIMENSION, level.dimension().location().toString());
        });
    }

    private static BoundConnector readBound(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(CONNECTOR_X) || !tag.contains(CONNECTOR_Y) || !tag.contains(CONNECTOR_Z)
                || !tag.contains(CONNECTOR_DIMENSION)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(CONNECTOR_DIMENSION));
        return dimension == null ? null : new BoundConnector(
                new BlockPos(tag.getInt(CONNECTOR_X), tag.getInt(CONNECTOR_Y), tag.getInt(CONNECTOR_Z)), dimension);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        BoundConnector bound = readBound(stack);
        if (bound == null) {
            lines.add(Component.translatable("tooltip.llmera_mekanism.connection_tool.unbound")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.llmera_mekanism.connection_tool.bound", format(bound.pos()))
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal(bound.dimension().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private record BoundConnector(BlockPos pos, ResourceLocation dimension) {
    }
}
