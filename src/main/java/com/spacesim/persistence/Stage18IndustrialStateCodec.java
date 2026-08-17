package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.ProcessKind;
import com.spacesim.persistence.Stage18IndustrialState.ProcessOrderSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic bounded binary codec for the Stage-18 industrial persistence extension. */
public final class Stage18IndustrialStateCodec {
    private static final int MAGIC = 0x53313849;
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_MAP_ROWS = 1_000_000;

    private Stage18IndustrialStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one current Stage-18 industrial snapshot deterministically.
     *
     * @param state immutable current-schema industrial state
     * @return binary industrial payload
     */
    public static byte[] encode(Stage18IndustrialState state) {
        Stage18IndustrialState checked = Objects.requireNonNull(state, "state");
        if (checked.schemaVersion() != Stage18IndustrialState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported industrial schema for encoding: " + checked.schemaVersion());
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                writeState(output, checked);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("Stage-18 industrial save exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-18 industrial encoding failure", exception);
        }
    }

    /**
     * Decodes one industrial payload and requires exact compatibility with current content semantics.
     *
     * @param bytes encoded industrial payload
     * @return validated immutable industrial state
     */
    public static Stage18IndustrialState decode(byte[] bytes) {
        return decodeAgainstFingerprint(bytes, Stage18IndustrialContentFingerprint.current());
    }

    /**
     * Decodes one payload against an explicit expected content fingerprint.
     *
     * @param bytes encoded industrial payload
     * @param expectedContentFingerprint required semantic fingerprint
     * @return validated immutable industrial state
     */
    public static Stage18IndustrialState decodeAgainstFingerprint(
            byte[] bytes, String expectedContentFingerprint) {
        Objects.requireNonNull(bytes, "bytes");
        String expected = Objects.requireNonNull(expectedContentFingerprint, "expectedContentFingerprint");
        if (bytes.length <= 0 || bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Stage-18 industrial save size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-18 industrial save magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-18 industrial file version: " + fileVersion);
            }
            Stage18IndustrialState state = readState(input);
            if (!state.contentFingerprint().equals(expected)) {
                throw new IllegalArgumentException(
                        "Stage-18 industrial content fingerprint mismatch: save="
                                + state.contentFingerprint() + ",runtime=" + expected);
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-18 industrial state");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-18 industrial save is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-18 industrial save", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-18 industrial persistent state", exception);
        }
    }

