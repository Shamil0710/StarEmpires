package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B17 machine evidence for adaptation through the existing M22.4 Industrial Union
 * production-state/retool contract.
 *
 * <p>The probe deliberately does not invent an adaptation score. A yard must leave its qualified
 * assembly series, carry positive work and energy debt, remain blocked while that debt is only
 * partially paid, and become legal for the target family only after the ordinary authored retool
 * burden is fully settled.</p>
 */
final class Stage22CorePairAdaptationRetoolAcceptanceTest {
    private static final String LOGISTICS_FAMILY = "ship_family.industrial_union.freight";
    private static final String SCREEN_FAMILY = "ship_family.industrial_union.destroyer";

    @Test
    void b17AdaptationRequiresFiniteRetoolBeforeTargetFamilyBecomesLegal() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B17",
                "industrial_union_finite_series_retool",
                "stage22.4+stage22.6.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(2),
                (scenario, variant, profile, coordinate) -> observeRetool());

        assertEquals(0, vector.hardRuleBreachCount());
        assertEquals(1d, vector.guardMetricMeans().get("target_blocked_before_full_retool"));
        assertEquals(1d, vector.guardMetricMeans().get("partial_payment_remains_blocked"));
        assertEquals(1d, vector.guardMetricMeans().get("target_legal_after_full_retool"));
        assertTrue(vector.metricMeans().get("retool_work_seconds") > 0d);
        assertTrue(vector.metricMeans().get("retool_energy_j") > 0d);
        assertFalse(vector.evidenceFingerprint().isBlank());
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload observeRetool() {
        var unqualified = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        var firstRetool = Stage22IndustrialUnionIndustrialProgram.beginRetool(unqualified, LOGISTICS_FAMILY);
        var firstPaid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                firstRetool,
                firstRetool.retoolWorkRemainingSeconds(),
                firstRetool.retoolEnergyRemainingJ());
        var logisticsQualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(firstPaid);

        // Establish that the source series is genuinely usable before the adaptation request.
        Stage22IndustrialUnionIndustrialProgram.modifierFor(logisticsQualified, LOGISTICS_FAMILY);

        var pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(logisticsQualified, SCREEN_FAMILY);
        long totalWork = pending.retoolWorkRemainingSeconds();
        long totalEnergy = pending.retoolEnergyRemainingJ();
        boolean positiveBurden = totalWork > 0L && totalEnergy > 0L;
        boolean targetBlockedBeforeFullRetool = targetBlocked(pending);

        long partialWork = Math.max(1L, totalWork / 2L);
        long partialEnergy = Math.max(1L, totalEnergy / 2L);
        var partial = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, partialWork, partialEnergy);
        boolean partialPaymentRemainsBlocked = partial.retooling()
                && partial.retoolWorkRemainingSeconds() > 0L
                && partial.retoolEnergyRemainingJ() > 0L
                && targetBlocked(partial);

        var fullyPaid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                partial,
                partial.retoolWorkRemainingSeconds(),
                partial.retoolEnergyRemainingJ());
        var screenQualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(fullyPaid);
        var targetModifier = Stage22IndustrialUnionIndustrialProgram.modifierFor(screenQualified, SCREEN_FAMILY);
        boolean targetLegalAfterFullRetool = !screenQualified.retooling()
                && targetModifier.workMultiplier() > 0d
                && targetModifier.energyMultiplier() > 0d;

        ArrayList<String> breaches = new ArrayList<>();
        if (!positiveBurden) breaches.add("adaptation_retool_burden_not_positive");
        if (!targetBlockedBeforeFullRetool) breaches.add("target_family_legal_before_retool_payment");
        if (!partialPaymentRemainsBlocked) breaches.add("partial_retool_payment_unblocked_target_family");
        if (!targetLegalAfterFullRetool) breaches.add("target_family_not_legal_after_paid_retool");

        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                Map.of(
                        "retool_work_seconds", (double) totalWork,
                        "retool_energy_j", (double) totalEnergy,
                        "partial_work_paid_seconds", (double) partialWork,
                        "partial_energy_paid_j", (double) partialEnergy),
                Map.of(
                        "positive_retool_burden", positiveBurden ? 1d : 0d,
                        "target_blocked_before_full_retool", targetBlockedBeforeFullRetool ? 1d : 0d,
                        "partial_payment_remains_blocked", partialPaymentRemainsBlocked ? 1d : 0d,
                        "target_legal_after_full_retool", targetLegalAfterFullRetool ? 1d : 0d),
                breaches);
    }

    private static boolean targetBlocked(Stage22IndustrialUnionProductionState.YardSeriesState state) {
        assertTrue(state.retooling());
        assertThrows(IllegalStateException.class,
                () -> Stage22IndustrialUnionIndustrialProgram.modifierFor(state, SCREEN_FAMILY));
        return true;
    }
}
