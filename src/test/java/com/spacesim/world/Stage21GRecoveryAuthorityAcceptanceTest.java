package com.spacesim.world;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderSubmissionService.ServiceCapability;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import com.spacesim.world.Stage21EPhysicalConsequenceService.ConsequenceReport;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21GRecoveryAuthorityAcceptanceTest {
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @Test
    void stage21ELossBecomesDemandOnlyAndCannotRestoreTheDestroyedFleetIdentity() {
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), List.of());
        int ownerRuntimeId = identities.runtimeId("faction.trade_league").orElseThrow();
        String owner = identities.stableId(ownerRuntimeId).orElseThrow();
        String opponent = identities.stableId(identities.runtimeId("faction.miners").orElseThrow()).orElseThrow();
        FleetId lostFleet = new FleetId(21_710_001L);
        FleetForceRegistry before = registry(lostFleet, ownerRuntimeId, BETA);
        ConsequenceReport report = new Stage21EPhysicalConsequenceService()
                .reconcile(operation(lostFleet, ownerRuntimeId), before, new FleetForceRegistry(List.of()));
        SettlementRecoveryService recovery = pendingRecovery(owner, opponent, 10L);
        var fit = ShipEngineeringCatalogLoader.loadDefault()
                .findDemonstratorFit("fit.escort_destroyer_schema_v1");
        var installedFit = com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(fit);

        recovery.recordPhysicalLosses(1L, report.operationId(), report, before, identities, 10L);
        var demand = recovery.requestReplacement(1L, lostFleet, installedFit, 10L);

        assertEquals(ReplacementStatus.DEMANDED, demand.status());
        assertEquals(lostFleet, demand.lostFleetId());
        assertNull(demand.commissionedFleetId());
        assertNull(demand.completedAssetSystemId());
        assertEquals(1L, recovery.snapshot().losses().stream()
                .filter(loss -> loss.lostFleetId().equals(lostFleet)).count());
        assertFalse(report.survivors().contains(lostFleet));

        recovery.recordPhysicalLosses(1L, report.operationId(), report, before, identities, 10L);
        assertEquals(1, recovery.snapshot().losses().size(),
                "replaying the same physical consequence must not mint another loss");
        assertEquals(demand.id(), recovery.requestReplacement(1L, lostFleet, installedFit, 10L).id(),
                "one destroyed FleetId owns at most one replacement demand");

        assertThrows(IllegalArgumentException.class, () -> recovery.requestReplacement(
                1L, new FleetId(21_710_999L), installedFit, 10L));
    }

    @Test
    void survivingCommandGroupDemobilizesThroughOrdinaryStage21DReturnOrder() {
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), List.of());
        int ownerRuntimeId = identities.runtimeId("faction.trade_league").orElseThrow();
        String owner = identities.stableId(ownerRuntimeId).orElseThrow();
        String opponent = identities.stableId(identities.runtimeId("faction.miners").orElseThrow()).orElseThrow();
        FleetId fleet = new FleetId(21_711_001L);
        long groupId = 71L;
        SettlementRecoveryService recovery = pendingRecovery(owner, opponent, 20L);
        recovery.registerDemobilization(1L, groupId, owner, 20L);
        recovery.finalizeRecoveryPlan(1L, 20L);

        FleetCommandState command = new FleetCommandState(
                2L,
                1L,
                List.of(new CommandGroupState(
                        groupId, ownerRuntimeId, "Surviving Group", List.of(fleet), ALPHA,
                        false, false, FleetReadinessState.FULL)),
                List.of());
        FleetForceRegistry forces = registry(fleet, ownerRuntimeId, BETA);
        FleetOrderSubmissionService submission = new FleetOrderSubmissionService(
                new FleetStrategicRoutePlanner(topology()));

        var result = recovery.submitReturnOrder(
                command,
                forces,
                identities,
                submission,
                1L,
                groupId,
                OrderSource.AI,
                20L,
                (factionId, from, to, tick, destination) -> true,
                (factionId, systemId, tick) -> new ServiceCapability(true, true, true, 1L, 1L),
                (factionId, type, route, tick) -> 0);

        assertEquals(OrderType.RETURN, result.returnOrder().type());
        assertEquals(List.of(BETA, ALPHA), result.returnOrder().route());
        assertEquals(ALPHA, result.returnOrder().targetSystemId());
        assertEquals(result.returnOrder(), result.commandState().requireOrder(result.returnOrder().id()));
        var directive = result.recoveryState().demobilizations().get(0);
        assertEquals(SettlementRecoveryState.ObligationStatus.COMPLETE, directive.status());
        assertEquals(result.returnOrder().id(), directive.returnOrderId());
        assertTrue(result.recoveryState().requireSettlement(1L).status() == SettlementStatus.COMPLETE);

        var repeated = recovery.submitReturnOrder(
                result.commandState(),
                forces,
                identities,
                submission,
                1L,
                groupId,
                OrderSource.AI,
                21L,
                (factionId, from, to, tick, destination) -> true,
                (factionId, systemId, tick) -> new ServiceCapability(true, true, true, 1L, 1L),
                (factionId, type, route, tick) -> 0);
        assertEquals(result.returnOrder().id(), repeated.returnOrder().id());
    }

    private static SettlementRecoveryService pendingRecovery(String factionA, String factionB, long tick) {
        Settlement settlement = new Settlement(
                1L, "proposal.stage21g.acceptance", "war.stage21g.acceptance",
                factionA, factionB, tick, tick, SettlementStatus.PENDING, false);
        return new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                tick,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    private static FleetForceRegistry registry(FleetId fleetId, int factionId, StarSystemId systemId) {
        EntityState entity = new EntityState(
                new EntityId(1_000_000L + fleetId.value()),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(factionId),
                null, null, null, null, null, null, null, null, null);
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                entity,
                new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000))));
    }

    private static OperationState operation(FleetId fleetId, int factionId) {
        return new OperationState(
                710L,
                OperationType.RAID,
                1L,
                1L,
                factionId,
                List.of(fleetId),
                BETA,
                BETA,
                "system:" + BETA.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(2_000, 1_000, 20L),
                new WithdrawalPolicy(BETA, 1_500, true, true),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
    }

    private static GalaxyTopology topology() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        return new GalaxyTopology(
                new GalaxyId(21L),
                "Stage 21G Recovery Acceptance",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
    }
}
