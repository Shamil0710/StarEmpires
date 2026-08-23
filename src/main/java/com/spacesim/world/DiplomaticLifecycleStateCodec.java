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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for {@link DiplomaticLifecycleState}. */
public final class DiplomaticLifecycleStateCodec {
    private static final int MAGIC = 0x53323143; // S21C
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_CHILD_ROWS = 100_000;

    private DiplomaticLifecycleStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one validated Stage-21C lifecycle state.
     *
     * @param state lifecycle state
     * @return deterministic bounded bytes
     */
    public static byte[] encode(DiplomaticLifecycleState state) {
        DiplomaticLifecycleState checked = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_VERSION);
                output.writeInt(checked.schemaVersion());
                output.writeLong(checked.simulationTick());
                output.writeLong(checked.nextProposalSequence());
                output.writeLong(checked.nextCrisisSequence());
                output.writeLong(checked.nextWarSequence());
                writeRelationMemories(output, checked.relationMemories());
                writeProposals(output, checked.proposals());
                writeCrises(output, checked.crises());
                writeWars(output, checked.wars());
                writeObligations(output, checked.obligationDecisions());
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21C diplomacy payload exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21C encoding failure", exception);
        }
    }

    /**
     * Decodes and validates one Stage-21C lifecycle state.
     *
     * @param bytes encoded lifecycle payload
     * @return immutable validated state
     */
    public static DiplomaticLifecycleState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21C diplomacy payload size is outside bounds");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21C diplomacy magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21C diplomacy file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            long tick = input.readLong();
            long nextProposal = input.readLong();
            long nextCrisis = input.readLong();
            long nextWar = input.readLong();
            DiplomaticLifecycleState state = new DiplomaticLifecycleState(
                    schemaVersion,
                    tick,
                    nextProposal,
                    nextCrisis,
                    nextWar,
                    readRelationMemories(input),
                    readProposals(input),
                    readCrises(input),
                    readWars(input),
                    readObligations(input));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21C diplomacy payload");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21C diplomacy payload is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-21C diplomacy payload", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-21C diplomacy payload", exception);
        }
    }

    private static void writeRelationMemories(DataOutputStream output, List<RelationMemory> memories) throws IOException {
        writeCount(output, memories.size(), MAX_ROWS, "relation memories");
        for (RelationMemory memory : memories) {
            writeString(output, memory.ownerFactionId());
            writeString(output, memory.targetFactionId());
            writeCount(output, memory.events().size(), MAX_CHILD_ROWS, "relation events");
            for (RelationEvent event : memory.events()) {
                writeString(output, event.eventId());
                writeString(output, event.factor().name());
                output.writeInt(event.impact());
                output.writeLong(event.observedTick());
                writeString(output, event.subjectId());
            }
        }
    }

    private static List<RelationMemory> readRelationMemories(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_ROWS, "relation memories");
        List<RelationMemory> result = new ArrayList<>(count);
        for (int row = 0; row < count; row++) {
            String owner = readString(input);
            String target = readString(input);
            int eventCount = readCount(input, MAX_CHILD_ROWS, "relation events");
            List<RelationEvent> events = new ArrayList<>(eventCount);
            for (int index = 0; index < eventCount; index++) {
                events.add(new RelationEvent(
                        readString(input),
                        enumValue(RelationFactor.class, readString(input), "relation factor"),
                        input.readInt(),
                        input.readLong(),
                        readString(input)));
            }
            result.add(new RelationMemory(owner, target, events));
        }
        return result;
    }

    private static void writeProposals(DataOutputStream output, List<Proposal> proposals) throws IOException {
        writeCount(output, proposals.size(), MAX_ROWS, "proposals");
        for (Proposal proposal : proposals) {
            writeString(output, proposal.proposalId());
            writeString(output, proposal.sourceGoalId());
            writeString(output, proposal.proposerFactionId());
            writeString(output, proposal.recipientFactionId());
            writeString(output, proposal.kind().name());
            writeString(output, proposal.issueId());
            writeTerms(output, proposal.demands());
            writeTerms(output, proposal.concessions());
            output.writeLong(proposal.createdTick());
            output.writeLong(proposal.deadlineTick());
            output.writeLong(proposal.updatedTick());
            writeString(output, proposal.status().name());
            writeString(output, proposal.linkedCrisisId());
            writeString(output, proposal.linkedTreatyId());
        }
    }

    private static List<Proposal> readProposals(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_ROWS, "proposals");
        List<Proposal> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Proposal(
                    readString(input),
                    readString(input),
                    readString(input),
                    readString(input),
                    enumValue(ProposalKind.class, readString(input), "proposal kind"),
                    readString(input),
                    readTerms(input),
                    readTerms(input),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    enumValue(ProposalStatus.class, readString(input), "proposal status"),
                    readString(input),
                    readString(input)));
        }
        return result;
    }

    private static void writeCrises(DataOutputStream output, List<Crisis> crises) throws IOException {
        writeCount(output, crises.size(), MAX_ROWS, "crises");
        for (Crisis crisis : crises) {
            writeString(output, crisis.crisisId());
            writeString(output, crisis.initiatorFactionId());
            writeString(output, crisis.targetFactionId());
            writeString(output, crisis.issueId());
            writeTerms(output, crisis.demands());
            writeTerms(output, crisis.concessions());
            output.writeLong(crisis.deadlineTick());
            writeString(output, crisis.escalation().name());
            writeString(output, crisis.causalProposalId());
            writeString(output, crisis.decisionEvidenceId());
            output.writeLong(crisis.createdTick());
            output.writeLong(crisis.updatedTick());
        }
    }

    private static List<Crisis> readCrises(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_ROWS, "crises");
        List<Crisis> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Crisis(
                    readString(input),
                    readString(input),
                    readString(input),
                    readString(input),
                    readTerms(input),
                    readTerms(input),
                    input.readLong(),
                    enumValue(CrisisEscalation.class, readString(input), "crisis escalation"),
                    readString(input),
                    readString(input),
                    input.readLong(),
                    input.readLong()));
        }
        return result;
    }

    private static void writeWars(DataOutputStream output, List<War> wars) throws IOException {
        writeCount(output, wars.size(), MAX_ROWS, "wars");
        for (War war : wars) {
            writeString(output, war.warId());
            writeString(output, war.factionA());
            writeString(output, war.factionB());
            writeCount(output, war.goals().size(), MAX_CHILD_ROWS, "war goals");
            for (WarGoal goal : war.goals()) {
                writeString(output, goal.goalId());
                writeString(output, goal.claimantFactionId());
                writeString(output, goal.kind().name());
                writeString(output, goal.subjectId());
                output.writeBoolean(goal.mandatory());
            }
            writeString(output, war.startEvidence().kind().name());
            writeString(output, war.startEvidence().evidenceId());
            output.writeLong(war.startEvidence().observedTick());
            writeString(output, war.startEvidence().crisisId());
            writeCount(output, war.stage19ConflictIds().size(), MAX_CHILD_ROWS, "Stage-19 conflict IDs");
            for (String conflictId : war.stage19ConflictIds()) {
                writeString(output, conflictId);
            }
            writeString(output, war.status().name());
            output.writeLong(war.startedTick());
            output.writeLong(war.statusChangedTick());
            output.writeLong(war.reEscalationCooldownUntilTick());
        }
    }

    private static List<War> readWars(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_ROWS, "wars");
        List<War> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String warId = readString(input);
            String factionA = readString(input);
            String factionB = readString(input);
            int goalCount = readCount(input, MAX_CHILD_ROWS, "war goals");
            List<WarGoal> goals = new ArrayList<>(goalCount);
            for (int goalIndex = 0; goalIndex < goalCount; goalIndex++) {
                goals.add(new WarGoal(
                        readString(input),
                        readString(input),
                        enumValue(WarGoalKind.class, readString(input), "war goal kind"),
                        readString(input),
                        input.readBoolean()));
            }
            WarStartEvidence evidence = new WarStartEvidence(
                    enumValue(WarStartKind.class, readString(input), "war start kind"),
                    readString(input),
                    input.readLong(),
                    readString(input));
            int conflictCount = readCount(input, MAX_CHILD_ROWS, "Stage-19 conflict IDs");
            List<String> conflicts = new ArrayList<>(conflictCount);
            for (int conflictIndex = 0; conflictIndex < conflictCount; conflictIndex++) {
                conflicts.add(readString(input));
            }
            result.add(new War(
                    warId,
                    factionA,
                    factionB,
                    goals,
                    evidence,
                    conflicts,
                    enumValue(WarStatus.class, readString(input), "war status"),
                    input.readLong(),
                    input.readLong(),
                    input.readLong()));
        }
        return result;
    }

    private static void writeObligations(DataOutputStream output, List<ObligationDecision> decisions) throws IOException {
        writeCount(output, decisions.size(), MAX_ROWS, "obligation decisions");
        for (ObligationDecision decision : decisions) {
            writeString(output, decision.decisionId());
            writeString(output, decision.treatyId());
            writeString(output, decision.obligatedFactionId());
            writeString(output, decision.beneficiaryFactionId());
            writeString(output, decision.threatEvidenceId());
            writeString(output, decision.outcome().name());
            output.writeInt(decision.reputationImpact());
            output.writeLong(decision.decisionTick());
        }
    }

    private static List<ObligationDecision> readObligations(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_ROWS, "obligation decisions");
        List<ObligationDecision> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new ObligationDecision(
                    readString(input),
                    readString(input),
                    readString(input),
                    readString(input),
                    readString(input),
                    enumValue(ObligationOutcome.class, readString(input), "obligation outcome"),
                    input.readInt(),
                    input.readLong()));
        }
        return result;
    }

    private static void writeTerms(DataOutputStream output, List<Term> terms) throws IOException {
        writeCount(output, terms.size(), MAX_CHILD_ROWS, "terms");
        for (Term term : terms) {
            writeString(output, term.kind().name());
            writeString(output, term.subjectId());
            output.writeLong(term.amountMilliCredits());
        }
    }

    private static List<Term> readTerms(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_CHILD_ROWS, "terms");
        List<Term> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Term(
                    enumValue(TermKind.class, readString(input), "term kind"),
                    readString(input),
                    input.readLong()));
        }
        return result;
    }

    private static void writeCount(DataOutputStream output, int count, int maximum, String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count exceeds bounded range");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count exceeds bounded range");
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Stage-21C diplomacy string exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Stage-21C diplomacy string length is outside bounds");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated Stage-21C diplomacy string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Stage-21C " + label + ": " + value, exception);
        }
    }
}