package com.spacesim.persistence;

import com.spacesim.components.EngineeringComponent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded deterministic binary codec for the complete Stage-17.5 engineering persistence DTO. */
final class Stage228EngineeringStateBinaryCodec {
    private static final int MAGIC = 0x45313735; // E175
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 64 * 1024;
    private static final int MAX_ROWS = 4096;

    private Stage228EngineeringStateBinaryCodec() {
        throw new AssertionError("No instances");
    }

    static byte[] encode(EntityState.EngineeringState state) {
        EntityState.EngineeringState canonical = canonicalize(Objects.requireNonNull(state, "state"));
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                writeString(out, canonical.hullId());
                writeInstalledModules(out, canonical.installedModules());
                writeConsumables(out, canonical.consumables());
                writeNonNegativeDouble(out, canonical.sharedBusEnergyJ(), "sharedBusEnergyJ");
                writeNonNegativeDouble(out, canonical.shipHeatStoredJ(), "shipHeatStoredJ");
                writeMountDoubles(out, canonical.localHeatJByMount(), "localHeatJByMount");
                writeMountDoubles(out, canonical.thrustLimitNByMount(), "thrustLimitNByMount");
                writeNonNegativeDouble(out, canonical.coolantBusCapacityW(), "coolantBusCapacityW");
                writeMountDoubles(out, canonical.ftlCooldownSecondsByMount(), "ftlCooldownSecondsByMount");
                writeInstance(out, canonical.instanceState());
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Engineering payload exceeds bounded size");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory engineering encoding failure", exception);
        }
    }

    static EntityState.EngineeringState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Engineering payload size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid engineering payload magic");
            }
            int version = in.readInt();
            if (version != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported engineering payload version: " + version);
            }
            EntityState.EngineeringState decoded = new EntityState.EngineeringState(
                    readString(in),
                    readInstalledModules(in),
                    readConsumables(in),
                    readNonNegativeDouble(in, "sharedBusEnergyJ"),
                    readNonNegativeDouble(in, "shipHeatStoredJ"),
                    readMountDoubles(in, "localHeatJByMount"),
                    readMountDoubles(in, "thrustLimitNByMount"),
                    readNonNegativeDouble(in, "coolantBusCapacityW"),
                    readMountDoubles(in, "ftlCooldownSecondsByMount"),
                    readInstance(in));
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after engineering payload");
            }
            return canonicalize(decoded);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Engineering payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode engineering payload", exception);
        }
    }

    private static EntityState.EngineeringState canonicalize(EntityState.EngineeringState state) {
        EngineeringComponent restored = EngineeringStatePersistenceMapper.restore(state);
        return Objects.requireNonNull(EngineeringStatePersistenceMapper.capture(restored), "canonical engineering state");
    }

    private static void writeInstalledModules(
            DataOutputStream out,
            List<EntityState.InstalledModuleState> modules) throws IOException {
        writeCount(out, modules.size(), "installedModules");
        for (EntityState.InstalledModuleState module : modules) {
            writeString(out, module.mountId());
            writeString(out, module.moduleId());
        }
    }

    private static List<EntityState.InstalledModuleState> readInstalledModules(DataInputStream in) throws IOException {
        int count = readCount(in, "installedModules");
        ArrayList<EntityState.InstalledModuleState> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new EntityState.InstalledModuleState(readString(in), readString(in)));
        }
        return List.copyOf(result);
    }

    private static void writeConsumables(
            DataOutputStream out,
            EntityState.EngineeringConsumableState consumables) throws IOException {
        EntityState.EngineeringConsumableState checked = Objects.requireNonNull(consumables, "consumables");
        writeNonNegativeDouble(out, checked.cargoMassKg(), "cargoMassKg");
        writeNonNegativeDouble(out, checked.storesMassKg(), "storesMassKg");
        writeNonNegativeDouble(out, checked.missionPayloadMassKg(), "missionPayloadMassKg");
        writeNonNegativeDouble(out, checked.missionIntegrationVolumeM3(), "missionIntegrationVolumeM3");
        writeCount(out, checked.interfaceLoads().size(), "interfaceLoads");
        for (EntityState.EngineeringConsumableLoadState load : checked.interfaceLoads()) {
            writeString(out, load.mountId());
            writeString(out, load.interfaceId());
            writeString(out, load.kindName());
            writeNonNegativeDouble(out, load.amount(), "load.amount");
            writeNonNegativeDouble(out, load.massKg(), "load.massKg");
            if (load.itemCount() < 0L) {
                throw new IllegalArgumentException("load.itemCount must be non-negative");
            }
            out.writeLong(load.itemCount());
        }
    }

    private static EntityState.EngineeringConsumableState readConsumables(DataInputStream in) throws IOException {
        double cargo = readNonNegativeDouble(in, "cargoMassKg");
        double stores = readNonNegativeDouble(in, "storesMassKg");
        double missionPayload = readNonNegativeDouble(in, "missionPayloadMassKg");
        double missionVolume = readNonNegativeDouble(in, "missionIntegrationVolumeM3");
        int count = readCount(in, "interfaceLoads");
        ArrayList<EntityState.EngineeringConsumableLoadState> loads = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String mountId = readString(in);
            String interfaceId = readString(in);
            String kind = readString(in);
            double amount = readNonNegativeDouble(in, "load.amount");
            double mass = readNonNegativeDouble(in, "load.massKg");
            long itemCount = in.readLong();
            if (itemCount < 0L) {
                throw new IllegalArgumentException("load.itemCount must be non-negative");
            }
            loads.add(new EntityState.EngineeringConsumableLoadState(
                    mountId, interfaceId, kind, amount, mass, itemCount));
        }
        return new EntityState.EngineeringConsumableState(
                cargo, stores, missionPayload, missionVolume, List.copyOf(loads));
    }

    private static void writeInstance(DataOutputStream out, EntityState.ShipInstanceState state) throws IOException {
        out.writeBoolean(state != null);
        if (state == null) {
            return;
        }
        writeMountDoubles(out, state.compartmentIntegrityById(), "compartmentIntegrityById");
        writeMountDoubles(out, state.moduleIntegrityByMount(), "moduleIntegrityByMount");
        writeCount(out, state.shieldsByMount().size(), "shieldsByMount");
        for (EntityState.ShieldRuntimeState shield : state.shieldsByMount()) {
            writeString(out, shield.mountId());
            writeNonNegativeDouble(out, shield.reserveJ(), "shield.reserveJ");
            writeNonNegativeDouble(out, shield.accumulatedHeatJ(), "shield.accumulatedHeatJ");
            out.writeBoolean(shield.collapsed());
            writeNonNegativeDouble(out, shield.restartRemainingSeconds(), "shield.restartRemainingSeconds");
            writeUnitInterval(out, shield.emitterIntegrity(), "shield.emitterIntegrity");
        }
        writeMountDoubles(out, state.serviceAgeByMount(), "serviceAgeByMount");
        writeCount(out, state.weaponFeeds().size(), "weaponFeeds");
        for (EntityState.WeaponFeedState feed : state.weaponFeeds()) {
            writeString(out, feed.mountId());
            writeString(out, feed.interfaceId());
            writeString(out, feed.ammunitionContentId());
        }
        writeMountDoubles(out, state.weaponCooldownByMount(), "weaponCooldownByMount");
    }

    private static EntityState.ShipInstanceState readInstance(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        List<EntityState.MountDoubleState> compartments = readMountDoubles(in, "compartmentIntegrityById");
        List<EntityState.MountDoubleState> modules = readMountDoubles(in, "moduleIntegrityByMount");
        int shieldCount = readCount(in, "shieldsByMount");
        ArrayList<EntityState.ShieldRuntimeState> shields = new ArrayList<>(shieldCount);
        for (int index = 0; index < shieldCount; index++) {
            shields.add(new EntityState.ShieldRuntimeState(
                    readString(in),
                    readNonNegativeDouble(in, "shield.reserveJ"),
                    readNonNegativeDouble(in, "shield.accumulatedHeatJ"),
                    in.readBoolean(),
                    readNonNegativeDouble(in, "shield.restartRemainingSeconds"),
                    readUnitInterval(in, "shield.emitterIntegrity")));
        }
        List<EntityState.MountDoubleState> maintenance = readMountDoubles(in, "serviceAgeByMount");
        int feedCount = readCount(in, "weaponFeeds");
        ArrayList<EntityState.WeaponFeedState> feeds = new ArrayList<>(feedCount);
        for (int index = 0; index < feedCount; index++) {
            feeds.add(new EntityState.WeaponFeedState(readString(in), readString(in), readString(in)));
        }
        return new EntityState.ShipInstanceState(
                compartments,
                modules,
                List.copyOf(shields),
                maintenance,
                List.copyOf(feeds),
                readMountDoubles(in, "weaponCooldownByMount"));
    }

    private static void writeMountDoubles(
            DataOutputStream out,
            List<EntityState.MountDoubleState> values,
            String label) throws IOException {
        writeCount(out, values.size(), label);
        for (EntityState.MountDoubleState row : values) {
            writeString(out, row.mountId());
            writeNonNegativeDouble(out, row.value(), label + ".value");
        }
    }

    private static List<EntityState.MountDoubleState> readMountDoubles(
            DataInputStream in,
            String label) throws IOException {
        int count = readCount(in, label);
        ArrayList<EntityState.MountDoubleState> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new EntityState.MountDoubleState(
                    readString(in), readNonNegativeDouble(in, label + ".value")));
        }
        return List.copyOf(result);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeCount(DataOutputStream out, int count, String label) throws IOException {
        if (count < 0 || count > MAX_ROWS) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
        out.writeInt(count);
    }

    private static int readCount(DataInputStream in, String label) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ROWS) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
        return count;
    }

    private static void writeNonNegativeDouble(DataOutputStream out, double value, String label) throws IOException {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
        out.writeDouble(value);
    }

    private static double readNonNegativeDouble(DataInputStream in, String label) throws IOException {
        double value = in.readDouble();
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
        return value;
    }

    private static void writeUnitInterval(DataOutputStream out, double value, String label) throws IOException {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
        out.writeDouble(value);
    }

    private static double readUnitInterval(DataInputStream in, String label) throws IOException {
        double value = in.readDouble();
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
        return value;
    }
}
