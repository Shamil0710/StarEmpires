package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.economy.MarketConsumption;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerState;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit fixtures: the pilot owns the local industrial station, the local hub has no water and a
 * Union reserve freighter's tank is empty, so its departure must buy reaction mass from the pilot.
 */
@Tag("slow")
class GeneratedCampaignPersonalStationSaleTest {
    private static final String STATION = "industry.1";
    private static final String WATER = "commodity.material.purified_water";
    private static final String BUYER = "faction.beta";

    @Test void npcDeparturePaysThePilotAtTheStationAskForTheWaterItTakes() {
        var c = dryHubCampaign();
        var world = c.coordinator().runtime().world();
        var fleet = emptyTankedReserve(c);
        var route = List.of(new StarSystemId(1), world.getTopology().neighbors(new StarSystemId(1)).iterator().next());
        var storage = c.coordinator().runtime().infrastructure().endpoint(STATION).storage();
        double water = storage.commodityMassKg(WATER);
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        long treasury = treasury(c);
        long ask = c.pilotCommodityPrice(STATION, WATER, true);

        var preparation = world.prepareFleetPropellantDeparture(fleet, route);

        assertTrue(preparation.ready(), preparation.reason());
        assertEquals(List.of(STATION), preparation.stationIds());
        double bought = preparation.loadedMassKg();
        long paid = (long) Math.ceil(bought * ask);
        assertTrue(bought > 0);
        assertEquals(water - bought, storage.commodityMassKg(WATER), 1e-6);
        assertEquals(wallet + paid, c.playerState().orElseThrow().walletMilliCredits(), "the pilot is paid the station ask");
        assertEquals(treasury - paid, treasury(c), "the buyer's treasury pays");
        var sale = c.playerJournal().entries().stream().filter(e -> e.action().equals("STATION_SALE")).toList();
        assertEquals(1, sale.size());
        assertEquals(paid, sale.get(0).walletDeltaMilliCredits());
        var saved = c.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }

    @Test void thePilotsReserveIsNeverSoldAndADepartureNeedingMoreWaits() {
        double needed = boughtWithoutTerms();
        var c = dryHubCampaign();
        double stock = water(c);
        // Exactly the needed water above the reserve: the departure buys it and the reserve stays.
        long reserve = (long) Math.floor(stock - needed - 10);
        set(c, "SET_STATION_RESERVE", reserve);
        assertTrue(depart(c).ready());
        assertTrue(water(c) >= reserve, "stock never falls below the pilot's reserve");
        // Half of what is needed above the reserve: nothing is taken, the freighter waits.
        var short_ = dryHubCampaign();
        set(short_, "SET_STATION_RESERVE", (long) Math.floor(stock - needed / 2));
        assertFalse(depart(short_).ready());
        assertEquals(stock, water(short_), 1e-9);
    }

    @Test void npcsPayThePilotsPriceUpToTheMarketCeilingAndRefuseAbove() {
        long ceiling = Stage228CampaignAuthority.npcAcceptableAsk(WATER);
        var c = dryHubCampaign();
        set(c, "SET_STATION_PRICE", ceiling);
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var preparation = depart(c);
        assertTrue(preparation.ready(), preparation.reason());
        assertEquals(wallet + (long) Math.ceil(preparation.loadedMassKg() * ceiling), c.playerState().orElseThrow().walletMilliCredits(),
                "the buyer pays the pilot's price");

        var dear = dryHubCampaign();
        set(dear, "SET_STATION_PRICE", ceiling + 1);
        double stock = water(dear);
        assertFalse(depart(dear).ready(), "a price above every market's ceiling is refused");
        assertEquals(stock, water(dear), 1e-9);
    }

    @Test void saleTermsNeedTheOwnedStationAndSurviveSaveLoad() {
        var c = ownedStationCampaign();
        assertFalse(c.previewPilotAction("SET_STATION_PRICE", "hub.1", WATER, 80).allowed(), "not the pilot's station");
        assertFalse(c.previewPilotAction("SET_STATION_RESERVE", STATION, WATER, -1).allowed());
        assertFalse(c.previewPilotAction("SET_STATION_PRICE", STATION, "commodity.unknown", 80).allowed());
        set(c, "SET_STATION_PRICE", 80);
        set(c, "SET_STATION_RESERVE", 5_000_000);
        assertEquals(new com.spacesim.persistence.StationSalePolicy(STATION, WATER, 80, 5_000_000), c.stationSalePolicy(STATION, WATER));
        assertEquals(80, c.stationSaleAsk(STATION, WATER));
        assertEquals(2, c.playerJournal().entries().stream().filter(e -> e.action().startsWith("SET_STATION_")).count());
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
        assertEquals(c.captureState(), restored.captureState());
        assertEquals(c.stationSalePolicy(STATION, WATER), restored.stationSalePolicy(STATION, WATER));
        set(restored, "SET_STATION_PRICE", 0);
        set(restored, "SET_STATION_RESERVE", 0);
        assertTrue(restored.captureState().stationSalePolicies().isEmpty(), "default terms are not kept");
    }

    private static double boughtWithoutTerms() {
        return depart(dryHubCampaign()).loadedMassKg();
    }

    private static com.spacesim.world.FleetPropellantLogisticsAuthority.DeparturePreparation depart(Stage228CampaignAuthority c) {
        var world = c.coordinator().runtime().world();
        var fleet = emptyTankedReserve(c);
        return world.prepareFleetPropellantDeparture(fleet,
                List.of(new StarSystemId(1), world.getTopology().neighbors(new StarSystemId(1)).iterator().next()));
    }

