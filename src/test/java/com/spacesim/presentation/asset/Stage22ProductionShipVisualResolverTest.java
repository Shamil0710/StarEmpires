package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageLoader;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ScaleAuthority;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.ResolvedVisual;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void installedEngineeringFitResolvesThroughSameExactProductionAuthority() {
        var empireFamily = Stage22EmpirePackageLoader.loadDefault().shipFamilies().stream()
                .filter(family -> family.roleId().equals("role.military.destroyer"))
                .findFirst().orElseThrow();
        var empireEngineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        InstalledFit empireInstalled = InstalledFit.fromDemonstrator(
                empireEngineering.findDemonstratorFit(empireFamily.primaryFitId()));
        ResolvedVisual empire = Stage22ProductionShipVisualResolver.resolveInstalledFit(
                "fleet:701",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                empireInstalled,
                RuntimeVisualState.IDLE);

        var unionFamily = Stage22IndustrialUnionPackageLoader.loadDefault().shipFamilies().stream()
                .filter(family -> family.roleId().equals("role.military.destroyer"))
                .findFirst().orElseThrow();
        var unionEngineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        InstalledFit unionInstalled = InstalledFit.fromDemonstrator(
                unionEngineering.findDemonstratorFit(unionFamily.primaryFitId()));
        ResolvedVisual union = Stage22ProductionShipVisualResolver.resolveInstalledFit(
                "fleet:702",
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                unionInstalled,
                RuntimeVisualState.THRUSTING);

        assertEquals(empireFamily.primaryFitId(), empire.key().fitId());
        assertEquals(empireFamily.roleId(), empire.key().roleId());
        assertEquals("assets/ships/empire/production/destroyer/destroyer_base.png", empire.assetRef());
        assertEquals(unionFamily.primaryFitId(), union.key().fitId());
        assertEquals(unionFamily.roleId(), union.key().roleId());
        assertEquals("assets/ships/industrial_union/production/destroyer/destroyer_base.png", union.assetRef());
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
    void adapterPreservesProductionPathAndExactScaleForCurrentRenderer() {
        ResolvedVisual visual = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet:303",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "role.support.freight",
                RuntimeVisualState.IDLE);
        var adapted = Stage22ProductionShipSpriteAdapter.adapt(visual);

        assertEquals(visual.assetRef(), adapted.binding().texturePath());
        assertTrue(Stage22ProductionShipSpriteAdapter.isProductionPath(adapted.binding().texturePath()));
        assertEquals(visual.worldLengthM(), adapted.worldLengthM());
        assertEquals(visual.worldWidthM(), adapted.worldWidthM());
        assertEquals(ScaleAuthority.EXACT_PHYSICAL_CONTENT, adapted.scaleAuthority());
        assertTrue(adapted.binding().assetId().startsWith("stage22.production:"));
    }

    @Test
    void coreCargoUpgradeChangesOnlyArtworkAndKeepsCurrentPhysicalScale() {
        ResolvedSprite legacy = legacySprite(VisualRole.CARGO_TRANSPORT_SHIP, 321d, 87d);

        ResolvedSprite empire = Stage22ProductionShipSpriteAdapter.upgradeCoreCargoProjection(
                "fleet:501",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                legacy,
                RuntimeVisualState.IDLE);
        ResolvedSprite union = Stage22ProductionShipSpriteAdapter.upgradeCoreCargoProjection(
                "fleet:502",
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                legacy,
                RuntimeVisualState.IDLE);
        ResolvedSprite unknown = Stage22ProductionShipSpriteAdapter.upgradeCoreCargoProjection(
                "fleet:503",
                "faction.third_party",
                legacy,
                RuntimeVisualState.IDLE);

        assertEquals("assets/ships/empire/production/freight/freight_base.png", empire.binding().texturePath());
        assertEquals("assets/ships/industrial_union/production/freight/freight_base.png", union.binding().texturePath());
        assertEquals(legacy.worldLengthM(), empire.worldLengthM());
        assertEquals(legacy.worldWidthM(), empire.worldWidthM());
        assertEquals(legacy.worldLengthM(), union.worldLengthM());
        assertEquals(legacy.worldWidthM(), union.worldWidthM());
        assertEquals(legacy.scaleAuthority(), empire.scaleAuthority());
        assertEquals(legacy.scaleAuthority(), union.scaleAuthority());
        assertSame(legacy, unknown);
    }

    @Test
    void coreMediumCombatCompatibilityUsesFactionDestroyerArtWithoutRewritingPhysicalHull() {
        ResolvedSprite provisional = legacySprite(VisualRole.MEDIUM_COMBAT_SHIP, 230d, 74d);

        ResolvedSprite empire = Stage22ProductionShipSpriteAdapter.upgradeCoreProjection(
                "fleet:601",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                provisional,
                RuntimeVisualState.IDLE);
        ResolvedSprite union = Stage22ProductionShipSpriteAdapter.upgradeCoreProjection(
                "fleet:602",
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                provisional,
                RuntimeVisualState.THRUSTING);
        ResolvedSprite generatedCompatibility = Stage22ProductionShipSpriteAdapter.upgradeCoreProjection(
                "fleet:603",
                "faction.alpha",
                provisional,
                RuntimeVisualState.IDLE);

        assertEquals("assets/ships/empire/production/destroyer/destroyer_base.png",
                empire.binding().texturePath());
        assertEquals("assets/ships/industrial_union/production/destroyer/destroyer_base.png",
                union.binding().texturePath());
        assertEquals(230d, empire.worldLengthM());
        assertEquals(74d, empire.worldWidthM());
        assertEquals(230d, union.worldLengthM());
        assertEquals(74d, union.worldWidthM());
        assertEquals(provisional.scaleAuthority(), empire.scaleAuthority());
        assertEquals(provisional.scaleAuthority(), union.scaleAuthority());
        assertSame(provisional, generatedCompatibility);
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

    private static ResolvedSprite legacySprite(VisualRole role, double lengthM, double widthM) {
        SpriteBinding binding = new SpriteBinding(
                "legacy.test." + role.name().toLowerCase(java.util.Locale.ROOT),
                role,
                "assets/ships/ship_sprite_reference_pack_v1.png",
                new AtlasRegion(0, 0, 1, 1),
                0.5f,
                0.5f,
                SourceFacing.RIGHT,
                lengthM,
                widthM,
                List.of());
        return new ResolvedSprite(
                binding,
                lengthM,
                widthM,
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                "test.runtime.physical-authority");
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
