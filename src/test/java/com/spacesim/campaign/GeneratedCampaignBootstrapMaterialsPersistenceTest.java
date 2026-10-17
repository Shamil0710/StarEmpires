package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignBootstrapMaterialsPersistenceTest {
    @Test void newBillRequiresOwnerRetainsNativeCustodyAndCannotClaimHistoricalHeader() {
        var initial = FoundedCampaignFixture.restore();
        String design = Stage23BootstrapMaterialsCatalog.FACILITY_ID;
        var station = initial.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> initial.hasFacilityConstructionLine(s.stationId(), design)).findFirst().orElseThrow();
        var before = initial.captureState(); var ref = initial.pilotMarketReference(station.stationId()).orElseThrow();
        var p = before.playerState();
        var systems = new ArrayList<>(p.discoveredSystemIds()); if (!systems.contains(ref.systemId())) systems.add(ref.systemId());
        // Explicit ownership and basic-alloy fixtures isolate persistence; ordinary material supply remains separate.
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), systems, p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(),
                p.threatIntel(), p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(before.stage21Runtime(),
                before.smallCraft(), before.hangars(), before.flightDeck(), before.operations(), owner));
        var runtime = campaign.coordinator().runtime(); var store = runtime.infrastructure().endpoint(station.stationId()).storage();
        var bill = Stage23BootstrapMaterialsCatalog.construction().requiredMassByCommodityKg(design);
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var capacities = new TreeMap<String, Double>();
        bill.forEach((id, mass) -> capacities.merge(ontology.findCommodity(id).storageClassId(), mass, Double::sum));
        var source = new Stage18StationStorage(ontology, Stage22CivilianMiningProductionPath.loadProducts(), "fixture.bill", capacities, bill, Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", capacities.keySet(), 25_000_000, 25_000_000);
        var logistics = new Stage18LogisticsRuntime(ontology, Stage22CivilianMiningProductionPath.loadProducts());
        bill.forEach((id, mass) -> assertTrue(logistics.transferCommodity(source, store, id, mass, handling, handling.openInterval(1)).transferred()));
        String order = Stage18FacilityConstructionWorkQueue.PREFIX + "bootstrap-native";
        runtime.constructionQueue().start(order, "fixture.bootstrap", design, "location.orbital_station", store, runtime.world().getAuthoritativeWorldTick());
        var pending = campaign.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(pending)));
        assertEquals(pending, loaded.captureState());
        byte[] changedContent = Stage228GeneratedCampaignPersistenceCodec.encode(pending);
        var framing = java.nio.ByteBuffer.wrap(changedContent);
        int contentOffset = 14 + Short.toUnsignedInt(framing.getShort(12));
        for (int index = 0; index < 14; index++) contentOffset += 4 + framing.getInt(contentOffset);
        changedContent[contentOffset + 4 + framing.getInt(contentOffset) - 1] ^= 1;
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(changedContent))
                .getMessage().contains("content fingerprint mismatch"));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(pending.stage21Runtime(),
                pending.smallCraft(), pending.hangars(), pending.flightDeck(), pending.operations(), before.playerState()));
        byte[] bytes = Stage228GeneratedCampaignPersistenceCodec.encode(pending);
        java.nio.ByteBuffer.wrap(bytes).putInt(4, 20).putInt(8, 20);
        int length = ((bytes[12] & 0xff) << 8) | (bytes[13] & 0xff); bytes[14 + length - 1] = '0';
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(bytes))
                .getMessage().contains("native campaign v21"));
        loaded.coordinator().runtime().constructionQueue().cancel(order, loaded.coordinator().runtime().infrastructure().endpoint(station.stationId()).storage());
        for (var input : bill.entrySet()) assertTrue(loaded.coordinator().runtime().infrastructure().endpoint(station.stationId())
                .storage().commodityMassKg(input.getKey()) >= input.getValue());
        var finishing = Stage228CampaignAuthority.restore(pending);
        var finishingRuntime = finishing.coordinator().runtime();
        float step = finishing.coordinator().session().fixedStepSeconds();
        var actualOrder = finishingRuntime.constructionQueue().capture().get(0);
        // Explicit completed-work interval fixture validates new installation admission. Full finite work is tested separately.
        var budget = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.completion-work",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), actualOrder.requiredWorkSeconds() / step).openInterval(step);
        var completeOrders = finishingRuntime.constructionQueue().advance(finishingRuntime.world().getAuthoritativeWorldTick() + 1,
                id -> finishingRuntime.infrastructure().endpoint(id).storage(), ignored -> budget);
        assertThrows(IllegalStateException.class, () -> finishingRuntime.adoptCompletedFacilityConstruction(completeOrders));
        finishing.coordinator().setPaused(false); finishing.advanceFrame(step); finishing.coordinator().setPaused(true);
        finishingRuntime.adoptCompletedFacilityConstruction(completeOrders);
        var built = finishingRuntime.industry().industrial().station(station.stationId()).facilities().stream()
                .filter(f -> f.definitionId().equals(design)).findFirst().orElseThrow();
        assertFalse(built.enabled()); assertEquals(0, built.allocatedProcessPowerW());
        var completed = finishing.captureState();
        assertEquals(completed, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(completed))).captureState());
        finishingRuntime.allocateFacilityResources(station.stationId(), built.facilityInstanceId());
        for (String recipe : List.of("refining.carbon_material", "refining.industrial_chemicals", "refining.ceramic_glass",
                "refining.refractory_alloy", "refining.electronic_grade_material")) assertTrue(finishing.hasRefiningLine(station.stationId(), recipe), recipe);
        var operating = finishing.captureState();
        finishingRuntime.adoptCompletedFacilityConstruction(completeOrders);
        assertEquals(operating, finishing.captureState(), "Repeated adoption cannot reset the new plant's actual operating resources");
        var restoredOperating = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(operating)));
        assertEquals(operating, restoredOperating.captureState());
    }
}