    private static void set(Stage228CampaignAuthority c, String action, long value) {
        var preview = c.previewPilotAction(action, STATION, WATER, Math.toIntExact(value));
        assertTrue(preview.allowed(), action + ' ' + value);
        assertEquals(0, preview.walletChangeMilliCredits());
        c.submitPilotAction(preview);
    }

    private static double water(Stage228CampaignAuthority c) {
        return c.coordinator().runtime().infrastructure().endpoint(STATION).storage().commodityMassKg(WATER);
    }

    @Test void aBuyerThatCannotPayGetsNothingFromThePilotsStation() {
        var c = dryHubCampaign();
        var world = c.coordinator().runtime().world();
        assertTrue(world.transferFromFactionTreasury(BUYER, new WalletComponent(), "fixture.sink", treasury(c), "fixture.empty-treasury"));
        var fleet = emptyTankedReserve(c);
        var route = List.of(new StarSystemId(1), world.getTopology().neighbors(new StarSystemId(1)).iterator().next());
        double water = c.coordinator().runtime().infrastructure().endpoint(STATION).storage().commodityMassKg(WATER);
        long wallet = c.playerState().orElseThrow().walletMilliCredits();

        var preparation = world.prepareFleetPropellantDeparture(fleet, route);

        assertFalse(preparation.ready(), "no free stock and no payable stock: the freighter waits");
        assertEquals(water, c.coordinator().runtime().infrastructure().endpoint(STATION).storage().commodityMassKg(WATER));
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
    }

    @Test void theSellersRecurringRunStartsNoNewCycleForTheSoldStation() {
        var pilot = new ScenarioPilot(ownedStationCampaign(), 400_000);
        var order = pilot.campaign().coordinator().runtime().freight().capture().orders().stream()
                .filter(o -> o.destinationEndpointId().equals(STATION) || o.sourceEndpointId().equals(STATION)).findFirst().orElseThrow();
        var fleet = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> order.orderId().equals(f.activeOrderId())).findFirst().orElseThrow().fleetId();
        while (freighter(pilot, fleet).phase() != FreightPhase.AT_SOURCE) pilot.ticks(1000);
        double cargo = freighter(pilot, fleet).cargoMassKg();
        pilot.ticks(20_000);
        assertEquals(FreightPhase.AT_SOURCE, freighter(pilot, fleet).phase(), order.orderId() + " dispatches no new cycle");
        assertEquals(cargo, freighter(pilot, fleet).cargoMassKg(), 1e-9, order.orderId() + " loads nothing for the sold station");
    }

    private static com.spacesim.persistence.Stage20FreightPersistentState.FreighterState freighter(ScenarioPilot pilot, FleetId fleet) {
        return pilot.campaign().coordinator().runtime().freight().findFreighter(fleet).orElseThrow();
    }

    private static long treasury(Stage228CampaignAuthority c) {
        return c.coordinator().runtime().world().findFactionEconomicState(BUYER).orElseThrow().treasuryMilliCredits();
    }

    private static Stage228CampaignAuthority dryHubCampaign() {
        var c = ownedStationCampaign();
        var hub = c.coordinator().runtime().infrastructure().endpoint("hub.1").storage();
        if (hub.commodityMassKg(WATER) > 0) MarketConsumption.consume(hub, WATER, hub.commodityMassKg(WATER));
        return c;
    }

    /** An idle Union reserve freighter in the home system with its reaction-mass tank emptied. */
    private static FleetId emptyTankedReserve(Stage228CampaignAuthority c) {
        var runtime = c.coordinator().runtime();
        var reserve = runtime.freight().capture().freighters().stream()
                .filter(f -> f.stableFactionId().equals(BUYER) && f.phase() == FreightPhase.IDLE && f.currentSystemId().value() == 1
                        && !c.playerState().orElseThrow().ownedFleetIds().contains(f.fleetId()))
                .findFirst().orElseThrow();
        var placement = runtime.world().findFleet(reserve.fleetId()).orElseThrow();
        var fitted = runtime.world().findSession(placement.systemId()).orElseThrow().getEntityRegistry()
                .require(placement.localEntityId()).getComponent(EngineeringComponent.class);
        var old = fitted.runtimeState;
        var stores = old.consumables();
        var loads = stores.interfaceLoads().stream().map(l -> l.kind() == InterfaceKind.REACTION_MASS
                ? new ConsumableLoad(l.mountId(), l.interfaceId(), l.kind(), 0, 0, 0) : l).toList();
        fitted.setRuntimeState(new RuntimeState(new ConsumableState(stores.cargoMassKg(), stores.storesMassKg(),
                stores.missionPayloadMassKg(), stores.missionIntegrationVolumeM3(), loads), old.sharedBusEnergyJ(), old.shipHeatStoredJ(),
                old.localHeatJByMount(), old.thrustLimitNByMount(), old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
        return reserve.fleetId();
    }

    /** New seed-2 game whose pilot owns the industrial station of the starting system. */
    static Stage228CampaignAuthority ownedStationCampaign() {
        var start = Stage228CampaignAuthority.create(2);
        start.submitIndependentPilotStart(start.previewIndependentPilotStart());
        var p = start.playerState().orElseThrow();
        var ref = start.pilotMarketReference(STATION).orElseThrow();
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        return Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                start.captureState(), start.coordinator().runtime().captureState(), owner));
    }
}
