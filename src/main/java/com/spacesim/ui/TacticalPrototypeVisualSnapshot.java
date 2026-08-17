package com.spacesim.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable presentation-only snapshot for the Stage-17.5I top-down tactical prototype.
 *
 * <p>Every record in this snapshot is a visual projection of authoritative simulation state or a
 * presentation-only decoration derived from such state. No field is fed back into combat, guidance,
 * damage, power, heat, ammunition or persistence. A renderer may omit, recolor or replace any item
 * without changing simulation truth.</p>
 *
 * @param ships projected ship/wreck silhouettes
 * @param bodies projected kinetic/guided/interceptor/decoy/debris markers
 * @param beams projected beam segments
 * @param shields projected energetic shield arcs
 * @param impacts projected impact/penetration markers
 * @param damage projected local compartment-damage markers
 */
public record TacticalPrototypeVisualSnapshot(
        List<ShipGlyph> ships,
        List<BodyGlyph> bodies,
        List<BeamGlyph> beams,
        List<ShieldGlyph> shields,
        List<ImpactGlyph> impacts,
        List<DamageGlyph> damage) {

    /** Visual body categories required by the Stage-17.5I prototype set. */
    public enum BodyKind {
        /** Unguided physical kinetic projectile. */ KINETIC_PROJECTILE,
        /** Guided offensive missile. */ GUIDED_MISSILE,
        /** Guided defensive interceptor. */ INTERCEPTOR,
        /** Explicit deceptive hypothesis rendered as a false contact. */ DECOY,
        /** Cosmetic debris emitted only after authoritative destruction state. */ DEBRIS
    }

    /** Visual impact categories; names describe presentation only. */
    public enum ImpactKind {
        /** Incoming energy was visibly absorbed by the field. */ SHIELD,
        /** Material protection received the physical interaction. */ ARMOR,
        /** Residual/internal energy reached the hull after protection. */ PENETRATION
    }

    /**
     * One top-down ship or wreck silhouette.
     *
     * @param entityId stable authoritative owner identity
     * @param xM world x position in meters
     * @param yM world y position in meters
     * @param headingRad world heading in radians
     * @param lengthM physical hull length
     * @param widthM physical hull width
     * @param thrustFraction presentation fraction [0,1] derived from authoritative thrust command/state
     * @param integrityFraction mean physical compartment integrity [0,1]
     * @param wreck whether authoritative damage state has no surviving compartment integrity
     */
    public record ShipGlyph(
            long entityId,
            double xM,
            double yM,
            double headingRad,
            double lengthM,
            double widthM,
            double thrustFraction,
            double integrityFraction,
            boolean wreck) {
        /**
         * Validates one immutable ship glyph.
         *
         * @param entityId stable authoritative owner identity
         * @param xM world x position in meters
         * @param yM world y position in meters
         * @param headingRad world heading in radians
         * @param lengthM physical hull length
         * @param widthM physical hull width
         * @param thrustFraction presentation fraction [0,1] derived from authoritative thrust command/state
         * @param integrityFraction mean physical compartment integrity [0,1]
         * @param wreck whether authoritative damage state has no surviving compartment integrity
         */
        public ShipGlyph {
            requirePositiveId(entityId, "entityId");
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireFinite(headingRad, "headingRad");
            requirePositiveFinite(lengthM, "lengthM");
            requirePositiveFinite(widthM, "widthM");
            requireUnit(thrustFraction, "thrustFraction");
            requireUnit(integrityFraction, "integrityFraction");
        }
    }

    /**
     * One compact moving/static body marker.
     *
     * @param kind visual category
     * @param bodyId authoritative body/hypothesis identity
     * @param xM world x position
     * @param yM world y position
     * @param headingRad current velocity/apparent heading
     * @param lengthM physical or presentation marker length
     * @param widthM physical or presentation marker width
     * @param trailLengthM presentation trail length; zero is allowed
     */
    public record BodyGlyph(
            BodyKind kind,
            long bodyId,
            double xM,
            double yM,
            double headingRad,
            double lengthM,
            double widthM,
            double trailLengthM) {
        /**
         * Validates one immutable body glyph.
         *
         * @param kind visual category
         * @param bodyId authoritative body/hypothesis identity
         * @param xM world x position
         * @param yM world y position
         * @param headingRad current velocity/apparent heading
         * @param lengthM physical or presentation marker length
         * @param widthM physical or presentation marker width
         * @param trailLengthM presentation trail length; zero is allowed
         */
        public BodyGlyph {
            Objects.requireNonNull(kind, "kind");
            requirePositiveId(bodyId, "bodyId");
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireFinite(headingRad, "headingRad");
            requirePositiveFinite(lengthM, "lengthM");
            requirePositiveFinite(widthM, "widthM");
            requireNonNegativeFinite(trailLengthM, "trailLengthM");
        }
    }

    /**
     * One authoritative beam solution projected as a line segment.
     *
     * @param eventId stable presentation/event identity
     * @param startXM emitter x
     * @param startYM emitter y
     * @param endXM target/exposure x
     * @param endYM target/exposure y
     * @param deliveredEnergyJ authoritative delivered beam energy
     * @param spotRadiusM authoritative effective exposure radius
     */
    public record BeamGlyph(
            long eventId,
            double startXM,
            double startYM,
            double endXM,
            double endYM,
            double deliveredEnergyJ,
            double spotRadiusM) {
        /**
         * Validates one immutable beam glyph.
         *
         * @param eventId stable presentation/event identity
         * @param startXM emitter x
         * @param startYM emitter y
         * @param endXM target/exposure x
         * @param endYM target/exposure y
         * @param deliveredEnergyJ authoritative delivered beam energy
         * @param spotRadiusM authoritative effective exposure radius
         */
        public BeamGlyph {
            requirePositiveId(eventId, "eventId");
            requireFinite(startXM, "startXM");
            requireFinite(startYM, "startYM");
            requireFinite(endXM, "endXM");
            requireFinite(endYM, "endYM");
            requireNonNegativeFinite(deliveredEnergyJ, "deliveredEnergyJ");
            requireNonNegativeFinite(spotRadiusM, "spotRadiusM");
        }
    }

    /**
     * One projected physical shield sector.
     *
     * @param ownerEntityId authoritative ship identity
     * @param xM owner x
     * @param yM owner y
     * @param radiusM cosmetic display radius around the physical hull
     * @param centerRad world-space sector center
     * @param halfArcRad authoritative half coverage arc
     * @param reserveFraction current field reserve/capacity fraction [0,1]
     * @param collapsed authoritative collapse state
     */
    public record ShieldGlyph(
            long ownerEntityId,
            double xM,
            double yM,
            double radiusM,
            double centerRad,
            double halfArcRad,
            double reserveFraction,
            boolean collapsed) {
        /**
         * Validates one immutable shield glyph.
         *
         * @param ownerEntityId authoritative ship identity
         * @param xM owner x
         * @param yM owner y
         * @param radiusM cosmetic display radius around the physical hull
         * @param centerRad world-space sector center
         * @param halfArcRad authoritative half coverage arc
         * @param reserveFraction current field reserve/capacity fraction [0,1]
         * @param collapsed authoritative collapse state
         */
        public ShieldGlyph {
            requirePositiveId(ownerEntityId, "ownerEntityId");
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requirePositiveFinite(radiusM, "radiusM");
            requireFinite(centerRad, "centerRad");
            if (!Double.isFinite(halfArcRad) || halfArcRad <= 0d || halfArcRad > Math.PI) {
                throw new IllegalArgumentException("halfArcRad must be in (0,pi]");
            }
            requireUnit(reserveFraction, "reserveFraction");
        }
    }

    /**
     * One projected impact/protection event.
     *
     * @param eventId stable event identity
     * @param kind presentation category derived from authoritative physical result
     * @param xM world impact x
     * @param yM world impact y
     * @param energyJ authoritative energy associated with the visible event
     */
    public record ImpactGlyph(long eventId, ImpactKind kind, double xM, double yM, double energyJ) {
        /**
         * Validates one immutable impact glyph.
         *
         * @param eventId stable event identity
         * @param kind presentation category derived from authoritative physical result
         * @param xM world impact x
         * @param yM world impact y
         * @param energyJ authoritative energy associated with the visible event
         */
        public ImpactGlyph {
            requirePositiveId(eventId, "eventId");
            Objects.requireNonNull(kind, "kind");
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireNonNegativeFinite(energyJ, "energyJ");
        }
    }

    /**
     * One local compartment-damage marker projected into world space.
     *
     * @param ownerEntityId authoritative ship identity
     * @param compartmentId authoritative compartment ID
     * @param xM world x of authored compartment center
     * @param yM world y of authored compartment center
     * @param severity one minus current authoritative compartment integrity [0,1]
     */
    public record DamageGlyph(
            long ownerEntityId,
            String compartmentId,
            double xM,
            double yM,
            double severity) {
        /**
         * Validates one immutable damage glyph.
         *
         * @param ownerEntityId authoritative ship identity
         * @param compartmentId authoritative compartment ID
         * @param xM world x of authored compartment center
         * @param yM world y of authored compartment center
         * @param severity one minus current authoritative compartment integrity [0,1]
         */
        public DamageGlyph {
            requirePositiveId(ownerEntityId, "ownerEntityId");
            if (compartmentId == null || compartmentId.isBlank()) {
                throw new IllegalArgumentException("compartmentId must be non-blank");
            }
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireUnit(severity, "severity");
        }
    }

    /**
     * Validates, sorts and freezes all visual lists for deterministic rendering/testing.
     *
     * @param ships projected ship/wreck silhouettes
     * @param bodies projected kinetic/guided/interceptor/decoy/debris markers
     * @param beams projected beam segments
     * @param shields projected energetic shield arcs
     * @param impacts projected impact/penetration markers
     * @param damage projected local compartment-damage markers
     */
    public TacticalPrototypeVisualSnapshot {
        ships = sortedCopy(ships, Comparator.comparingLong(ShipGlyph::entityId));
        bodies = sortedCopy(bodies, Comparator.comparing((BodyGlyph value) -> value.kind().name())
                .thenComparingLong(BodyGlyph::bodyId));
        beams = sortedCopy(beams, Comparator.comparingLong(BeamGlyph::eventId));
        shields = sortedCopy(shields, Comparator.comparingLong(ShieldGlyph::ownerEntityId));
        impacts = sortedCopy(impacts, Comparator.comparingLong(ImpactGlyph::eventId)
                .thenComparing(value -> value.kind().name()));
        damage = sortedCopy(damage, Comparator.comparingLong(DamageGlyph::ownerEntityId)
                .thenComparing(DamageGlyph::compartmentId));
    }

    /** @return completely empty presentation snapshot */
    public static TacticalPrototypeVisualSnapshot empty() {
        return new TacticalPrototypeVisualSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<? super T> comparator) {
        Objects.requireNonNull(source, "visual list");
        if (source.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("visual lists must not contain null");
        }
        return source.stream().sorted(comparator).toList();
    }

    private static void requirePositiveId(long value, String label) {
        if (value <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireUnit(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
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

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
