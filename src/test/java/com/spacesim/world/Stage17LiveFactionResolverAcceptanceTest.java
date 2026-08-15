package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17LiveFactionResolverAcceptanceTest {
    private static final String DYNAMIC_ID = "faction.player.live_runtime";
    private static final int DYNAMIC_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;

    @Test
    void dynamicFactionRestoresResolvesAndSurvivesSnapshot() {
        WorldSimulation base = DemoGalaxyFactory.create(17_301L);
        WorldState state = withDynamicFaction(
                base.snapshot(),
                new FactionStrategicState(DYNAMIC_ID, 0, List.of(), List.of()),
                0L);

        WorldSimulation restored = WorldSimulation.restore(
                state,
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        assertEquals(DYNAMIC_RUNTIME_ID, restored.findFactionRuntimeId(DYNAMIC_ID).orElseThrow());
        assertEquals(DYNAMIC_ID, restored.findFactionStableId(DYNAMIC_RUNTIME_ID).orElseThrow());
        assertEquals(0L, restored.findFactionEconomicState(DYNAMIC_ID).orElseThrow().treasuryMilliCredits());
        assertTrue(restored.findFactionStrategicState(DYNAMIC_ID).isPresent());
        assertEquals(state.factionIdentities(), restored.getWorldFactionIdentities());

        WorldState snapshot = restored.snapshot();
        assertEquals(state.factionIdentities(), snapshot.factionIdentities());
        assertTrue(snapshot.factions().stream()
                .anyMatch(faction -> faction.factionContentId().equals(DYNAMIC_ID)));
        assertTrue(snapshot.factionStrategies().stream()
                .anyMatch(strategy -> strategy.factionContentId().equals(DYNAMIC_ID)));
    }

    @Test
    void dynamicFactionUsesOrdinaryFiscalTransferPath() {
        WorldSimulation base = DemoGalaxyFactory.create(17_302L);
        long initialStationBalance = 1_000_000L;
        Entity station = new Entity()
                .add(new IdentityComponent("Dynamic Test Market", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new WalletComponent(initialStationBalance))
                .add(new FactionComponent(DYNAMIC_RUNTIME_ID));
        EntityId stationId = base.createEntity(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, station);

        FactionStrategicState taxPolicy = new FactionStrategicState(
                DYNAMIC_ID,
                0,
                List.of(),
                List.of(),
                1_000,
                0,
                List.of(),
                List.of(),
                List.of());
        WorldState state = withDynamicFaction(base.snapshot(), taxPolicy, 0L);
        WorldSimulation restored = WorldSimulation.restore(
                state,
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        WorldSimulation.FiscalPolicyReport report = restored.applyFiscalPolicy(DYNAMIC_ID);

        assertEquals(100_000L, report.taxCollectedMilliCredits());
        assertEquals(0L, report.tariffCollectedMilliCredits());
        assertEquals(1, report.taxedStations());
        assertEquals(100_000L,
                restored.findFactionEconomicState(DYNAMIC_ID).orElseThrow().treasuryMilliCredits());
        Entity restoredStation = restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow()
                .getEntityRegistry()
                .find(stationId);
        assertNotNull(restoredStation);
        assertEquals(900_000L,
                restoredStation.getComponent(WalletComponent.class).getBalanceMilliCredits());
    }

    @Test
    void marketAccessPolicyCanExplicitlyAllowDynamicParticipant() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldFactionIdentityState dynamicIdentity = dynamicIdentity();
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(
                content,
                List.of(dynamicIdentity));
        SimulationSession session = SimulationSession.createDemo(17_303L);

        Entity ownerMarket = null;
        String ownerStableId = null;
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (entity.getComponent(MarketComponent.class) == null || faction == null) {
                continue;
            }
            String stable = resolver.stableId(faction.factionId).orElse(null);
            if (stable != null && !stable.equals(DYNAMIC_ID)) {
                ownerMarket = entity;
                ownerStableId = stable;
                break;
            }
        }
        assertNotNull(ownerMarket);
        assertNotNull(ownerStableId);

        FactionStrategicState policy = new FactionStrategicState(
                ownerStableId,
                10,
                List.of(new FactionRelationState(DYNAMIC_ID, 50)),
                List.of());
        FactionPolicyRuntime.install(session, resolver, List.of(policy));

        FactionMarketAccessComponent access = ownerMarket.getComponent(FactionMarketAccessComponent.class);
        assertNotNull(access);
        assertTrue(access.canTrade(DYNAMIC_RUNTIME_ID));
        assertFalse(access.canTrade(-1));
    }

    private static WorldState withDynamicFaction(
            WorldState source,
            FactionStrategicState dynamicStrategy,
            long treasuryMilliCredits) {
        List<FactionEconomicState> factions = new ArrayList<>(source.factions());
        factions.add(new FactionEconomicState(DYNAMIC_ID, treasuryMilliCredits, 0L, 0L));

        List<FactionStrategicState> strategies = new ArrayList<>(source.factionStrategies());
        strategies.add(dynamicStrategy);

        List<WorldFactionIdentityState> identities = new ArrayList<>(source.factionIdentities());
        identities.add(dynamicIdentity());

        return new WorldState(
                WorldState.CURRENT_VERSION,
                source.topology(),
                source.systems(),
                factions,
                strategies,
                source.nextConstructionProjectIdValue(),
                source.constructionProjects(),
                source.factionEconomicPressures(),
                source.nextFleetIdValue(),
                source.fleets(),
                source.fleetJumps(),
                identities);
    }

    private static WorldFactionIdentityState dynamicIdentity() {
        return new WorldFactionIdentityState(
                DYNAMIC_ID,
                DYNAMIC_RUNTIME_ID,
                "Live Runtime Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
    }
}
