package com.spacesim.world;

import com.spacesim.world.DiplomaticLifecycleState.Crisis;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ObligationDecision;
import com.spacesim.world.DiplomaticLifecycleState.ObligationOutcome;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStartEvidence;
import com.spacesim.world.DiplomaticLifecycleState.WarStartKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiplomaticLifecycleStateCodecTest {

    @Test
    void fullLifecycleRoundTripsDeterministically() {
        DiplomaticLifecycleState state = representativeState();

        byte[] encoded = DiplomaticLifecycleStateCodec.encode(state);
        DiplomaticLifecycleState decoded = DiplomaticLifecycleStateCodec.decode(encoded);

        assertEquals(state, decoded);
        assertArrayEquals(encoded, DiplomaticLifecycleStateCodec.encode(decoded));
        assertEquals(-15, decoded.relationMemories().get(0).derivedRelation());
        assertEquals(WarStartKind.CRISIS_DECISION, decoded.wars().get(0).startEvidence().kind());
    }

    @Test
    void corruptTruncatedTrailingAndFuturePayloadsFailClosed() {
        byte[] encoded = DiplomaticLifecycleStateCodec.encode(representativeState());

        byte[] corruptMagic = encoded.clone();
        corruptMagic[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> DiplomaticLifecycleStateCodec.decode(corruptMagic));
        assertThrows(
                IllegalArgumentException.class,
                () -> DiplomaticLifecycleStateCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> DiplomaticLifecycleStateCodec.decode(trailing));
    }

    @Test
    void warCannotPersistWithoutCanonicalParticipantsGoalsAndCausalEvidence() {
        List<WarGoal> goals = goals();

        assertThrows(IllegalArgumentException.class, () -> new WarStartEvidence(
                WarStartKind.CRISIS_DECISION, "decision.war", 30L, ""));
        assertThrows(IllegalArgumentException.class, () -> new War(
                "war.invalid",
                "faction.z",
                "faction.a",
                goals,
                new WarStartEvidence(WarStartKind.OBSERVED_HOSTILE_ATTACK, "attack.1", 30L, ""),
                List.of("conflict.a", "conflict.z"),
                WarStatus.ACTIVE,
                30L,
                30L,
                0L));
        assertThrows(IllegalArgumentException.class, () -> new War(
                "war.invalid-goals",
                "faction.a",
                "faction.z",
                List.of(goals.get(0)),
                new WarStartEvidence(WarStartKind.OBSERVED_HOSTILE_ATTACK, "attack.1", 30L, ""),
                List.of("conflict.a", "conflict.z"),
                WarStatus.ACTIVE,
                30L,
                30L,
                0L));
    }

    private static DiplomaticLifecycleState representativeState() {
        RelationMemory memory = new RelationMemory(
                "faction.a",
                "faction.z",
                List.of(
                        new RelationEvent(
                                "event.trade",
                                RelationFactor.TRADE_DEPENDENCE,
                                10,
                                5L,
                                "route.alpha"),
                        new RelationEvent(
                                "event.threat",
                                RelationFactor.THREAT,
                                -25,
                                10L,
                                "contact.hostile")));
        List<Term> demands = List.of(new Term(TermKind.MARKET_ACCESS, "market.alpha", 0L));
        Proposal proposal = new Proposal(
                "proposal.1",
                "goal.obtain-access",
                "faction.a",
                "faction.z",
                ProposalKind.ULTIMATUM,
                "issue.access",
                demands,
                List.of(),
                10L,
                40L,
                20L,
                ProposalStatus.REJECTED,
                "crisis.1",
                "");
        Crisis crisis = new Crisis(
                "crisis.1",
                "faction.a",
                "faction.z",
                "issue.access",
                demands,
                List.of(),
                50L,
                CrisisEscalation.WAR_AUTHORIZED,
                "proposal.1",
                "decision.war-authorized",
                20L,
                30L);
        War war = new War(
                "war.1",
                "faction.a",
                "faction.z",
                goals(),
                new WarStartEvidence(
                        WarStartKind.CRISIS_DECISION,
                        "decision.war-authorized",
                        30L,
                        "crisis.1"),
                List.of("war.1:faction.a", "war.1:faction.z"),
                WarStatus.CEASEFIRE,
                30L,
                40L,
                700L);
        ObligationDecision obligation = new ObligationDecision(
                "obligation.1",
                "treaty.1",
                "faction.a",
                "faction.z",
                "attack.ally",
                ObligationOutcome.REFUSED,
                -35,
                35L);
        return new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                40L,
                2L,
                2L,
                2L,
                List.of(memory),
                List.of(proposal),
                List.of(crisis),
                List.of(war),
                List.of(obligation));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal("goal.a", "faction.a", WarGoalKind.ACCESS, "market.alpha", true),
                new WarGoal("goal.z", "faction.z", WarGoalKind.SECURITY, "border.z", true));
    }
}