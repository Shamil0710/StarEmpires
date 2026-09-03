package com.spacesim.content;

import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.CivilianRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CivilianMinorEcosystemValidatorTest {
    @Test
    void defaultEcosystemResolvesRealProvidersLicensedAssetsAndCorePairScenarioBindings() {
        var report = Stage22CivilianMinorEcosystemValidator.validateDefault();

        assertEquals(64, report.ecosystemFingerprint().length());
        assertEquals(5, report.civilianRoleCount());
        assertEquals(4, report.licensedProductionPathCount());
        assertEquals(3, report.serviceProviderCount());
        assertEquals(3, report.preservedMinorActorCount());
        assertEquals(List.of(CivilianRole.MINING), report.unresolvedProductionRoles());
        assertFalse(report.productionClosureReady());
        assertTrue(report.b08BindingReady());
        assertTrue(report.b16BindingReady());
        assertTrue(report.insuranceHookDeferred());
    }

    @Test
    void minorActorsNeverAcquireCorePackageFallback() {
        var ecosystem = Stage22CivilianMinorEcosystemCatalog.loadDefault();
        var governance = Stage22ContentGovernanceLoader.loadDefault();

        ecosystem.minorActors().forEach(actor -> {
            assertTrue(actor.preserveStableId(), actor.stableFactionId());
            assertFalse(actor.majorPackageFallbackAllowed(), actor.stableFactionId());
            assertEquals(null, governance.canonicalPackageKey(actor.stableFactionId()), actor.stableFactionId());
        });
    }
}
