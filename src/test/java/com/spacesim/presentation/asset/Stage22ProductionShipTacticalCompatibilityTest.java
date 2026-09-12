package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ScaleAuthority;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22ProductionShipTacticalCompatibilityTest {
    @Test
    void exactStrategicInstalledFitSelectsDestroyerArtIndependentlyOfLegacyRole() {
        var engineering = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        String strategicFitId = Stage175ICombatTestContentPack.stage21StrategicFitId(
                "fit.test_doctrine_a_kinetic_v1");
        InstalledFit strategicFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(strategicFitId));
        ResolvedSprite legacyLightCombat = legacySprite(VisualRole.LIGHT_COMBAT_ESCORT_SHIP);

        ResolvedSprite empire = Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(
                "combatant:901",
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                strategicFit,
                legacyLightCombat,
                RuntimeVisualState.IDLE);
        ResolvedSprite union = Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(
                "combatant:902",
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                strategicFit,
                legacyLightCombat,
                RuntimeVisualState.DAMAGED);

        assertEquals("assets/ships/empire/production/destroyer/destroyer_base.png",
                empire.binding().texturePath());
        assertEquals("assets/ships/industrial_union/production/destroyer/destroyer_base.png",
                union.binding().texturePath());
        assertEquals(VisualRole.MEDIUM_COMBAT_SHIP, empire.binding().role());
        assertEquals(VisualRole.MEDIUM_COMBAT_SHIP, union.binding().role());
        assertEquals(230d, empire.worldLengthM());
        assertEquals(74d, empire.worldWidthM());
        assertEquals(ScaleAuthority.EXACT_PHYSICAL_CONTENT, empire.scaleAuthority());
        assertTrue(empire.authorityId().contains(strategicFitId));
        assertTrue(union.authorityId().contains(strategicFitId));
    }

    @Test
    void nonCoreFactionRemainsLegacyEvenWhenExactStrategicFitIsPresent() {
        var engineering = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        InstalledFit strategicFit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(
                        "fit.test_doctrine_e_balanced_v1")));
        ResolvedSprite legacy = legacySprite(VisualRole.UTILITY_SHIP);

        assertSame(legacy, Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(
                "combatant:903",
                "faction.alpha",
                strategicFit,
                legacy,
                RuntimeVisualState.IDLE));
    }

    @Test
    void coreFactionRejectsMissingOrNonStrategicFitInsteadOfGuessingFromRole() {
        var engineering = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        InstalledFit baseFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.test_doctrine_a_kinetic_v1"));
        ResolvedSprite legacyMedium = legacySprite(VisualRole.MEDIUM_COMBAT_SHIP);

        assertThrows(IllegalArgumentException.class, () ->
                Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(
                        "combatant:904",
                        Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                        baseFit,
                        legacyMedium,
                        RuntimeVisualState.IDLE));
        assertThrows(NullPointerException.class, () ->
                Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(
                        "combatant:905",
                        Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                        null,
                        legacyMedium,
                        RuntimeVisualState.IDLE));
    }

    private static ResolvedSprite legacySprite(VisualRole role) {
        SpriteBinding binding = new SpriteBinding(
                "legacy.tactical." + role.name().toLowerCase(java.util.Locale.ROOT),
                role,
                "assets/ships/ship_sprite_reference_pack_v1.png",
                new AtlasRegion(0, 0, 1, 1),
                0.5f,
                0.5f,
                SourceFacing.RIGHT,
                230d,
                74d,
                List.of());
        return new ResolvedSprite(
                binding,
                230d,
                74d,
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                "test.stage21.tactical");
    }
}
