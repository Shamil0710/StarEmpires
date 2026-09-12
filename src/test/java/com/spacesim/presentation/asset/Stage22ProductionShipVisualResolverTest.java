package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageLoader;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.ResolvedVisual;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22ProductionShipVisualResolverTest {
    @Test
    void freightRoleSelectsFactionSpecificProductionAssetWithoutLegacyFallback() {
        ResolvedVisual empire = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:101",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "role.support.freight",
                RuntimeVisualState.IDLE);
        ResolvedVisual union = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:202",
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                "role.support.freight",
                RuntimeVisualState.IDLE);

        assertEquals("assets/ships/empire/production/freight/freight_base.png", empire.assetRef());
        assertEquals("fit.empire.freight.bulk_v1", empire.key().fitId());
        assertEquals(Stage22EmpirePackageCatalog.STABLE_FACTION_ID, empire.key().stableFactionId());
        assertEquals(AssetStatus.CONCEPT, empire.key().shipVisualProfileStatus());
        assertEquals("assets/ships/industrial_union/production/freight/freight_base.png", union.assetRef());
        assertEquals("fit.industrial_union.freight.bulk_v1", union.key().fitId());
        assertEquals(Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID, union.key().stableFactionId());
        assertEquals(AssetStatus.CONCEPT, union.key().shipVisualProfileStatus());
        assertNotEquals(empire.key().shipVisualProfileId(), union.key().shipVisualProfileId());
        assertNotEquals(empire.key().packageFingerprint(), union.key().packageFingerprint());
        assertTrue(empire.worldLengthM() > 0d && empire.worldWidthM() > 0d);
        assertTrue(union.worldLengthM() > 0d && union.worldWidthM() > 0d);
    }

    @Test
    void everyAuthoredPrimaryAndRefitFitResolvesThroughExactProductionAuthority() {
        Stage22EmpirePackageLoader.loadDefault().shipFamilies().forEach(family -> {
            assertFitResolves(
                    Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                    family.primaryFitId(),
                    family.roleId());
            assertFitResolves(
                    Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                    family.refitFitId(),
                    family.roleId());
        });
        Stage22IndustrialUnionPackageLoader.loadDefault().shipFamilies().forEach(family -> {
            assertFitResolves(
                    Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                    family.primaryFitId(),
                    family.roleId());
            assertFitResolves(
                    Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                    family.refitFitId(),
                    family.roleId());
        });
    }

    @Test
    void runtimeStateParticipatesInKeyWithoutChangingCurrentBaseAsset() {
        ResolvedVisual idle = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:303",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "role.military.destroyer",
                RuntimeVisualState.IDLE);
        ResolvedVisual thrusting = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:303",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "role.military.destroyer",
                RuntimeVisualState.THRUSTING);

        assertEquals(idle.assetRef(), thrusting.assetRef());
        assertNotEquals(idle.key(), thrusting.key());
        assertEquals(RuntimeVisualState.IDLE, idle.key().runtimeState());
        assertEquals(RuntimeVisualState.THRUSTING, thrusting.key().runtimeState());
    }

    @Test
    void unsupportedFactionAndRoleFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:404", "faction.unknown", "role.support.freight", RuntimeVisualState.IDLE));
        assertThrows(IllegalArgumentException.class, () -> Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:405",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "role.unsupported.test",
                RuntimeVisualState.IDLE));
    }

    private static void assertFitResolves(String factionId, String fitId, String roleId) {
        ResolvedVisual resolved = Stage22ProductionShipVisualResolver.resolveExactFit(
                "test:" + fitId, factionId, fitId, RuntimeVisualState.IDLE);
        assertEquals(factionId, resolved.key().stableFactionId());
        assertEquals(fitId, resolved.key().fitId());
        assertEquals(roleId, resolved.key().roleId());
        assertTrue(resolved.assetRef().contains("/production/"));
        assertTrue(resolved.assetRef().endsWith("_base.png"));
        assertTrue(resolved.key().fitFingerprint().length() == 64);
    }
}
