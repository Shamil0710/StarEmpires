package com.spacesim.persistence;

import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.economy.Stage23YardConstructionWorkQueue.Order;
import com.spacesim.economy.Stage23YardConstructionWorkQueue.State;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23YardConstructionPersistenceCodecTest {
    @Test void retainsExactWorkAndRejectsMalformedOrIncompatibleStructureContracts() {
        var catalog = Stage23YardConstructionCatalog.loadDefault();
        var state = new State(catalog.fingerprint(), 2, List.of(new Order("order", "yard", "yard.orbital_escort_v1", "station",
                "location.orbital_station", 1, 1)));
        var bytes = Stage23YardConstructionPersistenceCodec.encode(state); var original = bytes.clone();
        assertEquals(state, Stage23YardConstructionPersistenceCodec.decode(bytes)); assertArrayEquals(original, bytes);
        assertEquals(State.empty(), Stage23YardConstructionPersistenceCodec.decode(Stage23YardConstructionPersistenceCodec.encode(State.empty())));
        for (int length : new int[]{0, 81, bytes.length - 1})
            assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(Arrays.copyOf(bytes, length)));
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var version = bytes.clone(); ByteBuffer.wrap(version).putInt(3);
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(version));
        var count = bytes.clone(); ByteBuffer.wrap(count).putInt(78, State.CAPACITY + 1);
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(count));
        var changedSpecification = bytes.clone(); changedSpecification[6] = changedSpecification[6] == '0' ? (byte)'1' : (byte)'0';
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(changedSpecification));
        var excessWork = bytes.clone(); ByteBuffer.wrap(excessWork).putDouble(bytes.length - 8, catalog.find("yard.orbital_escort_v1").requiredWorkSeconds() + 1);
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(excessWork));
    }

    @Test void schemaTwoRetainsPartialSiteCustodyAndRejectsMaterialGrantsOrWorkBeforeDelivery() {
        var catalog = Stage23YardConstructionCatalog.loadDefault(); var spec = catalog.find("yard.orbital_escort_v1");
        String commodity = spec.requiredMassByCommodityKg().keySet().iterator().next();
        var order = new Order("site", "yard", spec.yardDefinitionId(), "station", "location.orbital_station", 1, 0,
                true, java.util.Map.of(commodity, 125d));
        var state = new State(catalog.fingerprint(), 2, List.of(order));
        var bytes = Stage23YardConstructionPersistenceCodec.encode(state);
        assertEquals(2, ByteBuffer.wrap(bytes).getInt()); assertEquals(state, Stage23YardConstructionPersistenceCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.decode(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> new State(catalog.fingerprint(), 1, List.of(order)), "A site cannot receive future material");
        for (var invalid : List.of(
                new Order("site", "yard", spec.yardDefinitionId(), "station", "location.orbital_station", 1, 1, true, order.deliveredMassByCommodityKg()),
                new Order("site", "yard", spec.yardDefinitionId(), "station", "location.orbital_station", 1, 0, true,
                        java.util.Map.of(commodity, spec.requiredMassByCommodityKg().get(commodity) + 1)),
                new Order("site", "yard", spec.yardDefinitionId(), "station", "location.orbital_station", 1, 0, false, order.deliveredMassByCommodityKg()),
                new Order("site", "yard", spec.yardDefinitionId(), "station", "location.orbital_station", 1, 0, true, java.util.Map.of("unknown", 1d))))
            assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionPersistenceCodec.encode(
                    new State(catalog.fingerprint(), 2, List.of(invalid))));
    }
}
