package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DContestedConstructionAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.contested_builder";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String RIVAL_FACTION = "faction.neutral";

    @Test
    void materialRivalClaimsSuspendOrdinaryConstructionInsteadOfLookingUnclaimed() {
        WorldSimulation world = restoreUnclaimedFrontier(17_440L);
        createAnchor(world, PLAYER_FACTION, PLAYER_RUNTIME_ID, "Player Claim Anchor");
        createAnchor(
                world,
                RIVAL_FACTION,
                world.findFactionRuntimeId(RIVAL_FACTION).orElseThrow(),
                "Rival Claim Anchor");

        world.declareTerritorialClaim(PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        world.declareTerritorialClaim(RIVAL_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        long start = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, start + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 100L);

        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        assertTrue(world.isTerritoriallyContested(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        TerritorialConstructionAuthorization.Decision decision =
                TerritorialConstructionAuthorization.evaluate(
                        world,
                        PLAYER_FACTION,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        assertFalse(decision.allowed());
        assertEquals(
                TerritorialConstructionAuthorization.Reason.CONTESTED_NO_ORDINARY_RIGHT,
                decision.reason());

        int projectsBefore = world.getConstructionProjects().size();
        assertThrows(
                IllegalStateException.class,
                () -> world.createConstructionProject(
                        PLAYER_FACTION,
                        "station.foundry",
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        900f,
                        600f));
        assertEquals(projectsBefore, world.getConstructionProjects().size());
    }

    private static void createAnchor(
            WorldSimulation world,
            String factionContentId,
            int runtimeFactionId,
            String name) {
        assertEquals(runtimeFactionId, world.findFactionRuntimeId(factionContentId).orElseThrow());
        world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new FactionComponent(runtimeFactionId)));
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) {
                throw new AssertionError("World did not reach contested target tick");
            }
        }
    }

    private static WorldSimulation restoreUnclaimedFrontier(long seed) {
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        strategies.add(new FactionStrategicState(PLAYER_FACTION, 0, List.of(), List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(PLAYER_FACTION, 0L, 0L, 0L));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                PLAYER_FACTION,
                PLAYER_RUNTIME_ID,
                "Contested Builder Test Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));

        return WorldSimulation.restore(
                new WorldState(
                        WorldState.CURRENT_VERSION,
                        base.topology(),
                        base.systems(),
                        factions,
                        strategies,
                        base.nextConstructionProjectIdValue(),
                        base.constructionProjects(),
                        base.factionEconomicPressures(),
                        base.nextFleetIdValue(),
                        base.fleets(),
                        base.fleetJumps(),
                        identities),
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
