package com.spacesim.persistence;

import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftFlightDeckOperations.RecoveryFailureKind;
import com.spacesim.world.SmallCraftId;

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

/** Deterministic bounded binary codec for M22.8C flight-deck continuity state. */
public final class Stage228FlightDeckPersistenceCodec {
    private static final int MAGIC = 0x53384644; // S8FD
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_PROFILES = 100_000;
    private static final int MAX_REQUESTS = 100_000;
    private static final int MAX_ACTIVE = 100_000;
    private static final int MAX_STRING_BYTES = 64 * 1024;

    private Stage228FlightDeckPersistenceCodec() {
        throw new AssertionError("utility class");
    }

    /** Encodes one exact deterministic C sidecar.
     * @param state validated persistent state
     * @return bounded deterministic bytes
     */
    public static byte[] encode(Stage228FlightDeckPersistentState state) {
        Stage228FlightDeckPersistentState checked = Objects.requireNonNull(state, "state");
        requireCount(checked.profiles().size(), MAX_PROFILES, "profile");
        requireCount(checked.queued().size(), MAX_REQUESTS, "queued request");
        requireCount(checked.active().size(), MAX_ACTIVE, "active operation");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                writeString(out, checked.runtimeVersion());
                writeString(out, checked.semanticContract());

                out.writeInt(checked.profiles().size());
                for (var profile : checked.profiles()) {
                    writeString(out, profile.hostStableId());
                    writeString(out, profile.bayStableId());
                    out.writeDouble(profile.launchWorkSeconds());
                    out.writeDouble(profile.recoveryWorkSeconds());
                }

                out.writeInt(checked.queued().size());
                for (var request : checked.queued()) {
                    writeRequest(out, request);
                }

                out.writeInt(checked.active().size());
                for (var operation : checked.active()) {
                    writeRequest(out, operation.request());
                    out.writeInt(operation.phase().ordinal());
                    out.writeDouble(operation.remainingWorkSeconds());
                    out.writeBoolean(operation.failureKind() != null);
                    if (operation.failureKind() != null) {
                        out.writeInt(operation.failureKind().ordinal());
                    }
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Flight-deck sidecar exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory M22.8C flight-deck encoding failure", exception);
        }
    }

    /** Decodes and fail-closed validates one native C sidecar.
     * @param bytes encoded sidecar
     * @return validated persistent state
     */
    public static Stage228FlightDeckPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Flight-deck sidecar size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid M22.8C flight-deck sidecar magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported M22.8C flight-deck file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = readString(in);
            String semanticContract = readString(in);

            int profileCount = readCount(in, MAX_PROFILES, "profile");
            ArrayList<Stage228FlightDeckPersistentState.DeckProfileState> profiles =
                    new ArrayList<>(profileCount);
            for (int index = 0; index < profileCount; index++) {
                profiles.add(new Stage228FlightDeckPersistentState.DeckProfileState(
                        readString(in),
                        readString(in),
                        in.readDouble(),
                        in.readDouble()));
            }

            int queueCount = readCount(in, MAX_REQUESTS, "queued request");
            ArrayList<Stage228FlightDeckPersistentState.RequestState> queued =
                    new ArrayList<>(queueCount);
            for (int index = 0; index < queueCount; index++) {
                queued.add(readRequest(in));
            }

            int activeCount = readCount(in, MAX_ACTIVE, "active operation");
            ArrayList<Stage228FlightDeckPersistentState.ActiveState> active =
                    new ArrayList<>(activeCount);
            for (int index = 0; index < activeCount; index++) {
                var request = readRequest(in);
                OperationPhase phase = readEnum(
                        in.readInt(), OperationPhase.values(), "operation phase");
                double remainingWork = in.readDouble();
                RecoveryFailureKind failure = null;
                if (in.readBoolean()) {
                    failure = readEnum(
                            in.readInt(),
                            RecoveryFailureKind.values(),
                            "recovery failure");
                }
                active.add(new Stage228FlightDeckPersistentState.ActiveState(
                        request, phase, remainingWork, failure));
            }

            if (in.read() != -1) {
                throw new IllegalArgumentException(
                        "Trailing bytes after M22.8C flight-deck sidecar");
            }
            return new Stage228FlightDeckPersistentState(
                    schemaVersion,
                    runtimeVersion,
                    semanticContract,
                    List.copyOf(profiles),
                    List.copyOf(queued),
                    List.copyOf(active));
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "M22.8C flight-deck sidecar is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException(
                    "Cannot decode M22.8C flight-deck sidecar", exception);
        }
    }

    private static void writeRequest(
            DataOutputStream out,
            Stage228FlightDeckPersistentState.RequestState request) throws IOException {
        out.writeLong(request.craftId().value());
        writeString(out, request.hostStableId());
        writeString(out, request.bayStableId());
        out.writeInt(request.kind().ordinal());
        out.writeLong(request.requestedTick());
    }

    private static Stage228FlightDeckPersistentState.RequestState readRequest(
            DataInputStream in) throws IOException {
        return new Stage228FlightDeckPersistentState.RequestState(
                new SmallCraftId(in.readLong()),
                readString(in),
                readString(in),
                readEnum(in.readInt(), OperationKind.values(), "operation kind"),
                in.readLong());
    }

    private static <E> E readEnum(int ordinal, E[] values, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Flight-deck string length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Flight-deck string length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(DataInputStream in, int maximum, String label) throws IOException {
        int value = in.readInt();
        requireCount(value, maximum, label);
        return value;
    }

    private static void requireCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
    }
}
