package com.spacesim.campaign;

import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignExtractionPersistenceTest {
    @Test void depletedGeneratedSourceAndPersonallyExtractedCargoRoundtripTogether() {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        var player = c.playerState().orElseThrow();
        var source = r.industry().sourceOutposts().sources().sources().stream()
                .filter(s -> s.sourceState().environment() == ExtractionEnvironment.FREE_BODY
                        && s.sourceState().requiredCapabilityTags().isEmpty()
                        && s.sourceState().remainingAccessibleMassKg() >= 1)
                .findFirst().orElseThrow();
        double before = source.sourceState().remainingAccessibleMassKg();
        // Explicit installed-equipment/interval fixture tests the storage/persistence seam only.
        // It does not claim lawful miner acquisition or physical player-command eligibility.
        var capability = new ExtractionCapability("fixture.authored-miner", Set.of("capability.extraction.asteroid_excavation"),
                4_000_000, 2.5, .125);
        var result = r.freight().extractPersonalCommodity(player.activeFleetId(), source.sourceState(),
                "extraction.asteroid_excavation", 1, capability, capability.openInterval(1), 0);
        assertTrue(result.committed());
        r.synchronizeFreightEngineeringCargo(player.activeFleetId());
        assertEquals(before - 1, source.sourceState().remainingAccessibleMassKg());
        assertEquals(player, c.playerState().orElseThrow());
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertEquals(before - 1, restored.coordinator().runtime().industry().sourceOutposts().sources().source(source.sourceId()).sourceState().remainingAccessibleMassKg());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),
                saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), null));
        var runtime = r.captureState(); var freight = runtime.freight(); var lot = freight.cargoLots().get(0);
        var fake = new CargoLotState(lot.lotId(), lot.fleetId(), lot.orderId(), lot.commodityId(), lot.massKg(),
                "source.absent", "player-extraction:source.absent", lot.loadedAtSimulationSeconds());
        var corrupted = new Stage20FreightPersistentState(freight.schemaVersion(), freight.rootSeed(), freight.generatorVersion(),
                freight.worldFingerprint(), freight.materializationVersion(), freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(), freight.nextCargoLotOrdinal(), freight.freighters(), List.of(fake), freight.orders());
        var invalid = new Stage20GeneratedWorldRuntimePersistentState(runtime.schemaVersion(), runtime.bridgeVersion(), runtime.campaign(),
                runtime.worldState(), runtime.activeSystemId(), runtime.strategicStepTicks(), runtime.remoteUpdateBudgetPerFrame(),
                corrupted, runtime.localFleetPhysicalStates());
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimeBridge.restore(invalid));
        assertEquals(saved, c.captureState());
    }
}
