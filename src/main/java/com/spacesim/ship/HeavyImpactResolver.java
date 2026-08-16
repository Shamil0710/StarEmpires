package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.CalibrationDomainDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaterialDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionLayerDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionStackDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalog.HeavyImpactModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-17.5F resolver for high-energy kinetic impact against ordered material layers.
 *
 * <p>The resolver never extrapolates a response model outside its authored Stage-17.5A calibration
 * domain. The current response coefficients are deliberately synthetic demonstrator data; the
 * runtime contract is designed so Stage 22 can replace the dataset without changing impact routing.</p>
 */
public final class HeavyImpactResolver {
    private static final double MIN_COSINE = 0.05d;

    /** Stable terminal material-response outcome. */
    public enum Outcome {
        /** Projectile energy was consumed by the protection stack. */ STOPPED,
        /** Authored shallow-angle response deflected a residual physical projectile. */ RICOCHET,
        /** Residual projectile energy passed through the ordered protection stack. */ PERFORATED
    }

    private final ShipEngineeringCatalog engineering;
    private final ShipProtectionCatalog protection;

    /**
     * Creates a resolver over immutable engineering/protection content.
     *
     * @param engineering material/protection definitions
     * @param protection Stage-17.5F response coefficients
     */
    public HeavyImpactResolver(ShipEngineeringCatalog engineering, ShipProtectionCatalog protection) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    /**
     * Resolves one physical projectile against one ordered protection stack.
     *
     * @param projectile authoritative projectile body
     * @param protectionStackId protection stack content ID
     * @param incidenceAngleRad signed angle from the base layer normal; zero is normal impact
     * @return deterministic material response
     * @throws OutsideCalibrationDomainException when a referenced response surface does not cover the current residual impact
     */
    public ImpactResult resolve(
            ProjectileBody projectile,
            String protectionStackId,
            double incidenceAngleRad) {
        ProjectileBody checkedProjectile = Objects.requireNonNull(projectile, "projectile");
        if (protectionStackId == null || protectionStackId.isBlank()) {
            throw new IllegalArgumentException("protectionStackId must be non-blank");
        }
        if (!Double.isFinite(incidenceAngleRad)) {
            throw new IllegalArgumentException("incidenceAngleRad must be finite");
        }
        ProtectionStackDefinition stack = engineering.findProtectionStack(protectionStackId);
        if (stack == null) {
            throw new IllegalArgumentException("Unknown protection stack: " + protectionStackId);
        }

        double projectileAreaM2 = Math.PI * checkedProjectile.diameterM() * checkedProjectile.diameterM() / 4d;
        double residualEnergyJ = checkedProjectile.kineticEnergyJ();
        double totalAbsorbedJ = 0d;
        double totalSpallMassKg = 0d;
        double totalSpallEnergyJ = 0d;
        List<LayerInteraction> interactions = new ArrayList<>();

        int layerIndex = 0;
        for (ProtectionLayerDefinition layer : stack.layers()) {
            MaterialDefinition material = engineering.findMaterial(layer.materialId());
            if (material == null) {
                throw new IllegalStateException("Validated protection stack lost material: " + layer.materialId());
            }
            String responseId = layer.responseSurfaceId() != null
                    ? layer.responseSurfaceId() : material.heavyImpactResponseSurfaceId();
            if (responseId == null) {
                throw new IllegalArgumentException("Protection layer has no heavy-impact response surface: " + layerIndex);
            }
            HeavyImpactResponseSurfaceDefinition surface = engineering.findResponseSurface(responseId);
            HeavyImpactModel model = protection.findHeavyImpactModel(responseId);
            if (surface == null || model == null) {
                throw new IllegalArgumentException("Missing Stage-17.5F response model: " + responseId);
            }

            double residualSpeedMps = speedForEnergy(checkedProjectile.massKg(), residualEnergyJ);
            requireInside(surface, checkedProjectile.massKg(), residualSpeedMps);

            double relativeAngle = incidenceAngleRad - layer.orientationRad();
            double angleFromNormal = Math.acos(Math.abs(Math.cos(relativeAngle)));
            double cosine = Math.max(MIN_COSINE, Math.abs(Math.cos(relativeAngle)));
            double effectiveThicknessM = layer.thicknessM() / cosine;
            double encounteredMassKg = material.densityKgPerM3() * effectiveThicknessM * projectileAreaM2
                    * layer.coverageFraction();
            double capacityJ = encounteredMassKg * model.specificAbsorptionJPerKg();

            if (angleFromNormal >= model.ricochetCriticalAngleRad()) {
                double retainedJ = residualEnergyJ * model.ricochetRetainedEnergyFraction();
                double absorbedJ = residualEnergyJ - retainedJ;
                totalAbsorbedJ += absorbedJ;
                double spallMassKg = encounteredMassKg * model.spallMassFraction()
                        * Math.min(1d, absorbedJ / Math.max(capacityJ, 1e-12d));
                double spallEnergyJ = absorbedJ * model.spallEnergyFraction();
                totalSpallMassKg += spallMassKg;
                totalSpallEnergyJ += spallEnergyJ;
                interactions.add(new LayerInteraction(
                        layerIndex, material.id(), responseId, effectiveThicknessM, encounteredMassKg,
                        capacityJ, absorbedJ, retainedJ, spallMassKg, spallEnergyJ));
                ProjectileBody residualProjectile = ricochetBody(checkedProjectile, retainedJ, relativeAngle);
                return new ImpactResult(
                        protectionStackId,
                        checkedProjectile.kineticEnergyJ(),
                        totalAbsorbedJ,
                        retainedJ,
                        false,
                        Outcome.RICOCHET,
                        interactions,
                        new FragmentCloud(totalSpallMassKg, totalSpallEnergyJ, false),
                        residualProjectile,
                        0d);
            }

            double absorbedJ = Math.min(residualEnergyJ, capacityJ);
            residualEnergyJ -= absorbedJ;
            totalAbsorbedJ += absorbedJ;
            double spallMassKg = encounteredMassKg * model.spallMassFraction()
                    * (capacityJ > 0d ? absorbedJ / capacityJ : 0d);
            double spallEnergyJ = absorbedJ * model.spallEnergyFraction();
            totalSpallMassKg += spallMassKg;
            totalSpallEnergyJ += spallEnergyJ;
            interactions.add(new LayerInteraction(
                    layerIndex,
                    material.id(),
                    responseId,
                    effectiveThicknessM,
                    encounteredMassKg,
                    capacityJ,
                    absorbedJ,
                    residualEnergyJ,
                    spallMassKg,
                    spallEnergyJ));
            layerIndex++;
            if (residualEnergyJ <= 0d) {
                residualEnergyJ = 0d;
                break;
            }
        }

        boolean penetrated = residualEnergyJ > 0d;
        FragmentCloud fragments = new FragmentCloud(totalSpallMassKg, totalSpallEnergyJ, penetrated);
        double internalDamageEnergyJ = penetrated ? residualEnergyJ + totalSpallEnergyJ : 0d;
        ProjectileBody residualProjectile = penetrated
                ? scaledBody(checkedProjectile, residualEnergyJ, checkedProjectile.velocityXMps(), checkedProjectile.velocityYMps())
                : null;
        return new ImpactResult(
                protectionStackId,
                checkedProjectile.kineticEnergyJ(),
                totalAbsorbedJ,
                residualEnergyJ,
                penetrated,
                penetrated ? Outcome.PERFORATED : Outcome.STOPPED,
                interactions,
                fragments,
                residualProjectile,
                internalDamageEnergyJ);
    }

