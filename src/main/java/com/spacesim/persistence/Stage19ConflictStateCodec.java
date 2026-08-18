package com.spacesim.persistence;

import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ConflictStatus;
import com.spacesim.persistence.Stage19ConflictState.MobilizationPosture;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ObservedConsequences;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;

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
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for the separate Stage-19 warfare persistence extension. */
public final class Stage19ConflictStateCodec {
    private static final int MAGIC = 0x53313957;
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_CONFLICTS = 100_000;
    private static final int MAX_OBJECTIVES_PER_CONFLICT = 100_000;

    private Stage19ConflictStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one current Stage-19 conflict snapshot deterministically.
     *
     * @param state immutable current-schema conflict state
     * @return bounded binary conflict payload
     */
    public static byte[] encode(Stage19ConflictState state) {
        Stage19ConflictState checked = Objects.requireNonNull(state, "state");
        if (checked.schemaVersion() != Stage19ConflictState.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-19 conflict schema for encoding: " + checked.schemaVersion());
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
                throw new IllegalArgumentException("Stage-19 conflict save exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-19 conflict encoding failure", exception);
        }
    }

    /**
     * Decodes one current Stage-19 warfare payload.
     *
     * @param bytes encoded conflict payload
     * @return validated immutable conflict state
     */
    public static Stage19ConflictState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Stage-19 conflict save size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-19 conflict save magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-19 conflict file version: " + fileVersion);
            }
            Stage19ConflictState state = readState(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-19 conflict state");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-19 conflict save is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-19 conflict save", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-19 conflict persistent state", exception);
        }
    }

    /**
     * Atomically writes one Stage-19 conflict extension to disk.
     *
     * @param path destination path
     * @param state conflict snapshot
     * @throws IOException when the filesystem cannot write or replace the file
     */
    public static void write(Path path, Stage19ConflictState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "warfare-" + prefix;
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
     * Reads and validates one Stage-19 conflict extension from disk.
     *
     * @param path source path
     * @return decoded current-schema conflict state
     * @throws IOException when the file cannot be read
     */
    public static Stage19ConflictState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Stage-19 conflict save size is outside bounded range");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeState(DataOutputStream output, Stage19ConflictState state) throws IOException {
        output.writeInt(state.schemaVersion());
        output.writeLong(state.simulationTick());
        writeCount(output, state.conflicts().size(), MAX_CONFLICTS, "conflicts");
        for (ConflictSnapshot conflict : state.conflicts()) {
            writeString(output, conflict.conflictId());
            writeString(output, conflict.actorFactionId());
            writeString(output, conflict.opponentFactionId());
            writeString(output, conflict.escalation().name());
            writeString(output, conflict.mobilization().name());
            writeString(output, conflict.status().name());
            writeCount(output, conflict.objectives().size(), MAX_OBJECTIVES_PER_CONFLICT, "objectives");
            for (ObjectiveSnapshot objective : conflict.objectives()) {
                writeString(output, objective.id());
                writeString(output, objective.subjectId());
                output.writeBoolean(objective.mandatory());
                writeString(output, objective.evidence().name());
            }
            ObservedConsequences consequences = conflict.consequences();
            output.writeDouble(consequences.confirmedOwnDestroyedMassKg());
            output.writeDouble(consequences.confirmedOwnUndeliveredCargoKg());
            output.writeDouble(consequences.observedOpponentDestroyedMassKg());
            output.writeDouble(consequences.observedOpponentUndeliveredCargoKg());
            writeString(output, conflict.lastDecision().name());
            output.writeLong(conflict.lastDecisionTick());
        }
    }

    private static Stage19ConflictState readState(DataInputStream input) throws IOException {
        int schemaVersion = input.readInt();
        long simulationTick = input.readLong();
        int conflictCount = readCount(input, MAX_CONFLICTS, "conflicts");
        List<ConflictSnapshot> conflicts = new ArrayList<>(conflictCount);
        for (int conflictIndex = 0; conflictIndex < conflictCount; conflictIndex++) {
            String conflictId = readString(input);
            String actorFactionId = readString(input);
            String opponentFactionId = readString(input);
            EscalationLevel escalation = enumValue(EscalationLevel.class, readString(input), "escalation");
            MobilizationPosture mobilization = enumValue(
                    MobilizationPosture.class, readString(input), "mobilization");
            ConflictStatus status = enumValue(ConflictStatus.class, readString(input), "conflict status");
            int objectiveCount = readCount(input, MAX_OBJECTIVES_PER_CONFLICT, "objectives");
            List<ObjectiveSnapshot> objectives = new ArrayList<>(objectiveCount);
            for (int objectiveIndex = 0; objectiveIndex < objectiveCount; objectiveIndex++) {
                objectives.add(new ObjectiveSnapshot(
                        readString(input),
                        readString(input),
                        input.readBoolean(),
                        enumValue(ObjectiveEvidence.class, readString(input), "objective evidence")));
            }
            ObservedConsequences consequences = new ObservedConsequences(
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble(),
                    input.readDouble());
            Decision lastDecision = enumValue(Decision.class, readString(input), "last decision");
            long lastDecisionTick = input.readLong();
            conflicts.add(new ConflictSnapshot(
                    conflictId,
                    actorFactionId,
                    opponentFactionId,
                    escalation,
                    mobilization,
                    status,
                    objectives,
                    consequences,
                    lastDecision,
                    lastDecisionTick));
        }
        return new Stage19ConflictState(schemaVersion, simulationTick, conflicts);
    }

    private static void writeCount(DataOutputStream output, int count, int maximum, String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count exceeds bounded range");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count exceeds bounded range");
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Stage-19 conflict string exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Stage-19 conflict string length is outside bounded range");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated Stage-19 conflict string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Stage-19 " + label + ": " + value, exception);
        }
    }
}
