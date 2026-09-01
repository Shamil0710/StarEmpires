package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.LicenseMode;
import com.spacesim.content.Stage22CoreContentSeamCatalog.LineageDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable M22.3 Empire industrial-program authoring evidence.
 *
 * <p>The program declares lineage, reserve intent, bottlenecks and an explicitly priced alternate
 * manufacturing profile. It owns no inventory, treasury, procurement order, factory state or
 * automatic substitution. All referenced commodities and physical production remain Stage-18
 * authority; procurement continues through the M22.1 common policy binding.</p>
 */
public final class Stage22EmpireIndustrialProgram {
    /** Alternate cargo-module profile used only when an explicit retool/substitution path is selected. */
    public static final String CARGO_SUBSTITUTION_PROFILE =
            "manufacturing.profile.empire_cargo_structural_substitution_v1";

    private Stage22EmpireIndustrialProgram() {
        throw new AssertionError("utility class");
    }

    /** @return three manufacturer/design/procurement lineages used by every Empire hull family */
    public static List<LineageDefinition> lineages() {
        return List.of(
                new LineageDefinition(
                        "lineage.empire.admiralty_yards",
                        "organization.empire.admiralty_design_bureau",
                        "organization.empire.admiralty_yards",
                        "policy.core.empire.procurement.v1",
                        LicenseMode.IN_HOUSE),
                new LineageDefinition(
                        "lineage.empire.crown_naval_works",
                        "organization.empire.crown_naval_design_office",
                        "organization.empire.crown_naval_works",
                        "policy.core.empire.procurement.v1",
                        LicenseMode.IN_HOUSE),
                new LineageDefinition(
                        "lineage.empire.state_transport_bureau",
                        "organization.empire.state_transport_design_bureau",
                        "organization.empire.state_transport_bureau",
                        "policy.core.empire.procurement.v1",
                        LicenseMode.IN_HOUSE));
    }

    /**
     * Strategic inputs whose shortage must be visible to ordinary manufacturing/repair flows.
     * Values are planning evidence only, never free inventory grants.
     *
     * @return immutable strategic bottleneck definitions
     */
    public static List<BottleneckDefinition> bottlenecks() {
        return List.of(
                new BottleneckDefinition(
                        "bottleneck.empire.precision_components",
                        "commodity.component.precision_components",
                        Set.of("sensor", "fire_control", "hangar", "repair_support"),
                        "Precision assemblies limit sensors, command electronics and complex mobile support; shortage increases retool pressure rather than applying a faction debuff."),
                new BottleneckDefinition(
                        "bottleneck.empire.refractory_alloy",
                        "commodity.material.refractory_alloy",
                        Set.of("reactor", "drive", "weapon", "repair"),
                        "High-temperature machinery and damage repair depend on finite refractory material throughput."),
                new BottleneckDefinition(
                        "bottleneck.empire.heavy_components",
                        "commodity.component.heavy_components",
                        Set.of("hull", "drive", "cargo", "repair_support"),
                        "Capital-heavy serviceable construction exposes a large heavy-component and yard-throughput dependency."));
    }

    /**
     * Reserve targets consumed as planning intent by existing procurement/freight authorities.
     * They do not materialize stock.
     *
     * @return immutable reserve-policy authoring definitions
     */
    public static List<ReservePolicyDefinition> reservePolicies() {
        return List.of(
                new ReservePolicyDefinition(
                        "reserve_policy.empire.spares",
                        "commodity.component.heavy_components",
                        30,
                        "policy.core.empire.procurement.v1",
                        "Maintain finite repair spares near protected yard nodes; fulfillment requires ordinary procurement and freight."),
                new ReservePolicyDefinition(
                        "reserve_policy.empire.precision",
                        "commodity.component.precision_components",
                        21,
                        "policy.core.empire.procurement.v1",
                        "Protect a smaller high-value precision reserve for sensors, command electronics and complex repair work."),
                new ReservePolicyDefinition(
                        "reserve_policy.empire.ordnance_inputs",
                        "commodity.material.refractory_alloy",
                        14,
                        "policy.core.empire.procurement.v1",
                        "Retain finite ordnance/weapon-repair input coverage without bypassing manufacturing or ammunition logistics."));
    }

    /**
     * Creates the reviewed priced alternate cargo/tank manufacturing profile.
     *
     * <p>The path conserves five percent of output mass worth of structural alloy by using additional
     * light alloy already present in the profile, at 25% more process energy and 35% more work.
     * This is deliberately worse than the standard route and therefore cannot be a free upgrade.</p>
     *
     * @return validated alternate cargo/tank manufacturing profile
     */
    public static ProductProfileDefinition cargoStructuralSubstitution() {
        ProductProfileDefinition base = Stage22CommonManufacturingProfiles.definitions().stream()
                .filter(value -> value.id().equals(Stage22CommonManufacturingProfiles.CARGO_TANK_STORES))
                .findFirst()
                .orElseThrow();
        return Stage22ManufacturingSubstitutionProfile.derive(
                base,
                CARGO_SUBSTITUTION_PROFILE,
                "commodity.material.structural_alloy",
                "commodity.material.light_alloy",
                0.05d,
                1.25d,
                1.35d);
    }

