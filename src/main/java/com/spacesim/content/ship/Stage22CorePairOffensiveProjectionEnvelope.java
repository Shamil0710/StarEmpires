package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;

import java.util.Objects;

/**
 * M22.6 operational envelope for the paired Stage-22 strategic destroyer+tanker+support packages.
 *
 * <p>This authority derives projection burden from the ordinary engineering catalog: fitted hull
 * mass, each ship's own reaction-mass load, the tanker's transferable reaction mass, repair stores
 * and the shared paid FTL module. It grants no faction-name range or tempo modifier. Translation
 * physics are therefore common while authored hull/support choices remain visible in the raw
 * burden.</p>
 *
 * <p>Route travel and combat sustainment are deliberately separate axes. FTL travel does not
 * silently consume tactical propellant; overextension instead occurs when the declared combat
 * refill demand exceeds the finite tanker stock. The actor projection contains only the route and
 * support demand actually known to that actor.</p>
 */
public final class Stage22CorePairOffensiveProjectionEnvelope {
    /** Version pinned by the M22.6 acceptance surface after wet-mass/runtime reconciliation. */
    public static final String VERSION = "stage22.core_pair_offensive_projection_envelope.v2";

    private static final String PROPELLANT_FEED = "propellant_feed";
    private static final String REPLENISHMENT_TRANSFER = "replenishment_transfer";
    private static final String REPAIR_STORES = "repair_stores";

    private Stage22CorePairOffensiveProjectionEnvelope() {
        throw new AssertionError("utility class");
    }

    /**
     * Evaluates one remote-projection package using only physical values present in the catalog.
     *
     * @param catalog completed paired Stage-22 engineering catalog
     * @param destroyerFitId strategic destroyer fit
     * @param tankerFitId strategic tanker fit
     * @param supportFitId strategic repair/support fit
     * @param actualRouteEdges authoritative FTL route length
     * @param actorKnownRouteEdges route prefix known to the observing actor
     * @param requiredCombatRefills authoritative full destroyer-refill demand at the objective
     * @param actorKnownCombatRefills refill demand known to the observing actor
     * @return deterministic authoritative and actor-bounded projection result
     */
    public static ProjectionResult evaluate(
            ShipEngineeringCatalog catalog,
            String destroyerFitId,
            String tankerFitId,
            String supportFitId,
            int actualRouteEdges,
            int actorKnownRouteEdges,
            int requiredCombatRefills,
            int actorKnownCombatRefills) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(catalog, "catalog");
        if (actualRouteEdges < 1) throw new IllegalArgumentException("actualRouteEdges must be positive");
        if (actorKnownRouteEdges < 0 || actorKnownRouteEdges > actualRouteEdges) {
            throw new IllegalArgumentException("actorKnownRouteEdges must be within the authoritative route");
        }
        if (requiredCombatRefills < 0) {
            throw new IllegalArgumentException("requiredCombatRefills must be non-negative");
        }
        if (actorKnownCombatRefills < 0 || actorKnownCombatRefills > requiredCombatRefills) {
            throw new IllegalArgumentException("actorKnownCombatRefills must be within authoritative demand");
        }

        FitBurden destroyer = burden(checked, destroyerFitId);
        FitBurden tanker = burden(checked, tankerFitId);
        FitBurden support = burden(checked, supportFitId);
        ModuleDefinition ftl = requireModule(checked, Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID);
        requireSharedFtl(destroyer, ftl.id());
        requireSharedFtl(tanker, ftl.id());
        requireSharedFtl(support, ftl.id());

        double destroyerPropellantKg = requireInterfaceCapacity(checked, destroyer.fit(), PROPELLANT_FEED);
        double tankerPropellantKg = requireInterfaceCapacity(checked, tanker.fit(), PROPELLANT_FEED);
        double supportPropellantKg = requireInterfaceCapacity(checked, support.fit(), PROPELLANT_FEED);
        double tankerReplenishmentKg = requireInterfaceCapacity(checked, tanker.fit(), REPLENISHMENT_TRANSFER);
        double repairStoresAmount = requireInterfaceCapacity(checked, support.fit(), REPAIR_STORES);
        int supportableCombatRefills = (int) Math.floor(tankerReplenishmentKg / destroyerPropellantKg);
        if (supportableCombatRefills < 1) {
            throw new IllegalStateException("Strategic tanker cannot replenish one destroyer propellant load");
        }

        double translatedMassLimitKg = requirePositive(ftl, "translated_mass_max_kg");
        double destroyerTranslatedMassKg = translatableMass(
                destroyer, translatedMassLimitKg, destroyerPropellantKg);
        double tankerTranslatedMassKg = translatableMass(
                tanker, translatedMassLimitKg, tankerPropellantKg + tankerReplenishmentKg);
        double supportTranslatedMassKg = translatableMass(
                support, translatedMassLimitKg, supportPropellantKg);

        double spoolSeconds = requirePositive(ftl, "spool_time_s");
        double transitSeconds = requirePositive(ftl, "edge_transit_time_s");
        double cooldownSeconds = requirePositive(ftl, "cooldown_s");
        double jumpEnergyJ = requirePositive(ftl, "jump_energy_j");
        double secondsPerEdge = spoolSeconds + transitSeconds + cooldownSeconds;
        double travelSeconds = secondsPerEdge * actualRouteEdges;
        double fleetJumpEnergyJ = jumpEnergyJ * actualRouteEdges * 3d;
        double fleetDryMassKg = destroyer.dryMassKg() + tanker.dryMassKg() + support.dryMassKg();
        double fleetOwnPropellantKg = destroyerPropellantKg + tankerPropellantKg + supportPropellantKg;
        double deployedMassKg = destroyerTranslatedMassKg + tankerTranslatedMassKg + supportTranslatedMassKg;
        double remainingReplenishmentKg = Math.max(
                0d, tankerReplenishmentKg - destroyerPropellantKg * requiredCombatRefills);
        boolean overextended = requiredCombatRefills > supportableCombatRefills;

