package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;

import java.util.List;
import java.util.Objects;

/**
 * Canonical Stage-21A interest evidence aggregated only from actor-bounded observations.
 *
 * <p>Priority is an explanation metric, not a gameplay stat modifier. It is computed from the
 * strongest current observation for the same interest/target pair; supporting reports remain in
 * the trace for provenance without multiplying hidden bonuses.</p>
 *
 * @param kind measurable interest family
 * @param targetId stable target identity
 * @param priorityBasisPoints evidence priority in {@code [0,10000]}
 * @param supportingObservations canonical supporting observation rows
 */
public record FactionInterestEvidence(
        InterestKind kind,
        String targetId,
        int priorityBasisPoints,
        List<ActorObservation> supportingObservations)
        implements Comparable<FactionInterestEvidence> {

    /** Validates one immutable evidence aggregate. */
    public FactionInterestEvidence {
        Objects.requireNonNull(kind, "Interest kind not set");
        targetId = requireText(targetId, "Interest target ID");
        if (priorityBasisPoints < 0 || priorityBasisPoints > 10_000) {
            throw new IllegalArgumentException("Interest priority must be in [0,10000]");
        }
        supportingObservations = Objects.requireNonNull(
                        supportingObservations, "Supporting observations not set")
                .stream()
                .sorted()
                .distinct()
                .toList();
        if (supportingObservations.isEmpty()) {
            throw new IllegalArgumentException("Interest evidence requires at least one supporting observation");
        }
        for (ActorObservation observation : supportingObservations) {
            if (observation.interestKind() != kind || !observation.targetId().equals(targetId)) {
                throw new IllegalArgumentException("Supporting observation does not match interest identity");
            }
        }
        int strongest = supportingObservations.stream()
                .mapToInt(ActorObservation::severityBasisPoints)
                .max()
                .orElseThrow();
        if (priorityBasisPoints != strongest) {
            throw new IllegalArgumentException("Interest priority must equal strongest supporting evidence");
        }
    }

    /**
     * Returns canonical provenance rows for explanation/UI projection.
     *
     * @return immutable evidence provenance in observation order
     */
    public List<ObservationEvidence> provenance() {
        return supportingObservations.stream().map(ActorObservation::evidence).distinct().toList();
    }

    @Override
    public int compareTo(FactionInterestEvidence other) {
        Objects.requireNonNull(other, "other");
        int priority = Integer.compare(other.priorityBasisPoints, priorityBasisPoints);
        if (priority != 0) {
            return priority;
        }
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : targetId.compareTo(other.targetId);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
