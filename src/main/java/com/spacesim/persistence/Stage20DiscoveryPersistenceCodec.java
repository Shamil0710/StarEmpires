package com.spacesim.persistence;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceEstimate;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.StarSystemId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Deterministic bounded binary codec for the Stage-20G persistent static-discovery sidecar. */
public final class Stage20DiscoveryPersistenceCodec {
    private static final int MAGIC = 0x53323047; // S20G
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 4096;
    private static final int MAX_OWNERS = 100_000;
    private static final int MAX_ENTRIES_PER_OWNER = 1_000_000;
    private static final int MAX_EVIDENCE_PER_ENTRY = 4096;

    private Stage20DiscoveryPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one validated current discovery sidecar.
     *
     * @param state persistent discovery state
     * @return deterministic bounded bytes
     */
    public static byte[] encode(Stage20DiscoveryPersistentState state) {
        Stage20DiscoveryPersistentState checked = Objects.requireNonNull(state, "state");
        if (checked.envelopeVersion() != Stage20DiscoveryPersistentState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Cannot encode unsupported Stage-20G discovery version");
        }
        requireCount("knowledge owners", checked.knowledgeStates().size(), MAX_OWNERS);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.envelopeVersion());
                output.writeLong(checked.rootSeed());
                writeText(output, checked.worldGenerationVersion());
                writeText(output, checked.worldFingerprint());
                output.writeInt(checked.knowledgeStates().size());
                for (Stage20DiscoveryKnowledgeState knowledge : checked.knowledgeStates()) {
                    writeKnowledge(output, knowledge);
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-20G discovery sidecar exceeds size limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-20G encoding error", exception);
        }
    }

    /**
     * Decodes and validates one complete discovery sidecar.
     *
     * @param bytes complete binary sidecar
     * @return current persistent discovery state
     */
    public static Stage20DiscoveryPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20G discovery sidecar size is outside limits");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20G discovery persistence magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-20G discovery file version: " + fileVersion);
            }
            int envelopeVersion = input.readInt();
            if (envelopeVersion != Stage20DiscoveryPersistentState.CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-20G discovery envelope version: " + envelopeVersion);
            }
            long rootSeed = input.readLong();
            String generationVersion = readText(input, "worldGenerationVersion");
            String fingerprint = readText(input, "worldFingerprint");
            int ownerCount = readCount(input, "knowledge owners", MAX_OWNERS);
            List<Stage20DiscoveryKnowledgeState> knowledge = new ArrayList<>(ownerCount);
            for (int index = 0; index < ownerCount; index++) {
                knowledge.add(readKnowledge(input));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after Stage-20G discovery state");
            }
            return new Stage20DiscoveryPersistentState(
                    envelopeVersion, rootSeed, generationVersion, fingerprint, knowledge);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20G discovery sidecar is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stage-20G discovery sidecar cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Stage-20G discovery sidecar contains invalid values", exception);
        }
    }

    /**
     * Atomically writes one discovery sidecar where the filesystem supports atomic replacement.
     *
     * @param path target file
     * @param state persistent discovery state
     * @throws IOException when the file cannot be written
     */
    public static void write(Path path, Stage20DiscoveryPersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(Objects.requireNonNull(state, "state"));
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage20g-" + prefix;
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
     * Reads one bounded discovery sidecar file.
     *
     * @param path existing source file
     * @return decoded persistent discovery state
     * @throws IOException when the file cannot be read
     */
    public static Stage20DiscoveryPersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20G discovery file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeKnowledge(DataOutputStream output, Stage20DiscoveryKnowledgeState knowledge)
            throws IOException {
        writeText(output, knowledge.ownerId());
        requireCount("static knowledge entries", knowledge.entries().size(), MAX_ENTRIES_PER_OWNER);
        output.writeInt(knowledge.entries().size());
        for (StaticKnowledge entry : knowledge.entries()) {
            writeEntry(output, entry);
        }
    }

    private static Stage20DiscoveryKnowledgeState readKnowledge(DataInputStream input) throws IOException {
        String ownerId = readText(input, "ownerId");
        int entryCount = readCount(input, "static knowledge entries", MAX_ENTRIES_PER_OWNER);
        List<StaticKnowledge> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            entries.add(readEntry(input));
        }
        return new Stage20DiscoveryKnowledgeState(ownerId, entries);
    }

    private static void writeEntry(DataOutputStream output, StaticKnowledge entry) throws IOException {
        output.writeLong(entry.object().systemId().value());
        writeText(output, entry.object().kind().name());
        writeText(output, entry.object().objectId());
        writeText(output, entry.state().name());
        writeOptionalText(output, entry.classificationId());
        output.writeBoolean(entry.knownLocation().isPresent());
        if (entry.knownLocation().isPresent()) {
            writePosition(output, entry.knownLocation().orElseThrow());
        }
        writeResourceKnowledge(output, entry.resourceKnowledge());
        requireCount("discovery evidence", entry.evidence().size(), MAX_EVIDENCE_PER_ENTRY);
        output.writeInt(entry.evidence().size());
        for (DiscoveryEvidence evidence : entry.evidence()) {
            writeText(output, evidence.source().name());
            writeText(output, evidence.provenanceId());
            output.writeDouble(evidence.observedAtSeconds());
            output.writeBoolean(evidence.freshUntilSeconds().isPresent());
            if (evidence.freshUntilSeconds().isPresent()) {
                output.writeDouble(evidence.freshUntilSeconds().getAsDouble());
            }
        }
    }

    private static StaticKnowledge readEntry(DataInputStream input) throws IOException {
        StaticObjectRef object = new StaticObjectRef(
                new StarSystemId(input.readLong()),
                readEnum(input, StaticObjectKind.class, "static object kind"),
                readText(input, "objectId"));
        DiscoveryState state = readEnum(input, DiscoveryState.class, "discovery state");
        Optional<String> classification = readOptionalText(input, "classificationId");
        Optional<LocalPhysicalPosition> location = input.readBoolean()
                ? Optional.of(readPosition(input))
                : Optional.empty();
        ResourceKnowledge resource = readResourceKnowledge(input);
        int evidenceCount = readCount(input, "discovery evidence", MAX_EVIDENCE_PER_ENTRY);
        if (evidenceCount == 0) {
            throw new IllegalArgumentException("static knowledge requires discovery evidence");
        }
        List<DiscoveryEvidence> evidence = new ArrayList<>(evidenceCount);
        for (int index = 0; index < evidenceCount; index++) {
            DiscoverySource source = readEnum(input, DiscoverySource.class, "discovery source");
            String provenance = readText(input, "provenanceId");
            double observedAt = input.readDouble();
            OptionalDouble freshUntil = input.readBoolean()
                    ? OptionalDouble.of(input.readDouble())
                    : OptionalDouble.empty();
            evidence.add(new DiscoveryEvidence(source, provenance, observedAt, freshUntil));
        }
        evidence.sort(null);
        return new StaticKnowledge(
                object,
                state,
                classification,
                location,
                resource,
                evidence,
                evidence.get(0).observedAtSeconds(),
                evidence.get(evidence.size() - 1).observedAtSeconds());
    }

    private static void writeResourceKnowledge(DataOutputStream output, ResourceKnowledge knowledge)
            throws IOException {
        writeText(output, knowledge.level().name());
        writeOptionalText(output, knowledge.resourceFamilyId());
        output.writeBoolean(knowledge.estimate().isPresent());
        if (knowledge.estimate().isPresent()) {
            ResourceEstimate estimate = knowledge.estimate().orElseThrow();
            output.writeDouble(estimate.minimumGradeFraction());
            output.writeDouble(estimate.maximumGradeFraction());
            output.writeDouble(estimate.minimumRecoverableMassKg());
            output.writeDouble(estimate.maximumRecoverableMassKg());
            output.writeDouble(estimate.confidence());
        }
    }

    private static ResourceKnowledge readResourceKnowledge(DataInputStream input) throws IOException {
        ResourceKnowledgeLevel level = readEnum(input, ResourceKnowledgeLevel.class, "resource knowledge level");
        Optional<String> family = readOptionalText(input, "resourceFamilyId");
        Optional<ResourceEstimate> estimate = input.readBoolean()
                ? Optional.of(new ResourceEstimate(
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble()))
                : Optional.empty();
        return new ResourceKnowledge(level, family, estimate);
    }

    private static void writePosition(DataOutputStream output, LocalPhysicalPosition position) throws IOException {
        output.writeLong(position.cellX());
        output.writeLong(position.cellY());
        output.writeDouble(position.offsetXM());
        output.writeDouble(position.offsetYM());
    }

    private static LocalPhysicalPosition readPosition(DataInputStream input) throws IOException {
        return new LocalPhysicalPosition(
                input.readLong(), input.readLong(), input.readDouble(), input.readDouble());
    }

    private static void writeOptionalText(DataOutputStream output, Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeText(output, value.orElseThrow());
        }
    }

    private static Optional<String> readOptionalText(DataInputStream input, String field) throws IOException {
        return input.readBoolean() ? Optional.of(readText(input, field)) : Optional.empty();
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("Persisted text is blank or exceeds character limit");
        }
        output.writeUTF(value);
    }

    private static String readText(DataInputStream input, String field) throws IOException {
        String value = input.readUTF();
        if (value.isBlank() || value.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException(field + " is blank or exceeds character limit");
        }
        return value;
    }

    private static int readCount(DataInputStream input, String field, int maximum) throws IOException {
        int count = input.readInt();
        requireCount(field, count, maximum);
        return count;
    }

    private static void requireCount(String field, int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(field + " count is outside limits");
        }
    }

    private static <E extends Enum<E>> E readEnum(
            DataInputStream input,
            Class<E> enumType,
            String field) throws IOException {
        String name = readText(input, field);
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown persisted " + field + ": " + name, exception);
        }
    }
}
