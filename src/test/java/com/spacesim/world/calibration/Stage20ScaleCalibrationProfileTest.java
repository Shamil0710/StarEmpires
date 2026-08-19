package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ScaleCalibrationProfileTest {
    private static final String REPRESENTATIVE_ID = "ESCORT_DESTROYER";
    private static final String FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final String LOAD_CASE_ID = "load.test";

    @Test
    void currentProfileIsVersionedDeterministicAndKeepsReferenceAuthorityVisible() {
        Stage20ScaleCalibrationProfile first = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20ScaleCalibrationProfile second = Stage20ScaleCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20ScaleCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(5, first.representativeShips().size());
        assertEquals(20, first.routeSamples().size());
        assertEquals(4, first.routeBands().size());

        Set<String> representativeIds = first.representativeShips().stream()
                .map(RepresentativeShipPropulsionEnvelope::representativeId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "TORPEDO_CORVETTE",
                "ESCORT_DESTROYER",
                "BATTLESHIP",
                "BULK_FREIGHTER_LOADED",
                "FLEET_TANKER_LOADED"), representativeIds);

        RepresentativeShipPropulsionEnvelope escort = first.representativeShips().stream()
                .filter(value -> REPRESENTATIVE_ID.equals(value.representativeId()))
                .findFirst()
                .orElseThrow();
        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, escort.authority());
        assertEquals(FIT_ID, escort.provenanceId());
        assertEquals("load.escort_destroyer.full_reaction_mass_v1", escort.loadCaseId());
        assertEquals(21_320_000d, escort.wetMassKg(), 0.001d);
        assertEquals(19_520_000d, escort.dryMassAfterReactionKg(), 0.001d);
        assertEquals(1_800_000d, escort.reactionMassKg(), 0.001d);
        assertEquals(escort.deltaVMps() / 2d, escort.symmetricPeakSpeedMps(), 1e-9d);
        assertEquals(
                escort.fullBurnDurationS(),
                escort.accelerationBurnDurationS() + escort.brakingBurnDurationS(),
                1e-9d);
        assertEquals(
                escort.characteristicRestToRestDistanceM(),
                escort.accelerationDistanceM() + escort.brakingDistanceM(),
                1e-6d);
        assertTrue(escort.terminalAccelerationMps2() > escort.initialAccelerationMps2());
        assertTrue(escort.accelerationDistanceM() > escort.brakingDistanceM());

        long provisionalCount = first.representativeShips().stream()
                .filter(value -> value.authority() == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE)
                .count();
        assertEquals(4L, provisionalCount);
        assertTrue(first.representativeShips().stream()
                .filter(value -> value.authority() == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE)
                .allMatch(value -> value.provenanceId().startsWith("ship_mathematics_v1_0_design_baseline:")));

        first.routeBands().forEach(band -> {
            assertTrue(band.distanceM() > 0d);
            assertTrue(band.minTravelTimeS() <= band.maxTravelTimeS());
            assertTrue(band.minRequiredDeltaVMps() <= band.maxRequiredDeltaVMps());
            assertTrue(band.minBrakingDistanceM() <= band.maxBrakingDistanceM());
            assertTrue(band.minReactionMassFractionConsumed() <= band.maxReactionMassFractionConsumed());
        });
    }

    @Test
    void productionCalibrationConsumesAuthoritativeEngineeringOutputsInsteadOfReplacingThem() {
        DerivedShipState state = deriveLoadedEscort(1_800_000d);

        RepresentativeShipPropulsionEnvelope envelope = Stage20ScaleCalibrationCalculator.deriveProduction(
                REPRESENTATIVE_ID, FIT_ID, LOAD_CASE_ID, state);

        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, envelope.authority());
        assertEquals(state.totalMassKg(), envelope.wetMassKg(), 0d);
        assertEquals(state.totalMassKg() - state.reactionMassKg(), envelope.dryMassAfterReactionKg(), 0d);
        assertEquals(state.reactionMassKg(), envelope.reactionMassKg(), 0d);
        assertEquals(state.availableThrustN(), envelope.thrustN(), 0d);
        assertEquals(state.massFlowKgPerS(), envelope.massFlowKgPerS(), 0d);
        assertEquals(state.accelerationMps2(), envelope.initialAccelerationMps2(), 0d);
        assertEquals(state.effectiveExhaustVelocityMps(), envelope.effectiveExhaustVelocityMps(), 0d);
        assertEquals(state.deltaVMps(), envelope.deltaVMps(), 0d);
    }

    @Test
    void moreReactionMassChangesTheDerivedSpatialEnvelope() {
        RepresentativeShipPropulsionEnvelope halfLoad = Stage20ScaleCalibrationCalculator.deriveProduction(
                REPRESENTATIVE_ID, FIT_ID, "load.half", deriveLoadedEscort(900_000d));
        RepresentativeShipPropulsionEnvelope fullLoad = Stage20ScaleCalibrationCalculator.deriveProduction(
                REPRESENTATIVE_ID, FIT_ID, "load.full", deriveLoadedEscort(1_800_000d));

        assertTrue(fullLoad.reactionMassFraction() > halfLoad.reactionMassFraction());
        assertTrue(fullLoad.deltaVMps() > halfLoad.deltaVMps());
        assertTrue(fullLoad.fullBurnDurationS() > halfLoad.fullBurnDurationS());
        assertTrue(fullLoad.characteristicRestToRestDistanceM() > halfLoad.characteristicRestToRestDistanceM());
        assertTrue(fullLoad.initialAccelerationMps2() < halfLoad.initialAccelerationMps2());
    }

    @Test
    void calibrationRejectsStatesWithoutAUsablePropulsionEnvelope() {
        DerivedShipCalculator calculator = new DerivedShipCalculator(ShipEngineeringCatalogLoader.loadDefault());
        DerivedShipState empty = calculator.deriveDemonstrator(
                FIT_ID, ConsumableState.empty(), DamageState.pristine());
        DerivedShipState valid = deriveLoadedEscort(900_000d);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveProduction(
                        REPRESENTATIVE_ID, FIT_ID, LOAD_CASE_ID, empty));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveProduction(" ", FIT_ID, LOAD_CASE_ID, valid));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveProduction(REPRESENTATIVE_ID, "", LOAD_CASE_ID, valid));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveProduction(REPRESENTATIVE_ID, FIT_ID, "", valid));
        assertThrows(NullPointerException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveProduction(
                        REPRESENTATIVE_ID, FIT_ID, LOAD_CASE_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20ScaleCalibrationProfile("", List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new Stage20ScaleCalibrationProfile("v", null, List.of(), List.of()));
    }

    private static DerivedShipState deriveLoadedEscort(double reactionMassKg) {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        ConsumableState loads = new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "core_drive",
                        "propellant_feed",
                        InterfaceKind.REACTION_MASS,
                        reactionMassKg,
                        reactionMassKg,
                        0L)));
        return new DerivedShipCalculator(catalog).deriveDemonstrator(
                FIT_ID, loads, DamageState.pristine());
    }
}
