package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;

import java.util.List;
import java.util.Objects;

/**
 * Persistent actor-bounded source evidence retained by a Stage-21B strategic goal.
 *
 * <p>The compact snapshot intentionally stores provenance delivered to the actor rather than a
 * reference to hidden world truth. This keeps save/load explanation stable even after the original
 * observation snapshot has rotated out of memory.</p>
 *
 * @param kind Stage-21A interest family that justified the goal
 * @param targetId stable route/system/faction/resource/obligation identity
 * @param priorityBasisPoints strongest observed evidence magnitude in {@code [0,10000]}
 * @param provenance canonical delivered evidence rows
 */
public record StrategicGoalEvidence(
        InterestKind kind,
        String targetId,
        int priorityBasisPoints,
        List<ObservationEvidence> provenance) {

    /** Validates and canonicalizes one persistent evidence snapshot. */
    public StrategicGoalEvidence {
        Objects.requireNonNull(kind, "Strategic goal evidence kind not set");
        targetId = requireText(targetId, "Strategic goal evidence target ID");
        if (priorityBasisPoints < 0 || priorityBasisPoints > 10_000) {
            throw new IllegalArgumentException("Strategic goal evidence priority must be in [0,10000]");
        }
        provenance = Objects.requireNonNull(provenance, "Strategic goal evidence provenance not set")
                .stream()
                .map(row -> Objects.requireNonNull(row, "Strategic goal provenance row not set"))
                .sorted()
                .distinct()
                .toList();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("Strategic goal evidence requires provenance");
        }
    }

    /**
     * Freezes one current Stage-21A interest aggregate into persistent goal provenance.
     *
     * @param evidence current actor-bounded interest aggregate
     * @return persistence-safe evidence snapshot
     */
    public static StrategicGoalEvidence from(FactionInterestEvidence evidence) {
        FactionInterestEvidence checked = Objects.requireNonNull(evidence, "Faction interest evidence not set");
        return new StrategicGoalEvidence(
                checked.kind(),
                checked.targetId(),
                checked.priorityBasisPoints(),
                checked.provenance());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