    /**
     * Validates all definitions against accepted Stage-18 ontology and the current Empire package.
     *
     * @return immutable validation evidence for the authored industrial program
     */
    public static ValidationReport validateDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        Stage22FactionProfileCatalog promotedProfiles = Stage22EmpireFactionProfileCatalog.loadDefault();

        Set<String> lineageIds = new TreeSet<>();
        for (LineageDefinition lineage : lineages()) {
            if (!lineageIds.add(lineage.id())) {
                throw new IllegalStateException("Duplicate Empire industrial lineage: " + lineage.id());
            }
            if (promotedProfiles.findPolicy(lineage.procurementPolicyRef()) == null) {
                throw new IllegalStateException("Empire lineage lacks common procurement policy: " + lineage.id());
            }
        }
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            if (!lineageIds.contains(family.lineageId())) {
                throw new IllegalStateException("Empire ship family lacks declared lineage: " + family.familyId());
            }
        }

        for (BottleneckDefinition bottleneck : bottlenecks()) {
            requireCommodity(ontology, bottleneck.commodityId(), bottleneck.id());
        }
        for (ReservePolicyDefinition reserve : reservePolicies()) {
            requireCommodity(ontology, reserve.commodityId(), reserve.id());
            if (promotedProfiles.findPolicy(reserve.procurementPolicyRef()) == null) {
                throw new IllegalStateException("Reserve intent lacks common procurement policy: " + reserve.id());
            }
        }

        ProductProfileDefinition alternate = cargoStructuralSubstitution();
        alternate.inputs().forEach(input -> requireCommodity(ontology, input.commodityId(), alternate.id()));
        ProductProfileDefinition standard = Stage22CommonManufacturingProfiles.definitions().stream()
                .filter(value -> value.id().equals(Stage22CommonManufacturingProfiles.CARGO_TANK_STORES))
                .findFirst().orElseThrow();
        if (alternate.energyJPerOutputKg() <= standard.energyJPerOutputKg()
                || alternate.workSecondsPerOutputKg() <= standard.workSecondsPerOutputKg()) {
            throw new IllegalStateException("Empire legal substitution must carry explicit positive process cost");
        }
        return new ValidationReport(
                lineages().size(), bottlenecks().size(), reservePolicies().size(),
                alternate.energyJPerOutputKg() / standard.energyJPerOutputKg(),
                alternate.workSecondsPerOutputKg() / standard.workSecondsPerOutputKg());
    }

    private static void requireCommodity(Stage18ResourceOntologyCatalog ontology, String id, String subject) {
        if (ontology.findCommodity(id) == null) {
            throw new IllegalStateException("Unknown Stage-18 commodity for " + subject + ": " + id);
        }
    }

    /** One physically grounded industrial dependency. */
    public record BottleneckDefinition(
            String id,
            String commodityId,
            Set<String> affectedFunctions,
            String semanticReason) {
        /** Validates one industrial bottleneck definition. */
        public BottleneckDefinition {
            id = requireText(id, "bottleneck id");
            commodityId = requireText(commodityId, "bottleneck commodityId");
            affectedFunctions = Set.copyOf(Objects.requireNonNull(affectedFunctions, "affectedFunctions"));
            if (affectedFunctions.isEmpty()) {
                throw new IllegalArgumentException("Bottleneck must affect at least one function");
            }
            semanticReason = requireText(semanticReason, "bottleneck semanticReason");
        }
    }

    /** One planning target expressed as days of finite ordinary commodity coverage. */
    public record ReservePolicyDefinition(
            String id,
            String commodityId,
            int targetCoverageDays,
            String procurementPolicyRef,
            String semanticIntent) {
        /** Validates one reserve-policy definition. */
        public ReservePolicyDefinition {
            id = requireText(id, "reserve id");
            commodityId = requireText(commodityId, "reserve commodityId");
            if (targetCoverageDays <= 0) {
                throw new IllegalArgumentException("Reserve targetCoverageDays must be positive");
            }
            procurementPolicyRef = requireText(procurementPolicyRef, "procurementPolicyRef");
            semanticIntent = requireText(semanticIntent, "reserve semanticIntent");
        }
    }

    /** Immutable diagnostic evidence for the authored industrial program. */
    public record ValidationReport(
            int lineageCount,
            int bottleneckCount,
            int reservePolicyCount,
            double substitutionEnergyMultiplier,
            double substitutionWorkMultiplier) { }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }
}
