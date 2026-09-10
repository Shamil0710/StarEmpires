package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairOffensiveProjectionEnvelopeTest {
    private static final int REMOTE_ROUTE_EDGES = 8;
    private static final int COMBAT_REFILLS = 4;

    @Test
    void bothFactionsUseTheSamePaidFtlPhysicsWhileAuthoredSupportBurdenRemainsVisible() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();

        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult empire = empire(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, COMBAT_REFILLS, COMBAT_REFILLS);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult union = union(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, COMBAT_REFILLS, COMBAT_REFILLS);

        assertEquals(empire.secondsPerEdge(), union.secondsPerEdge());
        assertEquals(empire.travelSeconds(), union.travelSeconds());
        assertEquals(empire.fleetJumpEnergyJ(), union.fleetJumpEnergyJ());
        assertTrue(empire.fleetDryMassKg() > 0d);
        assertTrue(union.fleetDryMassKg() > 0d);
        assertTrue(empire.fleetOwnPropellantKg() > 0d);
        assertTrue(union.fleetOwnPropellantKg() > 0d);
        assertNotEquals(empire.fleetDryMassKg(), union.fleetDryMassKg());
        assertNotEquals(empire.tankerReplenishmentKg(), union.tankerReplenishmentKg());
        assertTrue(empire.repairStoresAmount() > 0d);
        assertTrue(union.repairStoresAmount() > 0d);
        assertTrue(empire.deployedMassKg() > empire.fleetDryMassKg());
        assertTrue(union.deployedMassKg() > union.fleetDryMassKg());
    }

    @Test
    void wetTranslatedMassAndJumpCostsMatchTheExistingStage21RuntimeAuthority() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult empire = empire(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, COMBAT_REFILLS, COMBAT_REFILLS);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult union = union(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, COMBAT_REFILLS, COMBAT_REFILLS);

        assertRuntimePackage(catalog, empire,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT);
        assertRuntimePackage(catalog, union,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT);
    }

    @Test
    void finiteTankerStoresCreateARealCombatSustainmentBoundaryIndependentFromFtlDistance() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult baseline = empire(catalog, 2, 2, 1, 1);
        int envelope = baseline.supportableCombatRefills();

        assertTrue(envelope > 1);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult atLimit = empire(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, envelope, envelope);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult beyond = empire(
                catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES, envelope + 1, envelope + 1);

        assertTrue(!atLimit.overextended());
        assertTrue(atLimit.remainingReplenishmentKg() >= 0d);
        assertTrue(atLimit.remainingReplenishmentKg() < atLimit.destroyerPropellantKg());
        assertTrue(beyond.overextended());
        assertEquals(Stage22CorePairOffensiveProjectionEnvelope.RouteAssessment.KNOWN_OVEREXTENDED,
                beyond.actorProjection().assessment());
        assertEquals(atLimit.travelSeconds(), beyond.travelSeconds(),
                "tactical sustainment demand must not silently alter FTL travel physics");
    }

    @Test
    void actorKnowledgeDoesNotRevealUnknownRouteOrSupportDemand() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult hidden = empire(
                catalog, REMOTE_ROUTE_EDGES, 2, COMBAT_REFILLS, 1);

        assertEquals(2, hidden.actorProjection().knownRouteEdges());
        assertEquals(1, hidden.actorProjection().knownCombatRefills());
        assertEquals(Stage22CorePairOffensiveProjectionEnvelope.RouteAssessment.UNKNOWN_BEYOND_KNOWN_OPERATION,
                hidden.actorProjection().assessment());
        assertEquals(hidden.actorProjection().supportableCombatRefills() - 1,
                hidden.actorProjection().knownSupportMarginRefills());
    }

    @Test
    void ordinaryNonFtlCombatFitCannotEnterTheProjectionEnvelope() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                catalog,
                "fit.empire.destroyer.screen_v1",
                Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT,
                1,
                1,
                1,
                1));
    }

    private static void assertRuntimePackage(
            ShipEngineeringCatalog catalog,
            Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult expected,
            String destroyerFitId,
            String tankerFitId,
            String supportFitId) {
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        JumpPlan destroyer = planAndCommit(runtime, catalog, destroyerFitId);
        JumpPlan tanker = planAndCommit(runtime, catalog, tankerFitId);
        JumpPlan support = planAndCommit(runtime, catalog, supportFitId);

        assertEquals(expected.destroyerTranslatedMassKg(), destroyer.translatedMassKg(), 1e-6d);
        assertEquals(expected.tankerTranslatedMassKg(), tanker.translatedMassKg(), 1e-6d);
        assertEquals(expected.supportTranslatedMassKg(), support.translatedMassKg(), 1e-6d);
        assertEquals(expected.deployedMassKg(),
                destroyer.translatedMassKg() + tanker.translatedMassKg() + support.translatedMassKg(), 1e-6d);
        assertEquals(expected.secondsPerEdge(),
                destroyer.spoolSeconds() + destroyer.edgeTransitSeconds() + destroyer.cooldownSeconds(), 0d);
        assertEquals(expected.fleetJumpEnergyJ(),
                (destroyer.requiredEnergyJ() + tanker.requiredEnergyJ() + support.requiredEnergyJ())
                        * REMOTE_ROUTE_EDGES,
                1e-6d);
    }

    private static JumpPlan planAndCommit(
            ShipEngineeringRuntime runtime, ShipEngineeringCatalog catalog, String fitId) {
        DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        ConsumableState consumables = fullReactionMass(catalog, definition);
        var state = runtime.initialize(fit, consumables, DamageState.pristine());
        JumpPlan plan = runtime.planJump(fit, state, DamageState.pristine());
        assertTrue(plan.allowed(), () -> fitId + " jump rejected: " + plan.failure());
        var committed = runtime.commitJump(state, plan);
        assertEquals(plan.cooldownSeconds(), committed.ftlCooldownSecondsByMount().get(plan.mountId()), 0d);
        assertEquals(state.localHeatJByMount().getOrDefault(plan.mountId(), 0d) + plan.jumpHeatJ(),
                committed.localHeatJByMount().get(plan.mountId()), 1e-6d);
        return plan;
    }

    private static ConsumableState fullReactionMass(
            ShipEngineeringCatalog catalog, DemonstratorFitDefinition fit) {
        List<ConsumableLoad> loads = new ArrayList<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() != InterfaceKind.REACTION_MASS) continue;
                loads.add(new ConsumableLoad(
                        assignment.mountId(),
                        definition.id(),
                        InterfaceKind.REACTION_MASS,
                        definition.capacity(),
                        definition.capacity(),
                        0L));
            }
        }
        return new ConsumableState(0d, 0d, 0d, 0d, loads);
    }

    private static Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult empire(
            ShipEngineeringCatalog catalog,
            int actualEdges,
            int knownEdges,
            int requiredRefills,
            int knownRefills) {
        return Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                catalog,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT,
                actualEdges,
                knownEdges,
                requiredRefills,
                knownRefills);
    }

    private static Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult union(
            ShipEngineeringCatalog catalog,
            int actualEdges,
            int knownEdges,
            int requiredRefills,
            int knownRefills) {
        return Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                catalog,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT,
                actualEdges,
                knownEdges,
                requiredRefills,
                knownRefills);
    }
}
