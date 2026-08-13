package com.spacesim.persistence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class WorldIoSupport {
    private static final int MAX_STRING_BYTES = 1024 * 1024;

    private WorldIoSupport() {
        throw new AssertionError("Utility class");
    }

    static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Persistent string too large");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Invalid persistent string length");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Persistent string truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeCount(DataOutputStream out, int count, int maximum, String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        out.writeInt(count);
    }

    static int readCount(DataInputStream in, int maximum, String label) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }

    static void writeOptionalEntityId(DataOutputStream out, EntityId id) throws IOException {
        out.writeBoolean(id != null);
        if (id != null) {
            out.writeLong(id.value());
        }
    }

    static EntityId readOptionalEntityId(DataInputStream in) throws IOException {
        return in.readBoolean() ? new EntityId(in.readLong()) : null;
    }
}
