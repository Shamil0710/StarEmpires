package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CivilianMinorEcosystemValidatorTest {
    @Test
    void defaultEcosystemResolvesProvidersProductionPathsAndCorePairScenarioBindings() {
        var report = Stage22CivilianMinorEcosystemValidator.validateDefault();
        var repeated = Stage22CivilianMinorEcosystemValidator.validateDefault();

        assertEquals(64, report.ecosystemFingerprint().length());
        assertEquals(report.ecosystemFingerprint(), repeated.ecosystemFingerprint());
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
    void minorActorsKeepGovernedIdentityClassesLocalizedNamesAndNeverAcquireCorePackageFallback() {
        var ecosystem = Stage22CivilianMinorEcosystemCatalog.loadDefault();
        var governance = Stage22ContentGovernanceLoader.loadDefault();
        Map<String, IdentityClass> expectedClasses = Map.of(
                "faction.neutral", IdentityClass.MINOR_AUTHORED,
                "faction.trade_league", IdentityClass.TRANSNATIONAL_NETWORK,
                "faction.miners", IdentityClass.MINOR_AUTHORED);

        ecosystem.minorActors().forEach(actor -> {
            var identity = governance.findFactionIdentity(actor.stableFactionId());
            assertEquals(expectedClasses.get(actor.stableFactionId()), identity.identityClass(), actor.stableFactionId());
            assertFalse(governance.canonicalDisplayName(actor.stableFactionId(), "").isBlank(), actor.stableFactionId());
            assertTrue(actor.preserveStableId(), actor.stableFactionId());
            assertFalse(actor.majorPackageFallbackAllowed(), actor.stableFactionId());
            assertNull(governance.canonicalPackageKey(actor.stableFactionId()), actor.stableFactionId());
        });
    }

    @Test
    void invalidMinorAndDeferredHookContractsFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                new Stage22CivilianMinorEcosystemCatalog.MinorActorPolicy(
                        "faction.miners",
                        "preserved minor",
                        "governed mining spawn",
                        true,
                        true));
        assertThrows(IllegalArgumentException.class, () ->
                new Stage22CivilianMinorEcosystemCatalog.EcosystemHook(
                        Stage22CivilianMinorEcosystemCatalog.HookKind.INSURANCE,
                        "com.spacesim.trade.InterSystemTradeService",
                        true,
                        "deferred insurance must not own runtime authority"));
    }
}
