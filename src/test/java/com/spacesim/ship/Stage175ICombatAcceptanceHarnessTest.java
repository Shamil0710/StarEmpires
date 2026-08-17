package com.spacesim.ship;

import com.spacesim.ship.Stage175ICombatAcceptanceHarness.InformationPreset;
import com.spacesim.ship.Stage175ICombatAcceptanceHarness.Scenario;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatAcceptanceHarnessTest {
    private static final Stage175ICombatAcceptanceHarness HARNESS = new Stage175ICombatAcceptanceHarness();

    @Test
    void requiredPairMatrixContainsExactlyTheCanonicalElevenPairs() {
        List<Scenario> matrix = Stage175ICombatAcceptanceHarness.requiredPairMatrix();
        Set<String> pairs = matrix.stream()
                .map(value -> value.leftDoctrine().name() + ":" + value.rightDoctrine().name())
                .collect(Collectors.toSet());

        assertEquals(11, matrix.size());
        assertEquals(11, pairs.size());
        assertTrue(pairs.contains("A_KINETIC_LINE:A_KINETIC_LINE"));
        assertTrue(pairs.contains("A_KINETIC_LINE:B_MISSILE_STRIKE"));
        assertTrue(pairs.contains("A_KINETIC_LINE:C_HIGH_MOBILITY_BEAM"));
        assertTrue(pairs.contains("A_KINETIC_LINE:D_DEFENSIVE_EW"));
        assertTrue(pairs.contains("A_KINETIC_LINE:E_BALANCED_CONTROL"));
        assertTrue(pairs.contains("B_MISSILE_STRIKE:C_HIGH_MOBILITY_BEAM"));
        assertTrue(pairs.contains("B_MISSILE_STRIKE:D_DEFENSIVE_EW"));
        assertTrue(pairs.contains("B_MISSILE_STRIKE:E_BALANCED_CONTROL"));
        assertTrue(pairs.contains("C_HIGH_MOBILITY_BEAM:D_DEFENSIVE_EW"));
        assertTrue(pairs.contains("C_HIGH_MOBILITY_BEAM:E_BALANCED_CONTROL"));
        assertTrue(pairs.contains("D_DEFENSIVE_EW:E_BALANCED_CONTROL"));
    }

    @Test
    void identicalScenarioProducesExactlyTheSameOpaqueFingerprint() {
        Scenario scenario = Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.E_BALANCED_CONTROL);

        var first = HARNESS.run(scenario);
        var second = HARNESS.run(scenario);

        assertEquals(first, second);
        assertEquals(64, first.fingerprint().length());
    }

    @Test
    void completeRequiredMatrixRunsThroughProductionSubsystemsDeterministically() {
        var first = Stage175ICombatAcceptanceHarness.requiredPairMatrix().stream().map(HARNESS::run).toList();
        var second = Stage175ICombatAcceptanceHarness.requiredPairMatrix().stream().map(HARNESS::run).toList();

        assertEquals(first, second);
        assertEquals(11, first.size());
        assertTrue(first.stream().allMatch(value -> value.fingerprint().length() == 64));
        assertTrue(first.stream().anyMatch(value -> value.left().kineticShots() + value.right().kineticShots() > 0));
        assertTrue(first.stream().anyMatch(value -> value.left().guidedLaunches() + value.right().guidedLaunches() > 0));
        assertTrue(first.stream().anyMatch(value -> value.left().beamDwells() + value.right().beamDwells() > 0));
        assertTrue(first.stream().anyMatch(value -> value.left().shieldAbsorbedJ() + value.right().shieldAbsorbedJ() > 0d));
    }

    @Test
    void ammunitionDepletionChangesPhysicalFireAndFingerprintRatherThanApplyingPenalty() {
        Scenario full = Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.A_KINETIC_LINE);
        Scenario empty = new Scenario(
                full.leftDoctrine(), full.rightDoctrine(), full.leftCount(), full.rightCount(),
                full.spacingM(), 0d, 1d, 0d, InformationPreset.NOMINAL, false);

        var fullResult = HARNESS.run(full);
        var emptyResult = HARNESS.run(empty);

        assertTrue(fullResult.left().kineticShots() > emptyResult.left().kineticShots());
        assertEquals(0L, emptyResult.left().ammunitionBefore());
        assertNotEquals(fullResult.fingerprint(), emptyResult.fingerprint());
    }

    @Test
    void preDamageReducesRealCapabilityAndChangesFingerprint() {
        Scenario pristine = Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.E_BALANCED_CONTROL);
        Scenario damaged = new Scenario(
                pristine.leftDoctrine(), pristine.rightDoctrine(), pristine.leftCount(), pristine.rightCount(),
                pristine.spacingM(), 1d, 0.55d, 0d, InformationPreset.NOMINAL, false);

        var pristineResult = HARNESS.run(pristine);
        var damagedResult = HARNESS.run(damaged);

        assertTrue(damagedResult.left().accelerationMps2() < pristineResult.left().accelerationMps2());
        assertTrue(damagedResult.left().postExchangeSensorApertureM2()
                < pristineResult.left().postExchangeSensorApertureM2());
        assertNotEquals(pristineResult.fingerprint(), damagedResult.fingerprint());
    }

    @Test
    void staleInformationChangesTrackQualityAndDeterministicOutcomeWithoutHitChance() {
        Scenario nominal = Scenario.nominal(DoctrineId.C_HIGH_MOBILITY_BEAM, DoctrineId.E_BALANCED_CONTROL);
        Scenario degraded = new Scenario(
                nominal.leftDoctrine(), nominal.rightDoctrine(), nominal.leftCount(), nominal.rightCount(),
                nominal.spacingM(), 1d, 1d, 0d, InformationPreset.DEGRADED, false);

        var nominalResult = HARNESS.run(nominal);
        var degradedResult = HARNESS.run(degraded);

        assertTrue(nominalResult.left().trackQuality().ordinal() >= degradedResult.left().trackQuality().ordinal());
        assertTrue(nominalResult.left().beamDwells() >= degradedResult.left().beamDwells());
        assertNotEquals(nominalResult.fingerprint(), degradedResult.fingerprint());
    }

    @Test
    void protectedLogisticsVariantExercisesPhysicalInterceptorSchedulingAndThermalAvailability() {
        Scenario base = Scenario.nominal(DoctrineId.B_MISSILE_STRIKE, DoctrineId.E_BALANCED_CONTROL);
        Scenario protectedCool = new Scenario(
                base.leftDoctrine(), base.rightDoctrine(), base.leftCount(), base.rightCount(),
                20_000d, 1d, 1d, 0d, InformationPreset.NOMINAL, true);
        Scenario protectedHot = new Scenario(
                base.leftDoctrine(), base.rightDoctrine(), base.leftCount(), base.rightCount(),
                20_000d, 1d, 1d, 1d, InformationPreset.NOMINAL, true);

        var cool = HARNESS.run(protectedCool);
        var hot = HARNESS.run(protectedHot);

        assertTrue(cool.left().guidedLaunches() + cool.right().guidedLaunches() > 0);
        assertTrue(cool.left().defenseAssignments() + cool.right().defenseAssignments()
                >= hot.left().defenseAssignments() + hot.right().defenseAssignments());
        assertNotEquals(cool.fingerprint(), hot.fingerprint());
    }
}
