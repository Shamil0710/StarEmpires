package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class Stage21FClaimProvenanceAcceptanceTest {
    @Test
    void losingOccupationSupportNeverWithdrawsClaimThatPredatedTheInvasion() {
        WorldSimulation world = DemoGalaxyFactory.create(21_609L);
        FactionStrategicState actor = world.snapshot().factionStrategies().stream().findFirst().orElseThrow();
        StarSystemId target = world.getTopology().systems().stream()
                .map(StarSystemNode::id)
                .filter(systemId -> !actor.controls(systemId))
                .findFirst()
                .orElseThrow();
        world.declareTerritorialClaim(actor.factionContentId(), target);
        int runtimeFactionId = world.findFactionRuntimeId(actor.factionContentId()).orElseThrow();
        FleetId participant = new FleetId(21_609L);
        OperationState invasion = new OperationState(
                1L,
                OperationType.INVASION,
                1L,
                1L,
                runtimeFactionId,
                List.of(participant),
                target,
                target,
                "system:" + target.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(target, 1_500, true, true),
                OperationStatus.ACTIVE,
                world.getAuthoritativeWorldTick(),
                world.getAuthoritativeWorldTick(),
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(invasion));
        TerritorialTransitionService service = new TerritorialTransitionService();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities());
        FleetForceRegistry supplied = new FleetForceRegistry(List.of(entry(
                participant, runtimeFactionId, target, readiness(10_000))));

        long start = world.getAuthoritativeWorldTick();
        TerritorialTransitionService.AdvanceResult initial = service.advance(
                TerritorialTransitionState.empty(), world, operations, supplied, identities, 1L, start);
        advanceToAtLeast(world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        TerritorialTransitionService.AdvanceResult secured = service.advance(
                initial.transitions(), world, initial.operations(), supplied, identities,
                1L, world.getAuthoritativeWorldTick());

        assertFalse(secured.claimCreated());
        assertFalse(secured.occupation().claimCreatedByOccupation());
        assertNotNull(world.findFactionStrategicState(actor.factionContentId()).orElseThrow().claimFor(target));

        world.advanceFrame(1.0f);
        FleetForceRegistry unsupplied = new FleetForceRegistry(List.of(entry(
                participant, runtimeFactionId, target, readiness(0))));
        TerritorialTransitionService.AdvanceResult unsupported = service.advance(
                secured.transitions(), world, secured.operations(), unsupplied, identities,
                1L, world.getAuthoritativeWorldTick());

        assertFalse(unsupported.occupation().claimCreatedByOccupation());
        assertNotNull(world.findFactionStrategicState(actor.factionContentId()).orElseThrow().claimFor(target),
                "Stage 21F must not revoke a political claim it did not create");
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(90_000L + fleetId.value()),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(factionId),
                null, null, null, null, null, null, null, null, null);
        return new FleetForceRegistry.Entry(
                fleetId, factionId, FleetLocationKind.IN_SYSTEM, systemId, null, null, entity, readiness);
    }

    private static FleetReadinessState readiness(int supplyAccessBps) {
        return new FleetReadinessState(
                10_000, 10_000, 10_000, 10_000, 10_000, 10_000, supplyAccessBps);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) throw new AssertionError("world did not reach target authoritative tick");
        }
    }
}
