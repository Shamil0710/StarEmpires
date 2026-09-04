package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 executable foundation for canonical pairwise scenarios, mirroring and freeze discovery. */
class Stage22CorePairBalanceFoundationTest {
    @Test
    void canonicalSuiteCoversEveryB00ThroughB20AndTreatsProductionReadyB15ToB17AsRequired() {
        List<Stage22CorePairBalanceCatalog.ScenarioDefinition> scenarios = Stage22CorePairBalanceCatalog.scenarios();
        assertEquals(21, scenarios.size());
        for (int index = 0; index < 21; index++) {
            String expected = "B" + String.format(java.util.Locale.ROOT, "%02d", index);
            assertEquals(expected, scenarios.get(index).id());
            assertEquals(Stage22CorePairBalanceCatalog.Requirement.REQUIRED, scenarios.get(index).requirement());
            assertNotNull(Stage22CorePairBalanceCatalog.find(expected));
            assertFalse(scenarios.get(index).authorityEvidence().isBlank());
        }
        assertTrue(Stage22CorePairBalanceCatalog.find("B15").authorityEvidence().contains("Stage21F"));
        assertTrue(Stage22CorePairBalanceCatalog.find("B16").authorityEvidence().contains("Stage17"));
        assertTrue(Stage22CorePairBalanceCatalog.find("B17").authorityEvidence().contains("retool"));
    }

    @Test
    void pairedProtocolUsesSameSeedExactlyTwiceWithMirroredAssignments() {
        assertPaired(Stage22CorePairExperimentProtocol.tuningSchedule(), 30);
        assertPaired(Stage22CorePairExperimentProtocol.releaseCandidateSchedule(), 100);
        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairExperimentProtocol.pairedSchedule(0));
    }

    @Test
    void pairEvidenceUsesStableFactionIdsAndVisibleCausalStrengthAndCostSurfaces() {
        Stage22CorePairBalanceEvidence.PairEvidence evidence = Stage22CorePairBalanceEvidence.deriveCurrent();

        assertEquals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, evidence.empire().stableFactionId());
        assertEquals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID, evidence.industrialUnion().stableFactionId());
        assertNotEquals(evidence.empire().packageFingerprint(), evidence.industrialUnion().packageFingerprint());
        assertEquals(9, evidence.empire().roleFamilyCount());
        assertEquals(9, evidence.industrialUnion().roleFamilyCount());
        assertTrue(evidence.empire().totalPrimaryFittedMassKg() > 0d);
        assertTrue(evidence.industrialUnion().totalPrimaryFittedMassKg() > 0d);
        assertTrue(evidence.empire().capitalMassShare() > 0d);
        assertTrue(evidence.empire().supportMassShare() > 0d);
        assertTrue(evidence.industrialUnion().capitalMassShare() > 0d);
        assertTrue(evidence.industrialUnion().supportMassShare() > 0d);
        assertTrue(evidence.empire().projectionBundleMassKg() > evidence.empire().carrierMassKg());
        assertTrue(evidence.industrialUnion().projectionBundleMassKg() > evidence.industrialUnion().carrierMassKg());
        assertTrue(evidence.industrialUnion().maximumBuildTimeReduction() > 0d);
        assertTrue(evidence.industrialUnion().maximumThroughputImprovement() > 0d);
        assertTrue(evidence.unionDisruption().retoolWorkSeconds() > 0L);
        assertTrue(evidence.unionDisruption().retoolEnergyJ() > 0L);
        assertTrue(evidence.unionDisruption().correlatedDisruption());
        assertTrue(evidence.unionDisruption().correlatedThroughputDegradation() >= 0.25d);
        assertTrue(evidence.unionDisruption().correlatedThroughputDegradation()
                > evidence.unionDisruption().isolatedThroughputDegradation());
        assertEquals(21, evidence.card().requiredScenarios().size());
        assertTrue(evidence.card().prohibitedShortcut().contains("faction-name"));
    }

    @Test
    void currentFreezeSurfaceIsDeterministicAndPrintsDiscoveryPinsForLiteralFreezeCommit() {
        Stage22CorePairFreezeManifest.Snapshot first = Stage22CorePairFreezeManifest.captureCurrent();
        Stage22CorePairFreezeManifest.Snapshot second = Stage22CorePairFreezeManifest.captureCurrent();

        assertEquals(first, second);
        assertEquals(64, first.freezeFingerprint().length());
        assertEquals(21, first.scenarioVersions().size());
        assertEquals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, first.empireFactionId());
        assertEquals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID, first.unionFactionId());
        assertNotEquals(first.empirePackageFingerprint(), first.unionPackageFingerprint());
        var runtime = com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        assertEquals(runtime.engineering().getFingerprint(), first.runtimeContentFingerprints().get("engineering"));
        assertEquals(runtime.ammunition().getFingerprint(), first.runtimeContentFingerprints().get("ammunition"));
        assertEquals(runtime.launchers().getFingerprint(), first.runtimeContentFingerprints().get("launchers"));

        System.out.println("M22_6_DISCOVERY_FREEZE|aggregate|" + first.freezeFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|empire.package|" + first.empirePackageFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|union.package|" + first.unionPackageFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|empire.production|" + first.empireProductionFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|union.production|" + first.unionProductionFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|empire.engineering|" + first.empireEngineeringFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|union.engineering|" + first.unionEngineeringFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|empire.profile|" + first.empireProfileFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|core.profile|" + first.coreProfileCatalogFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|empire.character|" + first.empireCharacterFingerprint());
        System.out.println("M22_6_DISCOVERY_FREEZE|union.character|" + first.unionCharacterFingerprint());
        first.runtimeContentFingerprints().forEach((key, value) ->
                System.out.println("M22_6_DISCOVERY_FREEZE|runtime." + key + "|" + value));
    }

    private static void assertPaired(
            List<Stage22CorePairExperimentProtocol.RunCoordinate> runs,
            int seedCount) {
        assertEquals(seedCount * 2, runs.size());
        Map<Long, List<Stage22CorePairExperimentProtocol.Permutation>> bySeed = runs.stream()
                .collect(Collectors.groupingBy(
                        Stage22CorePairExperimentProtocol.RunCoordinate::seed,
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(Stage22CorePairExperimentProtocol.RunCoordinate::permutation,
                                Collectors.toList())));
        assertEquals(seedCount, bySeed.size());
        bySeed.forEach((seed, permutations) -> assertEquals(
                List.of(
                        Stage22CorePairExperimentProtocol.Permutation.DEFAULT,
                        Stage22CorePairExperimentProtocol.Permutation.MIRRORED),
                permutations,
                "seed=" + seed));
    }
}
