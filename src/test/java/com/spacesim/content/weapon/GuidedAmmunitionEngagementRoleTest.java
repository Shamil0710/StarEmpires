package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuidedAmmunitionEngagementRoleTest {
    @Test
    void productionAndStage19ITestContentDeclareExplicitGuidedRoles() {
        WeaponAmmunitionCatalog production = WeaponAmmunitionCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog stage19 = Stage175ICombatTestWeaponPack.loadAmmunition();

        assertEquals(
                GuidedEngagementRole.INTERCEPTOR,
                production.findGuided("ammo.interceptor_1t_v1").engagementRole());
        assertEquals(
                GuidedEngagementRole.STRIKE,
                stage19.findGuided("ammo.test_anti_ship_missile_2t_v1").engagementRole());
        assertEquals(
                GuidedEngagementRole.INTERCEPTOR,
                stage19.findGuided("ammo.test_interceptor_750kg_v1").engagementRole());
    }

    @Test
    void legacySchemaV1GuidedContentWithoutRoleDefaultsToStrike() {
        String json = """
                {
                  "schemaVersion": 1,
                  "migrationVersion": 1,
                  "kineticAmmunition": [],
                  "guidedAmmunition": [
                    {
                      "id": "ammo.legacy_guided_v1",
                      "materialId": "material.high_strength_steel_v1",
                      "shape": "SHELL",
                      "lengthM": 3.2,
                      "diameterM": 0.48,
                      "impactPayloadId": null,
                      "seekerId": "seeker.legacy_v1",
                      "dryMassKg": 800.0,
                      "propellantMassKg": 200.0,
                      "thrustN": 20000.0,
                      "exhaustVelocityMps": 5000.0,
                      "burnTimeSeconds": 40.0,
                      "seekerAngularSigmaRad": 0.0005,
                      "terminalReserveMps": 300.0
                    }
                  ]
                }
                """;

        WeaponAmmunitionCatalog catalog = WeaponAmmunitionCatalogLoader.parse(
                json,
                ShipEngineeringCatalogLoader.loadDefault());

        assertEquals(
                GuidedEngagementRole.STRIKE,
                catalog.findGuided("ammo.legacy_guided_v1").engagementRole());
    }
}
