package com.example.llmera.mekanism.llm;

import com.example.llmera.llm.ToolDefinition;
import com.example.llmera.llm.ToolExtension;
import com.example.llmera.llm.ToolInvocation;
import com.example.llmera.mekanism.network.MekanismMachineConnectorBlockEntity;
import com.example.llmera.mekanism.network.MekanismNetworkBridgeBlockEntity;
import com.example.llmera.network.CodeNetworkScanner;
import com.example.llmera.network.IntelligentTransmitterBlockEntity;
import com.example.llmera.network.LlmToolMode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.recipes.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.ChemicalCrystallizerRecipe;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.CombinerRecipe;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.api.recipes.FluidChemicalToChemicalRecipe;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.ItemStackChemicalToObjectRecipe;
import mekanism.api.recipes.ItemStackToChemicalRecipe;
import mekanism.api.recipes.ItemStackToEnergyRecipe;
import mekanism.api.recipes.ItemStackToFluidOptionalItemRecipe;
import mekanism.api.recipes.ItemStackToFluidRecipe;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import mekanism.api.recipes.PressurizedReactionRecipe;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.SawmillRecipe;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.api.security.IBlockSecurityUtils;
import mekanism.api.security.ISecurityObject;
import mekanism.api.security.ISecurityUtils;
import mekanism.api.security.SecurityMode;
import mekanism.common.content.network.InventoryNetwork;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.tile.prefab.TileEntityRecipeMachine;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MekanismToolExtension {
    private static final Gson GSON = new Gson();
    private static final int MAX_ARRAY = 64;

    private static final ToolDefinition MACHINE_DIAGNOSTICS = new ToolDefinition(
            "mekanism_machine_diagnostics",
            "Read a Mekanism machine through an online bridge or powered machine connector. Reports identity, activity, redstone, energy, progress, contents, side configuration, ejection, and known recipe errors. Optional x/y/z select a bridge or connector; target_x/target_y/target_z select one machine when a connector has several.",
            bridgeParameters());
    private static final ToolDefinition MACHINE_CONTROL = new ToolDefinition(
            "mekanism_machine_control",
            "Safely change a PUBLIC Mekanism machine through an online bridge or powered machine connector. action is set_redstone_control, set_auto_eject, toggle_auto_eject, or set_side_data_type. Redstone requires mode; set auto eject requires transmission and enabled; side configuration requires transmission, relative_side, and data_type. PRIVATE and TRUSTED targets are always rejected.",
            Map.ofEntries(
                    Map.entry("x", coordinate("optional bridge x")), Map.entry("y", coordinate("optional bridge y")), Map.entry("z", coordinate("optional bridge z")),
                    Map.entry("target_x", coordinate("optional connected machine x")), Map.entry("target_y", coordinate("optional connected machine y")), Map.entry("target_z", coordinate("optional connected machine z")),
                    Map.entry("action", Map.of("type", "string", "description", "set_redstone_control, set_auto_eject, toggle_auto_eject, or set_side_data_type")),
                    Map.entry("mode", Map.of("type", "string", "description", "disabled, high, low, or pulse")),
                    Map.entry("transmission", Map.of("type", "string", "description", "energy, fluid, chemical, item, or heat")),
                    Map.entry("enabled", Map.of("type", "boolean", "description", "auto-eject state")),
                    Map.entry("relative_side", Map.of("type", "string", "description", "front, back, left, right, top, or bottom")),
                    Map.entry("data_type", Map.of("type", "string", "description", "exact supported Mekanism data type, such as input, output, input_output, energy, extra, or none"))));
    private static final ToolDefinition LOGISTICS_DIAGNOSTICS = new ToolDefinition(
            "mekanism_logistics_diagnostics",
            "Read an InventoryNetwork through an online bridge facing a logistical transporter. Reports network size and acceptors, segment tier/settings/connections, and bounded in-transit item flow/path state without probing inserts.",
            bridgeParameters());
    private static final ToolDefinition RECIPE_LOOKUP = new ToolDefinition(
            "mekanism_recipe_lookup",
            "Query server-side Mekanism recipe types for recipes producing an item, fluid, or chemical, optionally expanding a bounded breadth-first production chain. Uses recipe definitions only, never JEI/EMI client classes.",
            Map.ofEntries(
                    Map.entry("target_kind", Map.of("type", "string", "description", "item, fluid, or chemical")),
                    Map.entry("target_id", Map.of("type", "string", "description", "registry id to produce")),
                    Map.entry("max_depth", Map.of("type", "integer", "description", "production-chain depth 0..3, default 0")),
                    Map.entry("max_recipes", Map.of("type", "integer", "description", "maximum returned recipes 1..20")),
                    Map.entry("max_nodes", Map.of("type", "integer", "description", "maximum chain nodes 1..100")),
                    Map.entry("x", coordinate("optional bridge x")), Map.entry("y", coordinate("optional bridge y")), Map.entry("z", coordinate("optional bridge z"))));

    private static final String GUIDANCE = """
            Mekanism integration tools:
              - Use mekanism_machine_diagnostics for machine state, energy, progress, contents, side configuration, ejection, and recipe warnings.
              - Use mekanism_logistics_diagnostics for logistical transporter topology, acceptors, congestion, and in-flight path failures.
              - Use mekanism_recipe_lookup for local Mekanism producers and bounded item/fluid/chemical production chains.
              - Use mekanism_machine_control only for redstone mode, supported auto-eject, or an exact supported transmission/relative-side/data-type change. It can write only when effective security is PUBLIC or the target has no security support; PRIVATE/TRUSTED and indeterminate security fail closed.
              - A powered machine connector can expose up to 16 linked machines. Supply target_x, target_y, and target_z when more than one machine is available.
            """;

    private MekanismToolExtension() {
    }

    public static ToolExtension extension() {
        return new ToolExtension(List.of(
                new ToolExtension.Tool(MACHINE_DIAGNOSTICS, Set.of(LlmToolMode.BUILD, LlmToolMode.PLAN), MekanismToolExtension::execute),
                new ToolExtension.Tool(MACHINE_CONTROL, Set.of(LlmToolMode.BUILD), MekanismToolExtension::execute),
                new ToolExtension.Tool(LOGISTICS_DIAGNOSTICS, Set.of(LlmToolMode.BUILD, LlmToolMode.PLAN), MekanismToolExtension::execute),
                new ToolExtension.Tool(RECIPE_LOOKUP, Set.of(LlmToolMode.BUILD, LlmToolMode.PLAN), MekanismToolExtension::execute)
        ), GUIDANCE);
    }

    private static Map<String, Object> bridgeParameters() {
        return Map.ofEntries(
                Map.entry("x", coordinate("optional bridge or connector x")),
                Map.entry("y", coordinate("optional bridge or connector y")),
                Map.entry("z", coordinate("optional bridge or connector z")),
                Map.entry("target_x", coordinate("optional connected machine x")),
                Map.entry("target_y", coordinate("optional connected machine y")),
                Map.entry("target_z", coordinate("optional connected machine z")));
    }

    private static Map<String, String> coordinate(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static String execute(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        return switch (invocation.name()) {
            case "mekanism_machine_diagnostics" -> machineDiagnostics(transmitter, invocation);
            case "mekanism_machine_control" -> machineControl(transmitter, invocation);
            case "mekanism_logistics_diagnostics" -> logisticsDiagnostics(transmitter, invocation);
            case "mekanism_recipe_lookup" -> recipeLookup(transmitter, invocation);
            default -> error("unknown Mekanism tool: " + invocation.name());
        };
    }

    private static String machineDiagnostics(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        BridgeRef ref = resolveBridge(transmitter, invocation, false, true);
        if (ref.error != null) return error(ref.error);
        if (!(ref.target instanceof TileEntityMekanism tile)) return error("bridge target is not a Mekanism machine");
        JsonObject out = base("mekanism_machine_diagnostics");
        out.add("bridge", position(ref.bridgePos));
        out.add("target", position(tile.getBlockPos()));
        out.addProperty("block_id", BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()).toString());
        out.addProperty("name", tile.getDisplayName().getString());
        out.addProperty("class", tile.getClass().getName());
        out.addProperty("active", tile.getActive());
        out.addProperty("can_function", tile.canFunction());
        out.addProperty("supports_redstone", tile.supportsRedstone());
        if (tile.supportsRedstone()) {
            out.addProperty("redstone_mode", tile.getControlType().getSerializedName());
            out.addProperty("redstone_powered", tile.isPowered());
        }
        JsonArray unavailable = new JsonArray();
        diagnosticSection(out, unavailable, "energy", () -> addEnergy(out, tile));
        diagnosticSection(out, unavailable, "progress", () -> addProgress(out, tile));
        diagnosticSection(out, unavailable, "contents", () -> addContents(out, tile));
        diagnosticSection(out, unavailable, "side_configuration", () -> addConfiguration(out, tile));
        diagnosticSection(out, unavailable, "known_errors", () -> addKnownErrors(out, tile));
        out.add("unavailable_sections", unavailable);
        return GSON.toJson(out);
    }

    private static void diagnosticSection(JsonObject out, JsonArray unavailable, String section, Runnable reader) {
        try {
            reader.run();
        } catch (RuntimeException | LinkageError e) {
            JsonObject failure = new JsonObject();
            failure.addProperty("section", section);
            failure.addProperty("reason", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            unavailable.add(failure);
            if (!out.has(section)) out.add(section, new JsonArray());
        }
    }

    private static void addEnergy(JsonObject out, IMekanismStrictEnergyHandler handler) {
        JsonArray containers = new JsonArray();
        long stored = 0, capacity = 0;
        List<IEnergyContainer> values = handler.getEnergyContainers(null);
        for (int i = 0; i < values.size() && i < MAX_ARRAY; i++) {
            IEnergyContainer container = values.get(i);
            JsonObject json = new JsonObject();
            json.addProperty("index", i);
            json.addProperty("stored", container.getEnergy());
            json.addProperty("max", container.getMaxEnergy());
            containers.add(json);
            stored += container.getEnergy();
            capacity += container.getMaxEnergy();
        }
        JsonObject energy = new JsonObject();
        energy.addProperty("stored", stored);
        energy.addProperty("max", capacity);
        Long usage = optionalLong(handler, "getLastUsage", "getEnergyUsage", "getEnergyUsed");
        if (usage != null) energy.addProperty("usage", usage);
        energy.add("containers", containers);
        energy.addProperty("truncated", values.size() > MAX_ARRAY);
        out.add("energy", energy);
    }

    private static void addProgress(JsonObject out, TileEntityMekanism tile) {
        JsonObject progress = new JsonObject();
        if (tile instanceof TileEntityProgressMachine<?> machine) {
            progress.addProperty("ticks", machine.getOperatingTicks());
            progress.addProperty("ticks_required", machine.getTicksRequired());
            progress.addProperty("fraction", machine.getScaledProgress());
        } else if (tile instanceof TileEntityFactory<?> factory) {
            JsonArray processes = new JsonArray();
            for (int i = 0; i < factory.progress.length && i < MAX_ARRAY; i++) {
                JsonObject process = new JsonObject();
                process.addProperty("index", i);
                process.addProperty("ticks", factory.getProgress(i));
                process.addProperty("ticks_required", factory.getTicksRequired());
                processes.add(process);
            }
            progress.add("processes", processes);
            progress.addProperty("truncated", factory.progress.length > MAX_ARRAY);
        } else {
            Number ticks = optionalNumber(tile, "getOperatingTicks", "getProgress");
            Number required = optionalNumber(tile, "getTicksRequired");
            if (ticks != null) progress.addProperty("ticks", ticks);
            if (required != null) progress.addProperty("ticks_required", required);
        }
        out.add("progress", progress);
    }

    private static void addContents(JsonObject out, TileEntityMekanism tile) {
        JsonArray items = new JsonArray();
        if (tile instanceof IMekanismInventory inventory && inventory.hasInventory()) {
            List<IInventorySlot> slots = inventory.getInventorySlots(null);
            for (int i = 0; i < slots.size() && i < MAX_ARRAY; i++) {
                JsonObject value = stack(slots.get(i).getStack());
                value.addProperty("index", i);
                value.addProperty("limit", slots.get(i).getLimit(ItemStack.EMPTY));
                items.add(value);
            }
            out.addProperty("item_slots_truncated", slots.size() > MAX_ARRAY);
        }
        out.add("item_slots", items);

        JsonArray fluids = new JsonArray();
        if (tile instanceof IMekanismFluidHandler handler && handler.canHandleFluid()) {
            List<IExtendedFluidTank> tanks = handler.getFluidTanks(null);
            for (int i = 0; i < tanks.size() && i < MAX_ARRAY; i++) {
                JsonObject value = fluid(tanks.get(i).getFluid());
                value.addProperty("index", i);
                value.addProperty("capacity", tanks.get(i).getCapacity());
                fluids.add(value);
            }
            out.addProperty("fluid_tanks_truncated", tanks.size() > MAX_ARRAY);
        }
        out.add("fluid_tanks", fluids);

        JsonArray chemicals = new JsonArray();
        if (tile instanceof IMekanismChemicalHandler handler && handler.canHandleChemicals()) {
            List<IChemicalTank> tanks = handler.getChemicalTanks(null);
            for (int i = 0; i < tanks.size() && i < MAX_ARRAY; i++) {
                JsonObject value = chemical(tanks.get(i).getStack());
                value.addProperty("index", i);
                value.addProperty("capacity", tanks.get(i).getCapacity());
                chemicals.add(value);
            }
            out.addProperty("chemical_tanks_truncated", tanks.size() > MAX_ARRAY);
        }
        out.add("chemical_tanks", chemicals);
    }

    private static void addConfiguration(JsonObject out, TileEntityMekanism tile) {
        JsonArray configurations = new JsonArray();
        if (tile instanceof TileEntityConfigurableMachine configurable) {
            TileComponentConfig component = configurable.getConfig();
            for (TransmissionType transmission : component.getTransmissions()) {
                ConfigInfo info = component.getConfig(transmission);
                JsonObject config = new JsonObject();
                config.addProperty("transmission", transmission.name().toLowerCase(Locale.ROOT));
                config.addProperty("can_eject", info.canEject());
                config.addProperty("auto_eject", info.isEjecting());
                JsonObject sides = new JsonObject();
                for (RelativeSide side : RelativeSide.values()) {
                    sides.addProperty(side.name().toLowerCase(Locale.ROOT), info.getDataType(side).getSerializedName());
                }
                config.add("sides", sides);
                JsonArray supported = new JsonArray();
                info.getSupportedDataTypes().forEach(type -> supported.add(type.getSerializedName()));
                config.add("supported_data_types", supported);
                configurations.add(config);
            }
        }
        out.add("side_configuration", configurations);
    }

    private static void addKnownErrors(JsonObject out, TileEntityMekanism tile) {
        JsonArray errors = new JsonArray();
        try {
            if (tile instanceof TileEntityRecipeMachine<?>) {
                Field typesField = TileEntityRecipeMachine.class.getDeclaredField("errorTypes");
                Field trackedField = TileEntityRecipeMachine.class.getDeclaredField("trackedErrors");
                typesField.setAccessible(true);
                trackedField.setAccessible(true);
                List<?> types = (List<?>) typesField.get(tile);
                boolean[] tracked = (boolean[]) trackedField.get(tile);
                for (int i = 0; i < types.size() && i < tracked.length && errors.size() < MAX_ARRAY; i++) {
                    if (tracked[i]) errors.add(String.valueOf(types.get(i)));
                }
            } else if (tile instanceof TileEntityFactory<?>) {
                Field trackerField = TileEntityFactory.class.getDeclaredField("errorTracker");
                trackerField.setAccessible(true);
                Object tracker = trackerField.get(tile);
                Field typesField = tracker.getClass().getDeclaredField("errorTypes");
                Field trackedField = tracker.getClass().getDeclaredField("trackedErrors");
                typesField.setAccessible(true);
                trackedField.setAccessible(true);
                List<?> types = (List<?>) typesField.get(tracker);
                boolean[][] tracked = (boolean[][]) trackedField.get(tracker);
                for (int process = 0; process < tracked.length && errors.size() < MAX_ARRAY; process++) {
                    for (int i = 0; i < types.size() && i < tracked[process].length && errors.size() < MAX_ARRAY; i++) {
                        if (tracked[process][i]) {
                            JsonObject warning = new JsonObject();
                            warning.addProperty("process", process);
                            warning.addProperty("error", String.valueOf(types.get(i)));
                            errors.add(warning);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            out.addProperty("errors_unavailable", e.getClass().getSimpleName());
        }
        out.add("known_errors", errors);
    }

    private static String machineControl(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        BridgeRef ref = resolveBridge(transmitter, invocation, false, true);
        if (ref.error != null) return error(ref.error);
        if (!(ref.target instanceof TileEntityMekanism tile)) return error("bridge target is not a Mekanism machine");
        String securityError = controlSecurityError(ref.level, tile);
        if (securityError != null) return error(securityError);
        String action = stringArg(invocation, "action");
        if (action == null) return error("action is required");

        BlockEntity expectedTarget = ref.target;
        BlockState expectedState = ref.level.getBlockState(tile.getBlockPos());
        if (ref.level.getBlockEntity(tile.getBlockPos()) != expectedTarget
                || !ref.level.getBlockState(tile.getBlockPos()).equals(expectedState)) {
            return error("target changed while validating control request");
        }
        JsonObject result;
        try {
            result = switch (action.toLowerCase(Locale.ROOT)) {
                case "set_redstone_control" -> setRedstone(invocation, tile);
                case "set_auto_eject" -> setAutoEject(invocation, tile);
                case "toggle_auto_eject" -> toggleAutoEject(invocation, tile);
                case "set_side_data_type" -> setSideData(invocation, tile);
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        if (result == null) return error("unsupported action; allowed: set_redstone_control, set_auto_eject, toggle_auto_eject, set_side_data_type");
        JsonObject out = base("mekanism_machine_control");
        out.addProperty("action", action);
        out.addProperty("security", "public_or_unsupported");
        out.add("bridge", position(ref.bridgePos));
        out.add("target", position(tile.getBlockPos()));
        out.add("change", result);
        return GSON.toJson(out);
    }

    private static String controlSecurityError(ServerLevel level, TileEntityMekanism tile) {
        try {
            ISecurityObject security = IBlockSecurityUtils.INSTANCE.securityCapability(level, tile.getBlockPos(), tile.getBlockState(), tile);
            if (security == null) {
                return tile.hasSecurity() ? "control denied: target security support could not be determined" : null;
            }
            SecurityMode mode = ISecurityUtils.INSTANCE.getEffectiveSecurityMode(security, false);
            return mode == SecurityMode.PUBLIC ? null : "control denied: effective security is " + mode.name().toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError e) {
            return "control denied: target security could not be safely determined";
        }
    }

    private static JsonObject setRedstone(ToolInvocation invocation, TileEntityMekanism tile) {
        if (!tile.supportsRedstone()) throw new IllegalArgumentException("target does not support redstone control");
        RedstoneControl mode = enumValue(RedstoneControl.class, stringArg(invocation, "mode"), "mode");
        if (!tile.supportsMode(mode)) throw new IllegalArgumentException("target does not support redstone mode " + mode.getSerializedName());
        String before = tile.getControlType().getSerializedName();
        tile.setControlType(mode);
        tile.markForSave();
        tile.sendUpdatePacket();
        return beforeAfter(before, tile.getControlType().getSerializedName());
    }

    private static JsonObject setAutoEject(ToolInvocation invocation, TileEntityMekanism tile) {
        if (!(tile instanceof TileEntityConfigurableMachine configurable)) throw new IllegalArgumentException("target has no configurable auto-eject support");
        TransmissionType transmission = transmission(invocation);
        Boolean enabled = boolArg(invocation, "enabled");
        if (enabled == null) throw new IllegalArgumentException("enabled is required");
        ConfigInfo info = configurable.getConfig().getConfig(transmission);
        if (info == null || !info.canEject()) throw new IllegalArgumentException("auto-eject is not supported for " + transmission.name().toLowerCase(Locale.ROOT));
        boolean before = info.isEjecting();
        info.setEjecting(enabled);
        tile.markForSave();
        tile.sendUpdatePacket();
        return beforeAfter(before, info.isEjecting());
    }

    private static JsonObject toggleAutoEject(ToolInvocation invocation, TileEntityMekanism tile) {
        if (!(tile instanceof TileEntityConfigurableMachine configurable)) throw new IllegalArgumentException("target has no configurable auto-eject support");
        TransmissionType transmission = transmission(invocation);
        ConfigInfo info = configurable.getConfig().getConfig(transmission);
        if (info == null || !info.canEject()) throw new IllegalArgumentException("auto-eject is not supported for " + transmission.name().toLowerCase(Locale.ROOT));
        boolean before = info.isEjecting();
        info.setEjecting(!before);
        tile.markForSave();
        tile.sendUpdatePacket();
        return beforeAfter(before, info.isEjecting());
    }

    private static JsonObject setSideData(ToolInvocation invocation, TileEntityMekanism tile) {
        if (!(tile instanceof TileEntityConfigurableMachine configurable)) throw new IllegalArgumentException("target has no side configuration");
        TransmissionType transmission = transmission(invocation);
        RelativeSide side = enumValue(RelativeSide.class, stringArg(invocation, "relative_side"), "relative_side");
        DataType dataType = enumValue(DataType.class, stringArg(invocation, "data_type"), "data_type");
        ConfigInfo info = configurable.getConfig().getConfig(transmission);
        if (info == null) throw new IllegalArgumentException("target does not support transmission " + transmission.name().toLowerCase(Locale.ROOT));
        if (!info.isSideEnabled(side)) throw new IllegalArgumentException("relative side is disabled");
        if (!info.supports(dataType)) throw new IllegalArgumentException("data type is not supported for this transmission");
        String before = info.getDataType(side).getSerializedName();
        if (info.setDataType(dataType, side)) configurable.getConfig().sideChanged(transmission, side);
        tile.markForSave();
        tile.sendUpdatePacket();
        return beforeAfter(before, info.getDataType(side).getSerializedName());
    }

    private static String logisticsDiagnostics(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        BridgeRef ref = resolveBridge(transmitter, invocation, true, true);
        if (ref.error != null) return error(ref.error);
        TileEntityLogisticalTransporterBase tile = (TileEntityLogisticalTransporterBase) ref.target;
        LogisticalTransporterBase anchor = tile.getTransmitter();
        InventoryNetwork network = anchor.getTransmitterNetwork();
        if (network == null) return error("target transporter has no committed InventoryNetwork");

        JsonObject out = base("mekanism_logistics_diagnostics");
        out.add("bridge", position(ref.bridgePos));
        out.addProperty("network_uuid", network.getUUID().toString());
        out.addProperty("transmitter_count", network.transmittersSize());
        out.addProperty("acceptor_connections", network.getAcceptorCount());
        Set<BlockPos> uniqueAcceptors = new HashSet<>();
        List<LogisticalTransporterBase> segments = network.getTransmitters().stream()
                .sorted(Comparator.comparing(LogisticalTransporterBase::getBlockPos, Comparator.comparingLong(BlockPos::asLong))).toList();
        for (LogisticalTransporterBase segment : segments) {
            for (Direction side : Direction.values()) {
                BlockPos acceptor = segment.getBlockPos().relative(side);
                if (network.hasAcceptor(acceptor)) uniqueAcceptors.add(acceptor);
            }
        }
        out.addProperty("unique_acceptors", uniqueAcceptors.size());

        JsonArray segmentJson = new JsonArray();
        JsonArray flows = new JsonArray();
        int stackTotal = 0, itemTotal = 0, dest = 0, home = 0, none = 0, uninitiated = 0, missingPath = 0, maxLoad = 0;
        for (LogisticalTransporterBase segment : segments) {
            Collection<TransporterStack> transit = segment.getTransit();
            maxLoad = Math.max(maxLoad, transit.size());
            stackTotal += transit.size();
            if (segmentJson.size() < MAX_ARRAY) segmentJson.add(segment(segment));
            for (TransporterStack flow : transit) {
                itemTotal += flow.itemStack.getCount();
                switch (flow.getPathType()) {
                    case DEST -> dest++;
                    case HOME -> home++;
                    case NONE -> none++;
                }
                if (!flow.initiatedPath) uninitiated++;
                if (!flow.hasPath()) missingPath++;
                if (flows.size() < MAX_ARRAY) flows.add(flow(segment, flow));
            }
        }
        out.addProperty("in_transit_stacks", stackTotal);
        out.addProperty("in_transit_items", itemTotal);
        out.addProperty("path_dest", dest);
        out.addProperty("path_home", home);
        out.addProperty("path_none", none);
        out.addProperty("uninitiated", uninitiated);
        out.addProperty("missing_path", missingPath);
        out.addProperty("max_segment_load", maxLoad);
        out.add("segments", segmentJson);
        out.add("flows", flows);
        out.addProperty("segments_truncated", segments.size() > MAX_ARRAY);
        out.addProperty("flows_truncated", stackTotal > MAX_ARRAY);
        return GSON.toJson(out);
    }

    private static JsonObject segment(LogisticalTransporterBase segment) {
        JsonObject json = position(segment.getBlockPos());
        json.addProperty("tier", segment.tier.name().toLowerCase(Locale.ROOT));
        json.addProperty("speed", segment.tier.getSpeed());
        json.addProperty("pull_amount", segment.tier.getPullAmount());
        json.addProperty("color", segment.getColor() == null ? "none" : segment.getColor().name().toLowerCase(Locale.ROOT));
        JsonObject connections = new JsonObject();
        for (Direction side : Direction.values()) {
            ConnectionType type = segment.getConnectionType(side);
            connections.addProperty(side.getName(), type.name().toLowerCase(Locale.ROOT));
        }
        json.add("connections", connections);
        json.addProperty("load", segment.getTransit().size());
        return json;
    }

    private static JsonObject flow(LogisticalTransporterBase segment, TransporterStack flow) {
        JsonObject json = stack(flow.itemStack);
        json.addProperty("progress", flow.progress);
        json.addProperty("path_state", flow.getPathType().name().toLowerCase(Locale.ROOT));
        json.addProperty("initiated", flow.initiatedPath);
        json.addProperty("path_length", flow.getPath().size());
        json.addProperty("color", flow.color == null ? "none" : flow.color.name().toLowerCase(Locale.ROOT));
        addPackedPos(json, "segment", segment.getWorldPositionLong());
        addPackedPos(json, "origin", flow.originalLocation);
        addPackedPos(json, "home", flow.homeLocation);
        if (flow.hasPath()) addPackedPos(json, "destination", flow.getPath().getLong(0));
        return json;
    }

    private static String recipeLookup(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation) {
        BridgeRef ref = resolveBridge(transmitter, invocation, false, false);
        if (ref.error != null) return error(ref.error);
        String kind = stringArg(invocation, "target_kind");
        String id = stringArg(invocation, "target_id");
        if (kind == null || !(kind.equals("item") || kind.equals("fluid") || kind.equals("chemical"))) return error("target_kind must be item, fluid, or chemical");
        ResourceLocation target;
        try {
            target = ResourceLocation.parse(id == null ? "" : id);
        } catch (RuntimeException e) {
            return error("target_id must be a valid resource location");
        }
        int maxDepth = clamp(intArg(invocation, "max_depth", 0), 0, 3);
        int maxRecipes = clamp(intArg(invocation, "max_recipes", 20), 1, 20);
        int maxNodes = clamp(intArg(invocation, "max_nodes", 100), 1, 100);

        List<RecipeData> recipes = allRecipeData(ref.level);
        ArrayDeque<ChainTarget> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(new ChainTarget(kind, target.toString(), 0));
        JsonArray nodes = new JsonArray();
        int returnedRecipes = 0;
        while (!queue.isEmpty() && nodes.size() < maxNodes && returnedRecipes < maxRecipes) {
            ChainTarget current = queue.removeFirst();
            String key = current.kind + ":" + current.id;
            if (!visited.add(key)) continue;
            JsonObject node = new JsonObject();
            node.addProperty("kind", current.kind);
            node.addProperty("id", current.id);
            node.addProperty("depth", current.depth);
            JsonArray producers = new JsonArray();
            for (RecipeData recipe : recipes) {
                if (returnedRecipes >= maxRecipes) break;
                if (!recipe.produces(current.kind, current.id)) continue;
                producers.add(recipe.json);
                returnedRecipes++;
                if (current.depth < maxDepth) {
                    for (StackRef input : recipe.inputs) {
                        if (queue.size() + nodes.size() < maxNodes) queue.addLast(new ChainTarget(input.kind, input.id, current.depth + 1));
                    }
                }
            }
            node.add("producers", producers);
            node.addProperty("producer_count_returned", producers.size());
            nodes.add(node);
        }
        JsonObject out = base("mekanism_recipe_lookup");
        out.add("bridge", position(ref.bridgePos));
        out.addProperty("target_kind", kind);
        out.addProperty("target_id", target.toString());
        out.addProperty("max_depth", maxDepth);
        out.add("nodes", nodes);
        out.addProperty("node_count", nodes.size());
        out.addProperty("recipe_count", returnedRecipes);
        out.addProperty("truncated", !queue.isEmpty() || returnedRecipes >= maxRecipes);
        return GSON.toJson(out);
    }

    private static List<RecipeData> allRecipeData(ServerLevel level) {
        List<RecipeData> result = new ArrayList<>();
        List<RecipeType<?>> types = List.of(
                MekanismRecipeTypes.TYPE_CRUSHING.value(), MekanismRecipeTypes.TYPE_ENRICHING.value(), MekanismRecipeTypes.TYPE_SMELTING.value(),
                MekanismRecipeTypes.TYPE_CHEMICAL_INFUSING.value(), MekanismRecipeTypes.TYPE_COMBINING.value(), MekanismRecipeTypes.TYPE_SEPARATING.value(),
                MekanismRecipeTypes.TYPE_WASHING.value(), MekanismRecipeTypes.TYPE_EVAPORATING.value(), MekanismRecipeTypes.TYPE_ACTIVATING.value(),
                MekanismRecipeTypes.TYPE_CENTRIFUGING.value(), MekanismRecipeTypes.TYPE_CRYSTALLIZING.value(), MekanismRecipeTypes.TYPE_DISSOLUTION.value(),
                MekanismRecipeTypes.TYPE_COMPRESSING.value(), MekanismRecipeTypes.TYPE_PURIFYING.value(), MekanismRecipeTypes.TYPE_INJECTING.value(),
                MekanismRecipeTypes.TYPE_NUCLEOSYNTHESIZING.value(), MekanismRecipeTypes.TYPE_ENERGY_CONVERSION.value(),
                MekanismRecipeTypes.TYPE_CHEMICAL_CONVERSION.value(), MekanismRecipeTypes.TYPE_OXIDIZING.value(), MekanismRecipeTypes.TYPE_PIGMENT_EXTRACTING.value(),
                MekanismRecipeTypes.TYPE_PIGMENT_MIXING.value(), MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value(), MekanismRecipeTypes.TYPE_PAINTING.value(),
                MekanismRecipeTypes.TYPE_REACTION.value(), MekanismRecipeTypes.TYPE_ROTARY.value(), MekanismRecipeTypes.TYPE_SAWING.value());
        for (RecipeType<?> type : types) {
            for (RecipeHolder<?> holder : getRecipes(level, type)) result.add(describeRecipe(holder));
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<? extends RecipeHolder<?>> getRecipes(ServerLevel level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static RecipeData describeRecipe(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        JsonObject json = new JsonObject();
        json.addProperty("recipe_id", holder.id().toString());
        json.addProperty("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString());
        JsonArray inputsJson = new JsonArray();
        JsonArray outputsJson = new JsonArray();
        List<StackRef> inputs = new ArrayList<>();
        List<StackRef> outputs = new ArrayList<>();
        try {
            if (recipe instanceof ItemStackToItemStackRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addItems(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ItemStackToChemicalRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addChemicals(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ItemStackToFluidRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addFluids(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ItemStackToFluidOptionalItemRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput());
                for (var value : r.getOutputDefinition()) { addFluid(outputsJson, outputs, value.fluid(), null); addItem(outputsJson, outputs, value.optionalItem(), "optional"); }
            } else if (recipe instanceof ChemicalToChemicalRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addChemicals(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ChemicalChemicalToChemicalRecipe r) {
                addIngredient(inputsJson, inputs, r.getLeftInput()); addIngredient(inputsJson, inputs, r.getRightInput()); addChemicals(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof FluidChemicalToChemicalRecipe r) {
                addIngredient(inputsJson, inputs, r.getFluidInput()); addIngredient(inputsJson, inputs, r.getChemicalInput()); addChemicals(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof FluidToFluidRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addFluids(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ChemicalCrystallizerRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addItems(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof CombinerRecipe r) {
                addIngredient(inputsJson, inputs, r.getMainInput()); addIngredient(inputsJson, inputs, r.getExtraInput()); addItems(outputsJson, outputs, r.getOutputDefinition(), null);
            } else if (recipe instanceof ItemStackChemicalToObjectRecipe<?> r) {
                addIngredient(inputsJson, inputs, r.getItemInput()); addIngredient(inputsJson, inputs, r.getChemicalInput()); addUnknownOutputs(outputsJson, outputs, r.getOutputDefinition());
            } else if (recipe instanceof ElectrolysisRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput());
                for (var value : r.getOutputDefinition()) { addChemical(outputsJson, outputs, value.left(), "left"); addChemical(outputsJson, outputs, value.right(), "right"); }
                json.addProperty("energy_multiplier", r.getEnergyMultiplier());
            } else if (recipe instanceof PressurizedReactionRecipe r) {
                addIngredient(inputsJson, inputs, r.getInputSolid()); addIngredient(inputsJson, inputs, r.getInputFluid()); addIngredient(inputsJson, inputs, r.getInputChemical());
                for (var value : r.getOutputDefinition()) { addItem(outputsJson, outputs, value.item(), null); addChemical(outputsJson, outputs, value.chemical(), null); }
                json.addProperty("duration", r.getDuration()); json.addProperty("energy", r.getEnergyRequired());
            } else if (recipe instanceof RotaryRecipe r) {
                if (r.hasFluidToChemical()) { addIngredient(inputsJson, inputs, r.getFluidInput()); addChemicals(outputsJson, outputs, r.getChemicalOutputDefinition(), null); }
                if (r.hasChemicalToFluid()) { addIngredient(inputsJson, inputs, r.getChemicalInput()); addFluids(outputsJson, outputs, r.getFluidOutputDefinition(), null); }
            } else if (recipe instanceof SawmillRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); addItems(outputsJson, outputs, r.getMainOutputDefinition(), "primary"); addItems(outputsJson, outputs, r.getSecondaryOutputDefinition(), "secondary");
                json.addProperty("secondary_chance", r.getSecondaryChance());
            } else if (recipe instanceof ItemStackToEnergyRecipe r) {
                addIngredient(inputsJson, inputs, r.getInput()); json.addProperty("unsupported_shape", "energy output is outside target_kind item|fluid|chemical");
            } else {
                json.addProperty("unsupported_shape", recipe.getClass().getName());
            }
            if (recipe instanceof NucleosynthesizingRecipe r) json.addProperty("duration", r.getDuration());
        } catch (RuntimeException e) {
            json.addProperty("unsupported_shape", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
        trim(inputsJson); trim(outputsJson);
        json.add("inputs", inputsJson); json.add("outputs", outputsJson);
        return new RecipeData(json, List.copyOf(inputs), List.copyOf(outputs));
    }

    private static void addUnknownOutputs(JsonArray json, List<StackRef> refs, List<?> values) {
        for (Object value : values) {
            if (value instanceof ItemStack stack) addItem(json, refs, stack, null);
            else if (value instanceof ChemicalStack stack) addChemical(json, refs, stack, null);
            else if (value instanceof FluidStack stack) addFluid(json, refs, stack, null);
            else {
                Object item = optionalInvoke(value, "item", "getItem", "getMainOutput");
                Object chemical = optionalInvoke(value, "chemical", "getChemical");
                Object fluid = optionalInvoke(value, "fluid", "getFluid");
                if (item instanceof ItemStack stack) addItem(json, refs, stack, null);
                if (chemical instanceof ChemicalStack stack) addChemical(json, refs, stack, null);
                if (fluid instanceof FluidStack stack) addFluid(json, refs, stack, null);
            }
        }
    }

    private static void addIngredient(JsonArray json, List<StackRef> refs, InputIngredient<?> ingredient) {
        int alternatives = 0;
        for (Object value : ingredient.getRepresentations()) {
            if (json.size() >= MAX_ARRAY || alternatives++ >= 16) break;
            if (value instanceof ItemStack stack) addItem(json, refs, stack, "alternative");
            else if (value instanceof FluidStack stack) addFluid(json, refs, stack, "alternative");
            else if (value instanceof ChemicalStack stack) addChemical(json, refs, stack, "alternative");
        }
    }

    private static void addItems(JsonArray json, List<StackRef> refs, List<ItemStack> values, String role) { values.forEach(value -> addItem(json, refs, value, role)); }
    private static void addFluids(JsonArray json, List<StackRef> refs, List<FluidStack> values, String role) { values.forEach(value -> addFluid(json, refs, value, role)); }
    private static void addChemicals(JsonArray json, List<StackRef> refs, List<ChemicalStack> values, String role) { values.forEach(value -> addChemical(json, refs, value, role)); }

    private static void addItem(JsonArray json, List<StackRef> refs, ItemStack value, String role) {
        if (value.isEmpty() || json.size() >= MAX_ARRAY) return;
        JsonObject record = stack(value); if (role != null) record.addProperty("role", role); json.add(record);
        refs.add(new StackRef("item", BuiltInRegistries.ITEM.getKey(value.getItem()).toString()));
    }

    private static void addFluid(JsonArray json, List<StackRef> refs, FluidStack value, String role) {
        if (value.isEmpty() || json.size() >= MAX_ARRAY) return;
        JsonObject record = fluid(value); if (role != null) record.addProperty("role", role); json.add(record);
        refs.add(new StackRef("fluid", BuiltInRegistries.FLUID.getKey(value.getFluid()).toString()));
    }

    private static void addChemical(JsonArray json, List<StackRef> refs, ChemicalStack value, String role) {
        if (value.isEmpty() || json.size() >= MAX_ARRAY) return;
        JsonObject record = chemical(value); if (role != null) record.addProperty("role", role); json.add(record);
        refs.add(new StackRef("chemical", chemicalId(value)));
    }

    private static BridgeRef resolveBridge(IntelligentTransmitterBlockEntity transmitter, ToolInvocation invocation,
                                           boolean logistics, boolean requireUniqueTarget) {
        if (!(transmitter.getLevel() instanceof ServerLevel level)) return BridgeRef.failure("transmitter level is not a ServerLevel");
        CodeNetworkScanner.NetworkView view = CodeNetworkScanner.scan(level, transmitter.getBlockPos());
        List<BridgeRef> candidates = new ArrayList<>();
        Integer x = integer(invocation.arguments().get("x"));
        Integer y = integer(invocation.arguments().get("y"));
        Integer z = integer(invocation.arguments().get("z"));
        boolean anyCoordinate = x != null || y != null || z != null;
        if (anyCoordinate && (x == null || y == null || z == null)) return BridgeRef.failure("x, y, and z must be supplied together");
        BlockPos selectedBridge = anyCoordinate ? new BlockPos(x, y, z) : null;
        Integer targetX = integer(invocation.arguments().get("target_x"));
        Integer targetY = integer(invocation.arguments().get("target_y"));
        Integer targetZ = integer(invocation.arguments().get("target_z"));
        boolean anyTargetCoordinate = targetX != null || targetY != null || targetZ != null;
        if (anyTargetCoordinate && (targetX == null || targetY == null || targetZ == null)) {
            return BridgeRef.failure("target_x, target_y, and target_z must be supplied together");
        }
        BlockPos selectedTarget = anyTargetCoordinate ? new BlockPos(targetX, targetY, targetZ) : null;
        for (BlockPos member : view.memberPositions()) {
            if (!view.memberHasChannel(member)) continue;
            if (selectedBridge != null && !member.equals(selectedBridge)) continue;
            BlockEntity memberEntity = level.getBlockEntity(member);
            if (memberEntity instanceof MekanismNetworkBridgeBlockEntity bridge) {
                BlockEntity target = bridge.target();
                if (selectedTarget != null && (target == null || !target.getBlockPos().equals(selectedTarget))) continue;
                if (logistics ? target instanceof TileEntityLogisticalTransporterBase
                        : MekanismNetworkBridgeBlockEntity.isSupportedTarget(target)) {
                    candidates.add(new BridgeRef(level, member.immutable(), target, null));
                }
                continue;
            }
            if (!logistics && memberEntity instanceof MekanismMachineConnectorBlockEntity connector
                    && connector.isPowered()) {
                for (BlockPos targetPos : connector.getConnections()) {
                    if (selectedTarget != null && !targetPos.equals(selectedTarget)) continue;
                    if (!level.hasChunkAt(targetPos)) continue;
                    BlockEntity target = level.getBlockEntity(targetPos);
                    if (MekanismMachineConnectorBlockEntity.isSupportedMachine(target)) {
                        candidates.add(new BridgeRef(level, member.immutable(), target, null));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return BridgeRef.failure(logistics
                    ? "no online bridge faces a logistical transporter"
                    : "no powered bridge or connector exposes a supported Mekanism machine");
        }
        if (requireUniqueTarget && selectedTarget == null && candidates.size() > 1) {
            return BridgeRef.failure("multiple Mekanism machines are connected; provide target_x, target_y, and target_z");
        }
        return candidates.getFirst();
    }

    private static JsonObject base(String tool) { JsonObject out = new JsonObject(); out.addProperty("ok", true); out.addProperty("tool", tool); return out; }
    private static String error(String message) { JsonObject out = new JsonObject(); out.addProperty("ok", false); out.addProperty("error", message); return GSON.toJson(out); }
    private static JsonObject beforeAfter(String before, String after) { JsonObject out = new JsonObject(); out.addProperty("before", before); out.addProperty("after", after); return out; }
    private static JsonObject beforeAfter(boolean before, boolean after) { JsonObject out = new JsonObject(); out.addProperty("before", before); out.addProperty("after", after); return out; }
    private static JsonObject position(BlockPos pos) { JsonObject out = new JsonObject(); out.addProperty("x", pos.getX()); out.addProperty("y", pos.getY()); out.addProperty("z", pos.getZ()); return out; }
    private static JsonObject stack(ItemStack value) { JsonObject out = new JsonObject(); out.addProperty("kind", "item"); out.addProperty("id", value.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(value.getItem()).toString()); out.addProperty("amount", value.getCount()); return out; }
    private static JsonObject fluid(FluidStack value) { JsonObject out = new JsonObject(); out.addProperty("kind", "fluid"); out.addProperty("id", value.isEmpty() ? "minecraft:empty" : BuiltInRegistries.FLUID.getKey(value.getFluid()).toString()); out.addProperty("amount", value.getAmount()); return out; }
    private static JsonObject chemical(ChemicalStack value) { JsonObject out = new JsonObject(); out.addProperty("kind", "chemical"); out.addProperty("id", chemicalId(value)); out.addProperty("amount", value.getAmount()); return out; }
    private static String chemicalId(ChemicalStack value) { return value.isEmpty() ? "mekanism:empty" : MekanismAPI.CHEMICAL_REGISTRY.getKey(value.getChemical()).toString(); }
    private static void addPackedPos(JsonObject json, String key, long packed) { if (packed != Long.MAX_VALUE) json.add(key, position(BlockPos.of(packed))); }
    private static void trim(JsonArray array) { while (array.size() > MAX_ARRAY) array.remove(array.size() - 1); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String stringArg(ToolInvocation invocation, String name) { Object value = invocation.arguments().get(name); return value == null ? null : value.toString().trim().toLowerCase(Locale.ROOT); }
    private static Boolean boolArg(ToolInvocation invocation, String name) { Object value = invocation.arguments().get(name); return value instanceof Boolean b ? b : value == null ? null : Boolean.valueOf(value.toString()); }
    private static int intArg(ToolInvocation invocation, String name, int fallback) { Integer value = integer(invocation.arguments().get(name)); return value == null ? fallback : value; }
    private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : value == null ? null : parseInteger(value.toString()); }
    private static Integer parseInteger(String value) { try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; } }
    private static TransmissionType transmission(ToolInvocation invocation) { return enumValue(TransmissionType.class, stringArg(invocation, "transmission"), "transmission"); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String argument) { if (value == null) throw new IllegalArgumentException(argument + " is required"); try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("invalid " + argument + ": " + value); } }

    private static Number optionalNumber(Object target, String... methods) { Object value = optionalInvoke(target, methods); return value instanceof Number number ? number : null; }
    private static Long optionalLong(Object target, String... methods) { Number value = optionalNumber(target, methods); return value == null ? null : value.longValue(); }
    private static Object optionalInvoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try { Method method = target.getClass().getMethod(name); return method.invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private record BridgeRef(ServerLevel level, BlockPos bridgePos, BlockEntity target, String error) {
        static BridgeRef failure(String error) { return new BridgeRef(null, null, null, error); }
    }
    private record StackRef(String kind, String id) { }
    private record ChainTarget(String kind, String id, int depth) { }
    private record RecipeData(JsonObject json, List<StackRef> inputs, List<StackRef> outputs) {
        boolean produces(String kind, String id) { return outputs.stream().anyMatch(output -> output.kind.equals(kind) && output.id.equals(id)); }
    }
}
