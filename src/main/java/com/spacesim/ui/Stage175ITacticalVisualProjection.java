package com.spacesim.ui;

import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.ship.BeamWeaponRuntime.BeamSolution;
import com.spacesim.ship.ElectronicWarfareState.DeceptionSource;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.KineticProtectionRuntime;
import com.spacesim.ship.ProjectileBody;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BeamGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.DamageGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShieldGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure Stage-17.5I adapter from authoritative combat state into tactical presentation primitives.
 *
 * <p>This class owns no simulation state and exposes no mutation back-channel. All scaling constants
 * are cosmetic read-only choices. They influence only glyph size/trails/debris placement and never
 * feed combat equations, culling, guidance, protection or persistence.</p>
 */
public final class Stage175ITacticalVisualProjection {
    private static final double TRAIL_SECONDS = 0.05d;
    private static final double MIN_BODY_MARKER_M = 12d;
    private static final double MIN_DECOY_MARKER_M = 18d;
    private static final double SHIELD_RADIUS_MULTIPLIER = 0.62d;
    private static final int WRECK_DEBRIS_COUNT = 6;

    private final List<ShipGlyph> ships = new ArrayList<>();
    private final List<BodyGlyph> bodies = new ArrayList<>();
    private final List<BeamGlyph> beams = new ArrayList<>();
    private final List<ShieldGlyph> shields = new ArrayList<>();
    private final List<ImpactGlyph> impacts = new ArrayList<>();
    private final List<DamageGlyph> damage = new ArrayList<>();

    /** Creates an empty one-frame presentation builder. */
    public Stage175ITacticalVisualProjection() {
    }

