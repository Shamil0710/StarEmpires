package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;

import java.util.List;

/** Test-only adapter that keeps Stage-21F package-local force-fixture construction beside its authority. */
public final class Stage22TerritorialTransitionProbe {
    private Stage22TerritorialTransitionProbe() {}

    /** Executes one physical occupation probe without inventing a new production authority. */
    public static Result run(
            WorldSimulation world,
            FactionIdentityResolver identities,
            String invaderStableId,
            int invaderRuntimeId,
            FleetId invasionFleet,
            StarSystemId target) {
        TerritorialTransitionService service = new TerritorialTransitionService();
        StrategicOperationState operations = new StrategicOperationState(
                2L,
                List.of(activeInvasion(invasionFleet, invaderRuntimeId, target)));
        FleetForceRegistry supplied = new FleetForceRegistry(List.of(entry(
                invasionFleet,
                invaderRuntimeId,
                target,
                new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000))));

        long start = world.getAuthoritativeWorldTick();
        var initial = service.advance(
                TerritorialTransitionState.empty(),
                world,
                operations,
                supplied,
                identities,
                1L,
                start);
        boolean initialGate = initial.occupation().status() == OccupationStatus.OCCUPYING
                && !initial.claimCreated()
                && world.controllingFaction(target).isEmpty();

        advanceToAtLeast(world, start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        var secured = service.advance(
                initial.transitions(),
                world,
                initial.operations(),
                supplied,
                identities,
                1L,
                world.getAuthoritativeWorldTick());
        boolean physicalClaimGate = secured.occupation().status() == OccupationStatus.SECURED
                && secured.claimCreated()
                && secured.operations().requireOperation(1L).status() == OperationStatus.COMPLETED
                && world.findFactionStrategicState(invaderStableId).orElseThrow().claimFor(target) != null;
        boolean sovereigntyNotImmediate = world.controllingFaction(target).isEmpty();
        return new Result(
                secured.occupation().securedTicks(),
                secured.claimCreated(),
                initialGate,
                physicalClaimGate,
                sovereigntyNotImmediate);
    }

    private static OperationState activeInvasion(
            FleetId fleetId,
            int factionRuntimeId,
            StarSystemId target) {
        return new OperationState(
                1L,
                OperationType.INVASION,
                1L,
                1L,
                factionRuntimeId,
                List.of(fleetId),
                target,
                target,
                "system:" + target.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(30_000L + fleetId.value()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new EntityState.FactionState(factionId),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                entity,
                readiness);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) {
                throw new AssertionError("world did not reach target authoritative tick");
            }
        }
    }

    /** Raw observation returned to the M22.6 content evidence layer. */
    public record Result(
            long securedTicks,
            boolean claimCreated,
            boolean initialPhysicalGate,
            boolean physicalClaimGate,
            boolean sovereigntyNotImmediate) {}
}
