package com.spacesim.persistence;

import com.spacesim.world.WorldFactionIdentityState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Binary persistence helpers for Stage-17 world-defined faction identity metadata. */
final class WorldFactionIdentityBinary {
    private static final int MAX_IDENTITIES = 10_000;
    private static final int MAX_STRING_BYTES = 16 * 1024;

    private WorldFactionIdentityBinary() {
        throw new AssertionError("WorldFactionIdentityBinary does not create instances");
    }

    static void write(DataOutputStream output, List<WorldFactionIdentityState> identities)
            throws IOException {
        Objects.requireNonNull(output, "Faction identity output not set");
        List<WorldFactionIdentityState> values = Objects.requireNonNull(
                identities, "Faction identities not set");
        writeCount(output, values.size());
        for (WorldFactionIdentityState identity : values) {
            WorldFactionIdentityState value = Objects.requireNonNull(
                    identity, "World faction identity not set");
            writeString(output, value.stableFactionId());
            output.writeInt(value.runtimeFactionId());
            writeString(output, value.displayName());
            writeString(output, value.origin().name());
        }
    }

    static List<WorldFactionIdentityState> read(DataInputStream input) throws IOException {
        Objects.requireNonNull(input, "Faction identity input not set");
        int count = readCount(input);
        List<WorldFactionIdentityState> identities = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            identities.add(new WorldFactionIdentityState(
                    readRequiredString(input, "stableFactionId"),
                    input.readInt(),
                    readRequiredString(input, "displayName"),
                    WorldFactionIdentityState.Origin.valueOf(
                            readRequiredString(input, "origin"))));
        }
        return List.copyOf(identities);
    }

    private static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count < 0 || count > MAX_IDENTITIES) {
            throw new IllegalArgumentException("Faction identity count exceeds supported bound");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_IDENTITIES) {
            throw new IllegalArgumentException("Faction identity count is corrupted");
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "Faction identity string not set")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Faction identity string exceeds supported length");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readRequiredString(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Faction identity " + label + " length is corrupted");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Faction identity " + label + " is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
