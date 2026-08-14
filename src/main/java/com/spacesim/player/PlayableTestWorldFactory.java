package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProcurementPolicyComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.List;
import java.util.Objects;

/**
 * Curated Stage-12 desktop test world built entirely from ordinary production simulation objects.
 *
 * <p>The scenario does not introduce a player-only economy. It starts from the ordinary demo
 * galaxy, assigns one existing physical FleetId to the player, creates a deliberate destination
 * shortage as an initial condition, and places the chosen ship near a compatible source market.
 * Once gameplay starts, all movement, docking, travel and trade use the same Stage-10/12 APIs as
 * the rest of the simulation.</p>
 */
public final class PlayableTestWorldFactory {
    /** Stable seed used by the downloadable/manual test build. */
    public static final long DEFAULT_TEST_SEED = 0x5EED_2026L;
    /** Starting personal balance in visible credits. */
    public static final double STARTING_CREDITS = 25_000d;
    /** Minimum cargo units available on the recommended source route. */
    public static final int RECOMMENDED_TEST_UNITS = 8;
    private static final float START_DISTANCE_FROM_MARKET = 70f;

    private PlayableTestWorldFactory() {
        throw new AssertionError("PlayableTestWorldFactory does not create instances");
    }

    /**
     * Creates a deterministic playable test scenario.
     *
     * @param rootSeed deterministic world seed
     * @return content catalog, playable runtime and recommended two-system trade route
     */
    public static Scenario create(long rootSeed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(rootSeed);
        Setup setup = findSetup(world, content);
        prepareDestinationShortage(world, setup);

        // Materialize real market prices through the ordinary fixed-tick pipeline before play.
        world.advanceFrame(0.1f);
        placePlayerShipNearSource(world, setup);

        MarketComponent sourceMarket = setup.sourceStation().getComponent(MarketComponent.class);
        MarketComponent destinationMarket = setup.destinationStation().getComponent(MarketComponent.class);
        int itemId = setup.item().runtimeId();
        float sourceSell = sourceMarket.sellPrices[itemId];
        float destinationBuy = destinationMarket.buyPrices[itemId];
        if (!(Float.isFinite(sourceSell)
                && Float.isFinite(destinationBuy)
                && sourceSell > 0f
                && destinationBuy > sourceSell)) {
            throw new IllegalStateException("Curated test route is not profitable after market initialization");
        }

        PlayerState player = new PlayerState(
                Money.fromCredits(STARTING_CREDITS),
                setup.factionContentId(),
                List.of(new PlayerReputationState(setup.factionContentId(), 20f)),
                List.of(setup.fleet().id()),
                setup.fleet().id(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        Route route = new Route(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                setup.stationName(),
                setup.stationName(),
                setup.item().id(),
                setup.item().displayName(),
                sourceSell,
                destinationBuy);
        return new Scenario(content, runtime, route);
    }

    private static Setup findSetup(WorldSimulation world, ContentCatalog content) {
        SimulationSession sourceSession = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        SimulationSession destinationSession = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(fleet.systemId())) {
                continue;
            }
            Entity ship = sourceSession.getEntityRegistry().find(fleet.localEntityId());
            ShipComponent role = ship == null ? null : ship.getComponent(ShipComponent.class);
            InventoryComponent cargo = ship == null ? null : ship.getComponent(InventoryComponent.class);
            if (role == null || cargo == null || cargo.getFreeCapacity() < RECOMMENDED_TEST_UNITS) {
                continue;
            }
            for (Entity sourceStation : sourceSession.getEngine().getEntities()) {
                MarketComponent sourceMarket = sourceStation.getComponent(MarketComponent.class);
                InventoryComponent sourceInventory = sourceStation.getComponent(InventoryComponent.class);
                FactionComponent sourceFaction = sourceStation.getComponent(FactionComponent.class);
                IdentityComponent sourceIdentity = sourceStation.getComponent(IdentityComponent.class);
                ProcurementPolicyComponent sourceProcurement =
                        sourceStation.getComponent(ProcurementPolicyComponent.class);
                if (sourceMarket == null
                        || sourceInventory == null
                        || sourceFaction == null
                        || sourceIdentity == null
                        || sourceProcurement != null) {
                    continue;
                }
                Entity destinationStation = findMarketByName(destinationSession, sourceIdentity.name);
                if (destinationStation == null
                        || destinationStation.getComponent(ProcurementPolicyComponent.class) != null) {
                    continue;
                }
                MarketComponent destinationMarket = destinationStation.getComponent(MarketComponent.class);
                for (ContentCatalog.ItemDefinition item : content.getItems()) {
                    int itemId = item.runtimeId();
                    if (role.canPurchaseItem(itemId)
                            && sourceMarket.isTradable(itemId)
                            && destinationMarket.isTradable(itemId)
                            && sourceInventory.stock[itemId] >= RECOMMENDED_TEST_UNITS
                            && destinationMarket.targetStock[itemId] > 1) {
                        ContentCatalog.FactionDefinition faction = content.findFaction(sourceFaction.factionId);
                        if (faction == null) {
                            continue;
                        }
                        return new Setup(
                                fleet,
                                ship,
                                sourceStation,
                                destinationStation,
                                sourceIdentity.name,
                                faction.id(),
                                item);
                    }
                }
            }
        }
        throw new IllegalStateException("Demo galaxy has no compatible curated Stage-12 trade route");
    }