        RouteAssessment assessment;
        if (actorKnownRouteEdges < actualRouteEdges || actorKnownCombatRefills < requiredCombatRefills) {
            assessment = RouteAssessment.UNKNOWN_BEYOND_KNOWN_OPERATION;
        } else if (overextended) {
            assessment = RouteAssessment.KNOWN_OVEREXTENDED;
        } else {
            assessment = RouteAssessment.KNOWN_SUPPORTED;
        }
        ActorProjection actorProjection = new ActorProjection(
                actorKnownRouteEdges,
                actorKnownCombatRefills,
                supportableCombatRefills,
                Math.max(0, supportableCombatRefills - actorKnownCombatRefills),
                assessment);

        return new ProjectionResult(
                actualRouteEdges,
                requiredCombatRefills,
                supportableCombatRefills,
                overextended,
                secondsPerEdge,
                travelSeconds,
                fleetJumpEnergyJ,
                fleetDryMassKg,
                fleetOwnPropellantKg,
                tankerReplenishmentKg,
                deployedMassKg,
                destroyerTranslatedMassKg,
                tankerTranslatedMassKg,
                supportTranslatedMassKg,
                destroyerPropellantKg,
                tankerPropellantKg,
                supportPropellantKg,
                remainingReplenishmentKg,
                repairStoresAmount,
                actorProjection);
    }

    private static FitBurden burden(ShipEngineeringCatalog catalog, String fitId) {
        DemonstratorFitDefinition fit = catalog.findDemonstratorFit(Objects.requireNonNull(fitId, "fitId"));
        if (fit == null) throw new IllegalArgumentException("Unknown strategic fit: " + fitId);
        HullDefinition hull = catalog.findHull(fit.hullId());
        if (hull == null) throw new IllegalStateException("Missing hull for fit: " + fitId);
        double dryMassKg = hull.bareHullMassKg();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            dryMassKg += requireModule(catalog, assignment.moduleId()).massKg();
        }
        if (!Double.isFinite(dryMassKg) || dryMassKg <= 0d || dryMassKg > hull.maxOperationalMassKg()) {
            throw new IllegalStateException("Invalid fitted operational mass: " + fitId);
        }
        return new FitBurden(fit, dryMassKg);
    }

    private static void requireSharedFtl(FitBurden burden, String ftlId) {
        long count = burden.fit().installedModules().stream()
                .filter(assignment -> ftlId.equals(assignment.moduleId()))
                .count();
        if (count != 1L) {
            throw new IllegalArgumentException(
                    "Projection fit must carry exactly one shared FTL module: " + burden.fit().id());
        }
    }

    private static double translatableMass(
            FitBurden burden, double translatedMassLimitKg, double reactionMassLoadKg) {
        double translatedMassKg = burden.dryMassKg() + reactionMassLoadKg;
        if (!Double.isFinite(translatedMassKg) || translatedMassKg > translatedMassLimitKg) {
            throw new IllegalStateException(
                    "Strategic fit exceeds FTL translated-mass envelope: " + burden.fit().id());
        }
        return translatedMassKg;
    }

    private static double requireInterfaceCapacity(
            ShipEngineeringCatalog catalog, DemonstratorFitDefinition fit, String interfaceId) {
        double capacity = 0d;
        int matches = 0;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(catalog, assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (!interfaceId.equals(definition.id())) continue;
                capacity += definition.capacity();
                matches++;
            }
        }
        if (matches != 1 || !Double.isFinite(capacity) || capacity <= 0d) {
            throw new IllegalStateException(
                    "Expected one finite positive " + interfaceId + " interface on " + fit.id());
        }
        return capacity;
    }

    private static ModuleDefinition requireModule(ShipEngineeringCatalog catalog, String moduleId) {
        ModuleDefinition module = catalog.findModule(moduleId);
        if (module == null) throw new IllegalStateException("Missing module: " + moduleId);
        return module;
    }

    private static double requirePositive(ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalStateException("Missing finite positive FTL parameter: " + key);
        }
        return value;
    }

    /** Actor-facing assessment without authoritative hidden route/support fields. */
    public enum RouteAssessment {
        KNOWN_SUPPORTED,
        KNOWN_OVEREXTENDED,
        UNKNOWN_BEYOND_KNOWN_OPERATION
    }

    /** Information available to one actor; authoritative route and demand totals are intentionally absent. */
    public record ActorProjection(
            int knownRouteEdges,
            int knownCombatRefills,
            int supportableCombatRefills,
            int knownSupportMarginRefills,
            RouteAssessment assessment) { }

    /** Authoritative deterministic projection outcome used by the M22.6 acceptance harness. */
    public record ProjectionResult(
            int actualRouteEdges,
            int requiredCombatRefills,
            int supportableCombatRefills,
            boolean overextended,
            double secondsPerEdge,
            double travelSeconds,
            double fleetJumpEnergyJ,
            double fleetDryMassKg,
            double fleetOwnPropellantKg,
            double tankerReplenishmentKg,
            double deployedMassKg,
            double destroyerTranslatedMassKg,
            double tankerTranslatedMassKg,
            double supportTranslatedMassKg,
            double destroyerPropellantKg,
            double tankerPropellantKg,
            double supportPropellantKg,
            double remainingReplenishmentKg,
            double repairStoresAmount,
            ActorProjection actorProjection) { }

    private record FitBurden(DemonstratorFitDefinition fit, double dryMassKg) { }
}
