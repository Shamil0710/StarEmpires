package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21FFleetAllegianceAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.stage21f.fleet-allegiance";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String FOREIGN_FACTION = "faction.trade_league";
    private static final StarSystemId TARGET = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;
    private static final FleetId INVADER = new FleetId(21_660L);

    @Test
    void territorialControlTransferDoesNotRewriteExistingOrdinaryFleetIdentityOrFaction() {
        Fixture fixture = fixture(21_660L);
        WorldSimulation world = fixture.world();
        int foreignRuntimeId = world.findFactionRuntimeId(FOREIGN_FACTION).orElseThrow();
        EntityId foreignLocalId = world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Existing Foreign Fleet", IdentityComponent.Kind.FLEET))
                        .add(new FactionComponent(foreignRuntimeId)));
        FleetId foreignFleetId = world.findFleetByLocal(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, foreignLocalId)
                .orElseThrow();

        world.createEntity(TARGET, new Entity()
                .add(new IdentityComponent("Occupation Control Anchor", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new FactionComponent(PLAYER_RUNTIME_ID)));

        TerritorialTransitionService service = new TerritorialTransitionService();
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(activeInvasion()));
        FleetForceRegistry forces = new FleetForceRegistry(List.of(invaderForce()));
        long start = world.getAuthoritativeWorldTick();
        TerritorialTransitionService.AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), world, operations, forces, fixture.identities(), 1L, start);
        advanceToAtLeast(world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        TerritorialTransitionService.AdvanceResult occupied = service.advance(
                initial.transitions(), world, initial.operations(), forces, fixture.identities(),
                1L, world.getAuthoritativeWorldTick());
        assertTrue(occupied.claimCreated());

        long claimTick = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, claimTick + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);
        assertEquals(PLAYER_FACTION, world.controllingFaction(TARGET).orElseThrow());

        FleetPlacementState unchangedPlacement = world.findFleet(foreignFleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, unchangedPlacement.locationKind());
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, unchangedPlacement.systemId());
        assertEquals(foreignLocalId, unchangedPlacement.localEntityId());
        assertEquals(foreignFleetId,
                world.findFleetByLocal(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, foreignLocalId).orElseThrow());
        Entity foreignFleet = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(foreignLocalId);
        assertEquals(foreignRuntimeId, foreignFleet.getComponent(FactionComponent.class).factionId);
    }

    private static Fixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(TARGET))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(), strategy.minimumMarketAccessRelation(), strategy.relations(), controlled,
                    strategy.stationTaxBasisPoints(), strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(), strategy.productionPolicies(), strategy.strategicGoals()));
        }
        strategies.add(new FactionStrategicState(PLAYER_FACTION, 0, List.of(), List.of()));
        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(PLAYER_FACTION, 0L, 0L, 0L));
        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                PLAYER_FACTION, PLAYER_RUNTIME_ID, "Stage 21F Fleet Allegiance Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));
        WorldSimulation world = WorldSimulation.restore(
                new WorldState(
                        WorldState.CURRENT_VERSION, base.topology(), base.systems(), factions, strategies,
                        base.nextConstructionProjectIdValue(), base.constructionProjects(), base.factionEconomicPressures(),
                        base.nextFleetIdValue(), base.fleets(), base.fleetJumps(), identities),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new Fixture(world, FactionIdentityResolver.createDefault(content, identities));
    }

    private static OperationState activeInvasion() {
        return new OperationState(
                1L, OperationType.INVASION, 1L, 1L, PLAYER_RUNTIME_ID, List.of(INVADER),
                TARGET, TARGET, "system:" + TARGET.value(), RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null);
    }

    private static FleetForceRegistry.Entry invaderForce() {
        EntityState entity = new EntityState(
                new EntityId(51_660L),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(PLAYER_RUNTIME_ID),
                null, null, null, null, null, null, null, null, null);
        FleetReadinessState readiness = new FleetReadinessState(
                10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);
        return new FleetForceRegistry.Entry(
                INVADER, PLAYER_RUNTIME_ID, FleetLocationKind.IN_SYSTEM, TARGET,
                null, null, entity, readiness);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) throw new AssertionError("world did not reach target authoritative tick");
        }
    }

    private record Fixture(WorldSimulation world, FactionIdentityResolver identities) {
    }
}
