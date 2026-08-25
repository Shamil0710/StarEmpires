package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.WorldStateCodec;
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

final class Stage21FDeterministicContinuationAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.stage21f.determinism";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final StarSystemId TARGET = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;
    private static final FleetId INVADER = new FleetId(21_650L);

    @Test
    void incrementalAndLumpedSupportedEvaluationProduceIdenticalTerritorialResult() {
        Fixture incremental = fixture(21_650L);
        Fixture lumped = fixture(21_650L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        FleetForceRegistry supplied = registry(10_000);

        long start = incremental.world().getAuthoritativeWorldTick();
        assertEquals(start, lumped.world().getAuthoritativeWorldTick());
        TerritorialTransitionService.AdvanceResult incrementalInitial = service.advance(
                TerritorialTransitionState.empty(), incremental.world(), operations(), supplied,
                incremental.identities(), 1L, start);
        TerritorialTransitionService.AdvanceResult lumpedInitial = service.advance(
                TerritorialTransitionState.empty(), lumped.world(), operations(), supplied,
                lumped.identities(), 1L, start);

        advanceToAtLeast(incremental.world(), start + 100L);
        TerritorialTransitionService.AdvanceResult incrementalMiddle = service.advance(
                incrementalInitial.transitions(), incremental.world(), incrementalInitial.operations(), supplied,
                incremental.identities(), 1L, incremental.world().getAuthoritativeWorldTick());
        advanceToAtLeast(incremental.world(), start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        TerritorialTransitionService.AdvanceResult incrementalFinal = service.advance(
                incrementalMiddle.transitions(), incremental.world(), incrementalMiddle.operations(), supplied,
                incremental.identities(), 1L, incremental.world().getAuthoritativeWorldTick());

        advanceToAtLeast(lumped.world(), start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        TerritorialTransitionService.AdvanceResult lumpedFinal = service.advance(
                lumpedInitial.transitions(), lumped.world(), lumpedInitial.operations(), supplied,
                lumped.identities(), 1L, lumped.world().getAuthoritativeWorldTick());

        assertEquals(incremental.world().getAuthoritativeWorldTick(), lumped.world().getAuthoritativeWorldTick());
        assertEquals(incrementalFinal.transitions(), lumpedFinal.transitions());
        assertEquals(incrementalFinal.operations(), lumpedFinal.operations());
        assertEquals(incremental.world().snapshot().factionStrategies(), lumped.world().snapshot().factionStrategies());
        assertTrue(incrementalFinal.claimCreated());
        assertTrue(lumpedFinal.claimCreated());
        assertTrue(incremental.world().controllingFaction(TARGET).isEmpty());
        assertTrue(lumped.world().controllingFaction(TARGET).isEmpty());
    }

    @Test
    void saveLoadAtUnsupportedDeadlineResumesToIdenticalCollapse() {
        Fixture baseline = fixture(21_651L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        FleetForceRegistry supplied = registry(10_000);
        FleetForceRegistry unsupplied = registry(0);
        long start = baseline.world().getAuthoritativeWorldTick();

        TerritorialTransitionService.AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), baseline.world(), operations(), supplied,
                baseline.identities(), 1L, start);
        advanceToAtLeast(baseline.world(), start + 150L);
        TerritorialTransitionService.AdvanceResult supported = service.advance(
                initial.transitions(), baseline.world(), initial.operations(), supplied,
                baseline.identities(), 1L, baseline.world().getAuthoritativeWorldTick());
        baseline.world().advanceFrame(1.0f);
        TerritorialTransitionService.AdvanceResult unsupported = service.advance(
                supported.transitions(), baseline.world(), supported.operations(), unsupplied,
                baseline.identities(), 1L, baseline.world().getAuthoritativeWorldTick());
        long unsupportedSince = unsupported.occupation().unsupportedSinceTick();
        assertTrue(unsupportedSince >= 0L);

        WorldState restoredState = WorldStateCodec.decode(WorldStateCodec.encode(baseline.world().snapshot()));
        StrategicOperationState restoredOperations = StrategicOperationStateCodec.decode(
                StrategicOperationStateCodec.encode(unsupported.operations()));
        TerritorialTransitionState restoredTransitions = TerritorialTransitionStateCodec.decode(
                TerritorialTransitionStateCodec.encode(unsupported.transitions()));
        WorldSimulation restoredWorld = WorldSimulation.restore(
                restoredState,
                baseline.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FactionIdentityResolver restoredIdentities = FactionIdentityResolver.createDefault(
                baseline.content(), restoredWorld.snapshot().factionIdentities());

        long collapseTick = unsupportedSince + TerritorialTransitionService.OCCUPATION_COLLAPSE_GRACE_TICKS + 1L;
        advanceToAtLeast(baseline.world(), collapseTick);
        advanceToAtLeast(restoredWorld, collapseTick);
        TerritorialTransitionService.AdvanceResult baselineFinal = service.advance(
                unsupported.transitions(), baseline.world(), unsupported.operations(), unsupplied,
                baseline.identities(), 1L, baseline.world().getAuthoritativeWorldTick());
        TerritorialTransitionService.AdvanceResult restoredFinal = service.advance(
                restoredTransitions, restoredWorld, restoredOperations, unsupplied,
                restoredIdentities, 1L, restoredWorld.getAuthoritativeWorldTick());

        assertEquals(baseline.world().getAuthoritativeWorldTick(), restoredWorld.getAuthoritativeWorldTick());
        assertEquals(baselineFinal.transitions(), restoredFinal.transitions());
        assertEquals(baselineFinal.operations(), restoredFinal.operations());
        assertEquals(baseline.world().snapshot().factionStrategies(), restoredWorld.snapshot().factionStrategies());
        assertEquals(TerritorialTransitionState.OccupationStatus.COLLAPSED, baselineFinal.occupation().status());
        assertEquals(0L, baselineFinal.occupation().securedTicks());
        assertTrue(baseline.world().controllingFaction(TARGET).isEmpty());
        assertTrue(restoredWorld.controllingFaction(TARGET).isEmpty());
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
                PLAYER_FACTION, PLAYER_RUNTIME_ID, "Stage 21F Determinism Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));
        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION, base.topology(), base.systems(), factions, strategies,
                base.nextConstructionProjectIdValue(), base.constructionProjects(), base.factionEconomicPressures(),
                base.nextFleetIdValue(), base.fleets(), base.fleetJumps(), identities);
        WorldSimulation world = WorldSimulation.restore(
                state, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new Fixture(world, content, FactionIdentityResolver.createDefault(content, identities));
    }

    private static StrategicOperationState operations() {
        return new StrategicOperationState(2L, List.of(new OperationState(
                1L, OperationType.INVASION, 1L, 1L, PLAYER_RUNTIME_ID, List.of(INVADER),
                TARGET, TARGET, "system:" + TARGET.value(), RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null)));
    }

    private static FleetForceRegistry registry(int supplyAccessBps) {
        EntityState entity = new EntityState(
                new EntityId(51_650L),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(PLAYER_RUNTIME_ID),
                null, null, null, null, null, null, null, null, null);
        FleetReadinessState readiness = new FleetReadinessState(
                10_000, 10_000, 10_000, 10_000, 10_000, 10_000, supplyAccessBps);
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                INVADER, PLAYER_RUNTIME_ID, FleetLocationKind.IN_SYSTEM, TARGET,
                null, null, entity, readiness)));
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) throw new AssertionError("world did not reach target authoritative tick");
        }
    }

    private record Fixture(
            WorldSimulation world,
            ContentCatalog content,
            FactionIdentityResolver identities) {
    }
}
