package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CivilianMinorEcosystemValidatorTest {
    @Test
    void defaultEcosystemResolvesProvidersProductionPathsAndCorePairScenarioBindings() {
        var report = Stage22CivilianMinorEcosystemValidator.validateDefault();

        assertEquals(64, report.ecosystemFingerprint().length());
        assertEquals(5, report.civilianRoleCount());
        assertEquals(5, report.licensedProductionPathCount());
        assertEquals(3, report.serviceProviderCount());
        assertEquals(3, report.preservedMinorActorCount());
        assertEquals(List.of(), report.unresolvedProductionRoles());
        assertTrue(report.productionClosureReady());
        assertTrue(report.miningCompatibilityBridgeReady());
        assertTrue(report.b08BindingReady());
        assertTrue(report.b16BindingReady());
        assertTrue(report.insuranceHookDeferred());
    }

    @Test
    void miningCompatibilityArchetypeHasExactLicensedPhysicalReplacementAndExtractionSupport() {
        var mining = Stage22CivilianMiningProductionPath.validateDefault();

        assertEquals("ship.basic_miner", mining.legacyRuntimeArchetype());
        assertEquals("fit.civilian.miners.asteroid_excavator_v1", mining.licensedFitId());
        assertEquals("module.civilian.miners.asteroid_excavation_section_v1", mining.miningModuleId());
        assertEquals("production_manifest.civilian.miners.asteroid_excavator_v1", mining.productionManifestId());
        assertEquals("extraction.asteroid_excavation", mining.extractionMethodId());
        assertEquals("capability.extraction.asteroid_excavation", mining.extractionCapabilityTag());
        assertTrue(mining.productionPathReady());
        assertTrue(mining.runtimeExtractionReady());
        assertTrue(mining.repairFitRejectedForMining());
        assertTrue(mining.ready());
    }

    @Test
    void minorActorsNeverAcquireCorePackageFallback() {
        var ecosystem = Stage22CivilianMinorEcosystemCatalog.loadDefault();
        var governance = Stage22ContentGovernanceLoader.loadDefault();

        ecosystem.minorActors().forEach(actor -> {
            assertTrue(actor.preserveStableId(), actor.stableFactionId());
            assertFalse(actor.majorPackageFallbackAllowed(), actor.stableFactionId());
            assertNull(governance.canonicalPackageKey(actor.stableFactionId()), actor.stableFactionId());
        });
    }
}
