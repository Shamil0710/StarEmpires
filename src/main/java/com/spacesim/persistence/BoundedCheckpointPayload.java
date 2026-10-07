package com.spacesim.persistence;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Reads a nested checkpoint within its declared boundary without copying its complete bytes. */
final class BoundedCheckpointPayload {
    private BoundedCheckpointPayload() { }

    /** Decoder of one complete bounded checkpoint. */
    @FunctionalInterface
    interface Decoder<T> {
        /**
         * Decodes the complete child stream.
         * @param source stream limited to the child payload
         * @param length exact declared payload length
         * @return validated decoded state
         */
        T decode(InputStream source, int length);
    }

    /**
     * Decodes a length-prefixed child without allowing it to consume its following sibling.
     * @param parent owning checkpoint input
     * @param maximum permitted child length
     * @param label diagnostic payload name
     * @param decoder complete child decoder
     * @param <T> decoded state type
     * @return validated child state
     * @throws IOException when the input cannot be read
     */
    static <T> T decode(DataInputStream parent, int maximum, String label, Decoder<T> decoder)
            throws IOException {
        int length = parent.readInt();
        if (length <= 0 || length > maximum) {
            throw new IllegalArgumentException(label + " payload size outside bounded range");
        }
        if (length > parent.available()) {
            throw new IllegalArgumentException(label + " payload is truncated");
        }
        var child = new PayloadInput(parent, length);
        T result = Objects.requireNonNull(decoder, "decoder").decode(child, length);
        if (child.remaining != 0) {
            throw new IllegalArgumentException("Trailing bytes after " + label + " payload");
        }
        return result;
    }

    /** Child boundary; closing it leaves its owning input available for subsequent payloads. */
    private static final class PayloadInput extends InputStream {
        private final InputStream parent;
        private int remaining;

        private PayloadInput(InputStream parent, int length) {
            this.parent = parent;
            remaining = length;
        }

        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = parent.read();
            if (value < 0) throw new EOFException("Truncated nested checkpoint payload");
            remaining--;
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int count = parent.read(bytes, offset, Math.min(length, remaining));
            if (count < 0) throw new EOFException("Truncated nested checkpoint payload");
            remaining -= count;
            return count;
        }

        @Override public long skip(long count) throws IOException {
            long skipped = parent.skip(Math.min(Math.max(count, 0), remaining));
            remaining -= (int) skipped;
            return skipped;
        }

        @Override public int available() throws IOException {
            return Math.min(remaining, parent.available());
        }

        @Override public void close() { /* The parent owns its lifetime. */ }
    }
}
