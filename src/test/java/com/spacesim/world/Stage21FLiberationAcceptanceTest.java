package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionService.AdvanceResult;
import com.spacesim.world.TerritorialTransitionService.ProjectionPhase;
import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21FLiberationAcceptanceTest {
    @Test
    void laterForeignStage17ControllerMarksPriorEstablishedOccupationLiberatedWithoutFreeForces() {
        WorldSimulation world = DemoGalaxyFactory.create(21_620L);
        FactionStrategicState defender = world.snapshot().factionStrategies().stream()
                .filter(strategy -> !strategy.controlledSystems().isEmpty())
                .findFirst()
                .orElseThrow();
        StarSystemId target = defender.controlledSystems().get(0);
        FactionStrategicState invader = world.snapshot().factionStrategies().stream()
                .filter(strategy -> !strategy.factionContentId().equals(defender.factionContentId()))
                .findFirst()
                .orElseThrow();
        int invaderRuntimeId = world.findFactionRuntimeId(invader.factionContentId()).orElseThrow();
        FleetId participant = new FleetId(21_620L);
        OperationState operation = new OperationState(
                1L,
                OperationType.INVASION,
                1L,
                1L,
                invaderRuntimeId,
                List.of(participant),
                target,
                target,
                "system:" + target.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(target, 1_500, true, true),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(operation));
        long tick = world.getAuthoritativeWorldTick();
        TerritorialTransitionState transitions = new TerritorialTransitionState(List.of(new OccupationState(
                invader.factionContentId(),
                target,
                1L,
                tick,
                tick,
                TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS,
                -1L,
                true,
                OccupationStatus.SECURED)));
        int fleetsBefore = world.snapshot().fleets().size();

        AdvanceResult result = new TerritorialTransitionService().advance(
                transitions,
                world,
                operations,
                new FleetForceRegistry(List.of()),
                FactionIdentityResolver.createDefault(ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities()),
                1L,
                tick);

        assertEquals(OccupationStatus.LIBERATED, result.occupation().status());
        assertEquals(defender.factionContentId(), world.controllingFaction(target).orElseThrow());
        assertEquals(fleetsBefore, world.snapshot().fleets().size());
        assertTrue(result.operations().requireOperation(1L).status().active(),
                "21F must not rewrite unrelated operation/force authority while observing liberation");
        assertEquals(ProjectionPhase.LIBERATED,
                new TerritorialTransitionService().project(world, result.transitions(), invader.factionContentId(), target).phase());
    }
}
