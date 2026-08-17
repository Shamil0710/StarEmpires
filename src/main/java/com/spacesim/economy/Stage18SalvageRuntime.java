package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-18H author of bounded physical salvage streams derived from constructed ship state.
 *
 * <p>This runtime never invents salvage mass. Hull material originates from the mass-closed Stage-18G
 * bare-hull bill and installed-module material originates from the mass-closed Stage-18D product
 * profile. Current integrity can only reduce accessible mass. The resulting pre-accounted streams are
 * then ordinary {@link SourceKind#SALVAGE_STREAM} inputs for the existing Stage-18B recovery method,
 * which applies its own additional process loss.</p>
 */
public final class Stage18SalvageRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ShipyardCatalog shipyards;
    private final Stage18ManufacturingCatalog manufacturing;
    private final Stage18ManufacturingProductRegistry products;

    /**
     * Creates bounded salvage derivation over authoritative construction/manufacturing catalogs.
     *
     * @param ontology Stage-18 resource ontology
     * @param shipyards Stage-18G physical hull construction catalog
     * @param manufacturing Stage-18D mass-composition catalog
     * @param products Stage-18D physical finished-product registry
     */
    public Stage18SalvageRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ShipyardCatalog shipyards,
            Stage18ManufacturingCatalog manufacturing,
            Stage18ManufacturingProductRegistry products) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.shipyards = Objects.requireNonNull(shipyards, "shipyards");
        this.manufacturing = Objects.requireNonNull(manufacturing, "manufacturing");
        this.products = Objects.requireNonNull(products, "products");
    }

    /**
     * One pre-accounted material stream derived from a destroyed constructed asset.
     *
     * @param streamId stable wreck-local stream identity
     * @param commodityId original Stage-18 constructed input commodity
     * @param constructedMassKg original mass of this commodity embodied in the asset
     * @param accessibleMassKg post-damage mass physically available to salvage before recovery losses
     * @param irrecoverableDamageLossKg constructed mass already lost to damage/destruction
     */
    public record SalvageStream(
            String streamId,
            String commodityId,
            double constructedMassKg,
            double accessibleMassKg,
            double irrecoverableDamageLossKg) {
        /**
         * Validates one bounded salvage stream.
         *
         * @param streamId stable stream identity
         * @param commodityId Stage-18 commodity identity
         * @param constructedMassKg original embodied mass
         * @param accessibleMassKg accessible pre-recovery mass
         * @param irrecoverableDamageLossKg mass destroyed before salvage
         */
        public SalvageStream {
            streamId = requireText(streamId, "streamId");
            commodityId = requireText(commodityId, "commodityId");
            requireNonNegative(constructedMassKg, "constructedMassKg");
            requireNonNegative(accessibleMassKg, "accessibleMassKg");
            requireNonNegative(irrecoverableDamageLossKg, "irrecoverableDamageLossKg");
            if (accessibleMassKg > constructedMassKg + EPSILON) {
                throw new IllegalArgumentException("accessible salvage mass exceeds constructed mass");
            }
            if (Math.abs(constructedMassKg - accessibleMassKg - irrecoverableDamageLossKg) > 1e-6d) {
                throw new IllegalArgumentException("salvage stream mass does not close");
            }
        }

        /**
         * Creates the Stage-18B finite salvage source consumed by the ordinary recovery method.
         *
         * @return finite pre-accounted salvage source, or {@code null} when no accessible mass remains
         */
        public PhysicalSourceState toPhysicalSource() {
            if (accessibleMassKg <= EPSILON) {
                return null;
            }
            return new PhysicalSourceState(
                    streamId,
                    SourceKind.SALVAGE_STREAM,
                    "salvage.stream.constructed_asset_material",
                    ExtractionEnvironment.SALVAGE_SITE,
                    commodityId,
                    accessibleMassKg,
                    accessibleMassKg,
                    1d,
                    1d,
                    java.util.Set.of("capability.process.recycling"));
        }
    }

    /**
     * Full deterministic salvage derivation for one ship wreck.
     *
     * @param wreckId stable wreck identity
     * @param hullId source hull ID
     * @param fit installed modules at destruction time
     * @param damage authoritative local damage/integrity snapshot at destruction time
     * @param streams material streams sorted by commodity ID
     * @param totalConstructedMassKg original hull plus installed-module material mass represented
     * @param totalAccessibleMassKg bounded pre-recovery salvage mass
     * @param totalIrrecoverableDamageLossKg mass already destroyed before recycling
     */
    public record WreckSalvage(
            String wreckId,
            String hullId,
            InstalledFit fit,
            Snapshot damage,
            List<SalvageStream> streams,
            double totalConstructedMassKg,
            double totalAccessibleMassKg,
            double totalIrrecoverableDamageLossKg) {
        /**
         * Freezes and validates one wreck salvage projection.
         *
         * @param wreckId wreck identity
         * @param hullId hull ID
         * @param fit installed fit
         * @param damage damage snapshot
         * @param streams bounded material streams
         * @param totalConstructedMassKg original represented mass
         * @param totalAccessibleMassKg accessible salvage mass
         * @param totalIrrecoverableDamageLossKg damage loss
         */
        public WreckSalvage {
            wreckId = requireText(wreckId, "wreckId");
            hullId = requireText(hullId, "hullId");
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(damage, "damage");
            List<SalvageStream> copy = new ArrayList<>(Objects.requireNonNull(streams, "streams"));
            copy.sort(Comparator.comparing(SalvageStream::commodityId));
            streams = List.copyOf(copy);
            requireNonNegative(totalConstructedMassKg, "totalConstructedMassKg");
            requireNonNegative(totalAccessibleMassKg, "totalAccessibleMassKg");
            requireNonNegative(totalIrrecoverableDamageLossKg, "totalIrrecoverableDamageLossKg");
            if (totalAccessibleMassKg > totalConstructedMassKg + EPSILON) {
                throw new IllegalArgumentException("wreck accessible salvage exceeds constructed mass");
            }
            if (Math.abs(totalConstructedMassKg - totalAccessibleMassKg - totalIrrecoverableDamageLossKg)
                    > 1e-5d) {
                throw new IllegalArgumentException("wreck salvage totals do not conserve mass");
            }
        }
    }

    /**
     * Derives bounded salvage from one actual fitted ship state.
     *
     * <p>Because the current hull catalog does not assign individual construction materials to
     * individual compartments, hull survival uses the arithmetic mean of authored compartment
     * integrity. This is an explicit conservative V1 approximation and never increases mass.
     * Installed modules use their own mount-local integrity.</p>
     *
     * @param wreckId stable wreck identity
     * @param fit installed fit at destruction time
     * @param damage physical damage snapshot
     * @return closed bounded salvage projection
     */
    public WreckSalvage deriveShipWreck(String wreckId, InstalledFit fit, Snapshot damage) {
        requireText(wreckId, "wreckId");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        Snapshot checkedDamage = Objects.requireNonNull(damage, "damage");
        HullPhysicalProfile hull = shipyards.findHullProfile(checkedFit.hullId());
        if (hull == null) {
            throw new IllegalArgumentException("No Stage-18G physical hull profile: " + checkedFit.hullId());
        }

        TreeMap<String, Double> constructed = new TreeMap<>();
        TreeMap<String, Double> accessible = new TreeMap<>();
        double hullIntegrity = meanCompartmentIntegrity(checkedDamage);
        for (PhysicalInputDefinition input : hull.buildInputsKg()) {
            addMass(constructed, input.commodityId(), input.massKg());
            addMass(accessible, input.commodityId(), input.massKg() * hullIntegrity);
        }

        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ProductDefinition product = products.findProduct(assignment.moduleId());
            ProductBindingDefinition binding = manufacturing.findProductBinding(assignment.moduleId());
            if (product == null || binding == null) {
                throw new IllegalArgumentException("No Stage-18D manufactured product profile: " + assignment.moduleId());
            }
            ProductProfileDefinition profile = manufacturing.findProductProfile(binding.profileId());
            if (profile == null) {
                throw new IllegalArgumentException("Unknown Stage-18D product profile: " + binding.profileId());
            }
            double integrity = checkedDamage.moduleDamage().moduleIntegrityByMount()
                    .getOrDefault(assignment.mountId(), 1d);
            requireFractionInclusive(integrity, "module integrity");
            for (ManufacturingInputDefinition input : profile.inputs()) {
                if (ontology.findCommodity(input.commodityId()) == null) {
                    throw new IllegalArgumentException("Unknown salvage composition commodity: " + input.commodityId());
                }
                double mass = product.unitMassKg() * input.fractionOfOutputMass();
                addMass(constructed, input.commodityId(), mass);
                addMass(accessible, input.commodityId(), mass * integrity);
            }
        }

        List<SalvageStream> streams = new ArrayList<>();
        double totalConstructed = 0d;
        double totalAccessible = 0d;
        for (Map.Entry<String, Double> entry : constructed.entrySet()) {
            double constructedMass = entry.getValue();
            double accessibleMass = Math.min(constructedMass, accessible.getOrDefault(entry.getKey(), 0d));
            double lost = Math.max(0d, constructedMass - accessibleMass);
            totalConstructed += constructedMass;
            totalAccessible += accessibleMass;
            streams.add(new SalvageStream(
                    wreckId + ".stream." + sanitize(entry.getKey()),
                    entry.getKey(),
                    constructedMass,
                    accessibleMass,
                    lost));
        }
        return new WreckSalvage(
                wreckId,
                checkedFit.hullId(),
                checkedFit,
                checkedDamage,
                streams,
                totalConstructed,
                totalAccessible,
                Math.max(0d, totalConstructed - totalAccessible));
    }

    private static double meanCompartmentIntegrity(Snapshot damage) {
        if (damage.compartmentIntegrityById().isEmpty()) {
            return 1d;
        }
        double sum = 0d;
        for (double integrity : damage.compartmentIntegrityById().values()) {
            requireFractionInclusive(integrity, "compartment integrity");
            sum += integrity;
        }
        return sum / damage.compartmentIntegrityById().size();
    }

    private static void addMass(Map<String, Double> target, String commodityId, double massKg) {
        if (massKg > EPSILON) {
            target.merge(commodityId, massKg, Double::sum);
        }
    }

    private static String sanitize(String id) {
        return id.replace('.', '_').replace('-', '_');
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFractionInclusive(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }
}