    /**
     * Atomically writes one industrial snapshot to disk.
     *
     * @param path destination path
     * @param state industrial snapshot
     * @throws IOException when the filesystem cannot write/replace the file
     */
    public static void write(Path path, Stage18IndustrialState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "industrial-" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reads and validates one current-content industrial snapshot from disk.
     *
     * @param path source path
     * @return decoded industrial state
     * @throws IOException when the file cannot be read
     */
    public static Stage18IndustrialState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Stage-18 industrial save size is outside bounded range");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeState(DataOutputStream output, Stage18IndustrialState state) throws IOException {
        output.writeInt(state.schemaVersion());
        writeString(output, state.contentFingerprint());
        output.writeLong(state.simulationTick());

        writeCount(output, state.sources().size(), "sources");
        for (PhysicalSourceSnapshot source : state.sources()) {
            writeString(output, source.sourceId());
            writeString(output, source.sourceKind().name());
            writeString(output, source.sourceTypeId());
            writeString(output, source.environment().name());
            writeString(output, source.outputCommodityId());
            output.writeDouble(source.initialAccessibleMassKg());
            output.writeDouble(source.remainingAccessibleMassKg());
            output.writeDouble(source.gradeFraction());
            output.writeDouble(source.sourceRecoveryFraction());
            writeStringSet(output, source.requiredCapabilityTags());
        }

        writeCount(output, state.stationStorages().size(), "stationStorages");
        for (StationStorageSnapshot storage : state.stationStorages()) {
            writeString(output, storage.stationId());
            writeDoubleMap(output, storage.capacityByStorageClassKg());
            writeDoubleMap(output, storage.commodityMassByIdKg());
            writeIntegerMap(output, storage.productCountById());
        }

        writeCount(output, state.facilities().size(), "facilities");
        for (FacilityInstallationSnapshot installation : state.facilities()) {
            writeString(output, installation.stationId());
            InstalledFacilityState facility = installation.state();
            writeString(output, facility.facilityInstanceId());
            writeString(output, facility.definitionId());
            output.writeDouble(facility.conditionFraction());
            output.writeDouble(facility.allocatedProcessPowerW());
            output.writeDouble(facility.availableHeatRejectionW());
            output.writeDouble(facility.availableLaborUnits());
            output.writeDouble(facility.availableMaintenanceWorkRate());
            writeString(output, facility.locationTag());
            output.writeBoolean(facility.enabled());
        }

        writeCount(output, state.yards().size(), "yards");
        for (YardInstallationSnapshot installation : state.yards()) {
            writeString(output, installation.stationId());
            InstalledYardState yard = installation.state();
            writeString(output, yard.yardInstanceId());
            writeString(output, yard.yardDefinitionId());
            output.writeDouble(yard.conditionFraction());
            output.writeDouble(yard.allocatedIntegrationPowerW());
            output.writeDouble(yard.availableIntegrationWorkRate());
            output.writeInt(yard.availableLaborCapacity());
            output.writeInt(yard.availableAutomationCapacity());
            output.writeBoolean(yard.enabled());
        }

        writeCount(output, state.constructionOrders().size(), "constructionOrders");
        for (ConstructionOrderSnapshot order : state.constructionOrders()) {
            writeString(output, order.orderId());
            writeString(output, order.facilityInstanceId());
            writeString(output, order.facilityDefinitionId());
            writeString(output, order.stationId());
            writeString(output, order.locationTag());
            writeDoubleMap(output, order.requiredMassByCommodityKg());
            writeDoubleMap(output, order.deliveredMassByCommodityKg());
            output.writeDouble(order.requiredWorkSeconds());
            output.writeDouble(order.completedWorkSeconds());
            writeString(output, order.status().name());
        }

        writeCount(output, state.processOrders().size(), "processOrders");
        for (ProcessOrderSnapshot order : state.processOrders()) {
            writeString(output, order.orderId());
            writeString(output, order.kind().name());
            writeString(output, order.operationId());
            writeString(output, order.stationId());
            writeString(output, order.sourceId());
            output.writeDouble(order.requestedAmount());
            output.writeInt(order.requestedUnits());
            output.writeDouble(order.completedFraction());
            writeDoubleMap(output, order.reservedCommodityMassByIdKg());
            writeIntegerMap(output, order.reservedProductCountById());
        }
    }

    private static Stage18IndustrialState readState(DataInputStream input) throws IOException {
        int schema = input.readInt();
        if (schema != Stage18IndustrialState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 industrial logical schema: " + schema);
        }
        String fingerprint = requireString(readString(input), "contentFingerprint");
        long tick = input.readLong();
        if (tick < 0L) {
            throw new IllegalArgumentException("industrial simulationTick must be non-negative");
        }

        int sourceCount = readCount(input, "sources");
        List<PhysicalSourceSnapshot> sources = new ArrayList<>(sourceCount);
        for (int index = 0; index < sourceCount; index++) {
            sources.add(new PhysicalSourceSnapshot(
                    requireString(readString(input), "sourceId"),
                    SourceKind.valueOf(requireString(readString(input), "sourceKind")),
                    requireString(readString(input), "sourceTypeId"),
                    ExtractionEnvironment.valueOf(requireString(readString(input), "environment")),
                    requireString(readString(input), "outputCommodityId"),
                    input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(),
                    readStringSet(input)));
        }

        int storageCount = readCount(input, "stationStorages");
        List<StationStorageSnapshot> storages = new ArrayList<>(storageCount);
        for (int index = 0; index < storageCount; index++) {
            storages.add(new StationStorageSnapshot(
                    requireString(readString(input), "stationId"),
                    readDoubleMap(input), readDoubleMap(input), readIntegerMap(input)));
        }

        int facilityCount = readCount(input, "facilities");
        List<FacilityInstallationSnapshot> facilities = new ArrayList<>(facilityCount);
        for (int index = 0; index < facilityCount; index++) {
            String stationId = requireString(readString(input), "facility.stationId");
            InstalledFacilityState state = new InstalledFacilityState(
                    requireString(readString(input), "facilityInstanceId"),
                    requireString(readString(input), "definitionId"),
                    input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble(),
                    requireString(readString(input), "locationTag"), input.readBoolean());
            facilities.add(new FacilityInstallationSnapshot(stationId, state));
        }

        int yardCount = readCount(input, "yards");
        List<YardInstallationSnapshot> yards = new ArrayList<>(yardCount);
        for (int index = 0; index < yardCount; index++) {
            String stationId = requireString(readString(input), "yard.stationId");
            InstalledYardState state = new InstalledYardState(
                    requireString(readString(input), "yardInstanceId"),
                    requireString(readString(input), "yardDefinitionId"),
                    input.readDouble(), input.readDouble(), input.readDouble(), input.readInt(), input.readInt(),
                    input.readBoolean());
            yards.add(new YardInstallationSnapshot(stationId, state));
        }

        int constructionCount = readCount(input, "constructionOrders");
        List<ConstructionOrderSnapshot> constructionOrders = new ArrayList<>(constructionCount);
        for (int index = 0; index < constructionCount; index++) {
            constructionOrders.add(new ConstructionOrderSnapshot(
                    requireString(readString(input), "construction.orderId"),
                    requireString(readString(input), "construction.facilityInstanceId"),
                    requireString(readString(input), "construction.facilityDefinitionId"),
                    requireString(readString(input), "construction.stationId"),
                    requireString(readString(input), "construction.locationTag"),
                    readDoubleMap(input), readDoubleMap(input), input.readDouble(), input.readDouble(),
                    OrderStatus.valueOf(requireString(readString(input), "construction.status"))));
        }

        int processCount = readCount(input, "processOrders");
        List<ProcessOrderSnapshot> processOrders = new ArrayList<>(processCount);
        for (int index = 0; index < processCount; index++) {
            processOrders.add(new ProcessOrderSnapshot(
                    requireString(readString(input), "process.orderId"),
                    ProcessKind.valueOf(requireString(readString(input), "process.kind")),
                    requireString(readString(input), "process.operationId"),
                    requireString(readString(input), "process.stationId"),
                    readString(input),
                    input.readDouble(), input.readInt(), input.readDouble(),
                    readDoubleMap(input), readIntegerMap(input)));
        }
        return new Stage18IndustrialState(
                schema, fingerprint, tick, sources, storages, facilities, yards, constructionOrders, processOrders);
    }

