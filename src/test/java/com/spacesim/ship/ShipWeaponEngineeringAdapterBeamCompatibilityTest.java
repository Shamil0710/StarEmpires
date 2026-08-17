package com.spacesim.ship;

import com.spacesim.combat.acceptance.Stage175ICombatTestContentPack;
import com.spacesim.combat.acceptance.Stage175IShipMaterializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipWeaponEngineeringAdapterBeamCompatibilityTest {
    @Test
    void beamAndGuidedModulesDoNotRequireFakeKineticLauncherProfiles() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var variation = pack.manifest().findVariation("variation.ct_baseline_v1");
        var beamShip = materializer.materialize("fit.ct_cruiser_beam_v1", variation);

        var kinetic = new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                beamShip.derived(),
                pack.ammunition(),
                pack.launchers(),
                beamShip.engineering().instanceState.weaponLoadout());

        assertTrue(kinetic.isEmpty());
    }
}
