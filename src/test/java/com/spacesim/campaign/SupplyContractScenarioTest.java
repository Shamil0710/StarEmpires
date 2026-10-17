package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.world.Stage21HNpcMissionService.PlayerCommand;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary paid supply contract: a new pilot buys the local miner and resource maps, learns of a
 * dispatcher's paid delivery from the journal while docked, accepts part of it, mines the commodity
 * from a mapped site, buys the recipient's station map, reaches its system by ordinary jumps, docks
 * and sells — completing the contract and receiving the issuer's escrowed premium. Only pilot inputs.
 */
@Tag("scenario")
@Tag("slow")
class SupplyContractScenarioTest {
    private static final long SEED = 2L;
    private static final double CRUISE_MPS = 3000d;
    private static final int ACCEPTED_KG = 100;

    @Test
    void ordinaryPaidSupplyContractIsMinedDeliveredAndSettled() {
        long t0 = System.nanoTime();
        var start = Stage228CampaignAuthority.create(SEED);
        start.submitIndependentPilotStart(start.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(start, 3_000_000L);
        var home = pilot.fleet().systemId();

        // 1. Buy the local miner and the local dispatcher's resource maps at the starting hub.
        var miner = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        String hub = "hub." + home.value();
        pilot.fly(endpoint(pilot, hub).position(), 500, CRUISE_MPS);
        pilot.action("DOCK", hub, "", 0);
        pilot.action("PURCHASE", Long.toString(miner.fleetId().value()), "", 0);
        for (var offer : com.spacesim.ui.GeneratedCampaignResourceMapUi.rows(pilot.campaign())) {
            var id = offer.selection().stableId().split("\\|");
            pilot.action("BUY_RESOURCE_MAP", id[1], id[2], 0);
        }
        pilot.action("UNDOCK", "", "", 0);
        pilot.action("SWITCH", Long.toString(miner.fleetId().value()), "", 0);
        phase("miner and maps", t0, pilot);

        // 2. Visit every known local station until a docked dispatcher announces a paid delivery.
        long t = System.nanoTime();
        var offer = findAnnouncedOffer(pilot);
        var proposal = pilot.campaign().coordinator().npcMissions().missions().stream()
                .filter(m -> m.missionId().equals(offer.subject())).findFirst().orElseThrow();
        String commodity = proposal.objective().requiredState();
        String recipient = proposal.objective().subjectId();
        var recipientSystem = new StarSystemId(proposal.objective().systemId());
        var accept = pilot.campaign().previewMissionCommand(PlayerCommand.ACCEPT, proposal.missionId(), ACCEPTED_KG);
        assertTrue(accept.allowed(), "the announced offer can be accepted for part of its quantity");
        var contract = pilot.campaign().submitMissionCommand(accept);
        long escrow = contract.escrowMilliCredits();
        assertTrue(escrow > 0, "the accepted part is funded from the issuer's treasury");
        // The recipient's position comes from the same dispatcher's paid station map, if not already known.
        for (var map : com.spacesim.ui.GeneratedCampaignStationMapUi.rows(pilot.campaign())) {
            var id = map.selection().stableId().split("\\|");
            if (id[2].equals(recipient)) pilot.action("BUY_STATION_MAP", id[1], id[2], 0);
        }
        int routeMaps = 0;
        for (var map : com.spacesim.ui.GeneratedCampaignRouteMapUi.offers(pilot.campaign())) {
            var id = map.selection().stableId().split("\\|", 3);
            pilot.action("BUY_ROUTE_MAP", id[1], id[2], 0);
            routeMaps++;
        }
        System.out.println("SCENARIO route maps bought: " + routeMaps + ", known systems " + pilot.player().discoveredSystemIds());
        var recipientLocation = personalLocation(pilot, recipient)
                .orElseThrow(() -> new AssertionError("no ordinary way to learn where " + recipient + " is"));
        phase("accept " + ACCEPTED_KG + " kg of " + commodity + " for " + recipient, t, pilot);

        // 3. Mine the commodity at the nearest mapped site, then travel and deliver.
        t = System.nanoTime();
        pilot.action("UNDOCK", "", "", 0);
        var site = pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()
                        && k.object().systemId().equals(pilot.fleet().systemId())
                        && k.classificationId().filter(commodity::equals).isPresent())
                .min(Comparator.comparingDouble(k -> k.knownLocation().orElseThrow().distanceTo(pilot.physical().position())))
                .orElseThrow(() -> new AssertionError("no mapped local site of " + commodity));
        pilot.fly(site.knownLocation().orElseThrow(), 500, CRUISE_MPS);
        pilot.action("START_MINING", site.object().objectId(), "", 100);
        while (cargoKg(pilot, commodity) < ACCEPTED_KG) pilot.tick();
        pilot.action("STOP_MINING", "", "", 0);
        phase("mined " + Math.round(cargoKg(pilot, commodity)) + " kg", t, pilot);

        t = System.nanoTime();
        var route = pilot.explore(recipientSystem, 12);
        pilot.saveAndReload();
        pilot.fly(recipientLocation, 500, CRUISE_MPS);
        pilot.action("DOCK", recipient, "", 0);
        pilot.tick();
        phase("travel " + route, t, pilot);

        // 4. Sell the contracted mass: the sale completes the contract and releases the escrow.
        t = System.nanoTime();
        long walletBefore = pilot.player().walletMilliCredits();
        long marketPaid = 0;
        for (int left = ACCEPTED_KG; left > 0; ) {
            int handling = (int) Math.floor(endpoint(pilot, recipient).handlingCapability().massRateKgPerSecond()
                    * pilot.campaign().coordinator().session().fixedStepSeconds());
            int batch = Math.min(left, handling);
            long marketBefore = marketWallet(pilot, recipient);
            pilot.action("SELL", recipient, commodity, batch);
            marketPaid += marketBefore - marketWallet(pilot, recipient);
            left -= batch;
            pilot.tick();
        }
        var settled = pilot.campaign().coordinator().npcMissions().missions().stream()
                .filter(m -> m.missionId().equals(contract.missionId())).findFirst().orElseThrow();
        assertEquals(MissionStatus.COMPLETED, settled.status(), "the delivery completes the contract");
        assertTrue(marketPaid > 0, "the recipient market pays for the delivered commodity");
        long expected = walletBefore + marketPaid + escrow;
        assertEquals(expected, pilot.player().walletMilliCredits(),
                "the pilot receives the market's payment plus the escrowed premium exactly once");
        pilot.saveAndReload();
        pilot.ticks(100);
        assertEquals(expected, pilot.player().walletMilliCredits(), "no premium is paid twice");
        phase("settled", t, pilot);
        phase("total", t0, pilot);
    }

