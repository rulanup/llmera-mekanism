package com.example.llmera.mekanism.client;

import com.example.llmera.mekanism.item.MekanismConnectionToolItem;
import com.example.llmera.mekanism.network.MekanismMachineConnectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Draws the connector's target graph while its connection tool is held. */
public final class MekanismMachineConnectorRenderer
        implements BlockEntityRenderer<MekanismMachineConnectorBlockEntity> {
    public MekanismMachineConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MekanismMachineConnectorBlockEntity connector, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || connector.getLevel() == null
                || !isHoldingBoundTool(minecraft, connector)) {
            return;
        }

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        int red = connector.isPowered() ? 70 : 240;
        int green = connector.isPowered() ? 220 : 80;
        int blue = connector.isPowered() ? 255 : 40;
        for (BlockPos target : connector.getConnections()) {
            float x = target.getX() - connector.getBlockPos().getX() + 0.5f;
            float y = target.getY() - connector.getBlockPos().getY() + 0.5f;
            float z = target.getZ() - connector.getBlockPos().getZ() + 0.5f;
            lines.addVertex(matrix, 0.5f, 0.5f, 0.5f)
                    .setColor(red, green, blue, 220)
                    .setNormal(0, 1, 0);
            lines.addVertex(matrix, x, y, z)
                    .setColor(red, green, blue, 220)
                    .setNormal(0, 1, 0);
        }
    }

    @Override
    public AABB getRenderBoundingBox(MekanismMachineConnectorBlockEntity connector) {
        BlockPos origin = connector.getBlockPos();
        int minX = origin.getX();
        int minY = origin.getY();
        int minZ = origin.getZ();
        int maxX = origin.getX();
        int maxY = origin.getY();
        int maxZ = origin.getZ();
        for (BlockPos target : connector.getConnections()) {
            minX = Math.min(minX, target.getX());
            minY = Math.min(minY, target.getY());
            minZ = Math.min(minZ, target.getZ());
            maxX = Math.max(maxX, target.getX());
            maxY = Math.max(maxY, target.getY());
            maxZ = Math.max(maxZ, target.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1);
    }

    @Override
    public boolean shouldRenderOffScreen(MekanismMachineConnectorBlockEntity connector) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static boolean isHoldingBoundTool(Minecraft minecraft,
                                               MekanismMachineConnectorBlockEntity connector) {
        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack off = minecraft.player.getOffhandItem();
        return MekanismConnectionToolItem.isBoundTo(main, connector.getLevel(), connector.getBlockPos())
                || MekanismConnectionToolItem.isBoundTo(off, connector.getLevel(), connector.getBlockPos());
    }
}
