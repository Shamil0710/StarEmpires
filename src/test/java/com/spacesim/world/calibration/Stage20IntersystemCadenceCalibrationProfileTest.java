package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.BandId;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.CadenceBand;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.HopCadenceSample;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IntersystemCadenceCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndContainsRequiredNamedBands() {
        Stage20IntersystemCadenceCalibrationProfile first =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Stage20IntersystemCadenceCalibrationProfile second =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(15, first.samples().size());
        assertEquals(4, first.bands().size());
        assertEquals(
                Set.of(BandId.NEIGHBOR_EDGE, BandId.REGIONAL_3_HOP,
                        BandId.REGIONAL_5_HOP, BandId.FLEET_REINFORCEMENT_3_HOP),
                first.bands().stream().map(CadenceBand::id).collect(Collectors.toSet()));
        assertEquals(
                Set.of("BATTLESHIP", "BULK_FREIGHTER_LOADED", "FLEET_TANKER_LOADED", "CARRIER_AVIATION_GROUP"),
                Set.copyOf(first.excludedRepresentatives()));
    }

    @Test
    void hopTimingChargesCooldownBetweenEdgesButNotBeforeFinalArrival() {
        Stage20IntersystemCadenceCalibrationProfile profile =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Map<String, HopCadenceSample> byKey = profile.samples().stream()
                .collect(Collectors.toMap(
                        value -> value.representativeId() + ":" + value.hopCount(),
                        Function.identity()));

        HopCadenceSample corvette1 = byKey.get("TORPEDO_CORVETTE:1");
        HopCadenceSample corvette3 = byKey.get("TORPEDO_CORVETTE:3");
        HopCadenceSample corvette5 = byKey.get("TORPEDO_CORVETTE:5");
        assertEquals(43.375d, corvette1.arrivalTimeS(), 1e-12d);
        assertEquals(133.375d, corvette1.readyAgainTimeS(), 1e-12d);
        assertEquals(310.125d, corvette3.arrivalTimeS(), 1e-12d);
        assertEquals(400.125d, corvette3.readyAgainTimeS(), 1e-12d);
        assertEquals(576.875d, corvette5.arrivalTimeS(), 1e-12d);
        assertEquals(666.875d, corvette5.readyAgainTimeS(), 1e-12d);
        assertEquals(corvette1.cooldownBetweenEdgesS(),
                corvette1.readyAgainTimeS() - corvette1.arrivalTimeS(), 1e-12d);

        HopCadenceSample cruiser3 = byKey.get("CRUISER:3");
        assertEquals(1587.73125d, cruiser3.arrivalTimeS(), 1e-9d);
        assertEquals(1677.73125d, cruiser3.readyAgainTimeS(), 1e-9d);
        assertTrue(cruiser3.arrivalTimeS() > corvette3.arrivalTimeS());
    }

    @Test
    void bandsAggregateCompatibleAndReinforcementSetsWithoutCapitalExceptions() {
        Stage20IntersystemCadenceCalibrationProfile profile =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Map<BandId, CadenceBand> bands = profile.bands().stream()
                .collect(Collectors.toMap(CadenceBand::id, Function.identity()));

        Set<String> allCompatible = Set.of(
                "TORPEDO_CORVETTE",
                "ESCORT_DESTROYER",
                "EARLY_CIVILIAN_FREIGHTER",
                "MINING_SHIP",
                "CRUISER");
        assertEquals(allCompatible, Set.copyOf(bands.get(BandId.NEIGHBOR_EDGE).representativeIds()));
        assertEquals(allCompatible, Set.copyOf(bands.get(BandId.REGIONAL_3_HOP).representativeIds()));
        assertEquals(allCompatible, Set.copyOf(bands.get(BandId.REGIONAL_5_HOP).representativeIds()));
        assertEquals(
                Set.of("TORPEDO_CORVETTE", "ESCORT_DESTROYER", "CRUISER"),
                Set.copyOf(bands.get(BandId.FLEET_REINFORCEMENT_3_HOP).representativeIds()));

        assertEquals(43.375d, bands.get(BandId.NEIGHBOR_EDGE).minArrivalTimeS(), 1e-12d);
        assertEquals(469.24375d, bands.get(BandId.NEIGHBOR_EDGE).maxArrivalTimeS(), 1e-9d);
        assertEquals(310.125d, bands.get(BandId.REGIONAL_3_HOP).minArrivalTimeS(), 1e-12d);
        assertEquals(1587.73125d, bands.get(BandId.REGIONAL_3_HOP).maxArrivalTimeS(), 1e-9d);
        assertEquals(576.875d, bands.get(BandId.REGIONAL_5_HOP).minArrivalTimeS(), 1e-12d);
        assertEquals(2706.21875d, bands.get(BandId.REGIONAL_5_HOP).maxArrivalTimeS(), 1e-9d);
        assertEquals(310.125d, bands.get(BandId.FLEET_REINFORCEMENT_3_HOP).minArrivalTimeS(), 1e-12d);
        assertEquals(1587.73125d, bands.get(BandId.FLEET_REINFORCEMENT_3_HOP).maxArrivalTimeS(), 1e-9d);
    }
}
