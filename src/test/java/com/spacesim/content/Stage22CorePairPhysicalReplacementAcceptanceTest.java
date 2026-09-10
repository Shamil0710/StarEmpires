package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairPhysicalReplacementAcceptanceTest {
    @Test
    void paidCoreReplacementsUseFreshIdentitiesAndInvalidPlansLeaveEveryAuthorityUntouched() {
        var rows = List.of(Stage22CorePairReplacementProbe.run(true), Stage22CorePairReplacementProbe.run(false));
        rows.forEach(row -> {
            assertTrue(row.valid(), row.toString());
            assertTrue(row.buildSeconds() > 0d);
            assertTrue(row.hullInputMassKg() > 0d);
        });
        Stage22CorePairEvidenceArchive.write("B14-paid-replacement", rows,
                "Exact core destroyers commissioned from explicit post-war loss obligations, finite hull materials and installed module stock. Source manufacturing, battle-loss derivation and recovery curves are separate scenario layers; no such evidence is inferred from this boundary probe.");
    }
}
