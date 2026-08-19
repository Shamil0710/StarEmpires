package com.spacesim.world;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Stage-20B placement normalization for genuine generated-content exclusion zones.
 *
 * <p>This service never clamps to the operational/content envelope. It only resolves an explicitly
 * supplied minimum radial placement constraint by the smallest displacement along the candidate's
 * existing radius vector. A true hard constraint is represented separately and conflicts fail
 * explicitly instead of teleporting or component-wise clamping the position.</p>
 */
public final class WorldGenerationPlacementNormalizer {
    /** Result state for one placement-normalization attempt. */
    public enum Status {
        /** Candidate already satisfies the requested placement constraints. */ UNCHANGED,
        /** Candidate was moved by the minimum required radial displacement. */ NORMALIZED,
        /** Placement cannot be resolved without inventing or violating geometry. */ FAILED
    }

    /** Explicit failure reasons for unresolved placement geometry. */
    public enum FailureReason {
        /** Candidate lies exactly on the center, so no existing radius vector is defined. */ UNDEFINED_RADIAL_DIRECTION,
        /** Required minimum radius is larger than a genuine hard maximum constraint. */ HARD_CONSTRAINT_CONFLICT,
        /** Candidate already lies outside a genuine hard maximum and must not be silently clamped. */ OUTSIDE_HARD_CONSTRAINT
    }

    private WorldGenerationPlacementNormalizer() {
        throw new AssertionError("No instances");
    }

    /**
     * Normalizes one candidate against an explicit minimum radial placement zone.
     *
     * <p>The method name intentionally matches the Stage-20B roadmap contract. The optional hard
     * maximum is not an operational envelope; callers may supply it only when another physical or
     * generation rule creates a genuine hard constraint.</p>
     *
     * @param center physical center of the radial placement constraint
     * @param candidate requested authoritative physical position
     * @param requiredMinimumRadiusM minimum allowed separation from {@code center}, in meters
     * @param hardMaximumRadiusM optional genuine hard maximum separation; never a content envelope
     * @return explicit unchanged, normalized or failed placement result
     */
    public static NormalizationResult normalizeBoundaryPlacement(
            LocalPhysicalPosition center,
            LocalPhysicalPosition candidate,
            double requiredMinimumRadiusM,
            OptionalDouble hardMaximumRadiusM) {
        LocalPhysicalPosition checkedCenter = Objects.requireNonNull(center, "center");
        LocalPhysicalPosition checkedCandidate = Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(hardMaximumRadiusM, "hardMaximumRadiusM");
        requireNonNegativeFinite(requiredMinimumRadiusM, "requiredMinimumRadiusM");
        if (hardMaximumRadiusM.isPresent()) {
            requireNonNegativeFinite(hardMaximumRadiusM.getAsDouble(), "hardMaximumRadiusM");
            if (requiredMinimumRadiusM > hardMaximumRadiusM.getAsDouble()) {
                return NormalizationResult.failed(checkedCandidate, FailureReason.HARD_CONSTRAINT_CONFLICT);
            }
        }

        LocalPhysicalPosition.Displacement radial = checkedCenter.displacementTo(checkedCandidate);
        double currentRadiusM = Math.hypot(radial.deltaXM(), radial.deltaYM());
        if (hardMaximumRadiusM.isPresent() && currentRadiusM > hardMaximumRadiusM.getAsDouble()) {
            return NormalizationResult.failed(checkedCandidate, FailureReason.OUTSIDE_HARD_CONSTRAINT);
        }
        if (currentRadiusM >= requiredMinimumRadiusM) {
            return NormalizationResult.unchanged(checkedCandidate);
        }
        if (currentRadiusM == 0d) {
            return NormalizationResult.failed(checkedCandidate, FailureReason.UNDEFINED_RADIAL_DIRECTION);
        }

        double scale = requiredMinimumRadiusM / currentRadiusM;
        LocalPhysicalPosition normalized = checkedCenter.translated(
                radial.deltaXM() * scale,
                radial.deltaYM() * scale);
        double displacementM = checkedCandidate.distanceTo(normalized);
        return NormalizationResult.normalized(checkedCandidate, normalized, displacementM);
    }

    /**
     * Immutable placement-normalization outcome.
     *
     * @param status explicit normalization status
     * @param originalPosition original requested position
     * @param resolvedPosition resulting position; unchanged original when failed
     * @param displacementM physical displacement applied in meters; zero unless normalized
     * @param failureReason explicit reason present only for failed results
     */
    public record NormalizationResult(
            Status status,
            LocalPhysicalPosition originalPosition,
            LocalPhysicalPosition resolvedPosition,
            double displacementM,
            Optional<FailureReason> failureReason) {
        /**
         * Validates one explicit normalization result.
         *
         * @param status explicit normalization status
         * @param originalPosition original requested position
         * @param resolvedPosition resulting position; unchanged original when failed
         * @param displacementM physical displacement applied in meters
         * @param failureReason explicit reason present only for failed results
         */
        public NormalizationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(originalPosition, "originalPosition");
            Objects.requireNonNull(resolvedPosition, "resolvedPosition");
            Objects.requireNonNull(failureReason, "failureReason");
            requireNonNegativeFinite(displacementM, "displacementM");
            if (status == Status.FAILED) {
                if (failureReason.isEmpty() || displacementM != 0d || !resolvedPosition.equals(originalPosition)) {
                    throw new IllegalArgumentException("failed normalization must preserve the original position and reason");
                }
            } else if (failureReason.isPresent()) {
                throw new IllegalArgumentException("successful normalization cannot carry a failure reason");
            } else if (status == Status.UNCHANGED && displacementM != 0d) {
                throw new IllegalArgumentException("unchanged normalization cannot apply displacement");
            } else if (status == Status.NORMALIZED && displacementM <= 0d) {
                throw new IllegalArgumentException("normalized placement must apply positive displacement");
            }
        }

        private static NormalizationResult unchanged(LocalPhysicalPosition position) {
            return new NormalizationResult(Status.UNCHANGED, position, position, 0d, Optional.empty());
        }

        private static NormalizationResult normalized(
                LocalPhysicalPosition original,
                LocalPhysicalPosition resolved,
                double displacementM) {
            return new NormalizationResult(Status.NORMALIZED, original, resolved, displacementM, Optional.empty());
        }

        private static NormalizationResult failed(LocalPhysicalPosition position, FailureReason reason) {
            return new NormalizationResult(Status.FAILED, position, position, 0d, Optional.of(reason));
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
