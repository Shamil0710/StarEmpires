package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.*;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import com.spacesim.ui.GeneratedCampaignModuleCustodyUi;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignModuleCustodyPersistenceTest {
    @Test void individualEquipmentSurvivesNativeLoadAndWorldCompositionWithoutGrantingForeignAccess() throws Exception {
        var campaign = FoundedCampaignFixture.restore();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var product = products.getProducts().stream().filter(p -> p.kind() == Stage18ManufacturingProductRegistry.ProductKind.MODULE)
                .min(Comparator.comparingDouble(Stage18ManufacturingProductRegistry.ProductDefinition::unitMassKg)).orElseThrow();
        var station = campaign.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> s.storage().remainingCapacityKg(product.storageClassId()) >= product.unitMassKg())
                .filter(s -> campaign.pilotMarketReference(s.stationId()).isPresent()).findFirst().orElseThrow();
        var base = campaign.captureState();
        long tick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        // Explicit physical removed-equipment fixture; ordinary player refit commands remain future work.
        var row = new StoredModule("fixture.refit/old.mount", station.stationId(), 31, tick,
                new RemovedModuleState(new InstalledModuleDefinition("old.mount", product.contentId()), .35, 920));
        var custody = new ShipyardModuleCustodyState(List.of(row));
        var saved = withCustody(base, custody);
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertTrue(saved.equals(loaded.captureState()), "Native save must retain equipment and all adjacent authorities exactly");
        var migratedV7 = Stage228GeneratedCampaignPersistenceCodec.decode(nativeV7(saved));
        assertTrue(saved.equals(migratedV7), "Native v7 migration must preserve used equipment and journal without granting repair work");
        var actual = loaded.coordinator().runtime().industry().industrial().station(station.stationId()).storage();
        assertEquals(station.storage().usedCapacityKg(product.storageClassId()) + product.unitMassKg(),
                actual.usedCapacityKg(product.storageClassId()), 1e-8);
        assertEquals(station.storage().productCount(product.contentId()), actual.productCount(product.contentId()));
        assertTrue(GeneratedCampaignModuleCustodyUi.rows(loaded).isEmpty(), "Foreign station condition must remain private");
        var ref = campaign.pilotMarketReference(station.stationId()).orElseThrow();
        var p = saved.playerState();
        var known = new ArrayList<>(p.discoveredSystemIds());
        if (!known.contains(ref.systemId())) known.add(ref.systemId());
        // Explicit existing-station ownership fixture; loading never grants station ownership.
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), known, p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(),
                p.threatIntel(), p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var composed = GeneratedCampaignPlayerWorldTransition.compose(saved,
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, loaded.coordinator().runtime().captureState().worldState(), owner));
        assertEquals(custody, composed.moduleCustody()); assertEquals(saved.playerJournal(), composed.playerJournal());
        var own = Stage228CampaignAuthority.restore(composed);
        assertEquals(1, GeneratedCampaignModuleCustodyUi.rows(own).size());
        assertEquals(tick, GeneratedCampaignModuleCustodyUi.rows(own).get(0).chronologicalTick());
        var old = Stage228GeneratedCampaignPersistenceCodec.decode(nativeV6(base));
        assertEquals(ShipyardModuleCustodyState.empty(), old.moduleCustody());
        assertEquals(base.playerJournal(), old.playerJournal());
        assertEquals(base.playerState(), old.playerState());
        assertTrue(base.stage21Runtime().equals(old.stage21Runtime()), "Native v6 migration must leave physical state unchanged");
        assertThrows(IllegalArgumentException.class, () -> withCustody(base, new ShipyardModuleCustodyState(List.of(
                new StoredModule("future", station.stationId(), 31, tick + 1, row.condition())))));
        assertThrows(IllegalArgumentException.class, () -> withCustody(base, new ShipyardModuleCustodyState(List.of(
                new StoredModule("missing", "missing.station", 31, tick, row.condition())))));
        double free = station.storage().remainingCapacityKg(product.storageClassId());
        var largest = products.getProducts().stream().filter(m -> m.kind() == Stage18ManufacturingProductRegistry.ProductKind.MODULE
                && m.storageClassId().equals(product.storageClassId()) && m.unitMassKg() <= free)
                .max(Comparator.comparingDouble(Stage18ManufacturingProductRegistry.ProductDefinition::unitMassKg)).orElseThrow();
        int excessCount = (int) Math.floor(free / largest.unitMassKg()) + 1;
        assertTrue(excessCount <= ShipyardModuleCustodyState.CAPACITY);
        var excess = new ArrayList<StoredModule>();
        for (int n = 0; n < excessCount; n++) excess.add(new StoredModule("excess/" + n, station.stationId(), 31, tick,
                new RemovedModuleState(new InstalledModuleDefinition("mount", largest.contentId()), .35, 920)));
        assertThrows(IllegalArgumentException.class, () -> withCustody(base, new ShipyardModuleCustodyState(excess)));
    }

    private static Stage228GeneratedCampaignPersistentState withCustody(Stage228GeneratedCampaignPersistentState base,
            ShipyardModuleCustodyState custody) {
        return Stage228GeneratedCampaignPersistentState.compose(base.stage21Runtime(), base.smallCraft(), base.hangars(),
                base.flightDeck(), base.operations(), base.playerState(), base.playerJournal(), custody);
    }

    private static byte[] nativeV7(Stage228GeneratedCampaignPersistentState base) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeInt(0x53323843); out.writeInt(7); out.writeInt(7); out.writeUTF("m22.8.generated-campaign.v7");
            for (var payload : List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(base.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(base.smallCraft()), Stage228HangarPersistenceCodec.encode(base.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(base.flightDeck()), Stage228OperationsPersistenceCodec.encode(base.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(base.playerState()), PlayerJournalPersistenceCodec.encode(base.playerJournal()),
                    ShipyardModuleCustodyPersistenceCodec.encode(base.moduleCustody()))) {
                out.writeInt(payload.length); out.write(payload);
            }
        }
        return buffer.toByteArray();
    }

    private static byte[] nativeV6(Stage228GeneratedCampaignPersistentState base) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeInt(0x53323843); out.writeInt(6); out.writeInt(6); out.writeUTF("m22.8.generated-campaign.v6");
            for (var payload : List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(base.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(base.smallCraft()), Stage228HangarPersistenceCodec.encode(base.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(base.flightDeck()), Stage228OperationsPersistenceCodec.encode(base.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(base.playerState()), PlayerJournalPersistenceCodec.encode(base.playerJournal()))) {
                out.writeInt(payload.length); out.write(payload);
            }
        }
        return buffer.toByteArray();
    }
}
