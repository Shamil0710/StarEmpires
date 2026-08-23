package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicGoalState.Lifecycle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Deterministic UTF-8 persistence codec for Stage-21B strategic intent state. */
public final class FactionStrategicIntentStateCodec {
    private static final String HEADER = "stage21b-strategic-intent-v3";

    private FactionStrategicIntentStateCodec() {
        throw new AssertionError("Utility class");
    }

    /**
     * Encodes strategic intent aggregates in stable faction/goal/provenance order.
     *
     * @param states strategic intent states to persist
     * @return deterministic UTF-8 checkpoint bytes
     */
    public static byte[] encode(Collection<FactionStrategicIntentState> states) {
        Objects.requireNonNull(states, "Strategic intent states not set");
        TreeSet<FactionStrategicIntentState> sorted = new TreeSet<>();
        for (FactionStrategicIntentState state : states) {
            FactionStrategicIntentState checked = Objects.requireNonNull(state, "Strategic intent state not set");
            if (!sorted.add(checked)) {
                throw new IllegalArgumentException("Duplicate strategic intent state: " + checked.factionContentId());
            }
        }

        StringBuilder builder = new StringBuilder(HEADER).append('\n');
        for (FactionStrategicIntentState state : sorted) {
            builder.append("S\t").append(token(state.factionContentId())).append('\t')
                    .append(state.nextGoalSequence()).append('\n');
            for (StrategicGoalState goal : state.goals()) {
                builder.append("G\t").append(token(goal.goalId())).append('\t')
                        .append(goal.type().wireId()).append('\t').append(token(goal.targetId())).append('\t')
                        .append(goal.sourceEvidence().kind().name()).append('\t')
                        .append(goal.sourceEvidence().priorityBasisPoints()).append('\t')
                        .append(goal.urgencyBasisPoints()).append('\t')
                        .append(goal.strategicValueBasisPoints()).append('\t')
                        .append(goal.feasibilityBasisPoints()).append('\t')
                        .append(goal.doctrinePreferenceBasisPoints());
                appendEnvelope(builder, goal.requestedBudget());
                appendEnvelope(builder, goal.allocatedBudget());
                builder.append('\t').append(blockers(goal.blockers())).append('\t').append(goal.lifecycle().name())
                        .append('\t').append(goal.createdAtTick()).append('\t').append(goal.updatedAtTick())
                        .append('\t').append(goal.nextReviewTick()).append('\t').append(goal.expiresAtTick())
                        .append('\t').append(goal.cooldownUntilTick());
                appendEnvelope(builder, goal.cancellationCost());
                builder.append('\t').append(goal.outcomeSignal().name()).append('\n');
                for (ObservationEvidence evidence : goal.sourceEvidence().provenance()) {
                    builder.append("P\t").append(evidence.channel().name()).append('\t')
                            .append(token(evidence.provenanceId())).append('\t')
                            .append(evidence.observedAtTick()).append('\t').append(evidence.freshUntilTick()).append('\n');
                }
                builder.append("E\n");
            }
            builder.append("Z\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decodes a complete Stage-21B strategic intent checkpoint.
     *
     * @param bytes checkpoint bytes produced by {@link #encode(Collection)}
     * @return immutable strategic intent states in stable faction order
     */
    public static List<FactionStrategicIntentState> decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Strategic intent checkpoint bytes not set");
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported strategic intent checkpoint header");
        }
        ArrayList<FactionStrategicIntentState> states = new ArrayList<>();
        String factionId = null;
        long nextSequence = 0L;
        ArrayList<StrategicGoalState> goals = new ArrayList<>();
        GoalFields goal = null;
        ArrayList<ObservationEvidence> provenance = new ArrayList<>();

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\t", -1);
            switch (parts[0]) {
                case "S" -> {
                    if (parts.length != 3 || factionId != null || goal != null) {
                        throw malformed(index, line);
                    }
                    factionId = untoken(parts[1]);
                    nextSequence = parseLong(parts[2], index);
                    goals = new ArrayList<>();
                }
                case "G" -> {
                    if (parts.length != 30 || factionId == null || goal != null) {
                        throw malformed(index, line);
                    }
                    goal = new GoalFields(
                            untoken(parts[1]), StrategicGoalType.fromWireId(parts[2]), untoken(parts[3]),
                            parseEnum(InterestKind.class, parts[4], index), parseInt(parts[5], index),
                            parseInt(parts[6], index), parseInt(parts[7], index), parseInt(parts[8], index),
                            parseInt(parts[9], index), envelope(parts, 10, index), envelope(parts, 14, index),
                            parseBlockers(parts[18], index), parseEnum(Lifecycle.class, parts[19], index),
                            parseLong(parts[20], index), parseLong(parts[21], index), parseLong(parts[22], index),
                            parseLong(parts[23], index), parseLong(parts[24], index), envelope(parts, 25, index),
                            parseEnum(StrategicGoalOutcomeSignal.class, parts[29], index));
                    provenance = new ArrayList<>();
                }
                case "P" -> {
                    if (parts.length != 5 || factionId == null || goal == null) {
                        throw malformed(index, line);
                    }
                    provenance.add(new ObservationEvidence(
                            parseEnum(ObservationChannel.class, parts[1], index), untoken(parts[2]),
                            parseLong(parts[3], index), parseLong(parts[4], index)));
                }
                case "E" -> {
                    if (parts.length != 1 || factionId == null || goal == null) {
                        throw malformed(index, line);
                    }
                    StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                            goal.kind(), goal.targetId(), goal.evidencePriorityBasisPoints(), provenance);
                    goals.add(new StrategicGoalState(
                            goal.goalId(), factionId, goal.type(), goal.targetId(), evidence,
                            goal.urgencyBasisPoints(), goal.strategicValueBasisPoints(),
                            goal.feasibilityBasisPoints(), goal.doctrinePreferenceBasisPoints(),
                            goal.requestedBudget(), goal.allocatedBudget(), goal.blockers(), goal.lifecycle(),
                            goal.createdAtTick(), goal.updatedAtTick(), goal.nextReviewTick(), goal.expiresAtTick(),
                            goal.cooldownUntilTick(), goal.cancellationCost(), goal.outcomeSignal()));
                    goal = null;
                    provenance = new ArrayList<>();
                }
                case "Z" -> {
                    if (parts.length != 1 || factionId == null || goal != null) {
                        throw malformed(index, line);
                    }
                    states.add(new FactionStrategicIntentState(factionId, nextSequence, goals));
                    factionId = null;
                    goals = new ArrayList<>();
                }
                default -> throw malformed(index, line);
            }
        }
        if (factionId != null || goal != null) {
            throw new IllegalArgumentException("Unterminated strategic intent checkpoint row");
        }
        TreeSet<FactionStrategicIntentState> sorted = new TreeSet<>(states);
        if (sorted.size() != states.size()) {
            throw new IllegalArgumentException("Duplicate faction state in strategic intent checkpoint");
        }
        return List.copyOf(sorted);
    }

