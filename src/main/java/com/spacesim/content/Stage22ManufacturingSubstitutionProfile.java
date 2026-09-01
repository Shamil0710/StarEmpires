package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reusable Stage-22 authoring seam for an explicitly priced alternate manufacturing profile.
 *
 * <p>This utility does not substitute inventory at runtime. It creates another ordinary immutable
 * Stage-18 product-profile definition from an accepted profile so a faction package may declare a
 * lawful contingency/retool route. Runtime work, inventory, facilities and manufacturing remain
 * owned by the Stage-18 authorities.</p>
 */
public final class Stage22ManufacturingSubstitutionProfile {
    private Stage22ManufacturingSubstitutionProfile() {
        throw new AssertionError("utility class");
    }

    /**
     * Replaces an exact mass fraction of one input with another while increasing work and energy.
     *
     * @param source accepted Stage-18 product profile
     * @param alternateProfileId stable ID for the alternate profile
     * @param constrainedCommodityId input being conserved
     * @param substituteCommodityId lawful replacement input
     * @param replacedMassFraction exact output-mass fraction moved to the replacement input
     * @param energyMultiplier strictly greater than one
     * @param workMultiplier strictly greater than one
     * @return immutable mass-closed ordinary Stage-18 product profile
     */
    public static ProductProfileDefinition derive(
            ProductProfileDefinition source,
            String alternateProfileId,
            String constrainedCommodityId,
            String substituteCommodityId,
            double replacedMassFraction,
            double energyMultiplier,
            double workMultiplier) {
        ProductProfileDefinition checked = Objects.requireNonNull(source, "source");
        requireText(alternateProfileId, "alternateProfileId");
        String constrained = requireText(constrainedCommodityId, "constrainedCommodityId");
        String substitute = requireText(substituteCommodityId, "substituteCommodityId");
        if (constrained.equals(substitute)) {
            throw new IllegalArgumentException("Substitute commodity must differ from constrained commodity");
        }
        if (!Double.isFinite(replacedMassFraction) || replacedMassFraction <= 0d || replacedMassFraction >= 1d) {
            throw new IllegalArgumentException("replacedMassFraction must be in (0,1)");
        }
        if (!Double.isFinite(energyMultiplier) || energyMultiplier <= 1d
                || !Double.isFinite(workMultiplier) || workMultiplier <= 1d) {
            throw new IllegalArgumentException("Substitution multipliers must be finite and greater than one");
        }

        List<ManufacturingInputDefinition> inputs = new ArrayList<>();
        boolean foundConstrained = false;
        boolean foundSubstitute = false;
        for (ManufacturingInputDefinition input : checked.inputs()) {
            double fraction = input.fractionOfOutputMass();
            if (input.commodityId().equals(constrained)) {
                if (fraction <= replacedMassFraction) {
                    throw new IllegalArgumentException("Replacement fraction must leave positive constrained input");
                }
                fraction -= replacedMassFraction;
                foundConstrained = true;
            } else if (input.commodityId().equals(substitute)) {
                fraction += replacedMassFraction;
                foundSubstitute = true;
            }
            inputs.add(new ManufacturingInputDefinition(input.commodityId(), fraction));
        }
        if (!foundConstrained || !foundSubstitute) {
            throw new IllegalArgumentException("Both constrained and substitute commodities must already exist in source profile");
        }
        return new ProductProfileDefinition(
                alternateProfileId,
                checked.displayName() + " (priced substitution)",
                inputs,
                checked.requiredCapabilityTags(),
                checked.energyJPerOutputKg() * energyMultiplier,
                checked.workSecondsPerOutputKg() * workMultiplier,
                checked.maintenanceWorkSecondsPerOutputKg() * workMultiplier);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }
}
