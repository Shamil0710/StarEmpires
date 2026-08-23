package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21CDiplomaticMidLifecyclePersistenceAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void proposalBoundaryRestoresAndContinuesWithOneBoundedCounterOfferTransition() {
        WorldSimulation world = DemoGalaxyFactory.create(21_320L);
        Stage19ConflictRuntime warfare = warfare(world);
        DiplomaticLifecycleService service = service(world, warfare);
        long deadline = deadline(world);
        var original = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.obtain-access",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ACCESS,
                "market.corona",
                List.of(),
                List.of(),
                deadline));
        String originalId = original.proposalId();

        DiplomaticLifecycleService restored = restore(world, warfare, service);
        var counter = DiplomaticCounterOfferService.counter(
                restored,
                originalId,
                ProposalKind.TRADE,
                List.of(),
                List.of(),
                deadline);

        assertEquals(originalId, DiplomaticCounterOfferService.causalProposalId(counter).orElseThrow());
        assertEquals(
                ProposalStatus.REJECTED,
                restored.snapshot().proposals().stream()
                        .filter(proposal -> proposal.proposalId().equals(originalId))
                        .findFirst()
                        .orElseThrow()
                        .status());
        assertEquals(ProposalStatus.OPEN, counter.status());
    }

    @Test
    void counterOfferBoundaryRestoresCausalLineageAndContinuesWithAcceptance() {
        WorldSimulation world = DemoGalaxyFactory.create(21_321L);
        Stage19ConflictRuntime warfare = warfare(world);
        DiplomaticLifecycleService service = service(world, warfare);
        long deadline = deadline(world);
        var original = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.obtain-access",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ACCESS,
                "market.corona",
                List.of(),
                List.of(),
                deadline));
        String originalId = original.proposalId();
        var counter = DiplomaticCounterOfferService.counter(
                service,
                originalId,
                ProposalKind.TRADE,
                List.of(),
                List.of(),
                deadline);
        String counterId = counter.proposalId();

        DiplomaticLifecycleService restored = restore(world, warfare, service);
        var restoredCounter = restored.snapshot().proposals().stream()
                .filter(proposal -> proposal.proposalId().equals(counterId))
                .findFirst()
                .orElseThrow();
        assertEquals(originalId, DiplomaticCounterOfferService.causalProposalId(restoredCounter).orElseThrow());

        var accepted = restored.accept(counterId);

        assertEquals(ProposalStatus.ACCEPTED, accepted.status());
        assertFalse(accepted.linkedTreatyId().isEmpty());
        assertTrue(world.evaluateFactionMarketAccess(MINERS, TRADE_LEAGUE).allowed());
        assertTrue(world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).allowed());
    }

    @Test
    void ultimatumBoundaryRestoresAndContinuesWithWarAuthorization() {
        WorldSimulation world = DemoGalaxyFactory.create(21_322L);
        Stage19ConflictRuntime warfare = warfare(world);
        DiplomaticLifecycleService service = service(world, warfare);
        long deadline = deadline(world);
        var ultimatum = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.coerce",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ULTIMATUM,
                "security.corona",
                List.of(),
                List.of(),
                deadline));
        var crisis = service.openCrisis(ultimatum.proposalId(), "decision.open", deadline);
        crisis = service.escalateCrisis(crisis.crisisId(), "decision.pressure", deadline);
        crisis = service.escalateCrisis(crisis.crisisId(), "decision.ultimatum", deadline);
        String crisisId = crisis.crisisId();
        assertEquals(CrisisEscalation.ULTIMATUM, crisis.escalation());

        DiplomaticLifecycleService restored = restore(world, warfare, service);
        var authorized = restored.escalateCrisis(crisisId, "decision.war-authorized", deadline);

        assertEquals(CrisisEscalation.WAR_AUTHORIZED, authorized.escalation());
    }

    @Test
    void ceasefireBoundaryRestoresAndContinuesWithPeaceWithoutLosingStage19Links() {
        WorldSimulation world = DemoGalaxyFactory.create(21_323L);
        Stage19ConflictRuntime warfare = warfare(world);
        DiplomaticLifecycleService service = service(world, warfare);
        long deadline = deadline(world);
        var ultimatum = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.coerce",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ULTIMATUM,
                "security.corona",
                List.of(),
                List.of(),
                deadline));
        var crisis = service.openCrisis(ultimatum.proposalId(), "decision.open", deadline);
        crisis = service.escalateCrisis(crisis.crisisId(), "decision.pressure", deadline);
        crisis = service.escalateCrisis(crisis.crisisId(), "decision.ultimatum", deadline);
        crisis = service.escalateCrisis(crisis.crisisId(), "decision.war-authorized", deadline);
        var war = service.declareWarFromCrisis(crisis.crisisId(), goals());
        String warId = war.warId();
        List<String> conflictIds = war.stage19ConflictIds();
        service.ceasefire(warId, DiplomaticLifecycleService.MINIMUM_REESCALATION_COOLDOWN_TICKS);

        DiplomaticLifecycleService restored = restore(world, warfare, service);
        for (String conflictId : conflictIds) {
            assertTrue(warfare.find(conflictId).isPresent());
        }
        var peace = restored.peace(warId, DiplomaticLifecycleService.MINIMUM_REESCALATION_COOLDOWN_TICKS);

        assertEquals(WarStatus.PEACE, peace.status());
        assertEquals(conflictIds, peace.stage19ConflictIds());
    }

    private static DiplomaticLifecycleService restore(
            WorldSimulation world,
            Stage19ConflictRuntime warfare,
            DiplomaticLifecycleService service) {
        byte[] encoded = DiplomaticLifecycleStateCodec.encode(service.snapshot());
        DiplomaticLifecycleState decoded = DiplomaticLifecycleStateCodec.decode(encoded);
        assertEquals(service.snapshot(), decoded);
        return new DiplomaticLifecycleService(world, warfare, decoded);
    }

    private static Stage19ConflictRuntime warfare(WorldSimulation world) {
        return new Stage19ConflictRuntime(Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
    }

    private static DiplomaticLifecycleService service(
            WorldSimulation world,
            Stage19ConflictRuntime warfare) {
        return new DiplomaticLifecycleService(
                world,
                warfare,
                DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static long deadline(WorldSimulation world) {
        return world.getAuthoritativeWorldTick() + 100L;
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal(
                        "war-goal.trade-league.security",
                        TRADE_LEAGUE,
                        WarGoalKind.SECURITY,
                        "security.anchor",
                        true),
                new WarGoal(
                        "war-goal.miners.security",
                        MINERS,
                        WarGoalKind.SECURITY,
                        "security.corona",
                        true));
    }
}
