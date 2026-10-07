package com.spacesim.world;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;

class Stage23FreightRouteReportTest {
    @Test void paidCopyRetainsHistoricalDataWithoutClaimingAPersonalFlight() {
        var destination = new StaticObjectRef(new StarSystemId(2), StaticObjectKind.INFRASTRUCTURE, "industry.2");
        var report = new Stage23FreightRouteReport.Report("order:1", List.of(new StarSystemId(3), new StarSystemId(2)));
        var evidence = new DiscoveryEvidence(DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA,
                Stage23FreightRouteReport.sharedProvenance("npc:dispatcher", report), 12, OptionalDouble.empty());
        assertEquals(report, Stage23FreightRouteReport.decodeShared(destination, evidence).orElseThrow());
        assertTrue(Stage23FreightRouteReport.decode(destination, evidence).isEmpty());
        assertNotEquals(Stage23FreightRouteReport.offerKey(destination, "receipt"), Stage23FreightRouteReport.offerKey(
                new StaticObjectRef(new StarSystemId(2), StaticObjectKind.INFRASTRUCTURE, "other.2"), "receipt"));
    }

    @Test void reportFreezesCompletedPathAndBindsItsActualDestination() {
        var path = new ArrayList<>(List.of(new StarSystemId(3), new StarSystemId(8), new StarSystemId(2)));
        var report = new Stage23FreightRouteReport.Report("freight:actual|order:α", path);
        var evidence = new DiscoveryEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                Stage23FreightRouteReport.provenance(report), 12, OptionalDouble.empty());
        path.set(1, new StarSystemId(7));
        var destination = new StaticObjectRef(new StarSystemId(2), StaticObjectKind.INFRASTRUCTURE, "industry.2");
        assertEquals(report, Stage23FreightRouteReport.decode(destination, evidence).orElseThrow());
        assertEquals(new StarSystemId(8), report.systems().get(1));
        assertThrows(UnsupportedOperationException.class, () -> report.systems().clear());
        assertTrue(Stage23FreightRouteReport.decode(new StaticObjectRef(new StarSystemId(7),
                StaticObjectKind.INFRASTRUCTURE, "industry.7"), evidence).isEmpty());
    }

    @Test void countersPlainVisitsAndMalformedOrWrongSourceEvidenceAreNotRouteArchives() {
        var destination = new StaticObjectRef(new StarSystemId(2), StaticObjectKind.INFRASTRUCTURE, "industry.2");
        for (var value : List.of("deliveredMassKg:100", "personal-station-visit:7:industry.2", "delivered-route:v1:",
                "delivered-route:v1:b3JkZXI:3,02", "delivered-route:v1:b3JkZXI:3,3,2", "delivered-route:v1:b3JkZXI:3,0,2"))
            assertTrue(Stage23FreightRouteReport.decode(destination, new DiscoveryEvidence(
                    DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, value, 0, OptionalDouble.empty())).isEmpty());
        var valid = Stage23FreightRouteReport.provenance(new Stage23FreightRouteReport.Report("order",
                List.of(new StarSystemId(3), new StarSystemId(2))));
        assertTrue(Stage23FreightRouteReport.decode(destination, new DiscoveryEvidence(
                DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA, valid, 0, OptionalDouble.empty())).isEmpty());
    }
}
