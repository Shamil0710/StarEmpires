package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16SupplyProjectOrderAcceptanceTest {
    @Test
    void persistentSupplyOrderBuysPhysicalCargoAndOwnerDeliversWithoutSelfSale() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_901L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placementView = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placementView.x(), placementView.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        ContentCatalog.ItemDefinition steel = scenario.content().findItem("item.steel");
        assertNotNull(steel);

        FleetPlacementState supplyFleet = findFleetByArchetype(
                runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "ship.steel_hauler");
        Entity supplyShip = entity(runtime, supplyFleet);
        InventoryComponent cargo = supplyShip.getComponent(InventoryComponent.class);
        clearInventory(cargo);
        cargo.capacity = 4;
        Entity supplier = findMarketByArchetype(
                runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "station.foundry");
        DiscoveredObjectRef supplierRef = ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, supplier);
        addOwnedFleetAndDiscoveries(runtime, supplyFleet.id(), List.of(supplierRef));

        TransformComponent shipTransform = supplyShip.getComponent(TransformComponent.class);
        TransformComponent supplierTransform = supplier.getComponent(TransformComponent.class);
        shipTransform.position.set(supplierTransform.position.x - 45f, supplierTransform.position.y);
        shipTransform.velocity.setZero();
        long playerBefore = runtime.player().walletMilliCredits();
        long supplierBefore = supplier.getComponent(WalletComponent.class).getBalanceMilliCredits();
        SimulationSession siteSession = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = siteSession.getEntityRegistry().find(project.constructionSiteEntityId());
        long siteMoneyBefore = site.getComponent(WalletComponent.class).getBalanceMilliCredits();

        PlayerFleetOrderService orders = new PlayerFleetOrderService(runtime);
        assertTrue(orders.supplyProject(supplyFleet.id(), projectId, steel.id()));
        assertEquals(FleetOrderType.SUPPLY_PROJECT, orders.order(supplyFleet.id()).orElseThrow().type());

        byte[] encoded = PlayableWorldStateCodec.encode(runtime.snapshot());
        PlayerRuntime restored = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(encoded),
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerFleetOrderState restoredOrder = new PlayerFleetOrderService(restored)
                .order(supplyFleet.id()).orElseThrow();
        assertEquals(FleetOrderType.SUPPLY_PROJECT, restoredOrder.type());
        assertEquals(project.constructionSiteEntityId(), restoredOrder.targetEntityId());
        assertEquals(steel.id(), restoredOrder.itemContentId());

        boolean bought = false;
        long walletAfterBuy = restored.player().walletMilliCredits();
        int delivered = 0;
        for (int step = 0; step < 6000 && delivered == 0; step++) {
            restored.advanceFrame(0.1f);
            FleetPlacementState currentPlacement = restored.world().findFleet(supplyFleet.id()).orElseThrow();
            if (currentPlacement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                Entity currentShip = entity(restored, currentPlacement);
                InventoryComponent currentCargo = currentShip.getComponent(InventoryComponent.class);
                if (!bought && currentCargo.stock[steel.runtimeId()] > 0) {
                    bought = true;
                    walletAfterBuy = restored.player().walletMilliCredits();
                }
            }
            ConstructionProjectState currentProject = restored.world().findConstructionProject(projectId).orElseThrow();
            delivered = currentProject.materials().stream()
                    .filter(material -> steel.id().equals(material.itemContentId()))
                    .findFirst().orElseThrow().deliveredAmount();
        }

        assertTrue(bought, "SUPPLY_PROJECT must acquire real cargo at the discovered supplier");
        assertTrue(walletAfterBuy < playerBefore,
                "ordinary supplier purchase must debit the shared player wallet");
        assertTrue(delivered > 0, "the same physical FleetId must deliver cargo into the owned site");
        Entity restoredSupplier = restored.world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().find(supplierRef.entityId());
        long supplierAfter = restoredSupplier.getComponent(WalletComponent.class).getBalanceMilliCredits();
        assertEquals(playerBefore - walletAfterBuy, supplierAfter - supplierBefore,
                "purchase money must move to the ordinary supplier wallet");
        Entity restoredSite = restored.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        assertEquals(siteMoneyBefore, restoredSite.getComponent(WalletComponent.class).getBalanceMilliCredits(),
                "owner delivery must not manufacture a self-sale or move site money");
        assertEquals(FleetOrderType.SUPPLY_PROJECT,
                new PlayerFleetOrderService(restored).order(supplyFleet.id()).orElseThrow().type(),
                "durable order remains assigned after partial fulfillment");
    }

    @Test
    void staleSupplierStockReplansToAnotherDiscoveredPhysicalMarket() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_902L);
        PlayerRuntime runtime = scenario.runtime();
        FleetPlacementState supplyFleet = findFleetByArchetype(
                runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "ship.steel_hauler");
        Entity localFoundry = findMarketByArchetype(
                runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "station.foundry");
        Entity innerFoundry = findMarketByArchetype(
                runtime, DemoGalaxyFactory.INNER_SYSTEM_ID, "station.foundry");
        DiscoveredObjectRef localRef = ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, localFoundry);
        DiscoveredObjectRef innerRef = ref(DemoGalaxyFactory.INNER_SYSTEM_ID, innerFoundry);
        addOwnedFleetAndDiscoveries(runtime, supplyFleet.id(), List.of(localRef, innerRef));
        DiscoveredObjectRef siteRef = ref(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                findMarketByArchetype(runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "station.mining_base"));
        PlayerSupplyProjectPlanner planner = new PlayerSupplyProjectPlanner(runtime);

        PlayerSupplyProjectPlan first = planner.plan(supplyFleet.id(), siteRef, "item.steel").orElseThrow();
        assertEquals(localRef, first.supplier(), "same-system known stock should win the fulfillment route baseline");

        ContentCatalog.ItemDefinition steel = scenario.content().findItem("item.steel");
        localFoundry.getComponent(InventoryComponent.class).stock[steel.runtimeId()] = 0;
        PlayerSupplyProjectPlan replanned = planner.plan(supplyFleet.id(), siteRef, steel.id()).orElseThrow();

        assertEquals(innerRef, replanned.supplier(),
                "stale source stock must be re-read instead of being reserved for the supply order");
        assertFalse(first.supplier().equals(replanned.supplier()));
    }

    @Test
    void cumulativeRouteDangerCanChangeDeterministicSupplierChoice() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(16_903L, content);
        List<JumpConnection> links = new ArrayList<>(base.topology().connections());
        links.add(new JumpConnection(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        GalaxyTopology topology = new GalaxyTopology(
                base.topology().id(),
                base.topology().name(),
                base.topology().sectors(),
                links);
        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                base.systems(),
                base.factions(),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps());
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        world.advanceFrame(1.0f);
        FleetPlacementState supplyFleet = findFleetByArchetype(
                world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "ship.steel_hauler");
        Entity innerFoundry = findMarketByArchetype(
                world, DemoGalaxyFactory.INNER_SYSTEM_ID, "station.foundry");
        Entity frontierFoundry = findMarketByArchetype(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, "station.foundry");
        Entity siteEntity = findMarketByArchetype(
                world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "station.mining_base");
        DiscoveredObjectRef innerRef = ref(DemoGalaxyFactory.INNER_SYSTEM_ID, innerFoundry);
        DiscoveredObjectRef frontierRef = ref(DemoGalaxyFactory.FRONTIER_SYSTEM_ID, frontierFoundry);
        PlayerState player = new PlayerState(
                100_000_000L,
                "faction.miners",
                List.of(),
                List.of(supplyFleet.id()),
                supplyFleet.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                List.of(innerRef, frontierRef),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        PlayerSupplyProjectPlanner planner = new PlayerSupplyProjectPlanner(runtime);
        DiscoveredObjectRef siteRef = ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, siteEntity);

        PlayerSupplyProjectPlan baseline = planner.plan(
                supplyFleet.id(), siteRef, "item.steel").orElseThrow();
        assertEquals(innerRef, baseline.supplier());
        assertEquals(innerRef, planner.plan(supplyFleet.id(), siteRef, "item.steel").orElseThrow().supplier(),
                "same authoritative state must produce the same supplier tie-breaking");

        long tick = runtime.world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        assertTrue(new PlayerThreatIntelService(runtime).observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                100f,
                1f,
                tick));
        PlayerSupplyProjectPlan safer = planner.plan(
                supplyFleet.id(), siteRef, "item.steel").orElseThrow();

        assertEquals(frontierRef, safer.supplier(),
                "supplier utility must include cumulative route danger rather than price alone");
        assertTrue(safer.route().path().contains(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertFalse(safer.route().path().contains(DemoGalaxyFactory.INNER_SYSTEM_ID),
                "the direct frontier connection should avoid the observed dangerous link");
    }

    private static PlayerConstructionPlacementView findValidPlacement(PlayerConstructionService construction) {
        for (float y = 100f; y <= Constants.WORLD_HEIGHT - 100f; y += 100f) {
            for (float x = 100f; x <= Constants.WORLD_WIDTH - 100f; x += 100f) {
                PlayerConstructionPlacementView view = construction.previewPlacement(x, y);
                if (view.allowed()) {
                    return view;
                }
            }
        }
        throw new AssertionError("Playable test world has no valid construction placement");
    }

    private static FleetPlacementState findFleetByArchetype(
            PlayerRuntime runtime,
            StarSystemId systemId,
            String archetypeId) {
        return findFleetByArchetype(runtime.world(), systemId, archetypeId);
    }

    private static FleetPlacementState findFleetByArchetype(
            WorldSimulation world,
            StarSystemId systemId,
            String archetypeId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM || !systemId.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            ArchetypeComponent archetype = entity == null ? null : entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)) {
                return placement;
            }
        }
        throw new AssertionError("No fleet archetype " + archetypeId + " in system " + systemId);
    }

    private static Entity findMarketByArchetype(
            PlayerRuntime runtime,
            StarSystemId systemId,
            String archetypeId) {
        return findMarketByArchetype(runtime.world(), systemId, archetypeId);
    }

    private static Entity findMarketByArchetype(
            WorldSimulation world,
            StarSystemId systemId,
            String archetypeId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("No market archetype " + archetypeId + " in system " + systemId);
    }

    private static Entity entity(PlayerRuntime runtime, FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
    }

    private static DiscoveredObjectRef ref(StarSystemId systemId, Entity entity) {
        EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
        assertNotNull(id);
        return new DiscoveredObjectRef(systemId, id.id);
    }

    private static void addOwnedFleetAndDiscoveries(
            PlayerRuntime runtime,
            FleetId fleetId,
            List<DiscoveredObjectRef> discoveries) {
        PlayerState previous = runtime.player();
        List<FleetId> fleets = new ArrayList<>(previous.ownedFleetIds());
        if (!fleets.contains(fleetId)) {
            fleets.add(fleetId);
        }
        List<StarSystemId> systems = new ArrayList<>(previous.discoveredSystemIds());
        List<DiscoveredObjectRef> objects = new ArrayList<>(previous.discoveredObjects());
        for (DiscoveredObjectRef reference : discoveries) {
            if (!systems.contains(reference.systemId())) {
                systems.add(reference.systemId());
            }
            if (!objects.contains(reference)) {
                objects.add(reference);
            }
        }
        runtime.replacePlayerState(new PlayerState(
                previous.walletMilliCredits(),
                previous.factionContentId(),
                previous.reputations(),
                fleets,
                previous.activeFleetId(),
                systems,
                objects,
                previous.homeSystemId(),
                previous.dockedAt(),
                previous.fleetOrders(),
                previous.threatIntel(),
                previous.ownedConstructionProjectIds(),
                previous.ownedStations()));
    }

    private static void clearInventory(InventoryComponent inventory) {
        for (int index = 0; index < inventory.stock.length; index++) {
            inventory.stock[index] = 0;
        }
    }
}
