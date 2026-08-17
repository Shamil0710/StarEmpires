package com.spacesim.ship;

import com.spacesim.content.ship.BeamMaterialResponseCatalog;
import com.spacesim.content.ship.BeamMaterialResponseCatalog.MaterialResponse;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaterialDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionLayerDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionStackDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.ship.BeamWeaponRuntime.BeamSolution;
import com.spacesim.ship.ShieldFieldRuntime.Interaction;
import com.spacesim.ship.ShipDamageRuntime.DamageEvent;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic beam → shield → material-ablation → compartment/subsystem damage resolver.
 *
 * <p>Beam geometry and delivered energy come from {@link BeamWeaponRuntime}. Material response is
 * authored per ordinary engineering material through {@link BeamMaterialResponseCatalog}; no global
 * beam-damage coefficient is hidden in this runtime. A layer must receive enough coupled energy to
 * ablate the illuminated mass before residual energy can continue inward. Energy that reaches the
 * interior is routed through the same {@link ShipDamageRuntime} topology used by kinetic penetration.</p>
 */
public final class BeamProtectionRuntime {
    private static final double EPSILON = 1e-9d;
    private final ShipEngineeringCatalog engineering;
    private final BeamMaterialResponseCatalog beamMaterials;
    private final ShieldFieldRuntime shields;
    private final ShipDamageRuntime damage;

    /**
     * Creates one production beam protection resolver.
     *
     * @param engineering ordinary engineering material/protection definitions
     * @param beamMaterials authored beam/material response definitions
     */
    public BeamProtectionRuntime(
            ShipEngineeringCatalog engineering,
            BeamMaterialResponseCatalog beamMaterials) {
        this(engineering, beamMaterials, new ShieldFieldRuntime(), new ShipDamageRuntime());
    }

