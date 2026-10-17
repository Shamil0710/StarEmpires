package com.spacesim.persistence;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialStationAdmissionCodecTest {
    @Test void retainsOnlyExplicitIdentitiesInCanonicalImmutableForm() {
        var input = Map.of("station.z", "faction.b", "station.a", "faction.a");
        var encoded = IndustrialStationAdmissionCodec.encode(input);
        var restored = IndustrialStationAdmissionCodec.decode(encoded);
        assertEquals(input, restored);
        assertArrayEquals(encoded, IndustrialStationAdmissionCodec.encode(restored));
        assertThrows(UnsupportedOperationException.class, () -> restored.put("other", "owner"));
        assertEquals(Map.of(), IndustrialStationAdmissionCodec.decode(IndustrialStationAdmissionCodec.encode(Map.of())));
    }

    @Test void rejectsTruncationTrailingDataUnknownSchemaAndUnboundedCounts() throws Exception {
        var encoded = IndustrialStationAdmissionCodec.encode(Map.of("station", "owner"));
        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            assertThrows(IllegalArgumentException.class, () -> IndustrialStationAdmissionCodec.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> IndustrialStationAdmissionCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
        for (int[] header : new int[][] {{2, 0}, {1, -1}, {1, 4097}, {1, 1}}) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { out.writeInt(header[0]); out.writeInt(header[1]); }
            assertThrows(IllegalArgumentException.class, () -> IndustrialStationAdmissionCodec.decode(bytes.toByteArray()));
        }
    }

    @Test void rejectsDuplicateStationsAndInvalidIdentities() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeInt(2);
            out.writeUTF("station"); out.writeUTF("owner.a");
            out.writeUTF("station"); out.writeUTF("owner.b");
        }
        assertThrows(IllegalArgumentException.class, () -> IndustrialStationAdmissionCodec.decode(bytes.toByteArray()));
        for (var invalid : java.util.List.of(Map.of("", "owner"), Map.of("station", " "), Map.of("x".repeat(257), "owner")))
            assertThrows(IllegalArgumentException.class, () -> IndustrialStationAdmissionCodec.encode(invalid));
    }
}