    /**
     * Projects one fitted physical ship, its local damage, shield and thrust presentation.
     *
     * @param entityId authoritative ship identity
     * @param hull physical hull geometry
     * @param snapshot authoritative compartment/module integrity snapshot
     * @param xM world x position
     * @param yM world y position
     * @param headingRad world heading
     * @param thrustFraction read-only normalized thrust-command/availability fraction
     * @param shieldDefinition fitted shield definition or null
     * @param shieldState current shield state or null
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addShip(
            long entityId,
            HullDefinition hull,
            Snapshot snapshot,
            double xM,
            double yM,
            double headingRad,
            double thrustFraction,
            ShieldFieldRuntime.Definition shieldDefinition,
            ShieldFieldRuntime.State shieldState) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        Snapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        requirePositiveId(entityId, "entityId");
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        requireFinite(headingRad, "headingRad");
        requireUnit(thrustFraction, "thrustFraction");

        double integrity = meanCompartmentIntegrity(checkedHull, checkedSnapshot);
        boolean wreck = checkedHull.compartments().stream()
                .allMatch(value -> checkedSnapshot.compartmentIntegrityById().getOrDefault(value.id(), 1d) <= 0d);
        ships.add(new ShipGlyph(
                entityId,
                xM,
                yM,
                headingRad,
                checkedHull.boundingDimensionsM().lengthM(),
                checkedHull.boundingDimensionsM().widthM(),
                thrustFraction,
                integrity,
                wreck));
        addDamageGlyphs(entityId, checkedHull, checkedSnapshot, xM, yM, headingRad);
        addShieldGlyph(entityId, checkedHull, xM, yM, headingRad, shieldDefinition, shieldState);
        if (wreck) {
            addCosmeticWreckDebris(entityId, checkedHull, xM, yM, headingRad);
        }
        return this;
    }

    /**
     * Projects one authoritative kinetic body.
     *
     * @param body physical projectile body
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addKinetic(ProjectileBody body) {
        ProjectileBody checked = Objects.requireNonNull(body, "body");
        double markerLength = Math.max(MIN_BODY_MARKER_M, checked.lengthM());
        double markerWidth = Math.max(MIN_BODY_MARKER_M * 0.32d, checked.diameterM());
        bodies.add(new BodyGlyph(
                BodyKind.KINETIC_PROJECTILE,
                checked.projectileId(),
                checked.xM(),
                checked.yM(),
                heading(checked.velocityXMps(), checked.velocityYMps()),
                markerLength,
                markerWidth,
                checked.speedMps() * TRAIL_SECONDS));
        return this;
    }

    /**
     * Projects one authoritative guided body as an offensive missile or defensive interceptor.
     *
     * @param body physical guided body
     * @param interceptor whether presentation should identify this body as an interceptor
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addGuided(GuidedWeaponBody body, boolean interceptor) {
        GuidedWeaponBody checked = Objects.requireNonNull(body, "body");
        bodies.add(new BodyGlyph(
                interceptor ? BodyKind.INTERCEPTOR : BodyKind.GUIDED_MISSILE,
                checked.bodyId(),
                checked.xM(),
                checked.yM(),
                heading(checked.velocityXMps(), checked.velocityYMps()),
                Math.max(MIN_BODY_MARKER_M, checked.lengthM()),
                Math.max(MIN_BODY_MARKER_M * 0.4d, checked.diameterM()),
                checked.speedMps() * TRAIL_SECONDS));
        return this;
    }

    /**
     * Projects an explicit deception hypothesis into an apparent world-space false contact.
     *
     * <p>The supplied base bearing/range are the observer's current true/estimated reference geometry;
     * the authoritative {@link DeceptionSource} supplies deterministic apparent biases.</p>
     *
     * @param bodyId stable presentation identity assigned to this explicit hypothesis
     * @param observerXM observer x
     * @param observerYM observer y
     * @param baseBearingRad current reference bearing
     * @param baseRangeM current reference range
     * @param source authoritative deception hypothesis
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addDeceptionHypothesis(
            long bodyId,
            double observerXM,
            double observerYM,
            double baseBearingRad,
            double baseRangeM,
            DeceptionSource source) {
        requirePositiveId(bodyId, "bodyId");
        requireFinite(observerXM, "observerXM");
        requireFinite(observerYM, "observerYM");
        requireFinite(baseBearingRad, "baseBearingRad");
        requireNonNegativeFinite(baseRangeM, "baseRangeM");
        DeceptionSource checked = Objects.requireNonNull(source, "source");
        double apparentBearing = baseBearingRad + checked.apparentBearingBiasRad();
        double apparentRange = Math.max(0d, baseRangeM + checked.apparentRangeBiasM());
        bodies.add(new BodyGlyph(
                BodyKind.DECOY,
                bodyId,
                observerXM + Math.cos(apparentBearing) * apparentRange,
                observerYM + Math.sin(apparentBearing) * apparentRange,
                apparentBearing,
                MIN_DECOY_MARKER_M,
                MIN_DECOY_MARKER_M,
                0d));
        return this;
    }

    /**
     * Projects an allowed authoritative beam solution as a continuous line, never as a projectile.
     *
     * @param eventId stable event identity
     * @param startXM emitter x
     * @param startYM emitter y
     * @param endXM target/exposure x
     * @param endYM target/exposure y
     * @param solution authoritative beam solution
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addBeam(
            long eventId,
            double startXM,
            double startYM,
            double endXM,
            double endYM,
            BeamSolution solution) {
        BeamSolution checked = Objects.requireNonNull(solution, "solution");
        if (!checked.allowed()) {
            return this;
        }
        beams.add(new BeamGlyph(
                eventId,
                startXM,
                startYM,
                endXM,
                endYM,
                checked.deliveredBeamEnergyJ(),
                checked.effectiveSpotRadiusM()));
        return this;
    }

    /**
     * Projects shield/material/internal consequences from one authoritative kinetic protection result.
     *
     * @param eventId stable base event identity; sub-events use deterministic offsets
     * @param xM world impact x
     * @param yM world impact y
     * @param result authoritative composed protection result
     * @return this projection builder
     */
    public Stage175ITacticalVisualProjection addImpact(
            long eventId,
            double xM,
            double yM,
            KineticProtectionRuntime.Result result) {
        requirePositiveId(eventId, "eventId");
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        KineticProtectionRuntime.Result checked = Objects.requireNonNull(result, "result");
        if (checked.shieldInteraction() != null && checked.shieldInteraction().absorbedEnergyJ() > 0d) {
            impacts.add(new ImpactGlyph(
                    eventId * 10L + 1L,
                    ImpactKind.SHIELD,
                    xM,
                    yM,
                    checked.shieldInteraction().absorbedEnergyJ()));
        }
        if (checked.armorReached() && checked.armorEntryProjectile() != null) {
            impacts.add(new ImpactGlyph(
                    eventId * 10L + 2L,
                    ImpactKind.ARMOR,
                    xM,
                    yM,
                    checked.armorEntryProjectile().kineticEnergyJ()));
        }
        if (checked.damageEvent() != null && checked.damageEvent().compartmentDamageEnergyJ() > 0d) {
            impacts.add(new ImpactGlyph(
                    eventId * 10L + 3L,
                    ImpactKind.PENETRATION,
                    xM,
                    yM,
                    checked.damageEvent().compartmentDamageEnergyJ()));
        }
        return this;
    }

