package com.spacesim.world;

import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Deterministic UTF-8 persistence codec for Stage-21A living-actor lifecycle state. */
public final class FactionLivingActorStateCodec {
    private static final String HEADER = "stage21a-living-actor-v1";

    private FactionLivingActorStateCodec() {
        throw new AssertionError("Utility class");
    }

    /**
     * Encodes all actor lifecycle states in stable faction-ID order.
     *
     * @param states actor states to persist
     * @return deterministic UTF-8 checkpoint bytes
     */
    public static byte[] encode(Collection<FactionLivingActorState> states) {
        Objects.requireNonNull(states, "Living actor states not set");
        TreeSet<FactionLivingActorState> sorted = new TreeSet<>();
        for (FactionLivingActorState state : states) {
            FactionLivingActorState checked = Objects.requireNonNull(state, "Living actor state not set");
            if (!sorted.add(checked)) {
                throw new IllegalArgumentException("Duplicate living actor state: " + checked.factionContentId());
            }
        }

        StringBuilder builder = new StringBuilder(HEADER).append('\n');
        for (FactionLivingActorState state : sorted) {
            builder.append("A\t")
                    .append(token(state.factionContentId())).append('\t')
                    .append(state.nextReviewTick()).append('\t')
                    .append(state.commitmentUntilTick()).append('\t')
                    .append(state.lastReviewTick()).append('\t')
                    .append(state.completedReviewCount()).append('\n');
            for (EventWakeup wakeup : state.pendingWakeups()) {
                builder.append("W\t")
                        .append(wakeup.reason().name()).append('\t')
                        .append(token(wakeup.sourceId())).append('\t')
                        .append(wakeup.observedAtTick()).append('\t')
                        .append(wakeup.eligibleAtTick()).append('\n');
            }
            builder.append("E\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decodes a complete Stage-21A lifecycle checkpoint.
     *
     * @param bytes checkpoint bytes produced by {@link #encode(Collection)}
     * @return immutable actor states in stable faction-ID order
     */
    public static List<FactionLivingActorState> decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Living actor checkpoint bytes not set");
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported living actor checkpoint header");
        }

        ArrayList<FactionLivingActorState> states = new ArrayList<>();
        String factionId = null;
        long nextReview = 0L;
        long commitment = 0L;
        long lastReview = -1L;
        long reviewCount = 0L;
        ArrayList<EventWakeup> wakeups = new ArrayList<>();

        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\t", -1);
            switch (parts[0]) {
                case "A" -> {
                    if (parts.length != 6 || factionId != null) {
                        throw malformed(index, line);
                    }
                    factionId = untoken(parts[1]);
                    nextReview = parseLong(parts[2], index);
                    commitment = parseLong(parts[3], index);
                    lastReview = parseLong(parts[4], index);
                    reviewCount = parseLong(parts[5], index);
                    wakeups.clear();
                }
                case "W" -> {
                    if (parts.length != 5 || factionId == null) {
                        throw malformed(index, line);
                    }
                    WakeupReason reason;
                    try {
                        reason = WakeupReason.valueOf(parts[1]);
                    } catch (IllegalArgumentException exception) {
                        throw malformed(index, line);
                    }
                    wakeups.add(new EventWakeup(
                            reason,
                            untoken(parts[2]),
                            parseLong(parts[3], index),
                            parseLong(parts[4], index)));
                }
                case "E" -> {
                    if (parts.length != 1 || factionId == null) {
                        throw malformed(index, line);
                    }
                    states.add(new FactionLivingActorState(
                            factionId,
                            nextReview,
                            commitment,
                            lastReview,
                            reviewCount,
                            wakeups));
                    factionId = null;
                    wakeups = new ArrayList<>();
                }
                default -> throw malformed(index, line);
            }
        }
        if (factionId != null) {
            throw new IllegalArgumentException("Unterminated living actor checkpoint row");
        }
        TreeSet<FactionLivingActorState> sorted = new TreeSet<>(states);
        if (sorted.size() != states.size()) {
            throw new IllegalArgumentException("Duplicate faction state in living actor checkpoint");
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
            throw new IllegalArgumentException("Malformed living actor text token", exception);
        }
    }

    private static long parseLong(String value, int lineIndex) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Malformed number at checkpoint line " + (lineIndex + 1), exception);
        }
    }

    private static IllegalArgumentException malformed(int lineIndex, String line) {
        return new IllegalArgumentException(
                "Malformed living actor checkpoint line " + (lineIndex + 1) + ": " + line);
    }
}
