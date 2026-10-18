package com.spacesim.campaign;

import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit ownership fixture of the local industrial station; the flight and docking are ordinary. */
@Tag("slow")
class GeneratedCampaignOwnStationRefuelTest {
    private static final String WATER = "commodity.material.purified_water";
    private static final int KG = 10_000;

    @Test void ownStationRefuelsFromItsStockWithoutASale() {
        var start = Stage228CampaignAuthority.create(2);
        start.submitIndependentPilotStart(start.previewIndependentPilotStart());
        String station = "industry." + start.coordinator().runtime().world().findFleet(start.playerState().orElseThrow().activeFleetId())
                .orElseThrow().systemId().value();
        var p = start.playerState().orElseThrow();
        var ref = start.pilotMarketReference(station).orElseThrow();
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                start.captureState(), start.coordinator().runtime().captureState(), owner));
        assertTrue(c.ownsProductionStation(station));
        var pilot = new ScenarioPilot(c, 400_000);
        pilot.fly(c.coordinator().runtime().infrastructure().endpoint(station).position(), 500, 1000);
        pilot.action("DOCK", station, "", 0);
        pilot.tick();

        var fitted = pilot.engineering();
        var binding = Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
                .filter(b -> b.commodityId().equals(WATER) && fitted.fit.installedModules().stream().anyMatch(m -> m.moduleId().equals(b.moduleId())))
                .findFirst().orElseThrow();
        String mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var storage = pilot.campaign().coordinator().runtime().infrastructure().endpoint(station).storage();
        double stock = storage.commodityMassKg(WATER);
        double tank = pilot.reactionMassKg();
        long wallet = pilot.player().walletMilliCredits();
        assertTrue(stock >= KG, "the station opens with a water reserve");

        var preview = pilot.campaign().previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, KG);
        assertTrue(preview.allowed());
        assertEquals(0, preview.walletChangeMilliCredits());
        pilot.action("BUY_CONSUMABLE", binding.id(), mount, KG);

        assertEquals(wallet, pilot.player().walletMilliCredits(), "own stock is not sold to its owner");
        assertEquals(stock - KG, storage.commodityMassKg(WATER), 1e-6);
        assertEquals(tank + KG, pilot.reactionMassKg(), 1e-6);
        assertTrue(!pilot.allowed("BUY_CONSUMABLE", binding.id(), mount, KG), "one handling interval per tick, as for a sale");
        pilot.saveAndReload();
        assertEquals(tank + KG, pilot.reactionMassKg(), 1e-6);
    }
}