    private static com.spacesim.player.PlayerJournalState.Entry findAnnouncedOffer(ScenarioPilot pilot) {
        var stations = pilot.campaign().coordinator().runtime().infrastructure().endpoints().stream()
                .filter(e -> e.systemId().equals(pilot.fleet().systemId()))
                .sorted(Comparator.comparing(e -> e.stationId())).toList();
        for (var station : stations) {
            pilot.fly(station.position(), 500, CRUISE_MPS);
            if (!pilot.allowed("DOCK", station.stationId(), "", 0)) continue;
            pilot.action("DOCK", station.stationId(), "", 0);
            for (int i = 0; i < 1200; i++) {
                var announced = pilot.campaign().playerJournal().entries().stream()
                        .filter(e -> e.action().equals("SUPPLY_OFFER_AVAILABLE")).reduce((a, b) -> b);
                if (announced.isPresent()) return announced.orElseThrow();
                pilot.tick();
            }
            pilot.action("UNDOCK", "", "", 0);
        }
        throw new AssertionError("no docked dispatcher announced a paid delivery in the starting system");
    }

    private static java.util.Optional<com.spacesim.world.LocalPhysicalPosition> personalLocation(ScenarioPilot pilot, String stationId) {
        return pilot.campaign().coordinator().runtime().discoveryState()
                .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> k.object().objectId().equals(stationId) && k.knownLocation().isPresent())
                .map(k -> k.knownLocation().orElseThrow()).findFirst();
    }

    private static void phase(String name, long startNanos, ScenarioPilot pilot) {
        System.out.printf("SCENARIO %-70s %6.1fs  tick=%d%n", name, (System.nanoTime() - startNanos) / 1e9,
                pilot.world().getAuthoritativeWorldTick());
    }

    private static com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint(ScenarioPilot pilot, String id) {
        return pilot.campaign().coordinator().runtime().infrastructure().endpoint(id);
    }

    private static long marketWallet(ScenarioPilot pilot, String stationId) {
        var ref = pilot.campaign().pilotMarketReference(stationId).orElseThrow();
        return pilot.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits();
    }

    private static double cargoKg(ScenarioPilot pilot, String commodity) {
        return pilot.campaign().coordinator().runtime().freight().findFreighter(pilot.fleetId()).orElseThrow()
                .cargoStorage().commodityMassByIdKg().getOrDefault(commodity, 0d);
    }
}
