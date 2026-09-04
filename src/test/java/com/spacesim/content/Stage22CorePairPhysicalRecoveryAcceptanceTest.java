package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** B14 paid repair and B03 support-construction integration for the exact core pair. */
class Stage22CorePairPhysicalRecoveryAcceptanceTest {
    @Test
    void bothPackagesRepairOnlyAfterFiniteSupportConstructionMaterialsAndWork() {
        var rows = new java.util.ArrayList<Object>();
        for (boolean empire : new boolean[] { true, false }) {
            double previousTime = 0d;
            double previousMass = 0d;
            for (double damage : new double[] { 0.25d, 0.5d, 0.75d }) {
                var result = Stage22CorePairRecoveryProbe.run(empire, damage);
                rows.add(java.util.Map.of("faction", empire ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                        : Stage22CorePairBalanceEvidence.UNION_FACTION_ID, "damageFraction", damage, "result", result));
                assertTrue(result.valid(), result.toString());
                assertTrue(result.repairSeconds() > previousTime);
                assertTrue(result.repairMassKg() > previousMass);
                previousTime = result.repairSeconds();
                previousMass = result.repairMassKg();
            }
        }
        Stage22CorePairEvidenceArchive.write("B14-paid-repair", rows,
                "Exact fitted destroyers and three deterministic damage levels; paid construction kits and repair stocks are declared inputs. This is repair authority evidence, not the full post-war replacement/attrition gate.");
    }
}
