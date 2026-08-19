package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ScaleCalibrationProfileTest {
    private static final String FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final String LOAD_CASE_ID = "load.test";

    @Test
    void currentProfileIsVersionedDeterministicAndPhysicallyClosed() {
        Stage20ScaleCalibrationProfile first = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20ScaleCalibrationProfile second = Stage20ScaleCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20ScaleCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(1, first.representativeShips().size());

        RepresentativeShipPropulsionEnvelope envelope = first.representativeShips().get(0);
        assertEquals(FIT_ID, envelope.sourceFitId());
        assertEquals("load.escort_destroyer.full_reaction_mass_v1", envelope.loadCaseId());
        assertEquals(21_320_000d, envelope.wetMassKg(), 0.001d);
        assertEquals(19_520_000d, envelope.dryMassAfterReactionKg(), 0.001d);
        assertEquals(1_800_000d, envelope.reactionMassKg(), 0.001d);
        assertEquals(envelope.deltaVMps() / 2d, envelope.symmetricPeakSpeedMps(), 1e-9d);
        assertEquals(
                envelope.fullBurnDurationS(),
                envelope.accelerationBurnDurationS() + envelope.brakingBurnDurationS(),
                1e-9d);
        assertEquals(
                envelope.characteristicRestToRestDistanceM(),
                envelope.accelerationDistanceM() + envelope.brakingDistanceM(),
                1e-6d);
        assertTrue(envelope.terminalAccelerationMps2() > envelope.initialAccelerationMps2());
        assertTrue(envelope.accelerationDistanceM() > envelope.brakingDistanceM());
    }

    @Test
    void calibrationConsumesAuthoritativeEngineeringOutputsInsteadOfReplacingThem() {
        DerivedShipState state = deriveLoadedEscort(1_800_000d);

        RepresentativeShipPropulsionEnvelope envelope = Stage20ScaleCalibrationCalculator.derive(
                FIT_ID, LOAD_CASE_ID, state);

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
        RepresentativeShipPropulsionEnvelope halfLoad = Stage20ScaleCalibrationCalculator.derive(
                FIT_ID, "load.half", deriveLoadedEscort(900_000d));
        RepresentativeShipPropulsionEnvelope fullLoad = Stage20ScaleCalibrationCalculator.derive(
                FIT_ID, "load.full", deriveLoadedEscort(1_800_000d));

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
                () -> Stage20ScaleCalibrationCalculator.derive(FIT_ID, LOAD_CASE_ID, empty));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.derive(" ", LOAD_CASE_ID, valid));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.derive(FIT_ID, "", valid));
        assertThrows(NullPointerException.class,
                () -> Stage20ScaleCalibrationCalculator.derive(FIT_ID, LOAD_CASE_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20ScaleCalibrationProfile("", List.of()));
        assertThrows(NullPointerException.class,
                () -> new Stage20ScaleCalibrationProfile("v", null));
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