    private static void requireInside(
            HeavyImpactResponseSurfaceDefinition surface,
            double massKg,
            double velocityMps) {
        CalibrationDomainDefinition domain = surface.calibrationDomain();
        if (velocityMps < domain.minImpactVelocityMps() || velocityMps > domain.maxImpactVelocityMps()
                || massKg < domain.minProjectileMassKg() || massKg > domain.maxProjectileMassKg()) {
            throw new OutsideCalibrationDomainException(
                    surface.id(), velocityMps, massKg, domain);
        }
    }

    private static double speedForEnergy(double massKg, double energyJ) {
        if (!Double.isFinite(massKg) || massKg <= 0d || !Double.isFinite(energyJ) || energyJ < 0d) {
            throw new IllegalArgumentException("massKg must be positive and energyJ non-negative");
        }
        return Math.sqrt(2d * energyJ / massKg);
    }

    private static ProjectileBody ricochetBody(
            ProjectileBody body,
            double retainedEnergyJ,
            double signedIncidenceAngleRad) {
        double incomingDirection = Math.atan2(body.velocityYMps(), body.velocityXMps());
        double normalDirection = incomingDirection - signedIncidenceAngleRad;
        double reflectedDirection = 2d * normalDirection + Math.PI - incomingDirection;
        double speedMps = speedForEnergy(body.massKg(), retainedEnergyJ);
        return scaledBody(
                body,
                retainedEnergyJ,
                Math.cos(reflectedDirection) * speedMps,
                Math.sin(reflectedDirection) * speedMps);
    }

