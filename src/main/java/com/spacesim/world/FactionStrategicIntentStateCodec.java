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
    private static final String HEADER = "stage21b-strategic-intent-v1";

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
            builder.append("S\t")
                    .append(token(state.factionContentId())).append('\t')
                    .append(state.nextGoalSequence()).append('\n');
            for (StrategicGoalState goal : state.goals()) {
                builder.append("G\t")
                        .append(token(goal.goalId())).append('\t')
                        .append(goal.type().wireId()).append('\t')
                        .append(token(goal.targetId())).append('\t')
                        .append(goal.sourceEvidence().kind().name()).append('\t')
                        .append(goal.sourceEvidence().priorityBasisPoints()).append('\t')
                        .append(goal.urgencyBasisPoints()).append('\t')
                        .append(goal.feasibilityBasisPoints()).append('\t')
                        .append(goal.requestedBudgetUnits()).append('\t')
                        .append(goal.allocatedBudgetUnits()).append('\t')
                        .append(goal.lifecycle().name()).append('\t')
                        .append(goal.createdAtTick()).append('\t')
                        .append(goal.updatedAtTick()).append('\t')
                        .append(goal.cooldownUntilTick()).append('\t')
                        .append(goal.cancellationCostUnits()).append('\n');
                for (ObservationEvidence evidence : goal.sourceEvidence().provenance()) {
                    builder.append("P\t")
                            .append(evidence.channel().name()).append('\t')
                            .append(token(evidence.provenanceId())).append('\t')
                            .append(evidence.observedAtTick()).append('\t')
                            .append(evidence.freshUntilTick()).append('\n');
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
                    if (parts.length != 15 || factionId == null || goal != null) {
                        throw malformed(index, line);
                    }
                    goal = new GoalFields(
                            untoken(parts[1]),
                            StrategicGoalType.fromWireId(parts[2]),
                            untoken(parts[3]),
                            parseEnum(InterestKind.class, parts[4], index),
                            parseInt(parts[5], index),
                            parseInt(parts[6], index),
                            parseInt(parts[7], index),
                            parseLong(parts[8], index),
                            parseLong(parts[9], index),
                            parseEnum(Lifecycle.class, parts[10], index),
                            parseLong(parts[11], index),
                            parseLong(parts[12], index),
                            parseLong(parts[13], index),
                            parseLong(parts[14], index));
                    provenance = new ArrayList<>();
                }
                case "P" -> {
                    if (parts.length != 5 || factionId == null || goal == null) {
                        throw malformed(index, line);
                    }
                    provenance.add(new ObservationEvidence(
                            parseEnum(ObservationChannel.class, parts[1], index),
                            untoken(parts[2]),
                            parseLong(parts[3], index),
                            parseLong(parts[4], index)));
                }
                case "E" -> {
                    if (parts.length != 1 || factionId == null || goal == null) {
                        throw malformed(index, line);
                    }
                    StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                            goal.kind(), goal.targetId(), goal.evidencePriorityBasisPoints(), provenance);
                    goals.add(new StrategicGoalState(
                            goal.goalId(),
                            factionId,
                            goal.type(),
                            goal.targetId(),
                            evidence,
                            goal.urgencyBasisPoints(),
                            goal.feasibilityBasisPoints(),
                            goal.requestedBudgetUnits(),
                            goal.allocatedBudgetUnits(),
                            goal.lifecycle(),
                            goal.createdAtTick(),
                            goal.updatedAtTick(),
                            goal.cooldownUntilTick(),
                            goal.cancellationCostUnits()));
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
        return new IllegalArgumentException(
                "Malformed strategic intent checkpoint line " + (lineIndex + 1) + ": " + line);
    }

    private record GoalFields(
            String goalId,
            StrategicGoalType type,
            String targetId,
            InterestKind kind,
            int evidencePriorityBasisPoints,
            int urgencyBasisPoints,
            int feasibilityBasisPoints,
            long requestedBudgetUnits,
            long allocatedBudgetUnits,
            Lifecycle lifecycle,
            long createdAtTick,
            long updatedAtTick,
            long cooldownUntilTick,
            long cancellationCostUnits) {
    }
}
