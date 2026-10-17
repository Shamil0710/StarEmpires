package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.world.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionReceiptSeriesCodecTest {
    @Test void holdRoundingUsesWholeHoldMagnitudeAcrossManySmallSeries() {
        var series = new ExtractionReceiptSeries(41, 100, (double) .1f, .733236979, 200_003, .733236979);
        double physical = 0;
        for (int i = 0; i < series.receiptCount(); i++) physical += series.receiptMassKg();
        var baseline = state(series, series.lastOrdinal() + 1);
        var lot = baseline.cargoLots().get(0);
        var lots = new java.util.ArrayList<Stage20FreightPersistentState.CargoLotState>();
        // Several short runs retain the same actual per-tick receipts, while hold additions remain sequential.
        for (long start = 0; start < series.receiptCount(); start += 100) {
            long count = Math.min(100, series.receiptCount() - start);
            var run = new ExtractionReceiptSeries(41 + start, 100 + start, series.fixedStepSeconds(), series.receiptMassKg(), count, series.receiptMassKg());
            lots.add(new Stage20FreightPersistentState.CargoLotState("freight-lot:" + run.firstOrdinal(), lot.fleetId(),
                    lot.orderId(), lot.commodityId(), run.remainingMassKg(), lot.sourceEndpointId(), lot.sourceProvenanceId(), run.receipt(0).loadedAtSeconds(), run));
        }
        var fleet = baseline.freighters().get(0);
        var hold = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                Stage20FreightPersistentState.cargoHoldId(fleet.fleetId()), Map.of("storage.dry_bulk", 1_000_000d), Map.of(lot.commodityId(), physical), Map.of());
        var rounded = new Stage20FreightPersistentState.FreighterState(fleet.fleetId(), fleet.stableFactionId(), fleet.ownershipOrdinal(), fleet.hullId(), fleet.fitId(),
                fleet.cargoCapacityKg(), fleet.currentSystemId(), fleet.physicalState(), fleet.phase(), fleet.activeOrderId(), fleet.routeIndex(), hold.snapshot());
        var actual = new Stage20FreightPersistentState(8, 1, "generator.fixture", "fingerprint.fixture", "materialization.fixture", "compatibility.fixture",
                27, series.lastOrdinal() + 1, List.of(rounded), lots, List.of(), List.of(), List.of());
        assertEquals(actual, Stage20FreightPersistenceCodec.decode(Stage20FreightPersistenceCodec.encode(actual)));
    }
    @Test void compactPayloadKeepsOverOneHundredThousandReceiptIdentitiesInsideExistingBounds() {
        var series = new ExtractionReceiptSeries(41, 100, (double) .1f, 2.5, 200_003, 2.5);
        var state = state(series, series.lastOrdinal() + 1);
        byte[] bytes = Stage20FreightPersistenceCodec.encode(state);
        assertTrue(bytes.length < 4096);
        assertEquals(8, java.nio.ByteBuffer.wrap(bytes).getInt(8));
        var restored = Stage20FreightPersistenceCodec.decode(bytes);
        assertEquals(state, restored);
        var actual = restored.cargoLots().get(0).receiptSeries();
        for (long index : new long[]{0, 1, 100_000, 200_002}) assertEquals(series.receipt(index), actual.receipt(index));
        assertThrows(IllegalArgumentException.class, () -> state(series, 42));
    }

    private static Stage20FreightPersistentState state(ExtractionReceiptSeries series, long nextOrdinal) {
        var fleet = new FleetId(26); String commodity = "commodity.feedstock.metallic_ore";
        // Explicit physical-hold fixture for the codec; no campaign acquisition is claimed.
        var hold = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                Stage20FreightPersistentState.cargoHoldId(fleet), Map.of("storage.dry_bulk", 1_000_000d),
                Map.of(commodity, series.remainingMassKg()), Map.of());
        var freighter = new Stage20FreightPersistentState.FreighterState(fleet, "faction.fixture", 0, "hull.fixture", "fit.fixture",
                1_000_000, new StarSystemId(1), LocalPhysicalKinematics.stationary(new LocalPhysicalPosition(0,0,0,0)),
                Stage20FreightPersistentState.FreightPhase.IDLE, "", 0, hold.snapshot());
        var lot = new Stage20FreightPersistentState.CargoLotState("freight-lot:41", fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), commodity, series.remainingMassKg(),
                "source.fixture", "player-extraction:source.fixture", series.receipt(0).loadedAtSeconds(), series);
        return new Stage20FreightPersistentState(8, 1, "generator.fixture", "fingerprint.fixture", "materialization.fixture", "compatibility.fixture",
                27, nextOrdinal, List.of(freighter), List.of(lot), List.of(), List.of(), List.of());
    }
}