    private static void appendEnvelope(StringBuilder builder, StrategicPlanningEnvelope envelope) {
        builder.append('\t').append(envelope.treasuryUnits()).append('\t').append(envelope.logisticsUnits())
                .append('\t').append(envelope.constructionUnits()).append('\t').append(envelope.readinessUnits());
    }

    private static StrategicPlanningEnvelope envelope(String[] parts, int offset, int lineIndex) {
        return new StrategicPlanningEnvelope(
                parseLong(parts[offset], lineIndex), parseLong(parts[offset + 1], lineIndex),
                parseLong(parts[offset + 2], lineIndex), parseLong(parts[offset + 3], lineIndex));
    }

    private static String blockers(List<StrategicGoalBlocker> blockers) {
        return blockers.isEmpty() ? "-" : blockers.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static List<StrategicGoalBlocker> parseBlockers(String value, int lineIndex) {
        if ("-".equals(value)) {
            return List.of();
        }
        try {
            return List.of(value.split(",")).stream().map(StrategicGoalBlocker::valueOf).sorted().distinct().toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed blocker at strategic intent line " + (lineIndex + 1), exception);
        }
    }

    private static String token(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untoken(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed strategic intent text token", exception);
        }
    }

    private static long parseLong(String value, int lineIndex) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Malformed number at strategic intent line " + (lineIndex + 1), exception);
        }
    }

    private static int parseInt(String value, int lineIndex) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Malformed number at strategic intent line " + (lineIndex + 1), exception);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, int lineIndex) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed enum at strategic intent line " + (lineIndex + 1), exception);
        }
    }

    private static IllegalArgumentException malformed(int lineIndex, String line) {
        return new IllegalArgumentException("Malformed strategic intent checkpoint line " + (lineIndex + 1) + ": " + line);
    }

    private record GoalFields(
            String goalId,
            StrategicGoalType type,
            String targetId,
            InterestKind kind,
            int evidencePriorityBasisPoints,
            int urgencyBasisPoints,
            int strategicValueBasisPoints,
            int feasibilityBasisPoints,
            int doctrinePreferenceBasisPoints,
            StrategicPlanningEnvelope requestedBudget,
            StrategicPlanningEnvelope allocatedBudget,
            List<StrategicGoalBlocker> blockers,
            Lifecycle lifecycle,
            long createdAtTick,
            long updatedAtTick,
            long nextReviewTick,
            long expiresAtTick,
            long cooldownUntilTick,
            StrategicPlanningEnvelope cancellationCost,
            StrategicGoalOutcomeSignal outcomeSignal) {
    }
}
