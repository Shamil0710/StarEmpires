package com.spacesim.persistence;

import com.spacesim.persistence.Stage228OperationsPersistentState.CarrierWingState;
import com.spacesim.persistence.Stage228OperationsPersistentState.MissionState;
import com.spacesim.persistence.Stage228OperationsPersistentState.PendingDeliveryState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetId;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;

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

/** Deterministic bounded binary codec for the M22.8M D/G/H operations sidecar. */
public final class Stage228OperationsPersistenceCodec {
    private static final int MAGIC = 0x53384f50; // S8OP
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 128 * 1024 * 1024;
    private static final int MAX_MISSIONS = 1_000_000;
    private static final int MAX_DELIVERIES = 1_000_000;
    private static final int MAX_WINGS = 100_000;
    private static final int MAX_WING_CRAFT = 1_000_000;
    private static final int MAX_STRING_BYTES = 64 * 1024;

    private Stage228OperationsPersistenceCodec() {
        throw new AssertionError("utility class");
    }

    /**
     * Encodes one validated operations sidecar.
     *
     * @param state validated persistent state
     * @return deterministic bounded bytes
     */
    public static byte[] encode(Stage228OperationsPersistentState state) {
        Stage228OperationsPersistentState checked = Objects.requireNonNull(state, "state");
        requireCount(checked.missions().size(), MAX_MISSIONS, "mission");
        requireCount(checked.pendingDeliveries().size(), MAX_DELIVERIES, "delivery");
        requireCount(checked.carrierWings().size(), MAX_WINGS, "carrier wing");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                writeString(out, checked.runtimeVersion());
                writeString(out, checked.semanticContract());
                out.writeLong(checked.nextMissionId());

                out.writeInt(checked.missions().size());
                for (MissionState mission : checked.missions()) {
                    out.writeLong(mission.missionId());
                    out.writeLong(mission.craftId().value());
                    out.writeInt(mission.source().ordinal());
                    out.writeInt(mission.type().ordinal());
                    out.writeInt(mission.targetKind().ordinal());
                    writeString(out, mission.targetReferenceId());
                    out.writeLong(mission.submittedTick());
                    out.writeInt(mission.status().ordinal());
                }

                out.writeInt(checked.pendingDeliveries().size());
                for (PendingDeliveryState delivery : checked.pendingDeliveries()) {
                    out.writeLong(delivery.craftId().value());
                    writeString(out, delivery.sourceStationId());
                    writeString(out, delivery.designId());
                }

                out.writeInt(checked.carrierWings().size());
                for (CarrierWingState wing : checked.carrierWings()) {
                    out.writeLong(wing.carrierFleetId().value());
                    writeString(out, wing.hostStableId());
                    writeString(out, wing.stableFactionId());
                    requireCount(wing.craftIds().size(), MAX_WING_CRAFT, "wing craft");
                    out.writeInt(wing.craftIds().size());
                    for (SmallCraftId id : wing.craftIds()) {
                        out.writeLong(id.value());
                    }
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("M22.8M operations sidecar exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory M22.8M operations encoding failure", exception);
        }
    }

    /**
     * Decodes and validates one native operations sidecar.
     *
     * @param bytes encoded bytes
     * @return validated persistent state
     */
    public static Stage228OperationsPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8M operations sidecar size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid M22.8M operations sidecar magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported M22.8M operations file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = readString(in);
            String semanticContract = readString(in);
            long nextMissionId = in.readLong();

            int missionCount = readCount(in, MAX_MISSIONS, "mission");
            ArrayList<MissionState> missions = new ArrayList<>(missionCount);
            for (int index = 0; index < missionCount; index++) {
                missions.add(new MissionState(
                        in.readLong(),
                        new SmallCraftId(in.readLong()),
                        readEnum(in.readInt(), OrderSource.values(), "order source"),
                        readEnum(in.readInt(), MissionType.values(), "mission type"),
                        readEnum(in.readInt(), TargetKind.values(), "target kind"),
                        readString(in),
                        in.readLong(),
                        readEnum(in.readInt(), MissionStatus.values(), "mission status")));
            }

            int deliveryCount = readCount(in, MAX_DELIVERIES, "delivery");
            ArrayList<PendingDeliveryState> deliveries = new ArrayList<>(deliveryCount);
            for (int index = 0; index < deliveryCount; index++) {
                deliveries.add(new PendingDeliveryState(
                        new SmallCraftId(in.readLong()),
                        readString(in),
                        readString(in)));
            }

            int wingCount = readCount(in, MAX_WINGS, "carrier wing");
            ArrayList<CarrierWingState> wings = new ArrayList<>(wingCount);
            for (int index = 0; index < wingCount; index++) {
                FleetId fleetId = new FleetId(in.readLong());
                String host = readString(in);
                String faction = readString(in);
                int craftCount = readCount(in, MAX_WING_CRAFT, "wing craft");
                ArrayList<SmallCraftId> craft = new ArrayList<>(craftCount);
                for (int row = 0; row < craftCount; row++) {
                    craft.add(new SmallCraftId(in.readLong()));
                }
                wings.add(new CarrierWingState(
                        fleetId, host, faction, List.copyOf(craft)));
            }

            if (in.read() != -1) {
                throw new IllegalArgumentException(
                        "Trailing bytes after M22.8M operations sidecar");
            }
            return new Stage228OperationsPersistentState(
                    schemaVersion,
                    runtimeVersion,
                    semanticContract,
                    nextMissionId,
                    List.copyOf(missions),
                    List.copyOf(deliveries),
                    List.copyOf(wings));
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "M22.8M operations sidecar is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException(
                    "Cannot decode M22.8M operations sidecar", exception);
        }
    }

    private static <E> E readEnum(int ordinal, E[] values, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                    "Invalid " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("M22.8M string length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("M22.8M string length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(
            DataInputStream in,
            int maximum,
            String label) throws IOException {
        int count = in.readInt();
        requireCount(count, maximum, label);
        return count;
    }

    private static void requireCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
    }
}
