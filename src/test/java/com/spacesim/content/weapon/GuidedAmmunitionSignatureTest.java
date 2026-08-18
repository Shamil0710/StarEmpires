package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SignatureState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidedAmmunitionSignatureTest {
    @Test
    void stage19GuidedBodiesExposeExplicitProductionSensorSourceStrengths() {
        WeaponAmmunitionCatalog catalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        var strike = catalog.findGuided("ammo.test_anti_ship_missile_2t_v1");
        var interceptor = catalog.findGuided("ammo.test_interceptor_750kg_v1");

        SignatureState strikeSignature = strike.signature().toRuntimeSignature();
        SignatureState interceptorSignature = interceptor.signature().toRuntimeSignature();

        assertEquals(0.65d, strikeSignature.radarCrossSectionM2(), 1e-12d);
        assertEquals(1_500_000d, strikeSignature.thermalRadiantPowerW(), 1e-6d);
        assertEquals(250_000_000d, strikeSignature.enginePlumeRadiantPowerW(), 1e-6d);
        assertEquals(0.25d, interceptorSignature.radarCrossSectionM2(), 1e-12d);
        assertTrue(strikeSignature.radarCrossSectionM2() > interceptorSignature.radarCrossSectionM2());
        assertTrue(strikeSignature.enginePlumeRadiantPowerW()
                > interceptorSignature.enginePlumeRadiantPowerW());
    }

    @Test
    void legacySchemaV1GuidedContentWithoutSignatureGetsNoHiddenDetectionGrant() {
        WeaponAmmunitionCatalog catalog = WeaponAmmunitionCatalogLoader.parse(
                legacyJson(null),
                ShipEngineeringCatalogLoader.loadDefault());

        SignatureState signature = catalog.findGuided("ammo.legacy_guided_v1")
                .signature()
                .toRuntimeSignature();

        assertEquals(SignatureState.zero(), signature,
                "legacy content without authored signature must not receive implicit observability");
    }

    @Test
    void physicalSignatureChangesAmmunitionSemanticFingerprint() {
        var engineering = ShipEngineeringCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog zero = WeaponAmmunitionCatalogLoader.parse(legacyJson(null), engineering);
        WeaponAmmunitionCatalog visible = WeaponAmmunitionCatalogLoader.parse(
                legacyJson("""
                        ,
                          \"signature\": {
                            \"thermalRadiantPowerW\": 1000.0,
                            \"enginePlumeRadiantPowerW\": 2000.0,
                            \"radarCrossSectionM2\": 0.1,
                            \"reflectedOpticalPowerW\": 300.0,
                            \"activeRadioEmissionPowerW\": 10.0,
                            \"jammerEmissionPowerW\": 0.0
                          }
                        """),
                engineering);

        assertNotEquals(zero.getFingerprint(), visible.getFingerprint());
    }

    private static String legacyJson(String signatureSuffix) {
        String suffix = signatureSuffix == null ? "" : signatureSuffix;
        return """
                {
                  "schemaVersion": 1,
                  "migrationVersion": 1,
                  "kineticAmmunition": [],
                  "guidedAmmunition": [
                    {
                      "id": "ammo.legacy_guided_v1",
                      "materialId": "material.high_strength_steel_v1",
                      "shape": "SHELL",
                      "engagementRole": "STRIKE",
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
                      "terminalReserveMps": 300.0%s
                    }
                  ]
                }
                """.formatted(suffix);
    }
}