    /**
     * Creates the resolver around explicit deterministic collaborators.
     *
     * @param engineering ordinary engineering material/protection definitions
     * @param beamMaterials authored beam/material response definitions
     * @param shields shield-field runtime
     * @param damage common local damage router
     */
    public BeamProtectionRuntime(
            ShipEngineeringCatalog engineering,
            BeamMaterialResponseCatalog beamMaterials,
            ShieldFieldRuntime shields,
            ShipDamageRuntime damage) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.beamMaterials = Objects.requireNonNull(beamMaterials, "beamMaterials");
        this.shields = Objects.requireNonNull(shields, "shields");
        this.damage = Objects.requireNonNull(damage, "damage");
    }

    /**
     * Resolves one allowed physical beam solution against the target's current protection state.
     *
     * @param beam allowed beam geometry/energy solution
     * @param shield optional fitted shield input, or {@code null}
     * @param threatDirectionRad hull-local incoming direction
     * @param protectionStackId material stack reached after the field
     * @param hull authoritative target hull
     * @param fit current target fit
     * @param layout target damage layout
     * @param snapshot current local damage snapshot
     * @param hitPointM hull-local beam aim point
     * @return deterministic protection/damage result
     */
    public Result resolve(
            BeamSolution beam,
            ShieldInput shield,
            double threatDirectionRad,
            String protectionStackId,
            HullDefinition hull,
            InstalledFit fit,
            HullDamageLayout layout,
            Snapshot snapshot,
            Vector3d hitPointM) {
        BeamSolution checkedBeam = Objects.requireNonNull(beam, "beam");
        if (!checkedBeam.allowed()) {
            throw new IllegalArgumentException("Cannot resolve rejected beam solution");
        }
        if (protectionStackId == null || protectionStackId.isBlank()) {
            throw new IllegalArgumentException("protectionStackId must be non-blank");
        }
        ProtectionStackDefinition stack = engineering.findProtectionStack(protectionStackId);
        if (stack == null) {
            throw new IllegalArgumentException("Unknown protection stack: " + protectionStackId);
        }
        double residualJ = checkedBeam.deliveredBeamEnergyJ();
        Interaction shieldInteraction = null;
        if (shield != null) {
            shieldInteraction = shields.interact(
                    shield.definition(),
                    shield.state(),
                    residualJ,
                    threatDirectionRad,
                    checkedBeam.dwellSeconds());
            residualJ = shieldInteraction.residualEnergyJ();
        }

        double spotAreaM2 = Math.PI * Math.max(1e-12d,
                checkedBeam.effectiveSpotRadiusM() * checkedBeam.effectiveSpotRadiusM());
        List<LayerInteraction> interactions = new ArrayList<>();
        MaterialResponse lastResponse = null;
        int index = 0;
        for (ProtectionLayerDefinition layer : stack.layers()) {
            if (residualJ <= EPSILON) {
                residualJ = 0d;
                break;
            }
            MaterialDefinition material = engineering.findMaterial(layer.materialId());
            if (material == null) {
                throw new IllegalStateException("Protection stack lost material: " + layer.materialId());
            }
            MaterialResponse response = beamMaterials.findByMaterialId(material.id());
            if (response == null) {
                throw new IllegalArgumentException("No beam response authored for material: " + material.id());
            }
            lastResponse = response;
            double cosine = Math.max(0.10d, Math.abs(Math.cos(layer.orientationRad())));
            double effectiveThicknessM = layer.thicknessM() / cosine;
            double illuminatedMassKg = material.densityKgPerM3()
                    * effectiveThicknessM
                    * spotAreaM2
                    * layer.coverageFraction();
            double ablationCapacityJ = illuminatedMassKg * response.ablationSpecificEnergyJPerKg();
            double incomingJ = residualJ;
            double coupledAvailableJ = incomingJ * response.absorptionFraction();
            boolean perforated = coupledAvailableJ + EPSILON >= ablationCapacityJ;
            double coupledUsedJ = Math.min(coupledAvailableJ, ablationCapacityJ);
            if (perforated) {
                double incidentSpentJ = ablationCapacityJ / response.absorptionFraction();
                residualJ = Math.max(0d, incomingJ - incidentSpentJ);
            } else {
                residualJ = 0d;
            }
            interactions.add(new LayerInteraction(
                    index++,
                    material.id(),
                    effectiveThicknessM,
                    illuminatedMassKg,
                    incomingJ,
                    coupledUsedJ,
                    ablationCapacityJ,
                    residualJ,
                    perforated));
            if (!perforated) {
                break;
            }
        }
        double internalEnergyJ = residualJ > 0d && lastResponse != null
                ? residualJ * lastResponse.internalResidualCouplingFraction()
                : 0d;
        DamageEvent damageEvent = internalEnergyJ > 0d
                ? damage.applyInternalEnergy(
                        Objects.requireNonNull(hull, "hull"),
                        Objects.requireNonNull(fit, "fit"),
                        Objects.requireNonNull(layout, "layout"),
                        Objects.requireNonNull(snapshot, "snapshot"),
                        internalEnergyJ,
                        Objects.requireNonNull(hitPointM, "hitPointM"))
                : null;
        return new Result(
                shieldInteraction,
                checkedBeam.deliveredBeamEnergyJ(),
                spotAreaM2,
                List.copyOf(interactions),
                residualJ,
                internalEnergyJ,
                damageEvent);
    }

    /**
     * Optional fitted shield input for a beam interaction.
     *
     * @param definition fitted shield definition
     * @param state current shield state
     */
    public record ShieldInput(ShieldFieldRuntime.Definition definition, ShieldFieldRuntime.State state) {
        /**
         * Validates one shield input.
         *
         * @param definition fitted shield definition
         * @param state current shield state
         */
        public ShieldInput {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * One ordered beam/material interaction.
     *
     * @param layerIndex protection-layer index
     * @param materialId ordinary material ID
     * @param effectiveThicknessM orientation-adjusted thickness
     * @param illuminatedMassKg material mass in the effective beam path
     * @param incomingEnergyJ beam energy entering the layer
     * @param coupledEnergyUsedJ energy coupled into ablation/removal
     * @param ablationCapacityJ coupled energy needed to clear the beam path
     * @param residualEnergyJ beam energy continuing inward
     * @param perforated whether this dwell cleared the illuminated path through the layer
     */
    public record LayerInteraction(
            int layerIndex,
            String materialId,
            double effectiveThicknessM,
            double illuminatedMassKg,
            double incomingEnergyJ,
            double coupledEnergyUsedJ,
            double ablationCapacityJ,
            double residualEnergyJ,
            boolean perforated) { }

    /**
     * Complete result of one beam/protection interaction.
     *
     * @param shieldInteraction optional shield result
     * @param incomingBeamEnergyJ delivered beam energy before protection
     * @param spotAreaM2 effective exposure area
     * @param layerInteractions ordered material interactions
     * @param residualBeamEnergyJ energy exiting the last material layer
     * @param internalDamageEnergyJ residual energy coupled into local internal damage
     * @param damageEvent local damage event, or {@code null} when material protection stopped the beam
     */
    public record Result(
            Interaction shieldInteraction,
            double incomingBeamEnergyJ,
            double spotAreaM2,
            List<LayerInteraction> layerInteractions,
            double residualBeamEnergyJ,
            double internalDamageEnergyJ,
            DamageEvent damageEvent) {
        /**
         * Validates and freezes one beam protection result.
         *
         * @param shieldInteraction optional shield result
         * @param incomingBeamEnergyJ delivered beam energy before protection
         * @param spotAreaM2 effective exposure area
         * @param layerInteractions ordered material interactions
         * @param residualBeamEnergyJ residual beam energy
         * @param internalDamageEnergyJ internal coupled energy
         * @param damageEvent optional local damage event
         */
        public Result {
            requireNonNegativeFinite(incomingBeamEnergyJ, "incomingBeamEnergyJ");
            requireNonNegativeFinite(spotAreaM2, "spotAreaM2");
            layerInteractions = List.copyOf(Objects.requireNonNull(layerInteractions, "layerInteractions"));
            requireNonNegativeFinite(residualBeamEnergyJ, "residualBeamEnergyJ");
            requireNonNegativeFinite(internalDamageEnergyJ, "internalDamageEnergyJ");
            if ((internalDamageEnergyJ > 0d) != (damageEvent != null)) {
                throw new IllegalArgumentException("internal damage energy and damage event must agree");
            }
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
