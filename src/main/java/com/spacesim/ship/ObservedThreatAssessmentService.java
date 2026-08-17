package com.spacesim.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds deterministic tactical threat priorities from actor-visible {@link TrackState} only.
 *
 * <p>The service deliberately has no access to ECS entities, authoritative target transforms,
 * fitted modules, ammunition, hull state or faction runtime. A caller supplies one
 * {@link ObservedContact} for each contact already present in that actor's information domain.
 * The resulting score is a behavioral priority heuristic, not hidden physical combat power and
 * not a probability.</p>
 */
public final class ObservedThreatAssessmentService {
    private static final double UNKNOWN_POSITION_CERTAINTY = 0.20d;
    private static final double UNKNOWN_POSITION_RANGE_RELEVANCE = 0.35d;

    /** Actor-known contact disposition used only to choose behavior toward an observed track. */
    public enum ContactDisposition {
        /** Contact is known friendly and must not be prioritized as a threat. */
        FRIENDLY,
        /** Contact allegiance or intent is not sufficiently established. */
        UNKNOWN,
        /** Contact is known hostile from information available to the observing actor. */
        HOSTILE
    }

    /**
     * One actor-visible tactical contact.
     *
     * @param track fused production information state visible to the actor
     * @param disposition actor-known disposition, never authoritative hidden allegiance
     */
    public record ObservedContact(TrackState track, ContactDisposition disposition) {
        /**
         * Validates the actor-visible contact boundary.
         *
         * @param track fused production information state visible to the actor
         * @param disposition actor-known disposition
         */
        public ObservedContact {
            Objects.requireNonNull(track, "track");
            Objects.requireNonNull(disposition, "disposition");
        }
    }

    /**
     * Immutable derived threat assessment suitable for deterministic tactical ordering.
     *
     * <p>{@code priorityScore} is intentionally dimensionless. It expresses how urgently an actor
     * should consider a contact given only observed disposition, track quality, freshness,
     * uncertainty and estimated range. It must never be interpreted as damage, fleet power or
     * probability of victory.</p>
     *
     * @param targetId observed target identity
     * @param disposition actor-known contact disposition
     * @param informationState production information quality
     * @param positionKnown whether an estimated Cartesian position exists
     * @param estimatedRangeM estimated observer-to-contact range, or canonical zero when unknown
     * @param trackAgeSeconds age of the freshest fused measurement
     * @param classificationConfidence classification evidence in {@code [0,1]}
     * @param positionUncertaintyM one-sigma Cartesian uncertainty, or canonical zero when unknown
     * @param priorityScore deterministic non-negative behavioral priority
     */
    public record Assessment(
            long targetId,
            ContactDisposition disposition,
            TrackState.InformationState informationState,
            boolean positionKnown,
            double estimatedRangeM,
            double trackAgeSeconds,
            double classificationConfidence,
            double positionUncertaintyM,
            double priorityScore) {
        /**
         * Validates the immutable assessment representation.
         *
         * @param targetId observed target identity
         * @param disposition actor-known contact disposition
         * @param informationState production information quality
         * @param positionKnown whether an estimated Cartesian position exists
         * @param estimatedRangeM estimated range or canonical zero when unknown
         * @param trackAgeSeconds track age in seconds
         * @param classificationConfidence classification evidence
         * @param positionUncertaintyM one-sigma position uncertainty or canonical zero when unknown
         * @param priorityScore deterministic behavioral priority
         */
        public Assessment {
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(informationState, "informationState");
            requireNonNegativeFinite(estimatedRangeM, "estimatedRangeM");
            requireNonNegativeFinite(trackAgeSeconds, "trackAgeSeconds");
            if (!Double.isFinite(classificationConfidence)
                    || classificationConfidence < 0d || classificationConfidence > 1d) {
                throw new IllegalArgumentException("classificationConfidence must be finite in [0,1]");
            }
            requireNonNegativeFinite(positionUncertaintyM, "positionUncertaintyM");
            requireNonNegativeFinite(priorityScore, "priorityScore");
            if (!positionKnown && (estimatedRangeM != 0d || positionUncertaintyM != 0d)) {
                throw new IllegalArgumentException("unknown position must use canonical zero range/uncertainty");
            }
        }
    }

