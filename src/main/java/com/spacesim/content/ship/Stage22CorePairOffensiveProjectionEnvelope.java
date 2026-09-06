package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;

import java.util.List;
import java.util.Objects;

/**
 * M22.6 operational envelope for the paired Stage-22 strategic destroyer+tanker+support packages.
 *
 * <p>This authority deliberately derives projection burden from the ordinary engineering catalog:
 * fitted hull mass, the shared paid FTL module, destroyer reaction-mass interfaces and tanker/support
 * stores. It does not grant faction-name range or tempo modifiers. The same translation physics is
 * therefore applied to both factions while authored hull/support choices remain visible in the
 * resulting burden.</p>
 *
 * <p>The actor view is intentionally narrower than the simulation result. Unknown route legs never
 * leak the authoritative route length through the actor-facing assessment.</p>
 */
public final class Stage22CorePairOffensiveProjectionEnvelope {
    /** Version pinned by the M22.6 acceptance surface. */
    public static final String VERSION = "stage22.core_pair_offensive_projection_envelope.v1";

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
     * @param actualRouteEdges authoritative route length in translation edges
     * @param actorKnownRouteEdges route prefix actually known to the observing actor
     * @return deterministic authoritative and actor-bounded projection result
     */
    public static ProjectionResult evaluate(
            ShipEngineeringCatalog catalog,
            String destroyerFitId,
            String tankerFitId,
            String supportFitId,
            int actualRouteEdges,
            int actorKnownRouteEdges) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(catalog, "catalog");
        if (actualRouteEdges < 1) throw new IllegalArgumentException("actualRouteEdges must be positive");
        if (actorKnownRouteEdges < 0 || actorKnownRouteEdges > actualRouteEdges) {
            throw new IllegalArgumentException("actorKnownRouteEdges must be within the authoritative route");
        }

        FitBurden destroyer = burden(checked, destroyerFitId);
        FitBurden tanker = burden(checked, tankerFitId);
        FitBurden support = burden(checked, supportFitId);
        ModuleDefinition ftl = requireModule(checked, Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID);
        requireSharedFtl(destroyer, ftl.id());
        requireSharedFtl(tanker, ftl.id());
        requireSharedFtl(support, ftl.id());

        double destroyerReactionMassKg = requireInterfaceCapacity(checked, destroyer.fit(), PROPELLANT_FEED);
        double tankerReactionMassKg = requireInterfaceCapacity(checked, tanker.fit(), REPLENISHMENT_TRANSFER);
        double repairStores = requireInterfaceCapacity(checked, support.fit(), REPAIR_STORES);
        int supportedCombatEdges = (int) Math.floor(tankerReactionMassKg / destroyerReactionMassKg);
        if (supportedCombatEdges < 1) {
            throw new IllegalStateException("Strategic tanker cannot replenish one destroyer reaction-mass load");
        }

        double translatedMassLimitKg = requirePositive(ftl, "translated_mass_max_kg");
        assertTranslatable(destroyer, translatedMassLimitKg, 0d);
        assertTranslatable(tanker, translatedMassLimitKg, tankerReactionMassKg);
        assertTranslatable(support, translatedMassLimitKg, 0d);

        double spoolSeconds = requirePositive(ftl, "spool_time_s");
        double transitSeconds = requirePositive(ftl, "edge_transit_time_s");
        double cooldownSeconds = requirePositive(ftl, "cooldown_s");
        double jumpEnergyJ = requirePositive(ftl, "jump_energy_j");
        double secondsPerEdge = spoolSeconds + transitSeconds + cooldownSeconds;
        double travelSeconds = secondsPerEdge * actualRouteEdges;
        double fleetJumpEnergyJ = jumpEnergyJ * actualRouteEdges * 3d;
        double fleetDryMassKg = destroyer.dryMassKg() + tanker.dryMassKg() + support.dryMassKg();
        double deployedMassKg = fleetDryMassKg + tankerReactionMassKg;
        double remainingReactionMassKg = Math.max(
                0d, tankerReactionMassKg - destroyerReactionMassKg * actualRouteEdges);
        boolean overextended = actualRouteEdges > supportedCombatEdges;

        RouteAssessment assessment;
        if (actorKnownRouteEdges < actualRouteEdges) {
            assessment = RouteAssessment.UNKNOWN_BEYOND_KNOWN_ROUTE;
        } else if (overextended) {
            assessment = RouteAssessment.KNOWN_OVEREXTENDED;
        } else {
            assessment = RouteAssessment.KNOWN_SUPPORTED;
        }
        ActorProjection actorProjection = new ActorProjection(
                actorKnownRouteEdges,
                supportedCombatEdges,
                Math.max(0, supportedCombatEdges - actorKnownRouteEdges),
                assessment);

        return new ProjectionResult(
                actualRouteEdges,
                supportedCombatEdges,
                overextended,
                secondsPerEdge,
                travelSeconds,
                fleetJumpEnergyJ,
                fleetDryMassKg,
                deployedMassKg,
                destroyerReactionMassKg,
                tankerReactionMassKg,
                remainingReactionMassKg,
                repairStores,
                actorProjection);
    }

    private static FitBurden burden(ShipEngineeringCatalog catalog, String fitId) {
        DemonstratorFitDefinition fit = catalog.findDemonstratorFit(Objects.requireNonNull(fitId, "fitId"));
        if (fit == null) throw new IllegalArgumentException("Unknown strategic fit: " + fitId);
        HullDefinition hull = catalog.findHull(fit.hullId());
        if (hull == null) throw new IllegalStateException("Missing hull for fit: " + fitId);
        double dryMassKg = hull.bareHullMassKg();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(catalog, assignment.moduleId());
            dryMassKg += module.massKg();
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
            throw new IllegalArgumentException("Projection fit must carry exactly one shared FTL module: " + burden.fit().id());
        }
    }

    private static void assertTranslatable(FitBurden burden, double translatedMassLimitKg, double consumableLoadKg) {
        double translatedMassKg = burden.dryMassKg() + consumableLoadKg;
        if (!Double.isFinite(translatedMassKg) || translatedMassKg > translatedMassLimitKg) {
            throw new IllegalStateException("Strategic fit exceeds FTL translated-mass envelope: " + burden.fit().id());
        }
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
            throw new IllegalStateException("Expected one finite positive " + interfaceId + " interface on " + fit.id());
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

    /** Actor-facing route assessment without authoritative hidden-route fields. */
    public enum RouteAssessment {
        KNOWN_SUPPORTED,
        KNOWN_OVEREXTENDED,
        UNKNOWN_BEYOND_KNOWN_ROUTE
    }

    /** Information available to one actor; authoritative route length is intentionally absent. */
    public record ActorProjection(
            int knownRouteEdges,
            int supportableCombatEdges,
            int knownSupportMarginEdges,
            RouteAssessment assessment) { }

    /** Authoritative deterministic projection outcome used by the M22.6 acceptance harness. */
    public record ProjectionResult(
            int actualRouteEdges,
            int supportableCombatEdges,
            boolean overextended,
            double secondsPerEdge,
            double travelSeconds,
            double fleetJumpEnergyJ,
            double fleetDryMassKg,
            double deployedMassKg,
            double destroyerReactionMassPerSupportedEdgeKg,
            double tankerReactionMassKg,
            double remainingReactionMassKg,
            double repairStores,
            ActorProjection actorProjection) { }

    private record FitBurden(DemonstratorFitDefinition fit, double dryMassKg) { }
}