    private static void prepareDestinationShortage(WorldSimulation world, Setup setup) {
        SimulationSession destinationSession = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        Entity destination = destinationSession.getEntityRegistry().find(
                setup.destinationStation().getComponent(EntityIdComponent.class).id);
        if (destination == null) {
            destination = setup.destinationStation();
        }
        InventoryComponent inventory = destination.getComponent(InventoryComponent.class);
        MarketComponent market = destination.getComponent(MarketComponent.class);
        inventory.stock[setup.item().runtimeId()] = 1;
        market.isDirty = true;
        setup.sourceStation().getComponent(MarketComponent.class).isDirty = true;
    }

    private static void placePlayerShipNearSource(WorldSimulation world, Setup setup) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity ship = session.getEntityRegistry().find(setup.fleet().localEntityId());
        TransformComponent shipTransform = Objects.requireNonNull(
                ship == null ? null : ship.getComponent(TransformComponent.class),
                "Chosen player ship has no TransformComponent");
        TransformComponent stationTransform = setup.sourceStation().getComponent(TransformComponent.class);
        shipTransform.position.set(
                Math.max(20f, stationTransform.position.x - START_DISTANCE_FROM_MARKET),
                stationTransform.position.y);
        shipTransform.velocity.setZero();
    }

    private static Entity findMarketByName(SimulationSession session, String stationName) {
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null
                    && identity.kind == IdentityComponent.Kind.STATION
                    && stationName.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Complete playable test-world bootstrap result.
     *
     * @param content authoritative content catalog
     * @param runtime initialized player runtime
     * @param route recommended manual acceptance route
     */
    public record Scenario(ContentCatalog content, PlayerRuntime runtime, Route route) {
        /**
         * Validates the scenario dependencies.
         *
         * @param content authoritative content catalog
         * @param runtime initialized player runtime
         * @param route recommended manual acceptance route
         */
        public Scenario {
            Objects.requireNonNull(content, "Scenario content not set");
            Objects.requireNonNull(runtime, "Scenario runtime not set");
            Objects.requireNonNull(route, "Scenario route not set");
        }
    }

    /**
     * Human-readable recommended two-system trade route for manual testing.
     *
     * @param sourceSystem source StarSystem
     * @param destinationSystem destination StarSystem
     * @param sourceStationName source market name
     * @param destinationStationName destination market name
     * @param itemContentId stable content ID of recommended cargo
     * @param itemDisplayName display name of recommended cargo
     * @param sourceSellPriceCredits initialized source station sell price
     * @param destinationBuyPriceCredits initialized destination station buy price
     */
    public record Route(
            StarSystemId sourceSystem,
            StarSystemId destinationSystem,
            String sourceStationName,
            String destinationStationName,
            String itemContentId,
            String itemDisplayName,
            float sourceSellPriceCredits,
            float destinationBuyPriceCredits) {
        /**
         * Validates route metadata.
         *
         * @param sourceSystem source StarSystem
         * @param destinationSystem destination StarSystem
         * @param sourceStationName source market name
         * @param destinationStationName destination market name
         * @param itemContentId stable content ID of recommended cargo
         * @param itemDisplayName display name of recommended cargo
         * @param sourceSellPriceCredits initialized source station sell price
         * @param destinationBuyPriceCredits initialized destination station buy price
         */
        public Route {
            Objects.requireNonNull(sourceSystem, "Route source system not set");
            Objects.requireNonNull(destinationSystem, "Route destination system not set");
            sourceStationName = requireText(sourceStationName, "Route source station name not set");
            destinationStationName = requireText(destinationStationName, "Route destination station name not set");
            itemContentId = requireText(itemContentId, "Route item content ID not set");
            itemDisplayName = requireText(itemDisplayName, "Route item display name not set");
            if (!Float.isFinite(sourceSellPriceCredits)
                    || !Float.isFinite(destinationBuyPriceCredits)
                    || sourceSellPriceCredits <= 0f
                    || destinationBuyPriceCredits <= sourceSellPriceCredits) {
                throw new IllegalArgumentException("Route prices must define a positive gross margin");
            }
        }

        /**
         * Returns the opposite test-route endpoint.
         *
         * @param current current endpoint
         * @return opposite endpoint, or {@code null} when current is outside the route
         */
        public StarSystemId otherEnd(StarSystemId current) {
            if (sourceSystem.equals(current)) {
                return destinationSystem;
            }
            if (destinationSystem.equals(current)) {
                return sourceSystem;
            }
            return null;
        }
    }

    private static String requireText(String value, String message) {
        String checked = Objects.requireNonNull(value, message).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return checked;
    }

    private record Setup(
            FleetPlacementState fleet,
            Entity ship,
            Entity sourceStation,
            Entity destinationStation,
            String stationName,
            String factionContentId,
            ContentCatalog.ItemDefinition item) {
    }
}
