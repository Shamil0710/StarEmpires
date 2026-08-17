package com.spacesim.ship;

import com.spacesim.ship.Stage175ICombatMatrixCatalog.ReferenceCostCoverage;
import com.spacesim.ship.Stage175ICombatMatrixCatalog.VariantKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatMatrixCatalogTest {
    private static final Stage175ICombatMatrixCatalog MATRIX = new Stage175ICombatMatrixCatalog();
    private static final Stage175ICombatAcceptanceHarness HARNESS = new Stage175ICombatAcceptanceHarness();

    @Test
    void everyRequiredNonCostVariationHasExactlyOneCanonicalCase() {
        var cases = MATRIX.requiredVariants();
        var byKind = cases.stream().collect(Collectors.toMap(
                Stage175ICombatMatrixCatalog.MatrixCase::kind,
                Function.identity()));

        assertEquals(EnumSet.allOf(VariantKind.class), MATRIX.representedVariantKinds());
        assertEquals(VariantKind.values().length, cases.size());
        assertEquals(VariantKind.values().length, byKind.size());
    }

    @Test
    void equalCountAndApproximateEqualMassCasesUseProductionDerivedFleetMass() {
        var byKind = MATRIX.requiredVariants().stream().collect(Collectors.toMap(
                Stage175ICombatMatrixCatalog.MatrixCase::kind,
                Function.identity()));
        var equalCount = byKind.get(VariantKind.EQUAL_COUNT);
        var equalMass = byKind.get(VariantKind.APPROX_EQUAL_MASS);

        assertEquals(equalCount.scenario().leftCount(), equalCount.scenario().rightCount());
        assertEquals(
                MATRIX.fleetMassKg(equalCount.scenario().leftDoctrine(), equalCount.scenario().leftCount()),
                equalCount.leftFleetMassKg(),
                0d);
        assertEquals(
                MATRIX.fleetMassKg(equalCount.scenario().rightDoctrine(), equalCount.scenario().rightCount()),
                equalCount.rightFleetMassKg(),
                0d);
        assertTrue(equalMass.relativeFleetMassMismatch() < 0.05d,
                () -> "equal-mass mismatch=" + equalMass.relativeFleetMassMismatch());
    }

    @Test
    void smallCompactAndLargeDispersedCasesChangePhysicalCountAndFormationGeometry() {
        var byKind = MATRIX.requiredVariants().stream().collect(Collectors.toMap(
                Stage175ICombatMatrixCatalog.MatrixCase::kind,
                Function.identity()));
        var compact = byKind.get(VariantKind.SMALL_COMPACT_FORMATION);
        var dispersed = byKind.get(VariantKind.LARGE_DISPERSED_FORMATION);

        assertTrue(compact.scenario().leftCount() < dispersed.scenario().leftCount());
        assertTrue(compact.scenario().rightCount() < dispersed.scenario().rightCount());
        assertTrue(compact.scenario().spacingM() < dispersed.scenario().spacingM());
        assertTrue(compact.leftFleetMassKg() < dispersed.leftFleetMassKg());
        assertTrue(compact.rightFleetMassKg() < dispersed.rightFleetMassKg());
        assertNotEquals(HARNESS.run(compact.scenario()).fingerprint(), HARNESS.run(dispersed.scenario()).fingerprint());
    }

    @Test
    void depletionDamageThermalInformationAndLogisticsVariantsReachPhysicalHarnessInputs() {
        var byKind = MATRIX.requiredVariants().stream().collect(Collectors.toMap(
                Stage175ICombatMatrixCatalog.MatrixCase::kind,
                Function.identity()));

        var partial = byKind.get(VariantKind.PARTIAL_AMMUNITION);
        var damaged = byKind.get(VariantKind.PRE_DAMAGED);
        var hot = byKind.get(VariantKind.THERMALLY_STRESSED);
        var degraded = byKind.get(VariantKind.DEGRADED_INFORMATION);
        var logistics = byKind.get(VariantKind.PROTECTED_LOGISTICS);

        assertTrue(partial.scenario().ammunitionFraction() < 1d);
        assertTrue(damaged.scenario().initialIntegrity() < 1d);
        assertTrue(hot.scenario().thermalStressFraction() > 0d);
        assertEquals(Stage175ICombatAcceptanceHarness.InformationPreset.DEGRADED,
                degraded.scenario().informationPreset());
        assertTrue(logistics.scenario().protectedLogisticsAsset());

        var partialResult = HARNESS.run(partial.scenario());
        var damagedResult = HARNESS.run(damaged.scenario());
        var hotResult = HARNESS.run(hot.scenario());
        var degradedResult = HARNESS.run(degraded.scenario());
        var logisticsResult = HARNESS.run(logistics.scenario());

        assertEquals(64, partialResult.fingerprint().length());
        assertEquals(64, damagedResult.fingerprint().length());
        assertEquals(64, hotResult.fingerprint().length());
        assertEquals(64, degradedResult.fingerprint().length());
        assertEquals(64, logisticsResult.fingerprint().length());
    }

    @Test
    void costEqualizationIsExplicitlyDeferredRatherThanInventingCrossComponentPrices() {
        assertEquals(
                ReferenceCostCoverage.DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS,
                MATRIX.referenceCostCoverage());
    }
}
