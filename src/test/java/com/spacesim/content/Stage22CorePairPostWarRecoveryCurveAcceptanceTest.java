package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 B14 recovery-curve evidence derived only from paid repair and replacement authorities. */
class Stage22CorePairPostWarRecoveryCurveAcceptanceTest {
    private static final double DECLARED_SURVIVOR_DAMAGE = 0.50d;
    private static final double BASELINE_CAPABILITY = 2d;

    @Test
    void b14PublishesT50AndT80FromPaidPhysicalRecoveryMilestones() {
        List<Object> archive = new ArrayList<>();
        for (boolean empire : new boolean[] {true, false}) {
            var repair = Stage22CorePairRecoveryProbe.run(empire, DECLARED_SURVIVOR_DAMAGE);
            var replacement = Stage22CorePairReplacementProbe.run(empire);
            assertTrue(repair.valid(), repair.toString());
            assertTrue(replacement.valid(), replacement.toString());

            RecoveryCurve curve = RecoveryCurve.from(repair.repairSeconds(), replacement.buildSeconds());
            assertTrue(curve.recoveryT50Seconds() > 0d);
            assertTrue(curve.recoveryT80Seconds() >= curve.recoveryT50Seconds());
            assertTrue(curve.lostCapabilityAreaSeconds() > 0d);
            assertEquals(BASELINE_CAPABILITY, curve.capabilityAtT80(), 0d);
            assertTrue(repair.repairMassKg() > 0d,
                    "repair milestone must remain backed by finite physical material consumption");
            assertTrue(replacement.hullInputMassKg() + replacement.moduleInputMassKg() > 0d,
                    "replacement milestone must remain backed by finite build inputs");

            archive.add(Map.ofEntries(
                    Map.entry("faction", replacement.factionId()),
                    Map.entry("declaredBaselineCapability", BASELINE_CAPABILITY),
                    Map.entry("declaredSurvivorDamage", DECLARED_SURVIVOR_DAMAGE),
                    Map.entry("repairSeconds", repair.repairSeconds()),
                    Map.entry("repairMassKg", repair.repairMassKg()),
                    Map.entry("replacementSeconds", replacement.buildSeconds()),
                    Map.entry("replacementHullInputMassKg", replacement.hullInputMassKg()),
                    Map.entry("replacementModuleInputMassKg", replacement.moduleInputMassKg()),
                    Map.entry("recoveryT50Seconds", curve.recoveryT50Seconds()),
                    Map.entry("recoveryT80Seconds", curve.recoveryT80Seconds()),
                    Map.entry("lostCapabilityAreaSeconds", curve.lostCapabilityAreaSeconds()),
                    Map.entry("capabilityAtT50", curve.capabilityAtT50()),
                    Map.entry("capabilityAtT80", curve.capabilityAtT80())));
        }

        Stage22CorePairEvidenceArchive.write(
                "B14-paid-recovery-curve",
                archive,
                "Declared post-war capability baseline is two mission-capable exact-core destroyers. The shock leaves one 50%-damaged survivor unavailable until ordinary paid repair completes and one declared fleet loss unavailable until ordinary Stage-21G fresh-identity replacement completes. T50/T80 and lost-capability area are derived from those two physical completion times only. This evidence does not claim the still-open combat-result-to-loss/recovery campaign handoff.");
    }

    private record RecoveryCurve(
            double recoveryT50Seconds,
            double recoveryT80Seconds,
            double lostCapabilityAreaSeconds,
            double capabilityAtT50,
            double capabilityAtT80) {
        private static RecoveryCurve from(double repairSeconds, double replacementSeconds) {
            if (!(repairSeconds > 0d) || !Double.isFinite(repairSeconds)
                    || !(replacementSeconds > 0d) || !Double.isFinite(replacementSeconds)) {
                throw new IllegalArgumentException("Paid recovery milestone times must be finite and positive");
            }
            double t50 = Math.min(repairSeconds, replacementSeconds);
            double t80 = Math.max(repairSeconds, replacementSeconds);
            double lostArea = t50 + 0.5d * (t80 - t50);
            return new RecoveryCurve(t50, t80, lostArea, 1d, BASELINE_CAPABILITY);
        }
    }
}