    private static void writeStringSet(DataOutputStream output, Set<String> values) throws IOException {
        writeCount(output, values.size(), "stringSet");
        for (String value : values) {
            writeString(output, value);
        }
    }

    private static Set<String> readStringSet(DataInputStream input) throws IOException {
        int count = readCount(input, "stringSet");
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String value = requireString(readString(input), "set value");
            if (!result.add(value)) {
                throw new IllegalArgumentException("Duplicate set value in industrial save: " + value);
            }
        }
        return Set.copyOf(result);
    }

    private static void writeDoubleMap(DataOutputStream output, Map<String, Double> values) throws IOException {
        writeMapCount(output, values.size(), "doubleMap");
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            writeString(output, entry.getKey());
            output.writeDouble(entry.getValue());
        }
    }

    private static Map<String, Double> readDoubleMap(DataInputStream input) throws IOException {
        int count = readMapCount(input, "doubleMap");
        Map<String, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = requireString(readString(input), "doubleMap key");
            double value = input.readDouble();
            if (!Double.isFinite(value) || value < 0d) {
                throw new IllegalArgumentException("Industrial double-map value must be finite and non-negative");
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate industrial double-map key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static void writeIntegerMap(DataOutputStream output, Map<String, Integer> values) throws IOException {
        writeMapCount(output, values.size(), "integerMap");
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            writeString(output, entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readIntegerMap(DataInputStream input) throws IOException {
        int count = readMapCount(input, "integerMap");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = requireString(readString(input), "integerMap key");
            int value = input.readInt();
            if (value < 0) {
                throw new IllegalArgumentException("Industrial integer-map value must be non-negative");
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate industrial integer-map key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static void writeCount(DataOutputStream output, int count, String name) throws IOException {
        if (count < 0 || count > MAX_ROWS) {
            throw new IllegalArgumentException(name + " count exceeds industrial save bound");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, String name) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ROWS) {
            throw new IllegalArgumentException(name + " count exceeds industrial save bound");
        }
        return count;
    }

    private static void writeMapCount(DataOutputStream output, int count, String name) throws IOException {
        if (count < 0 || count > MAX_MAP_ROWS) {
            throw new IllegalArgumentException(name + " count exceeds industrial save bound");
        }
        output.writeInt(count);
    }

    private static int readMapCount(DataInputStream input, String name) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_MAP_ROWS) {
            throw new IllegalArgumentException(name + " count exceeds industrial save bound");
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Industrial save string exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Invalid industrial save string length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Industrial save string is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String requireString(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
