package com.spacesim.combat.acceptance;

import com.spacesim.ship.BeamWeaponRuntime;
import com.spacesim.ship.ShipBeamEngineeringAdapter;
import com.spacesim.ship.ShipBeamEngineeringService;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IBeamEngineeringAcceptanceTest {
    @Test
    void fittedBeamUsesPhysicalSpotDwellAndCommonEngineeringHeatInsteadOfFakeAmmo() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var ship = materializer.materialize(
                "fit.ct_cruiser_beam_v1",
                pack.manifest().findVariation("variation.ct_baseline_v1"));
        ShipBeamEngineeringAdapter.FittedBeamMount beam = new ShipBeamEngineeringAdapter(pack.engineering())
                .deriveBeamMounts(ship.derived()).get(0);
        assertTrue(ship.engineering().instanceState.weaponLoadout().feeds().stream()
                .noneMatch(value -> value.mountId().equals(beam.mountId())));

        TrackState nearTrack = fireControlTrack(101L, 250_000d);
        TrackState farTrack = fireControlTrack(101L, 500_000d);
        BeamWeaponRuntime raw = new BeamWeaponRuntime();
        var near = raw.plan(beam.weapon(), nearTrack, 0d, 0d, 5d);
        var far = raw.plan(beam.weapon(), farTrack, 0d, 0d, 5d);
        assertTrue(near.allowed());
        assertTrue(far.allowed());
        assertTrue(far.effectiveSpotRadiusM() > near.effectiveSpotRadiusM());
        assertTrue(far.meanIrradianceWPerM2() < near.meanIrradianceWPerM2());

        double localHeatBefore = ship.engineering().runtimeState.localHeatJByMount()
                .getOrDefault(beam.mountId(), 0d);
        var committed = new ShipBeamEngineeringService(pack.engineering()).planAndCommit(
                ship.engineering(), beam.mountId(), beam.weapon(), nearTrack, 0d, 0d, 5d);
        assertNotNull(committed);
        assertTrue(committed.allowed());
        assertTrue(committed.deliveredBeamEnergyJ() > 0d);
        assertTrue(ship.engineering().runtimeState.localHeatJByMount().get(beam.mountId()) > localHeatBefore);
    }

    private static TrackState fireControlTrack(long targetId, double xM) {
        return new TrackState(
                targetId,
                InformationState.FIRE_CONTROL,
                true,
                xM,
                0d,
                new TrackCovariance(100d, 1e-10d, 100d),
                0.99d,
                0d,
                2,
                4);
    }
}
