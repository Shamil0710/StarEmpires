package com.spacesim.persistence;

import com.spacesim.persistence.Stage22FactionProfileBindingState.Binding;

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

/** Deterministic bounded binary codec for the Stage-22.1 faction-profile binding sidecar. */
public final class Stage22FactionProfileBindingCodec {
    private static final int MAGIC = 0x53323250; // S22P
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_TEXT_BYTES = 1024;
    private static final int MAX_BINDINGS = 64;

    private Stage22FactionProfileBindingCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one current validated profile-binding sidecar.
     *
     * @param state profile binding state
     * @return deterministic bounded bytes
     */
    public static byte[] encode(Stage22FactionProfileBindingState state) {
        Stage22FactionProfileBindingState checked = Objects.requireNonNull(state, "state");
        if (checked.envelopeVersion() != Stage22FactionProfileBindingState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Cannot encode unsupported Stage-22.1 binding envelope");
        }
        requireCount(checked.bindings().size());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.envelopeVersion());
                output.writeInt(checked.profileSchemaVersion());
                writeText(output, checked.catalogVersion());
                writeText(output, checked.catalogFingerprint());
                output.writeInt(checked.bindings().size());
                for (Binding binding : checked.bindings()) {
                    writeText(output, binding.stableFactionId());
                    output.writeInt(binding.runtimeFactionId());
                    writeText(output, binding.profileId());
                    output.writeInt(binding.profileVersion());
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-22.1 profile sidecar exceeds size limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-22.1 encoding error", exception);
        }
    }

    /**
     * Decodes one complete profile-binding sidecar and rejects unknown versions or trailing bytes.
     *
     * @param bytes complete binary sidecar
     * @return structurally validated current binding state
     */
    public static Stage22FactionProfileBindingState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-22.1 profile sidecar size is outside limits");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-22.1 profile persistence magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-22.1 profile file version: " + fileVersion);
            }
            int envelopeVersion = input.readInt();
            if (envelopeVersion != Stage22FactionProfileBindingState.CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-22.1 profile binding envelope: " + envelopeVersion);
            }
            int schemaVersion = input.readInt();
            String catalogVersion = readText(input, "catalogVersion");
            String fingerprint = readText(input, "catalogFingerprint");
            int bindingCount = input.readInt();
            requireCount(bindingCount);
            List<Binding> bindings = new ArrayList<>(bindingCount);
            for (int index = 0; index < bindingCount; index++) {
                bindings.add(new Binding(
                        readText(input, "stableFactionId"),
                        input.readInt(),
                        readText(input, "profileId"),
                        input.readInt()));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after Stage-22.1 profile state");
            }
            return new Stage22FactionProfileBindingState(
                    envelopeVersion, schemaVersion, catalogVersion, fingerprint, bindings);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-22.1 profile sidecar is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stage-22.1 profile sidecar cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Stage-22.1 profile sidecar contains invalid values", exception);
        }
    }

    /**
     * Atomically saves one sidecar where the filesystem supports atomic replacement.
     *
     * @param path target sidecar path
     * @param state current profile binding state
     * @throws IOException when the file cannot be written
     */
    public static void write(Path path, Stage22FactionProfileBindingState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(Objects.requireNonNull(state, "state"));
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage22-profile-" + prefix;
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
     * Loads one bounded sidecar file.
     *
     * @param path existing source path
     * @return decoded structurally validated binding state
     * @throws IOException when the file cannot be read
     */
    public static Stage22FactionProfileBindingState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-22.1 profile file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Stage-22.1 profile text length is outside limits");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String field) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(field + " byte length is outside limits");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated " + field);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireCount(int count) {
        if (count <= 0 || count > MAX_BINDINGS) {
            throw new IllegalArgumentException("Stage-22.1 profile binding count is outside limits");
        }
    }
}
