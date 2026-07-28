package com.example.llmera.mekanism.client;

import com.example.llmera.mekanism.LLMEraMekanism;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = LLMEraMekanism.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MekanismClientSetup {
    private MekanismClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LLMEraMekanism.MACHINE_CONNECTOR.get(),
                MekanismMachineConnectorRenderer::new);
    }
}
