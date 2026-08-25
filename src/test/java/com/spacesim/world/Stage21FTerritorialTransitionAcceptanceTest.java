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
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionService.AdvanceResult;
import com.spacesim.world.TerritorialTransitionService.ProjectionPhase;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21FTerritorialTransitionAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.stage21f";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String RECOGNIZER = "faction.neutral";
    private static final StarSystemId TARGET = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;
    private static final FleetId INVADER = new FleetId(21_600L);

    @Test
    void invasionNeedsSustainedPhysicalOccupationThenStage17StabilizationBeforeControl() {
        Fixture fixture = fixture(21_600L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        TerritorialTransitionState transitions = TerritorialTransitionState.empty();
        StrategicOperationState operations = operations(activeInvasion(INVADER));
        FleetForceRegistry supplied = registry(entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(10_000)));

        long start = fixture.world.getAuthoritativeWorldTick();
        AdvanceResult initial = service.advance(
                transitions, fixture.world, operations, supplied, fixture.identities, 1L, start);
        assertEquals(OccupationStatus.OCCUPYING, initial.occupation().status());
        assertFalse(initial.claimCreated());
        assertTrue(fixture.world.controllingFaction(TARGET).isEmpty());

        advanceToAtLeast(fixture.world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        long occupationTick = fixture.world.getAuthoritativeWorldTick();
        AdvanceResult secured = service.advance(
                initial.transitions(), fixture.world, initial.operations(), supplied, fixture.identities, 1L, occupationTick);

        assertEquals(OccupationStatus.SECURED, secured.occupation().status());
        assertTrue(secured.claimCreated());
        assertEquals(OperationStatus.COMPLETED, secured.operations().requireOperation(1L).status());
        assertTrue(fixture.world.findFactionStrategicState(PLAYER_FACTION).orElseThrow().claimFor(TARGET) != null);
        assertTrue(fixture.world.controllingFaction(TARGET).isEmpty(),
                "occupation/claim must not immediately recolour sovereignty");
        assertEquals(ProjectionPhase.OCCUPATION,
                service.project(fixture.world, secured.transitions(), PLAYER_FACTION, TARGET).phase());

        fixture.world.createEntity(TARGET, new Entity()
                .add(new IdentityComponent("Occupation Logistics Anchor", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new FactionComponent(PLAYER_RUNTIME_ID)));
        fixture.world.advanceFrame(1.0f);
        assertEquals(ProjectionPhase.STABILIZATION,
                service.project(fixture.world, secured.transitions(), PLAYER_FACTION, TARGET).phase());

        long stabilizationStart = fixture.world.getAuthoritativeWorldTick();
        advanceToAtLeast(
                fixture.world,
                stabilizationStart + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);
        assertEquals(PLAYER_FACTION, fixture.world.controllingFaction(TARGET).orElseThrow());
        assertEquals(ProjectionPhase.CONTROL,
                service.project(fixture.world, secured.transitions(), PLAYER_FACTION, TARGET).phase());

        fixture.world.recognizeTerritorialControl(RECOGNIZER, PLAYER_FACTION, TARGET);
        assertEquals(ProjectionPhase.RECOGNIZED_CONTROL,
                service.project(fixture.world, secured.transitions(), PLAYER_FACTION, TARGET).phase());
    }

    @Test
    void unsuppliedOccupationDecaysAndCollapsesWithoutCreatingClaim() {
        Fixture fixture = fixture(21_601L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        StrategicOperationState operations = operations(activeInvasion(INVADER));
        FleetForceRegistry supplied = registry(entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(10_000)));
        FleetForceRegistry unsupplied = registry(entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(0)));

        long start = fixture.world.getAuthoritativeWorldTick();
        AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), fixture.world, operations, supplied, fixture.identities, 1L, start);
        advanceToAtLeast(fixture.world, start + 150L);
        AdvanceResult halfway = service.advance(
                initial.transitions(), fixture.world, initial.operations(), supplied, fixture.identities,
                1L, fixture.world.getAuthoritativeWorldTick());
        assertTrue(halfway.occupation().securedTicks() > 0L);

        fixture.world.advanceFrame(1.0f);
        AdvanceResult unsupported = service.advance(
                halfway.transitions(), fixture.world, halfway.operations(), unsupplied, fixture.identities,
                1L, fixture.world.getAuthoritativeWorldTick());
        assertFalse(unsupported.supplyReady());
        long unsupportedSince = unsupported.occupation().unsupportedSinceTick();
        assertTrue(unsupportedSince >= 0L);

        advanceToAtLeast(
                fixture.world,
                unsupportedSince + TerritorialTransitionService.OCCUPATION_COLLAPSE_GRACE_TICKS + 1L);
        AdvanceResult collapsed = service.advance(
                unsupported.transitions(), fixture.world, unsupported.operations(), unsupplied, fixture.identities,
                1L, fixture.world.getAuthoritativeWorldTick());
        assertEquals(OccupationStatus.COLLAPSED, collapsed.occupation().status());
        assertEquals(0L, collapsed.occupation().securedTicks());
        assertTrue(fixture.world.findFactionStrategicState(PLAYER_FACTION).orElseThrow().claimFor(TARGET) == null);
        assertTrue(fixture.world.controllingFaction(TARGET).isEmpty());
    }

    @Test
    void realRivalFleetContestsOccupationAndWithdrawalUsesOperationLifecycleWithoutFreeResistance() {
        Fixture fixture = fixture(21_602L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        OperationState invasion = activeInvasion(INVADER);
        StrategicOperationState operations = operations(invasion);
        int rivalFaction = fixture.world.findFactionRuntimeId(RECOGNIZER).orElseThrow();
        FleetForceRegistry contestedForces = registry(
                entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(10_000)),
                entry(new FleetId(21_601L), rivalFaction, TARGET, ready(10_000)));

        long start = fixture.world.getAuthoritativeWorldTick();
        AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), fixture.world, operations, contestedForces,
                fixture.identities, 1L, start);
        advanceToAtLeast(fixture.world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS + 50L);
        AdvanceResult contested = service.advance(
                initial.transitions(), fixture.world, initial.operations(), contestedForces,
                fixture.identities, 1L, fixture.world.getAuthoritativeWorldTick());
        assertTrue(contested.rivalFleetPresent());
        assertEquals(OccupationStatus.CONTESTED, contested.occupation().status());
        assertEquals(0L, contested.occupation().securedTicks());
        assertFalse(contested.claimCreated());

        OperationState withdrawing = invasion.withLifecycle(
                OperationStatus.WITHDRAWING,
                fixture.world.getAuthoritativeWorldTick(),
                -1L,
                null,
                null);
        AdvanceResult withdrawal = service.advance(
                contested.transitions(), fixture.world, operations(withdrawing),
                registry(entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(10_000))),
                fixture.identities, 1L, fixture.world.getAuthoritativeWorldTick());
        assertEquals(OccupationStatus.WITHDRAWING, withdrawal.occupation().status());
        assertTrue(fixture.world.findFactionStrategicState(PLAYER_FACTION).orElseThrow().claimFor(TARGET) == null);
    }

    @Test
    void worldAndOccupationRoundTripPreserveExactTransitionProgress() {
        Fixture fixture = fixture(21_603L);
        TerritorialTransitionService service = new TerritorialTransitionService();
        StrategicOperationState operations = operations(activeInvasion(INVADER));
        FleetForceRegistry supplied = registry(entry(INVADER, PLAYER_RUNTIME_ID, TARGET, ready(10_000)));
        long start = fixture.world.getAuthoritativeWorldTick();
        AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), fixture.world, operations, supplied, fixture.identities, 1L, start);
        advanceToAtLeast(fixture.world, start + 120L);
        AdvanceResult progressed = service.advance(
                initial.transitions(), fixture.world, initial.operations(), supplied, fixture.identities,
                1L, fixture.world.getAuthoritativeWorldTick());

        byte[] worldBytes = WorldStateCodec.encode(fixture.world.snapshot());
        byte[] transitionBytes = TerritorialTransitionStateCodec.encode(progressed.transitions());
        WorldState restoredWorldState = WorldStateCodec.decode(worldBytes);
        TerritorialTransitionState restoredTransitions = TerritorialTransitionStateCodec.decode(transitionBytes);
        WorldSimulation restored = WorldSimulation.restore(
                restoredWorldState,
                fixture.content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        assertEquals(fixture.world.getAuthoritativeWorldTick(), restored.getAuthoritativeWorldTick());
        assertEquals(progressed.occupation(),
                restoredTransitions.occupationFor(PLAYER_FACTION, TARGET).orElseThrow());
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
                PLAYER_FACTION, PLAYER_RUNTIME_ID, "Stage 21F Test Faction", WorldFactionIdentityState.Origin.PLAYER_CREATED));

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

    private static StrategicOperationState operations(OperationState operation) {
        return new StrategicOperationState(2L, List.of(operation));
    }

    private static OperationState activeInvasion(FleetId fleetId) {
        return new OperationState(
                1L, OperationType.INVASION, 1L, 1L, PLAYER_RUNTIME_ID, List.of(fleetId),
                TARGET, TARGET, "system:" + TARGET.value(), RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null);
    }

    private static FleetForceRegistry registry(FleetForceRegistry.Entry... entries) {
        return new FleetForceRegistry(List.of(entries));
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(30_000L + fleetId.value()),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(factionId),
                null, null, null, null, null, null, null, null, null);
        return new FleetForceRegistry.Entry(
                fleetId, factionId, FleetLocationKind.IN_SYSTEM, systemId, null, null, entity, readiness);
    }

    private static FleetReadinessState ready(int supplyAccessBps) {
        return new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, supplyAccessBps);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) throw new AssertionError("world did not reach target authoritative tick");
        }
    }

    private record Fixture(WorldSimulation world, ContentCatalog content, FactionIdentityResolver identities) {}
}
