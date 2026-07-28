package com.example.llmera.mekanism.network;

import com.example.llmera.mekanism.LLMEraMekanism;
import com.example.llmera.network.CodeNetworkMember;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores the connector's machine targets and the energy used to keep those
 * targets live. Targets are positions rather than block-entity references so
 * they survive chunk unloads and world saves.
 */
public final class MekanismMachineConnectorBlockEntity extends BlockEntity
        implements CodeNetworkMember, IStrictEnergyHandler {
    public static final int CHANNEL_DEMAND = 1;
    public static final int MAX_CONNECTIONS = 16;
    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int MAX_RECEIVE = 20_000;

    private static final String TAG_CONNECTIONS = "Connections";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_POWERED = "Powered";
    private static final int DIRECT_SCAN_INTERVAL = 10;

    private final Set<BlockPos> connections = new LinkedHashSet<>();
    private int energy;
    private int tickCounter;
    private boolean powered;

    private final IEnergyStorage forgeEnergy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(MAX_RECEIVE, Math.min(maxReceive, ENERGY_CAPACITY - energy));
            if (!simulate && accepted > 0) {
                energy += accepted;
                markEnergyChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public MekanismMachineConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(LLMEraMekanism.MACHINE_CONNECTOR.get(), pos, state);
    }

    @Override
    public int channelDemand() {
        return CHANNEL_DEMAND;
    }

    public IEnergyStorage forgeEnergyStorage() {
        return forgeEnergy;
    }

    public int getEnergyStored() {
        return energy;
    }

    public int getMaxEnergyStored() {
        return ENERGY_CAPACITY;
    }

    public List<BlockPos> getConnections() {
        return List.copyOf(connections);
    }

    public int connectionCount() {
        return connections.size();
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean addConnection(BlockPos target) {
        if (target == null || connections.contains(target)) {
            return false;
        }
        if (connections.size() >= MAX_CONNECTIONS) {
            return false;
        }
        connections.add(target.immutable());
        markConnectionsChanged();
        return true;
    }

    public boolean removeConnection(BlockPos target) {
        if (target == null || !connections.remove(target)) {
            return false;
        }
        markConnectionsChanged();
        return true;
    }

    public boolean hasConnection(BlockPos target) {
        return connections.contains(target);
    }

    public Component statusMessage() {
        return Component.translatable("message.llmera_mekanism.connector.status",
                connectionCount(), MAX_CONNECTIONS, energy, ENERGY_CAPACITY,
                Component.translatable(powered
                        ? "message.llmera_mekanism.connector.online"
                        : "message.llmera_mekanism.connector.offline"));
    }

    public static boolean isSupportedMachine(BlockEntity entity) {
        return entity instanceof TileEntityMekanism;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  MekanismMachineConnectorBlockEntity connector) {
        if (level.isClientSide) {
            return;
        }
        if (++connector.tickCounter % DIRECT_SCAN_INTERVAL == 0) {
            connector.reconcileDirectConnections();
            connector.removeBrokenLoadedConnections();
        }
        connector.maintainConnections();
    }

    private void reconcileDirectConnections() {
        if (level == null) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity target = level.getBlockEntity(worldPosition.relative(direction));
            if (isSupportedMachine(target)) {
                addConnection(target.getBlockPos());
            }
        }
    }

    private void removeBrokenLoadedConnections() {
        if (level == null) {
            return;
        }
        List<BlockPos> broken = new ArrayList<>();
        for (BlockPos target : connections) {
            if (level.hasChunkAt(target) && !isSupportedMachine(level.getBlockEntity(target))) {
                broken.add(target);
            }
        }
        for (BlockPos target : broken) {
            removeConnection(target);
        }
    }

    private void maintainConnections() {
        if (level == null || level.isClientSide) {
            return;
        }
        long required = 0;
        boolean hasLoadedTarget = false;
        for (BlockPos target : connections) {
            if (!level.hasChunkAt(target)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(target);
            if (!isSupportedMachine(entity)) {
                continue;
            }
            hasLoadedTarget = true;
            required += energyCost(target);
            if (required > ENERGY_CAPACITY) {
                required = ENERGY_CAPACITY;
                break;
            }
        }
        boolean nextPowered = hasLoadedTarget && required > 0 && energy >= required;
        if (nextPowered) {
            energy -= (int) required;
            markEnergyChanged();
        }
        if (powered != nextPowered) {
            powered = nextPowered;
            markConnectionsChanged();
        }
    }

    /** One FE/tick per 16 blocks, with a minimum of one FE/tick per target. */
    public int energyCost(BlockPos target) {
        double distance = Math.sqrt(worldPosition.distSqr(target));
        return Math.max(1, (int) Math.ceil(distance / 16.0));
    }

    private void markEnergyChanged() {
        setChanged();
        if (level != null && !level.isClientSide && tickCounter % DIRECT_SCAN_INTERVAL == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void markConnectionsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLongArray(TAG_CONNECTIONS, connections.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt(TAG_ENERGY, energy);
        tag.putBoolean(TAG_POWERED, powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        connections.clear();
        for (long value : tag.getLongArray(TAG_CONNECTIONS)) {
            if (connections.size() >= MAX_CONNECTIONS) break;
            connections.add(BlockPos.of(value).immutable());
        }
        energy = Math.max(0, Math.min(ENERGY_CAPACITY, tag.getInt(TAG_ENERGY)));
        powered = tag.getBoolean(TAG_POWERED);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public int getEnergyContainerCount() {
        return 1;
    }

    @Override
    public long getEnergy(int container) {
        return container == 0 ? energy : 0;
    }

    @Override
    public void setEnergy(int container, long value) {
        if (container == 0) {
            energy = (int) Math.max(0, Math.min(ENERGY_CAPACITY, value));
            markEnergyChanged();
        }
    }

    @Override
    public long getMaxEnergy(int container) {
        return container == 0 ? ENERGY_CAPACITY : 0;
    }

    @Override
    public long getNeededEnergy(int container) {
        return container == 0 ? ENERGY_CAPACITY - energy : 0;
    }

    @Override
    public long insertEnergy(int container, long amount, Action action) {
        if (container != 0 || amount <= 0) return 0;
        long accepted = Math.min(MAX_RECEIVE, Math.min(amount, ENERGY_CAPACITY - energy));
        if (action.execute() && accepted > 0) {
            energy += (int) accepted;
            markEnergyChanged();
        }
        return accepted;
    }

    @Override
    public long extractEnergy(int container, long amount, Action action) {
        return 0;
    }
}
