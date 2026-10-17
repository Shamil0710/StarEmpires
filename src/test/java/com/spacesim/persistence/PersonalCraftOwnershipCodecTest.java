package com.spacesim.persistence;

import com.spacesim.world.SmallCraftId;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalCraftOwnershipCodecTest {
    @Test void canonicalRightsAreIndependentOfLiveRegistryAndMalformedInputIsRejected() {
        var ids = List.of(new SmallCraftId(2), new SmallCraftId(7));
        var bytes = PersonalCraftOwnershipCodec.encode(ids);
        assertEquals(ids, PersonalCraftOwnershipCodec.decode(bytes));
        assertArrayEquals(bytes, PersonalCraftOwnershipCodec.encode(List.of(ids.get(1), ids.get(0))));
        assertEquals(List.of(), PersonalCraftOwnershipCodec.decode(PersonalCraftOwnershipCodec.encode(List.of())));
        assertThrows(IllegalArgumentException.class, () -> PersonalCraftOwnershipCodec.encode(List.of(ids.get(0), ids.get(0))));
        assertThrows(IllegalArgumentException.class, () -> PersonalCraftOwnershipCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> PersonalCraftOwnershipCodec.decode(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        var duplicate = bytes.clone(); ByteBuffer.wrap(duplicate).putLong(16, 2);
        assertThrows(IllegalArgumentException.class, () -> PersonalCraftOwnershipCodec.decode(duplicate));
        var oversized = bytes.clone(); ByteBuffer.wrap(oversized).putInt(4, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> PersonalCraftOwnershipCodec.decode(oversized));
    }
}
