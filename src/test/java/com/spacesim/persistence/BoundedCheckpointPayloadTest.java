package com.spacesim.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedCheckpointPayloadTest {
    @Test void childCannotReadOrCloseItsFollowingSibling() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(4); out.writeInt(17); out.writeInt(29);
        }
        var closed = new boolean[1];
        var source = new ByteArrayInputStream(bytes.toByteArray()) {
            @Override public void close() { closed[0] = true; }
        };
        try (var parent = new DataInputStream(source)) {
            int value = BoundedCheckpointPayload.decode(parent, 4, "child", (child, length) -> {
                try (var in = new DataInputStream(child)) {
                    assertEquals(4, length);
                    int decoded = in.readInt();
                    assertEquals(-1, in.read());
                    assertEquals(0, in.available());
                    return decoded;
                } catch (IOException failure) { throw new IllegalStateException(failure); }
            });
            assertEquals(17, value);
            assertFalse(closed[0]);
            assertEquals(29, parent.readInt());
        }
        assertTrue(closed[0]);
    }

    @Test void nestedBulkReadAndSkipStopAtDeclaredBoundary() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(9); out.writeInt(5); out.write(new byte[]{1, 2, 3, 4, 5}); out.writeInt(41);
        }
        try (var parent = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            byte[] result = BoundedCheckpointPayload.decode(parent, 9, "outer", (outer, length) -> {
                try (var in = new DataInputStream(outer)) {
                    return BoundedCheckpointPayload.decode(in, 5, "inner", (inner, innerLength) -> {
                        try {
                            assertEquals(0, inner.skip(-1));
                            assertEquals(2, inner.skip(2));
                            byte[] tail = inner.readNBytes(100);
                            assertEquals(-1, inner.read());
                            return tail;
                        } catch (IOException failure) { throw new IllegalStateException(failure); }
                    });
                } catch (IOException failure) { throw new IllegalStateException(failure); }
            });
            assertArrayEquals(new byte[]{3, 4, 5}, result);
            assertEquals(41, parent.readInt());
        }
    }

    @Test void invalidLengthsAndUnconsumedBytesReject() throws IOException {
        for (int length : new int[]{-1, 0, 5, Integer.MAX_VALUE}) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { out.writeInt(length); out.writeInt(17); }
            try (var parent = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThrows(IllegalArgumentException.class, () ->
                        BoundedCheckpointPayload.decode(parent, 4, "child", (child, size) -> fail("Invalid length reached decoder")));
            }
        }
        var truncated = new DataInputStream(new ByteArrayInputStream(new byte[]{0, 0, 0, 5, 1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class, () ->
                BoundedCheckpointPayload.decode(truncated, 8, "child", (child, size) -> fail("Truncation reached decoder")));
        var unread = new DataInputStream(new ByteArrayInputStream(new byte[]{0, 0, 0, 1, 1}));
        assertThrows(IllegalArgumentException.class, () ->
                BoundedCheckpointPayload.decode(unread, 1, "child", (child, size) -> 17));
    }
}