    /**
     * Freezes the current presentation projection.
     *
     * @return immutable deterministically sorted snapshot
     */
    public TacticalPrototypeVisualSnapshot snapshot() {
        return new TacticalPrototypeVisualSnapshot(ships, bodies, beams, shields, impacts, damage);
    }

    private void addShieldGlyph(
            long entityId,
            HullDefinition hull,
            double xM,
            double yM,
            double headingRad,
            ShieldFieldRuntime.Definition definition,
            ShieldFieldRuntime.State state) {
        if (definition == null || state == null) {
            return;
        }
        double effectiveCapacity = definition.reserveCapacityJ() * state.emitterIntegrity();
        double reserveFraction = effectiveCapacity <= 0d ? 0d : Math.min(1d, state.reserveJ() / effectiveCapacity);
        double radius = Math.max(hull.boundingDimensionsM().lengthM(), hull.boundingDimensionsM().widthM())
                * SHIELD_RADIUS_MULTIPLIER;
        shields.add(new ShieldGlyph(
                entityId,
                xM,
                yM,
                radius,
                headingRad + definition.coverageCenterRad(),
                definition.coverageHalfArcRad(),
                reserveFraction,
                state.collapsed()));
    }

    private void addDamageGlyphs(
            long entityId,
            HullDefinition hull,
            Snapshot snapshot,
            double xM,
            double yM,
            double headingRad) {
        double cos = Math.cos(headingRad);
        double sin = Math.sin(headingRad);
        for (CompartmentDefinition compartment : hull.compartments()) {
            double integrity = snapshot.compartmentIntegrityById().getOrDefault(compartment.id(), 1d);
            if (integrity >= 1d) {
                continue;
            }
            double localX = compartment.centerM().xM();
            double localY = compartment.centerM().yM();
            double worldX = xM + localX * cos - localY * sin;
            double worldY = yM + localX * sin + localY * cos;
            damage.add(new DamageGlyph(entityId, compartment.id(), worldX, worldY, 1d - integrity));
        }
    }

    private void addCosmeticWreckDebris(
            long entityId,
            HullDefinition hull,
            double xM,
            double yM,
            double headingRad) {
        double radius = Math.max(hull.boundingDimensionsM().lengthM(), hull.boundingDimensionsM().widthM()) * 0.7d;
        for (int index = 0; index < WRECK_DEBRIS_COUNT; index++) {
            double phase = deterministicPhase(entityId, index) + headingRad;
            double radialFraction = 0.35d + deterministicUnit(entityId, index + 31) * 0.65d;
            double marker = Math.max(4d, hull.boundingDimensionsM().widthM() * (0.035d + 0.025d * radialFraction));
            bodies.add(new BodyGlyph(
                    BodyKind.DEBRIS,
                    debrisId(entityId, index),
                    xM + Math.cos(phase) * radius * radialFraction,
                    yM + Math.sin(phase) * radius * radialFraction,
                    phase,
                    marker * 1.8d,
                    marker,
                    0d));
        }
    }

    private static double meanCompartmentIntegrity(HullDefinition hull, Snapshot snapshot) {
        if (hull.compartments().isEmpty()) {
            return 1d;
        }
        return hull.compartments().stream()
                .mapToDouble(value -> snapshot.compartmentIntegrityById().getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
    }

    private static double heading(double velocityX, double velocityY) {
        return Math.atan2(velocityY, velocityX);
    }

    private static long debrisId(long entityId, int index) {
        if (entityId > Long.MAX_VALUE / 100L) {
            return Math.max(1L, entityId - index - 1L);
        }
        return entityId * 100L + index + 1L;
    }

    private static double deterministicPhase(long entityId, int index) {
        return deterministicUnit(entityId, index) * Math.PI * 2d;
    }

    private static double deterministicUnit(long entityId, int salt) {
        long value = entityId ^ (0x9E3779B97F4A7C15L * (salt + 1L));
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        long positive = value & Long.MAX_VALUE;
        return positive / (double) Long.MAX_VALUE;
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
