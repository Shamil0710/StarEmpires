package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomaticCounterOfferServiceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void boundedCounterOfferReversesPartiesPersistsCauseAndClosesOriginalTreatyOffer() {
        WorldSimulation world = DemoGalaxyFactory.create(21_310L);
        DiplomaticLifecycleService service = service(world);
        var original = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.obtain-access",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ACCESS,
                "market.corona",
                List.of(),
                List.of(),
                100L));
        original = service.materializeTreatyOffer(original.proposalId());
        String originalTreatyId = original.linkedTreatyId();

        var counter = DiplomaticCounterOfferService.counter(
                service,
                original.proposalId(),
                ProposalKind.TRADE,
                List.of(),
                List.of(),
                100L);

        assertEquals(MINERS, counter.proposerFactionId());
        assertEquals(TRADE_LEAGUE, counter.recipientFactionId());
        assertEquals(original.issueId(), counter.issueId());
        assertEquals(original.proposalId(), DiplomaticCounterOfferService.causalProposalId(counter).orElseThrow());
        assertEquals(
                ProposalStatus.REJECTED,
                service.snapshot().proposals().stream()
                        .filter(proposal -> proposal.proposalId().equals(original.proposalId()))
                        .findFirst()
                        .orElseThrow()
                        .status());
        assertEquals(
                DiplomaticTreatyState.Status.REJECTED,
                world.findDiplomaticTreaty(originalTreatyId).orElseThrow().status());

        service.accept(counter.proposalId());
        assertTrue(world.evaluateFactionMarketAccess(MINERS, TRADE_LEAGUE).allowed());
        assertTrue(world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).allowed());
    }

    @Test
    void infeasibleCounterDoesNotDestroyOriginalProposal() {
        WorldSimulation world = DemoGalaxyFactory.create(21_311L);
        DiplomaticLifecycleService service = service(world);
        var original = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.obtain-access",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ACCESS,
                "market.corona",
                List.of(),
                List.of(),
                100L));
        FactionEconomicState miners = world.findFactionEconomicState(MINERS).orElseThrow();
        long spendable = miners.treasuryMilliCredits() - miners.treasuryReserveFloorMilliCredits();

        assertThrows(IllegalStateException.class, () -> DiplomaticCounterOfferService.counter(
                service,
                original.proposalId(),
                ProposalKind.TRADE,
                List.of(),
                List.of(new Term(TermKind.TREASURY_PAYMENT, "counter-payment", spendable + 1L)),
                100L));

        assertEquals(
                ProposalStatus.OPEN,
                service.snapshot().proposals().stream()
                        .filter(proposal -> proposal.proposalId().equals(original.proposalId()))
                        .findFirst()
                        .orElseThrow()
                        .status());
    }

    private static DiplomaticLifecycleService service(WorldSimulation world) {
        return new DiplomaticLifecycleService(
                world,
                new Stage19ConflictRuntime(Stage19ConflictState.empty(world.getAuthoritativeWorldTick())),
                DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }
}
