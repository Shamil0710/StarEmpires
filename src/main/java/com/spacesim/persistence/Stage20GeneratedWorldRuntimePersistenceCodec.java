package com.spacesim.persistence;

import com.spacesim.world.StarSystemId;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState.LocalFleetPhysicalState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic bounded codec for one atomic Stage-20.5 generated-world runtime checkpoint. */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323552; // S25R
    private static final int FILE_FORMAT_VERSION = 3;
    private static final int PHYSICAL_SIDECAR_FILE_FORMAT_VERSION = 2;
    private static final int LEGACY_FILE_FORMAT_VERSION = 1;
    private static final int MAX_LOCAL_FLEETS = 1_000_000;
    private static final int MAX_ENGINEERING_INSTANCE_STATES = 1_000_000;
    private static final int MAX_BYTES = 512 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024 * 1024;

    private Stage20GeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes the generated campaign, ordinary world and freight state as one deterministic payload.
     *
     * @param state complete validated runtime checkpoint
     * @return new binary payload
     */
    public static byte[] encode(Stage20GeneratedWorldRuntimePersistentState state) {
        Stage20GeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] campaign = Stage20GeneratedCampaignPersistenceCodec.encode(checked.campaign());
        byte[] world = WorldStateCodec.encode(checked.worldState());
        byte[] freight = Stage20FreightPersistenceCodec.encode(checked.freight());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                writeText(output, checked.bridgeVersion());
                output.writeLong(checked.activeSystemId().value());
                writePayload(output, campaign, "campaign");
                writePayload(output, world, "world");
                writePayload(output, freight, "freight");
                writeLocalFleetPhysicalStates(output, checked.localFleetPhysicalStates());
                writeEngineeringInstanceStates(output, checked.worldState());
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-20.5 runtime checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory runtime checkpoint encoding failure", exception);
        }
    }

    /**
     * Decodes and cross-validates one atomic Stage-20.5 generated-world runtime checkpoint.
     *
     * @param bytes encoded checkpoint
     * @return immutable validated checkpoint
     */
    public static Stage20GeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20.5 runtime checkpoint size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20.5 runtime checkpoint magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION
                    && fileVersion != PHYSICAL_SIDECAR_FILE_FORMAT_VERSION
                    && fileVersion != LEGACY_FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-20.5 runtime file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            int expectedSchema = fileVersion == LEGACY_FILE_FORMAT_VERSION
                    ? 1 : Stage20GeneratedWorldRuntimePersistentState.CURRENT_VERSION;
            if (schemaVersion != expectedSchema) {
                throw new IllegalArgumentException(
                        "runtime checkpoint schema differs from its file format: " + schemaVersion);
            }
            String bridgeVersion = readText(input, "bridgeVersion");
            StarSystemId activeSystemId = new StarSystemId(input.readLong());
            Stage20GeneratedCampaignPersistentState campaign =
                    Stage20GeneratedCampaignPersistenceCodec.decode(readPayload(input, "campaign"));
            WorldState world = WorldStateCodec.decode(readPayload(input, "world"));
            Stage20FreightPersistentState freight =
                    Stage20FreightPersistenceCodec.decode(readPayload(input, "freight"));
            List<LocalFleetPhysicalState> localPhysical =
                    fileVersion >= PHYSICAL_SIDECAR_FILE_FORMAT_VERSION
                    ? readLocalFleetPhysicalStates(input)
                    : migrateLegacyLocalFleetPhysicalStates(world, freight);
            validateLocalFreightPhysicalMirror(freight, localPhysical);
            if (fileVersion >= FILE_FORMAT_VERSION) {
                world = readAndApplyEngineeringInstanceStates(input, world);
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Trailing bytes after Stage-20.5 runtime checkpoint");
            }
            return new Stage20GeneratedWorldRuntimePersistentState(
                    Stage20GeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                    bridgeVersion,
                    campaign,
                    world,
                    activeSystemId,
                    freight,
                    localPhysical);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20.5 runtime checkpoint is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-20.5 runtime checkpoint", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-20.5 runtime checkpoint", exception);
        }
    }

    /**
     * Rejects a serialized freight compatibility mirror that disagrees with the separately encoded
     * exact local-fleet physical authority before checkpoint construction can canonicalize a live
     * capture. This keeps external/corrupt save bytes fail-closed while allowing the atomic runtime
     * composition boundary to refresh its redundant freight mirror during an ordinary capture.
     */
    static void validateLocalFreightPhysicalMirror(
            Stage20FreightPersistentState freight,
            List<LocalFleetPhysicalState> localPhysical) {
        Objects.requireNonNull(freight, "freight");
        Objects.requireNonNull(localPhysical, "localPhysical");
        Map<FleetId, LocalPhysicalKinematics> exactByFleet = new HashMap<>();
        for (LocalFleetPhysicalState state : localPhysical) {
            LocalPhysicalKinematics previous = exactByFleet.putIfAbsent(
                    state.fleetId(), state.physicalState());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate local fleet physical state while validating freight mirror: "
                                + state.fleetId());
            }
        }
        for (Stage20FreightPersistentState.FreighterState fleet : freight.freighters()) {
            LocalPhysicalKinematics exact = exactByFleet.get(fleet.fleetId());
            if (exact != null && !fleet.physicalState().equals(exact)) {
                throw new IllegalArgumentException(
                        "serialized freight physical mirror differs from exact local fleet state: "
                                + fleet.fleetId());
            }
        }
    }

    private static void writeLocalFleetPhysicalStates(
            DataOutputStream output,
            List<LocalFleetPhysicalState> states) throws IOException {
        if (states.size() > MAX_LOCAL_FLEETS) {
            throw new IllegalArgumentException("too many local fleet physical states");
        }
        output.writeInt(states.size());
        for (LocalFleetPhysicalState state : states) {
            LocalPhysicalKinematics physical = state.physicalState();
            LocalPhysicalPosition position = physical.position();
            output.writeLong(state.fleetId().value());
            output.writeLong(state.systemId().value());
            output.writeLong(position.cellX());
            output.writeLong(position.cellY());
            output.writeDouble(position.offsetXM());
            output.writeDouble(position.offsetYM());
            output.writeDouble(physical.velocityXMps());
            output.writeDouble(physical.velocityYMps());
        }
    }

    private static List<LocalFleetPhysicalState> readLocalFleetPhysicalStates(
            DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_LOCAL_FLEETS) {
            throw new IllegalArgumentException("local fleet physical-state count is outside bounds");
        }
        ArrayList<LocalFleetPhysicalState> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new LocalFleetPhysicalState(
                    new FleetId(input.readLong()),
                    new StarSystemId(input.readLong()),
                    new LocalPhysicalKinematics(
                            new LocalPhysicalPosition(
                                    input.readLong(),
                                    input.readLong(),
                                    input.readDouble(),
                                    input.readDouble()),
                            input.readDouble(),
                            input.readDouble())));
        }
        return List.copyOf(result);
    }

    private static List<LocalFleetPhysicalState> migrateLegacyLocalFleetPhysicalStates(
            com.spacesim.world.WorldState world,
            Stage20FreightPersistentState freight) {
        var freightByFleet = new java.util.HashMap<FleetId, Stage20FreightPersistentState.FreighterState>();
        freight.freighters().forEach(value -> freightByFleet.put(value.fleetId(), value));
        ArrayList<LocalFleetPhysicalState> result = new ArrayList<>();
        for (var placement : world.fleets()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            var legacy = freightByFleet.get(placement.id());
            if (legacy == null) {
                throw new IllegalArgumentException(
                        "legacy checkpoint contains an unrecognized local ordinary fleet");
            }
            result.add(new LocalFleetPhysicalState(
                    placement.id(), placement.systemId(), legacy.physicalState()));
        }
        return List.copyOf(result);
    }

    private static void writeEngineeringInstanceStates(
            DataOutputStream output,
            WorldState world) throws IOException {
        List<EngineeringInstanceRow> rows = world.systems().stream()
                .sorted(Comparator.comparing(StarSystemSimulationState::systemId))
                .flatMap(system -> system.simulationState().entities().stream()
                        .filter(entity -> entity.engineering() != null
                                && entity.engineering().instanceState() != null)
                        .sorted(Comparator.comparing(EntityState::id))
                        .map(entity -> new EngineeringInstanceRow(
                                system.systemId(),
                                entity.id(),
                                entity.engineering().instanceState())))
                .toList();
        if (rows.size() > MAX_ENGINEERING_INSTANCE_STATES) {
            throw new IllegalArgumentException("too many engineering instance states");
        }
        output.writeInt(rows.size());
        for (EngineeringInstanceRow row : rows) {
            output.writeLong(row.systemId().value());
            output.writeLong(row.entityId().value());
            ContentBoundSaveCodec.writeShipInstance(output, row.instanceState());
        }
    }

    private static WorldState readAndApplyEngineeringInstanceStates(
            DataInputStream input,
            WorldState world) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ENGINEERING_INSTANCE_STATES) {
            throw new IllegalArgumentException(
                    "engineering instance-state count is outside bounds");
        }
        Map<StarSystemId, Map<EntityId, EntityState.ShipInstanceState>> rowsBySystem =
                new HashMap<>();
        for (int index = 0; index < count; index++) {
            StarSystemId systemId = new StarSystemId(input.readLong());
            EntityId entityId = new EntityId(input.readLong());
            EntityState.ShipInstanceState instance =
                    ContentBoundSaveCodec.readShipInstance(input);
            var rows = rowsBySystem.computeIfAbsent(systemId, ignored -> new HashMap<>());
            if (rows.putIfAbsent(entityId, instance) != null) {
                throw new IllegalArgumentException(
                        "duplicate engineering instance state: " + systemId + "/" + entityId);
            }
        }

        List<StarSystemSimulationState> systems = new ArrayList<>(world.systems().size());
        for (StarSystemSimulationState system : world.systems()) {
            Map<EntityId, EntityState.ShipInstanceState> rows =
                    rowsBySystem.remove(system.systemId());
            if (rows == null) {
                systems.add(system);
                continue;
            }
            GameState state = system.simulationState();
            List<EntityState> entities = new ArrayList<>(state.entities().size());
            for (EntityState entity : state.entities()) {
                EntityState.ShipInstanceState instance = rows.remove(entity.id());
                if (instance == null) {
                    entities.add(entity);
                    continue;
                }
                EntityState.EngineeringState engineering = entity.engineering();
                if (engineering == null) {
                    throw new IllegalArgumentException(
                            "engineering instance state has no core engineering entity: "
                                    + system.systemId() + "/" + entity.id());
                }
                EntityState.EngineeringState restoredEngineering =
                        new EntityState.EngineeringState(
                                engineering.hullId(),
                                engineering.installedModules(),
                                engineering.consumables(),
                                engineering.sharedBusEnergyJ(),
                                engineering.shipHeatStoredJ(),
                                engineering.localHeatJByMount(),
                                engineering.thrustLimitNByMount(),
                                engineering.coolantBusCapacityW(),
                                engineering.ftlCooldownSecondsByMount(),
                                instance);
                entities.add(new EntityState(
                        entity.id(),
                        entity.identity(),
                        entity.transform(),
                        entity.inventory(),
                        entity.wallet(),
                        entity.market(),
                        entity.production(),
                        entity.priceHistory(),
                        entity.faction(),
                        entity.reputation(),
                        entity.ship(),
                        entity.tradeAi(),
                        entity.mining(),
                        entity.combat(),
                        entity.asteroid(),
                        entity.archetype(),
                        restoredEngineering,
                        entity.sensorKnowledge()));
            }
            if (!rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "engineering instance state references absent entities in "
                                + system.systemId());
            }
            systems.add(new StarSystemSimulationState(
                    system.systemId(),
                    withEntities(state, entities)));
        }
        if (!rowsBySystem.isEmpty()) {
            throw new IllegalArgumentException(
                    "engineering instance state references absent star systems");
        }
        return withSystems(world, systems);
    }

    private static GameState withEntities(GameState state, List<EntityState> entities) {
        return new GameState(
                state.schemaVersion(),
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                List.copyOf(entities));
    }

    private static WorldState withSystems(
            WorldState world,
            List<StarSystemSimulationState> systems) {
        return new WorldState(
                world.schemaVersion(),
                world.topology(),
                List.copyOf(systems),
                world.factions(),
                world.factionStrategies(),
                world.nextConstructionProjectIdValue(),
                world.constructionProjects(),
                world.factionEconomicPressures(),
                world.nextFleetIdValue(),
                world.fleets(),
                world.fleetJumps(),
                world.factionIdentities(),
                world.factionDiplomacyStates());
    }

    private record EngineeringInstanceRow(
            StarSystemId systemId,
            EntityId entityId,
            EntityState.ShipInstanceState instanceState) { }

    private static void writePayload(DataOutputStream output, byte[] payload, String label)
            throws IOException {
        if (payload.length <= 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(label + " payload size is outside bounded range");
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(label + " payload size is outside bounded range");
        }
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException(label + " payload is truncated");
        }
        return payload;
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("checkpoint text exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(label + " text size is outside bounded range");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(label + " text is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