    private static ProjectileBody scaledBody(
            ProjectileBody body,
            double retainedEnergyJ,
            double directionXMps,
            double directionYMps) {
        double directionSpeed = Math.hypot(directionXMps, directionYMps);
        if (directionSpeed <= 0d) {
            throw new IllegalArgumentException("Residual projectile direction must be non-zero");
        }
        double targetSpeed = speedForEnergy(body.massKg(), retainedEnergyJ);
        double scale = targetSpeed / directionSpeed;
        return new ProjectileBody(
                body.projectileId(),
                body.sourceEntityId(),
                body.spawnTick(),
                body.materialId(),
                body.shape(),
                body.lengthM(),
                body.diameterM(),
                body.massKg(),
                body.xM(),
                body.yM(),
                directionXMps * scale,
                directionYMps * scale);
    }

    /** One ordered layer interaction. */
    public record LayerInteraction(
            int layerIndex,
            String materialId,
            String responseSurfaceId,
            double effectiveThicknessM,
            double encounteredMassKg,
            double absorptionCapacityJ,
            double absorbedEnergyJ,
            double residualEnergyJ,
            double spallMassKg,
            double spallEnergyJ) { }

    /**
     * Deterministic aggregate fragment/spall cloud emitted by protection interaction.
     *
     * @param massKg aggregate fragment mass
     * @param kineticEnergyJ aggregate fragment kinetic energy
     * @param internal whether this cloud is routed into ship-internal damage geometry
     */
    public record FragmentCloud(double massKg, double kineticEnergyJ, boolean internal) { }

    /**
     * Complete physical response of one stack to one projectile.
     *
     * @param protectionStackId ordered protection stack used for the response
     * @param incomingEnergyJ projectile energy entering material protection
     * @param absorbedEnergyJ energy absorbed by traversed/impacting layers
     * @param residualProjectileEnergyJ energy retained by residual or ricocheted projectile
     * @param penetrated whether the projectile perforated the stack
     * @param outcome terminal stop/ricochet/perforation outcome
     * @param layerInteractions ordered layer response diagnostics
     * @param fragments aggregate fragment/spall cloud
     * @param residualProjectile post-protection physical body for ricochet/perforation, or {@code null} when stopped
     * @param internalDamageEnergyJ energy routed into internal compartment damage
     */
    public record ImpactResult(
            String protectionStackId,
            double incomingEnergyJ,
            double absorbedEnergyJ,
            double residualProjectileEnergyJ,
            boolean penetrated,
            Outcome outcome,
            List<LayerInteraction> layerInteractions,
            FragmentCloud fragments,
            ProjectileBody residualProjectile,
            double internalDamageEnergyJ) {
        /**
         * Validates and freezes one complete impact result.
         *
         * @param protectionStackId ordered protection stack used for the response
         * @param incomingEnergyJ projectile energy entering material protection
         * @param absorbedEnergyJ energy absorbed by traversed/impacting layers
         * @param residualProjectileEnergyJ energy retained by residual or ricocheted projectile
         * @param penetrated whether the projectile perforated the stack
         * @param outcome terminal stop/ricochet/perforation outcome
         * @param layerInteractions ordered layer response diagnostics
         * @param fragments aggregate fragment/spall cloud
         * @param residualProjectile post-protection physical body for ricochet/perforation, or {@code null} when stopped
         * @param internalDamageEnergyJ energy routed into internal compartment damage
         */
        public ImpactResult {
            if (protectionStackId == null || protectionStackId.isBlank()) {
                throw new IllegalArgumentException("protectionStackId must be non-blank");
            }
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(layerInteractions, "layerInteractions");
            layerInteractions = List.copyOf(layerInteractions);
            Objects.requireNonNull(fragments, "fragments");
            if (outcome == Outcome.STOPPED && residualProjectile != null) {
                throw new IllegalArgumentException("Stopped impact cannot expose a residual projectile");
            }
            if (outcome != Outcome.STOPPED && residualProjectile == null) {
                throw new IllegalArgumentException("Ricochet/perforation requires a residual projectile");
            }
        }
    }

    /** Explicit failure used instead of silent response-surface extrapolation. */
    public static final class OutsideCalibrationDomainException extends IllegalArgumentException {
        /** Response surface that rejected the impact. */
        private final String responseSurfaceId;

        /**
         * Creates an out-of-domain diagnostic.
         *
         * @param responseSurfaceId rejected surface
         * @param velocityMps impact speed
         * @param massKg projectile mass
         * @param domain authored domain
         */
        public OutsideCalibrationDomainException(
                String responseSurfaceId,
                double velocityMps,
                double massKg,
                CalibrationDomainDefinition domain) {
            super("Impact outside calibration domain: surface=" + responseSurfaceId
                    + ",velocityMps=" + velocityMps + ",massKg=" + massKg + ",domain=" + domain);
            this.responseSurfaceId = responseSurfaceId;
        }

        /** @return rejected response surface ID */
        public String getResponseSurfaceId() {
            return responseSurfaceId;
        }
    }
}
