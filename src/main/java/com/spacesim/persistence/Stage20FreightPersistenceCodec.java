package com.spacesim.persistence;

import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic bounded binary codec for the complete Stage-20.5 physical-freight sidecar. */
@SuppressWarnings("doclint:missing")
public final class Stage20FreightPersistenceCodec {
    private static final int MAGIC = 0x53323046; // S20F
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_MAP_ROWS = 1_000_000;

    private Stage20FreightPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete current physical-freight snapshot deterministically.
     *
     * @param state validated freight state
     * @return new binary payload
     */
    public static byte[] encode(Stage20FreightPersistentState state) {
        Stage20FreightPersistentState checked = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                writeState(output, checked);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-20.5 freight save exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory freight encoding failure", exception);
        }
    }

    /**
     * Decodes and validates one complete current physical-freight snapshot.
     *
     * @param bytes encoded freight payload
     * @return immutable validated freight state
     */
    public static Stage20FreightPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20.5 freight save size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20.5 freight save magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-20.5 freight file version: " + fileVersion);
            }
            Stage20FreightPersistentState result = readState(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-20.5 freight state");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20.5 freight save is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-20.5 freight save", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-20.5 freight state", exception);
        }
    }

    private static void writeState(DataOutputStream output, Stage20FreightPersistentState state)
            throws IOException {
        output.writeInt(state.schemaVersion());
        output.writeLong(state.rootSeed());
        writeText(output, state.generatorVersion());
        writeText(output, state.worldFingerprint());
        writeText(output, state.materializationVersion());
        writeText(output, state.compatibilityAuthorityVersion());
        output.writeLong(state.nextFleetIdValue());
        output.writeLong(state.nextCargoLotOrdinal());

        writeCount(output, state.freighters().size(), "freighters", MAX_ROWS);
        for (FreighterState fleet : state.freighters()) {
            output.writeLong(fleet.fleetId().value());
            writeText(output, fleet.stableFactionId());
            output.writeInt(fleet.ownershipOrdinal());
            writeText(output, fleet.hullId());
            writeText(output, fleet.fitId());
            output.writeDouble(fleet.cargoCapacityKg());
            output.writeLong(fleet.currentSystemId().value());
            writeKinematics(output, fleet.physicalState());
            writeText(output, fleet.phase().name());
            writeText(output, fleet.activeOrderId());
            output.writeInt(fleet.routeIndex());
            writeStorage(output, fleet.cargoStorage());
        }

        writeCount(output, state.cargoLots().size(), "cargo lots", MAX_ROWS);
        for (CargoLotState lot : state.cargoLots()) {
            writeText(output, lot.lotId());
            output.writeLong(lot.fleetId().value());
            writeText(output, lot.orderId());
            writeText(output, lot.commodityId());
            output.writeDouble(lot.massKg());
            writeText(output, lot.sourceEndpointId());
            writeText(output, lot.sourceProvenanceId());
            output.writeDouble(lot.loadedAtSimulationSeconds());
        }

        writeCount(output, state.orders().size(), "transport orders", MAX_ROWS);
        for (TransportOrderState order : state.orders()) {
            writeText(output, order.orderId());
            output.writeLong(order.fleetId().value());
            writeText(output, order.stableFactionId());
            writeText(output, order.assignmentKind().name());
            writeText(output, order.commodityId());
            writeText(output, order.sourceEndpointId());
            writeText(output, order.destinationEndpointId());
            writeText(output, order.sourceProvenanceId());
            writeCount(output, order.orderedSystems().size(), "ordered systems", MAX_ROWS);
            for (StarSystemId systemId : order.orderedSystems()) {
                output.writeLong(systemId.value());
            }
            output.writeDouble(order.oneWayDeliverySeconds());
            output.writeDouble(order.roundTripCycleSeconds());
            output.writeDouble(order.deliveryDeadlineSeconds());
            output.writeDouble(order.deliveredMassKg());
            output.writeLong(order.delayedDeliveryCount());
        }
    }

    private static Stage20FreightPersistentState readState(DataInputStream input) throws IOException {
        int schemaVersion = input.readInt();
        long rootSeed = input.readLong();
        String generatorVersion = readText(input, "generatorVersion");
        String worldFingerprint = readText(input, "worldFingerprint");
        String materializationVersion = readText(input, "materializationVersion");
        String compatibilityVersion = readText(input, "compatibilityAuthorityVersion");
        long nextFleetId = input.readLong();
        long nextLotOrdinal = input.readLong();

        int fleetCount = readCount(input, "freighters", MAX_ROWS);
        ArrayList<FreighterState> fleets = new ArrayList<>(fleetCount);
        for (int index = 0; index < fleetCount; index++) {
            fleets.add(new FreighterState(
                    new FleetId(input.readLong()),
                    readText(input, "stableFactionId"),
                    input.readInt(),
                    readText(input, "hullId"),
                    readText(input, "fitId"),
                    input.readDouble(),
                    new StarSystemId(input.readLong()),
                    readKinematics(input),
                    readEnum(input, Stage20FreightPersistentState.FreightPhase.class, "freight phase"),
                    readText(input, "activeOrderId"),
                    input.readInt(),
                    readStorage(input)));
        }

        int lotCount = readCount(input, "cargo lots", MAX_ROWS);
        ArrayList<CargoLotState> lots = new ArrayList<>(lotCount);
        for (int index = 0; index < lotCount; index++) {
            lots.add(new CargoLotState(
                    readText(input, "lotId"),
                    new FleetId(input.readLong()),
                    readText(input, "orderId"),
                    readText(input, "commodityId"),
                    input.readDouble(),
                    readText(input, "sourceEndpointId"),
                    readText(input, "sourceProvenanceId"),
                    input.readDouble()));
        }

        int orderCount = readCount(input, "transport orders", MAX_ROWS);
        ArrayList<TransportOrderState> orders = new ArrayList<>(orderCount);
        for (int index = 0; index < orderCount; index++) {
            String orderId = readText(input, "orderId");
            FleetId fleetId = new FleetId(input.readLong());
            String factionId = readText(input, "stableFactionId");
            var kind = readEnum(input, Stage20FreightPersistentState.AssignmentKind.class,
                    "assignment kind");
            String commodityId = readText(input, "commodityId");
            String sourceId = readText(input, "sourceEndpointId");
            String destinationId = readText(input, "destinationEndpointId");
            String provenanceId = readText(input, "sourceProvenanceId");
            int routeCount = readCount(input, "ordered systems", MAX_ROWS);
            ArrayList<StarSystemId> route = new ArrayList<>(routeCount);
            for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
                route.add(new StarSystemId(input.readLong()));
            }
            orders.add(new TransportOrderState(
                    orderId,
                    fleetId,
                    factionId,
                    kind,
                    commodityId,
                    sourceId,
                    destinationId,
                    provenanceId,
                    route,
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readLong()));
        }
        return new Stage20FreightPersistentState(
                schemaVersion,
                rootSeed,
                generatorVersion,
                worldFingerprint,
                materializationVersion,
                compatibilityVersion,
                nextFleetId,
                nextLotOrdinal,
                fleets,
                lots,
                orders);
    }

    private static void writeKinematics(DataOutputStream output, LocalPhysicalKinematics value)
            throws IOException {
        LocalPhysicalPosition position = value.position();
        output.writeLong(position.cellX());
        output.writeLong(position.cellY());
        output.writeDouble(position.offsetXM());
        output.writeDouble(position.offsetYM());
        output.writeDouble(value.velocityXMps());
        output.writeDouble(value.velocityYMps());
    }

    private static LocalPhysicalKinematics readKinematics(DataInputStream input) throws IOException {
        return new LocalPhysicalKinematics(
                new LocalPhysicalPosition(
                        input.readLong(),
                        input.readLong(),
                        input.readDouble(),
                        input.readDouble()),
                input.readDouble(),
                input.readDouble());
    }

    private static void writeStorage(DataOutputStream output, StationStorageSnapshot storage)
            throws IOException {
        writeText(output, storage.stationId());
        writeDoubleMap(output, storage.capacityByStorageClassKg());
        writeDoubleMap(output, storage.commodityMassByIdKg());
        writeIntegerMap(output, storage.productCountById());
    }

    private static StationStorageSnapshot readStorage(DataInputStream input) throws IOException {
        return new StationStorageSnapshot(
                readText(input, "stationId"),
                readDoubleMap(input, "storage capacities"),
                readDoubleMap(input, "commodity masses"),
                readIntegerMap(input, "product counts"));
    }

    private static void writeDoubleMap(DataOutputStream output, Map<String, Double> values)
            throws IOException {
        TreeMap<String, Double> sorted = new TreeMap<>(values);
        writeCount(output, sorted.size(), "double map", MAX_MAP_ROWS);
        for (Map.Entry<String, Double> entry : sorted.entrySet()) {
            writeText(output, entry.getKey());
            output.writeDouble(entry.getValue());
        }
    }

    private static Map<String, Double> readDoubleMap(DataInputStream input, String label)
            throws IOException {
        int count = readCount(input, label, MAX_MAP_ROWS);
        Map<String, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = readText(input, label + " key");
            if (result.putIfAbsent(key, input.readDouble()) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static void writeIntegerMap(DataOutputStream output, Map<String, Integer> values)
            throws IOException {
        TreeMap<String, Integer> sorted = new TreeMap<>(values);
        writeCount(output, sorted.size(), "integer map", MAX_MAP_ROWS);
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            writeText(output, entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readIntegerMap(DataInputStream input, String label)
            throws IOException {
        int count = readCount(input, label, MAX_MAP_ROWS);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = readText(input, label + " key");
            if (result.putIfAbsent(key, input.readInt()) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Stage-20.5 freight text exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(label + " text size is outside bounded range");
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static void writeCount(DataOutputStream output, int count, String label, int limit)
            throws IOException {
        if (count < 0 || count > limit) {
            throw new IllegalArgumentException(label + " count is outside bounded range");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, String label, int limit) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > limit) {
            throw new IllegalArgumentException(label + " count is outside bounded range");
        }
        return count;
    }

    private static <T extends Enum<T>> T readEnum(
            DataInputStream input,
            Class<T> type,
            String label) throws IOException {
        String value = readText(input, label);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, exception);
        }
    }
}
