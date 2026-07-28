package com.example.llmera.mekanism;

import com.example.llmera.llm.ToolExtensionRegistry;
import com.example.llmera.mekanism.llm.MekanismToolExtension;
import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlock;
import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlockEntity;
import com.example.llmera.mekanism.network.MekanismMachineConnectorBlock;
import com.example.llmera.mekanism.network.MekanismMachineConnectorBlockEntity;
import com.example.llmera.mekanism.item.MekanismConnectionToolItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LLMEraMekanism.MODID)
public final class LLMEraMekanism {
    public static final String MODID = "llmera_mekanism";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<MekanismNetworkBridgeBlock> NETWORK_BRIDGE_BLOCK =
            BLOCKS.register("mekanism_network_bridge", MekanismNetworkBridgeBlock::new);
    public static final DeferredItem<BlockItem> NETWORK_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem("mekanism_network_bridge", NETWORK_BRIDGE_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MekanismNetworkBridgeBlockEntity>> NETWORK_BRIDGE =
            BLOCK_ENTITIES.register("mekanism_network_bridge", () -> BlockEntityType.Builder.of(
                    MekanismNetworkBridgeBlockEntity::new, NETWORK_BRIDGE_BLOCK.get()).build(null));
    public static final DeferredBlock<MekanismMachineConnectorBlock> MACHINE_CONNECTOR_BLOCK =
            BLOCKS.register("mekanism_machine_connector", MekanismMachineConnectorBlock::new);
    public static final DeferredItem<BlockItem> MACHINE_CONNECTOR_ITEM =
            ITEMS.registerSimpleBlockItem("mekanism_machine_connector", MACHINE_CONNECTOR_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MekanismMachineConnectorBlockEntity>> MACHINE_CONNECTOR =
            BLOCK_ENTITIES.register("mekanism_machine_connector", () -> BlockEntityType.Builder.of(
                    MekanismMachineConnectorBlockEntity::new, MACHINE_CONNECTOR_BLOCK.get()).build(null));
    public static final DeferredItem<MekanismConnectionToolItem> CONNECTION_TOOL =
            ITEMS.register("mekanism_connection_tool", MekanismConnectionToolItem::new);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.llmera_mekanism"))
                    .icon(() -> new ItemStack(NETWORK_BRIDGE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(NETWORK_BRIDGE_ITEM.get());
                        output.accept(MACHINE_CONNECTOR_ITEM.get());
                        output.accept(CONNECTION_TOOL.get());
                    })
                    .build());

    public LLMEraMekanism(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ToolExtensionRegistry.register(MekanismToolExtension.extension());
    }
}
