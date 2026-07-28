package com.example.llmera.mekanism;

import com.example.llmera.mekanism.network.MekanismMachineConnectorBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Exposes the connector through both standard and strict Mekanism energy APIs. */
@EventBusSubscriber(modid = LLMEraMekanism.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MekanismCapabilities {
    private MekanismCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                LLMEraMekanism.MACHINE_CONNECTOR.get(),
                (connector, side) -> connector.forgeEnergyStorage());
        event.registerBlockEntity(
                mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(),
                LLMEraMekanism.MACHINE_CONNECTOR.get(),
                (connector, side) -> connector);
    }
}
