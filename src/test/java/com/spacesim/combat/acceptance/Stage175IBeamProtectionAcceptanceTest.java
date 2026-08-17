package com.spacesim.combat.acceptance;

import com.spacesim.ship.BeamProtectionRuntime;
import com.spacesim.ship.BeamWeaponRuntime;
import com.spacesim.ship.ShipBeamEngineeringAdapter;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IBeamProtectionAcceptanceTest {
    @Test
    void closeBeamMustAblateAuthoredMaterialBeforeItCanDamageLocalSubsystems() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var baseline = pack.manifest().findVariation("variation.ct_baseline_v1");
        var attacker = materializer.materialize("fit.ct_cruiser_beam_v1", baseline);
        var target = materializer.materialize("fit.ct_frigate_escort_v1", baseline);
        var emitter = new ShipBeamEngineeringAdapter(pack.engineering())
                .deriveBeamMounts(attacker.derived()).get(0);
        var targetHull = pack.engineering().findHull(target.derived().hullId());
        var hitPoint = targetHull.compartments().stream()
                .filter(value -> value.id().equals("weapons"))
                .findFirst().orElseThrow().centerM();

        BeamWeaponRuntime beams = new BeamWeaponRuntime();
        var closeSolution = beams.plan(
                emitter.weapon(), fireControlTrack(2L, 10_000d, 0.01d), 0d, 0d, 5d);
        assertTrue(closeSolution.allowed());
        var close = new BeamProtectionRuntime(pack.engineering(), pack.beamMaterials()).resolve(
                closeSolution,
                null,
                0d,
                targetHull.structuralProtectionStackId(),
                targetHull,
                target.engineering().fit,
                pack.protection().findHullDamageLayout(targetHull.id()),
                target.engineering().instanceState.damage(),
                hitPoint);
        assertTrue(close.layerInteractions().stream().allMatch(BeamProtectionRuntime.LayerInteraction::perforated));
        assertTrue(close.internalDamageEnergyJ() > 0d);
        assertNotNull(close.damageEvent());
        assertTrue(close.damageEvent().snapshot().compartmentIntegrityById().get("weapons") < 1d);
        assertTrue(close.damageEvent().snapshot().moduleDamage().moduleIntegrityByMount().values().stream()
                .anyMatch(value -> value < 1d));

        var farSolution = beams.plan(
                emitter.weapon(), fireControlTrack(2L, 500_000d, 1d), 0d, 0d, 5d);
        assertTrue(farSolution.allowed());
        var far = new BeamProtectionRuntime(pack.engineering(), pack.beamMaterials()).resolve(
                farSolution,
                null,
                0d,
                targetHull.structuralProtectionStackId(),
                targetHull,
                target.engineering().fit,
                pack.protection().findHullDamageLayout(targetHull.id()),
                target.engineering().instanceState.damage(),
                hitPoint);
        assertTrue(far.spotAreaM2() > close.spotAreaM2());
        assertTrue(far.internalDamageEnergyJ() < close.internalDamageEnergyJ());
        assertNull(far.damageEvent());
    }

    private static TrackState fireControlTrack(long targetId, double xM, double positionVarianceM2) {
        return new TrackState(
                targetId,
                InformationState.FIRE_CONTROL,
                true,
                xM,
                0d,
                new TrackCovariance(positionVarianceM2, 1e-12d, positionVarianceM2),
                1d,
                0d,
                3,
                6);
    }
}