    /**
     * Assesses and deterministically orders actor-visible contacts.
     *
     * <p>The input collection itself defines the actor's knowledge boundary. Contacts absent from
     * the collection cannot influence the result. Results sort by descending derived priority and
     * then ascending stable target ID.</p>
     *
     * @param contacts actor-visible contacts only
     * @param observerXM actor's own known x position in meters
     * @param observerYM actor's own known y position in meters
     * @param nowSeconds authoritative current simulation time
     * @param tacticalReferenceRangeM positive distance scale used only to normalize range and uncertainty
     * @param freshnessReferenceSeconds positive age scale used only to normalize track freshness
     * @return immutable deterministic assessments
     */
    public List<Assessment> assess(
            List<ObservedContact> contacts,
            double observerXM,
            double observerYM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        Objects.requireNonNull(contacts, "contacts");
        if (!Double.isFinite(observerXM) || !Double.isFinite(observerYM)) {
            throw new IllegalArgumentException("observer coordinates must be finite");
        }
        if (!Double.isFinite(nowSeconds)) {
            throw new IllegalArgumentException("nowSeconds must be finite");
        }
        requirePositiveFinite(tacticalReferenceRangeM, "tacticalReferenceRangeM");
        requirePositiveFinite(freshnessReferenceSeconds, "freshnessReferenceSeconds");

        List<Assessment> assessments = new ArrayList<>(contacts.size());
        for (ObservedContact contact : contacts) {
            assessments.add(assessOne(
                    Objects.requireNonNull(contact, "contact"),
                    observerXM,
                    observerYM,
                    nowSeconds,
                    tacticalReferenceRangeM,
                    freshnessReferenceSeconds));
        }
        assessments.sort(Comparator.comparingDouble(Assessment::priorityScore)
                .reversed()
                .thenComparingLong(Assessment::targetId));
        return List.copyOf(assessments);
    }

    private static Assessment assessOne(
            ObservedContact contact,
            double observerXM,
            double observerYM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        TrackState track = contact.track();
        double ageSeconds = track.ageSeconds(nowSeconds);
        double freshness = 1d / (1d + ageSeconds / freshnessReferenceSeconds);
        double quality = switch (track.informationState()) {
            case DETECTED -> 0.20d;
            case CLASSIFIED -> 0.40d;
            case TRACKED -> 0.70d;
            case FIRE_CONTROL -> 1d;
        };
        double disposition = switch (contact.disposition()) {
            case FRIENDLY -> 0d;
            case UNKNOWN -> 0.35d;
            case HOSTILE -> 1d;
        };
        double classification = 0.25d + 0.75d * track.classificationConfidence();

        double estimatedRangeM = 0d;
        double positionUncertaintyM = 0d;
        double positionCertainty = UNKNOWN_POSITION_CERTAINTY;
        double rangeRelevance = UNKNOWN_POSITION_RANGE_RELEVANCE;
        if (track.positionKnown()) {
            estimatedRangeM = Math.hypot(
                    track.estimatedXM() - observerXM,
                    track.estimatedYM() - observerYM);
            positionUncertaintyM = Math.sqrt(track.covariance().positionVarianceM2());
            positionCertainty = 1d / (1d + positionUncertaintyM / tacticalReferenceRangeM);
            rangeRelevance = 1d / (1d + estimatedRangeM / tacticalReferenceRangeM);
        }

        double priority = disposition
                * quality
                * freshness
                * classification
                * positionCertainty
                * rangeRelevance;
        return new Assessment(
                track.targetId(),
                contact.disposition(),
                track.informationState(),
                track.positionKnown(),
                estimatedRangeM,
                ageSeconds,
                track.classificationConfidence(),
                positionUncertaintyM,
                priority);
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
