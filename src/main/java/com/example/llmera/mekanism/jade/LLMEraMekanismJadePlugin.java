package com.example.llmera.mekanism.jade;

import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlock;
import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("mekanism")
public final class LLMEraMekanismJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MekanismNetworkStatusProvider.INSTANCE,
                MekanismNetworkBridgeBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MekanismNetworkStatusProvider.INSTANCE,
                MekanismNetworkBridgeBlock.class);
    }
}
