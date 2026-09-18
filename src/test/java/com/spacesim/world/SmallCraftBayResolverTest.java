package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCraftBayResolverTest {
    @Test
    void productionEmpireCarrierResolvesInstalledHangarFromAuthoredPhysicalModule() {
        var catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var resolver = new SmallCraftBayResolver(catalog);
        InstalledFit carrier = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.empire.carrier.fleet_v1"));

        var bays = resolver.resolveShipBays(
                "fleet:carrier",
                carrier,
                ShipInstanceRuntimeState.legacyNeutral());

        assertEquals(1, bays.size());
        var bay = bays.get(0);
        assertEquals("mission_primary", bay.id().bayStableId());
        assertEquals(30d, bay.singleCraftEnvelopeM().lengthM(), 1e-9);
        assertEquals(20d, bay.singleCraftEnvelopeM().widthM(), 1e-9);
        assertEquals(15d, bay.singleCraftEnvelopeM().heightM(), 1e-9);
        assertEquals(9_000d, bay.pristineUsableVolumeM3(), 1e-9);
        assertEquals(12_000_000d, bay.pristineSupportedMassKg(), 1e-9);
    }

    @Test
    void realModuleDamageReducesEffectiveBayCapacity() {
        var catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var resolver = new SmallCraftBayResolver(catalog);
        InstalledFit carrier = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.empire.carrier.fleet_v1"));
        ShipInstanceRuntimeState neutral = ShipInstanceRuntimeState.legacyNeutral();
        ShipInstanceRuntimeState damaged = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of(),
                        new DamageState(Map.of("mission_primary", 0.5d))),
                neutral.shieldStatesByMount(),
                neutral.maintenance(),
                neutral.weaponLoadout(),
                neutral.weaponMountRuntime());

        var bay = resolver.resolveShipBays("fleet:carrier", carrier, damaged).get(0);

        assertEquals(0.5d, bay.conditionFraction(), 1e-9);
        assertEquals(4_500d, bay.effectiveUsableVolumeM3(), 1e-9);
        assertEquals(6_000_000d, bay.effectiveSupportedMassKg(), 1e-9);
    }

    @Test
    void stationBayUsesIdenticalPhysicalCapacityContract() {
        var resolver = new SmallCraftBayResolver(
                Stage22CorePairEngineeringCatalogLoader.loadDefault());

        var stationBay = resolver.stationBay(
                "station:orbital-1",
                "bay:a",
                new Dimensions3d(40d, 30d, 20d),
                24_000d,
                18_000_000d,
                0.75d);

        assertEquals(SmallCraftHangarCapacity.HostKind.STATION, stationBay.hostKind());
        assertEquals(18_000d, stationBay.effectiveUsableVolumeM3(), 1e-9);
        assertEquals(13_500_000d, stationBay.effectiveSupportedMassKg(), 1e-9);
        assertTrue(stationBay.acceptsEnvelope(new Dimensions3d(20d, 12d, 8d)));
    }
}
