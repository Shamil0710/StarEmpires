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
     * @param incidenceAngleRad angle from layer normal; zero is normal impact
     * @return deterministic material response
     * @throws OutsideCalibrationDomainException when a referenced response surface does not cover the impact
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
            requireInside(surface, checkedProjectile);

            double relativeAngle = incidenceAngleRad - layer.orientationRad();
            double cosine = Math.max(MIN_COSINE, Math.abs(Math.cos(relativeAngle)));
            double effectiveThicknessM = layer.thicknessM() / cosine;
            double encounteredMassKg = material.densityKgPerM3() * effectiveThicknessM * projectileAreaM2
                    * layer.coverageFraction();
            double capacityJ = encounteredMassKg * model.specificAbsorptionJPerKg();
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

        FragmentCloud fragments = new FragmentCloud(totalSpallMassKg, totalSpallEnergyJ);
        double internalDamageEnergyJ = residualEnergyJ + totalSpallEnergyJ;
        return new ImpactResult(
                protectionStackId,
                checkedProjectile.kineticEnergyJ(),
                totalAbsorbedJ,
                residualEnergyJ,
                residualEnergyJ > 0d,
                interactions,
                fragments,
                internalDamageEnergyJ);
    }

    private static void requireInside(
            HeavyImpactResponseSurfaceDefinition surface,
            ProjectileBody projectile) {
        CalibrationDomainDefinition domain = surface.calibrationDomain();
        double velocity = projectile.speedMps();
        double mass = projectile.massKg();
        if (velocity < domain.minImpactVelocityMps() || velocity > domain.maxImpactVelocityMps()
                || mass < domain.minProjectileMassKg() || mass > domain.maxProjectileMassKg()) {
            throw new OutsideCalibrationDomainException(
                    surface.id(), velocity, mass, domain);
        }
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
     * Deterministic aggregate fragment/spall cloud emitted by the traversed protection layers.
     *
     * @param massKg aggregate fragment mass
     * @param kineticEnergyJ aggregate fragment kinetic energy
     */
    public record FragmentCloud(double massKg, double kineticEnergyJ) { }

    /** Complete physical response of one stack to one projectile. */
    public record ImpactResult(
            String protectionStackId,
            double incomingEnergyJ,
            double absorbedEnergyJ,
            double residualProjectileEnergyJ,
            boolean penetrated,
            List<LayerInteraction> layerInteractions,
            FragmentCloud fragments,
            double internalDamageEnergyJ) {
        /** Freezes ordered layer results. */
        public ImpactResult {
            Objects.requireNonNull(layerInteractions, "layerInteractions");
            layerInteractions = List.copyOf(layerInteractions);
            Objects.requireNonNull(fragments, "fragments");
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
