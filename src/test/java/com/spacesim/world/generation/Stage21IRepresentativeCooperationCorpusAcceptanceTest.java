package com.spacesim.world.generation;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.CustomsTariffResolver;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positive political branches for the final Stage-21 representative generated-seed corpus.
 *
 * <p>The long soak deliberately covers coercion, limited war, territory, recovery and renewed trade.
 * These fixtures provide the complementary ordinary generated-world outcomes: materially useful
 * peaceful trade and an accepted alliance. Both are executed through the production Stage-21C
 * lifecycle and Stage-17 treaty/access authority, then lifted through the final Stage-21I checkpoint.
 * Nothing in this test grants a faction-only resource, combat modifier or scripted world result.</p>
 */
final class Stage21IRepresentativeCooperationCorpusAcceptanceTest {
    private static final long PEACEFUL_TRADE_SEED = Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 41L;
    private static final long ALLIANCE_SEED = Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 43L;

    @Test
    void representativeGeneratedSeedsSupportDeterministicPeacefulTradeAndAllianceWithoutForcedWar() {
        ScenarioResult peacefulFirst = runCooperationScenario(PEACEFUL_TRADE_SEED, ProposalKind.TRADE);
        ScenarioResult peacefulSecond = runCooperationScenario(PEACEFUL_TRADE_SEED, ProposalKind.TRADE);
        assertEquals(peacefulFirst.digest(), peacefulSecond.digest());
        assertArrayEquals(peacefulFirst.finalCheckpoint(), peacefulSecond.finalCheckpoint());

        ScenarioResult allianceFirst = runCooperationScenario(ALLIANCE_SEED, ProposalKind.ALLIANCE);
        ScenarioResult allianceSecond = runCooperationScenario(ALLIANCE_SEED, ProposalKind.ALLIANCE);
        assertEquals(allianceFirst.digest(), allianceSecond.digest());
        assertArrayEquals(allianceFirst.finalCheckpoint(), allianceSecond.finalCheckpoint());

        assertNotEquals(peacefulFirst.digest().proposalKind(), allianceFirst.digest().proposalKind(),
                "the representative corpus must contain distinct lawful cooperative outcomes");
    }

    private static ScenarioResult runCooperationScenario(long seed, ProposalKind proposalKind) {
        var generated = Stage20PlayableGeneratedWorldFactory.create(seed);
        var runtime = generated.runtime();
        List<String> participants = runtime.captureState().worldState().factions().stream()
                .map(faction -> faction.factionContentId())
                .distinct()
                .sorted()
                .limit(2)
                .toList();
        assertEquals(2, participants.size(), "representative cooperation seed requires two generated factions");
        String proposer = participants.get(0);
        String recipient = participants.get(1);
        long now = runtime.world().getAuthoritativeWorldTick();

        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                runtime.world(), warfare, DiplomaticLifecycleState.empty(now));
        assertTrue(diplomacy.snapshot().wars().isEmpty(),
                "cooperative corpus must begin without a scripted legal war");

        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.stage21i.corpus." + proposalKind.name().toLowerCase(),
                proposer,
                recipient,
                proposalKind,
                "cooperation:" + seed,
                List.of(),
                List.of(),
                now + 500L));
        var linked = diplomacy.materializeTreatyOffer(proposal.proposalId());
        assertFalse(linked.linkedTreatyId().isEmpty(),
                "trade/alliance corpus branches must materialize through Stage-17 treaty authority");
        var accepted = diplomacy.accept(linked.proposalId());
        assertEquals(ProposalStatus.ACCEPTED, accepted.status());
        assertTrue(diplomacy.snapshot().wars().isEmpty(),
                "successful cooperation must not fabricate a legal war as a side effect");

        DiplomaticTreatyState treaty = runtime.world().findDiplomaticTreaty(linked.linkedTreatyId()).orElseThrow();
        assertEquals(DiplomaticTreatyState.Status.ACTIVE, treaty.status());
        if (proposalKind == ProposalKind.TRADE) {
            assertTrue(runtime.world().evaluateFactionMarketAccess(proposer, recipient).allowed());
            assertTrue(runtime.world().evaluateFactionMarketAccess(recipient, proposer).allowed());
            assertEquals(0, CustomsTariffResolver.evaluate(
                    runtime.world().getFactionDiplomacyStates(), proposer, recipient,
                    runtime.world().getAuthoritativeWorldTick()).basisPoints());
            assertTrue(treaty.clauses().stream().anyMatch(clause ->
                    clause.kind() == DiplomaticTreatyClauseState.Kind.MARKET_ACCESS
                            && clause.direction() == DiplomaticTreatyClauseState.Direction.MUTUAL));
        } else if (proposalKind == ProposalKind.ALLIANCE) {
            assertTrue(treaty.clauses().stream().anyMatch(clause ->
                    clause.kind() == DiplomaticTreatyClauseState.Kind.GUARANTEE
                            && clause.direction() == DiplomaticTreatyClauseState.Direction.MUTUAL));
        } else {
            throw new AssertionError("Unexpected cooperation proposal kind: " + proposalKind);
        }

        byte[] stage20Bytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var finalState = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(stage20Bytes);
        byte[] finalBytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(finalState);
        assertArrayEquals(finalBytes, Stage21IGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(finalBytes)),
                "cooperative final checkpoint must remain byte-stable");

        ScenarioDigest digest = new ScenarioDigest(
                seed,
                proposer,
                recipient,
                proposalKind,
                accepted.proposalId(),
                accepted.linkedTreatyId(),
                treaty.status(),
                treaty.clauses());
        return new ScenarioResult(digest, finalBytes);
    }

    private record ScenarioDigest(
            long seed,
            String proposer,
            String recipient,
            ProposalKind proposalKind,
            String proposalId,
            String treatyId,
            DiplomaticTreatyState.Status treatyStatus,
            List<DiplomaticTreatyClauseState> treatyClauses) {
        private ScenarioDigest {
            treatyClauses = List.copyOf(treatyClauses);
        }
    }

    private record ScenarioResult(ScenarioDigest digest, byte[] finalCheckpoint) {
        private ScenarioResult {
            finalCheckpoint = finalCheckpoint.clone();
        }

        @Override
        public byte[] finalCheckpoint() {
            return finalCheckpoint.clone();
        }
    }
}
