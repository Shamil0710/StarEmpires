package com.spacesim.content.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairOffensiveProjectionEnvelopeTest {
    private static final int REMOTE_ROUTE_EDGES = 8;

    @Test
    void bothFactionsUseTheSamePaidFtlPhysicsWhileAuthoredSupportBurdenRemainsVisible() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();

        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult empire = empire(catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult union = union(catalog, REMOTE_ROUTE_EDGES, REMOTE_ROUTE_EDGES);

        assertEquals(empire.secondsPerEdge(), union.secondsPerEdge());
        assertEquals(empire.travelSeconds(), union.travelSeconds());
        assertEquals(empire.fleetJumpEnergyJ(), union.fleetJumpEnergyJ());
        assertTrue(empire.fleetDryMassKg() > 0d);
        assertTrue(union.fleetDryMassKg() > 0d);
        assertNotEquals(empire.fleetDryMassKg(), union.fleetDryMassKg());
        assertNotEquals(empire.tankerReactionMassKg(), union.tankerReactionMassKg());
        assertTrue(empire.repairStores() > 0d);
        assertTrue(union.repairStores() > 0d);
        assertTrue(empire.deployedMassKg() > empire.fleetDryMassKg());
        assertTrue(union.deployedMassKg() > union.fleetDryMassKg());
    }

    @Test
    void finiteTankerStoresCreateARealOverextensionBoundary() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult baseline = empire(catalog, 1, 1);
        int envelope = baseline.supportableCombatEdges();

        assertTrue(envelope > 1);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult atLimit = empire(catalog, envelope, envelope);
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult beyond = empire(catalog, envelope + 1, envelope + 1);

        assertTrue(!atLimit.overextended());
        assertTrue(atLimit.remainingReactionMassKg() >= 0d);
        assertTrue(atLimit.remainingReactionMassKg() < atLimit.destroyerReactionMassPerSupportedEdgeKg());
        assertTrue(beyond.overextended());
        assertEquals(Stage22CorePairOffensiveProjectionEnvelope.RouteAssessment.KNOWN_OVEREXTENDED,
                beyond.actorProjection().assessment());
    }

    @Test
    void actorKnowledgeDoesNotRevealUnknownRemoteRouteLegs() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult hidden = empire(catalog, REMOTE_ROUTE_EDGES, 2);

        assertEquals(2, hidden.actorProjection().knownRouteEdges());
        assertEquals(Stage22CorePairOffensiveProjectionEnvelope.RouteAssessment.UNKNOWN_BEYOND_KNOWN_ROUTE,
                hidden.actorProjection().assessment());
        assertEquals(hidden.actorProjection().supportableCombatEdges() - 2,
                hidden.actorProjection().knownSupportMarginEdges());
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
                1));
    }

    private static Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult empire(
            ShipEngineeringCatalog catalog, int actualEdges, int knownEdges) {
        return Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                catalog,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT,
                actualEdges,
                knownEdges);
    }

    private static Stage22CorePairOffensiveProjectionEnvelope.ProjectionResult union(
            ShipEngineeringCatalog catalog, int actualEdges, int knownEdges) {
        return Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                catalog,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT,
                actualEdges,
                knownEdges);
    }
}
