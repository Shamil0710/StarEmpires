package com.spacesim.persistence;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardModuleCustodyPersistenceCodecTest {
    private StoredModule module(String id) {
        String content = Stage22CivilianMiningProductionPath.loadProducts().getProducts().stream()
                .filter(p -> p.kind() == Stage18ManufacturingProductRegistry.ProductKind.MODULE)
                .findFirst().orElseThrow().contentId();
        return new StoredModule(id, "fixture.station", 31, 17,
                new RemovedModuleState(new InstalledModuleDefinition("fixture.mount", content), .35, 920));
    }

    @Test void exactConditionAndStableIdentityRoundTripInCanonicalOrder() {
        var first = new ShipyardModuleCustodyState(List.of(module("b"), module("a")));
        var second = new ShipyardModuleCustodyState(List.of(module("a"), module("b")));
        byte[] bytes = ShipyardModuleCustodyPersistenceCodec.encode(first);
        assertArrayEquals(bytes, ShipyardModuleCustodyPersistenceCodec.encode(second));
        assertEquals(first, ShipyardModuleCustodyPersistenceCodec.decode(bytes));
        assertEquals(.35, first.modules().get(0).condition().integrity());
        assertEquals(920, first.modules().get(0).condition().secondsSinceService());
        assertThrows(IllegalArgumentException.class, () -> new ShipyardModuleCustodyState(List.of(module("a"), module("a"))));
        assertThrows(IllegalArgumentException.class, () -> new ShipyardModuleCustodyState(
                java.util.Collections.nCopies(ShipyardModuleCustodyState.CAPACITY + 1, module("a"))));
    }

    @Test void invalidSchemaCountsConditionTruncationAndTrailingBytesCannotBeAdopted() {
        byte[] bytes = ShipyardModuleCustodyPersistenceCodec.encode(new ShipyardModuleCustodyState(List.of(module("a"))));
        byte[] future = bytes.clone(); ByteBuffer.wrap(future).putInt(0, 3);
        byte[] count = bytes.clone(); ByteBuffer.wrap(count).putInt(4, Integer.MAX_VALUE);
        byte[] damage = bytes.clone(); ByteBuffer.wrap(damage).putDouble(damage.length - 16, Double.NaN);
        byte[] age = bytes.clone(); ByteBuffer.wrap(age).putDouble(age.length - 8, -1);
        for (byte[] corrupt : List.of(future, count, damage, age, Arrays.copyOf(bytes, bytes.length - 1),
                Arrays.copyOf(bytes, bytes.length + 1)))
            assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyPersistenceCodec.decode(corrupt));
        assertEquals(ShipyardModuleCustodyState.empty(), ShipyardModuleCustodyPersistenceCodec.decode(
                ShipyardModuleCustodyPersistenceCodec.encode(ShipyardModuleCustodyState.empty())));
    }

    @Test void actorOwnershipSurvivesForeignStorageAndTransportWithoutChangingConditionOrLegacyOwnership() {
        var original = module("private");
        var owned = new StoredModule(original.custodyId(), "foreign.station", original.sourceAssetId(),
                original.removedAtTick(), original.condition(), "actor.player");
        var state = new ShipyardModuleCustodyState(List.of(owned, module("legacy")));
        byte[] bytes = ShipyardModuleCustodyPersistenceCodec.encode(state);
        assertEquals(2, ByteBuffer.wrap(bytes).getInt());
        assertEquals(state, ShipyardModuleCustodyPersistenceCodec.decode(bytes));
        var transported = state.relocate("private", "foreign.station", "freight-hold:31");
        var actual = transported.modules().stream().filter(m -> m.custodyId().equals("private")).findFirst().orElseThrow();
        assertEquals("actor.player", actual.ownerActorId());
        assertEquals(original.condition(), actual.condition());
        assertEquals(original.sourceAssetId(), actual.sourceAssetId());
        assertEquals(original.removedAtTick(), actual.removedAtTick());
        assertEquals(transported, ShipyardModuleCustodyPersistenceCodec.decode(ShipyardModuleCustodyPersistenceCodec.encode(transported)));
        assertNull(transported.modules().stream().filter(m -> m.custodyId().equals("legacy")).findFirst().orElseThrow().ownerActorId());
        var old = new ShipyardModuleCustodyState(List.of(module("old")));
        assertEquals(1, ByteBuffer.wrap(ShipyardModuleCustodyPersistenceCodec.encode(old)).getInt());
        assertNull(ShipyardModuleCustodyPersistenceCodec.decode(ShipyardModuleCustodyPersistenceCodec.encode(old)).modules().get(0).ownerActorId());
        assertThrows(IllegalArgumentException.class, () -> new StoredModule("bad", "station", 31, 17, original.condition(), ""));
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }
}
