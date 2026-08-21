package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound;
import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound.Status;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20Seed8WaterSharedProducerBoundDiagnosticsTest {
    private static final long ROOT_SEED = 8L;
    private static final String WATER = "commodity.feedstock.water_ice";

    @Test
    void measuresProducerAwareRelaxationAcrossSeed8WaterCapVectorsWithoutAcceptanceTarget() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        var probe = Stage20GeneratedWorldProductionProbe.run(ROOT_SEED, profile.inputs());
        var placement = probe.placement().orElseThrow();
        assertEquals(PlacementStatus.ACCEPTED, placement.status());

        int maximumShipsPerStart = capacity.requiredFreighterCountPerFactionStart();
        var topology = probe.topology().requireAcceptedTopology();
        var supply = probe.supplyThroughput().orElseThrow();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        var transport = profile.inputs().transport();
        var routes = Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(
                topology,
                probe.jumpEdges().orElseThrow(),
                probe.localLayouts().orElseThrow(),
                stations,
                transport,
                maximumShipsPerStart);
        CommodityRequirement water = profile.inputs().acceptance().bootstrapRequirements()
                .essentialCommodities().stream()
                .filter(value -> value.commodityId().equals(WATER))
                .findFirst()
                .orElseThrow();

        ArrayList<Assignment> assignments = new ArrayList<>(placement.assignments());
        assignments.sort(Comparator.comparing(Assignment::stableFactionId));
        assertEquals(2, assignments.size(), "seed-8 diagnostic currently measures the two-start corpus shape");

        TreeMap<String, Integer> minimumShips = new TreeMap<>();
        for (Assignment assignment : assignments) {
            var single = Stage20FreightPortfolioAllocator.allocate(
                    topology,
                    supply,
                    assignment.systemId(),
                    List.of(water),
                    maximumShipsPerStart,
                    routes::assessWithAllocatedFreighters);
            assertTrue(single.accepted(), "single-start water must fit the accepted physical fleet maximum");
            minimumShips.put(assignment.stableFactionId(), single.minimumRemoteFreightersRequired());
        }

        int totalVectors = 0;
        int provedInfeasible = 0;
        int possiblyFeasible = 0;
        ArrayList<Map<String, Integer>> possibleVectors = new ArrayList<>();
        String firstFaction = assignments.get(0).stableFactionId();
        String secondFaction = assignments.get(1).stableFactionId();
        boolean[][] possibleByCap = new boolean[maximumShipsPerStart + 1][maximumShipsPerStart + 1];

        for (int first = minimumShips.get(firstFaction); first <= maximumShipsPerStart; first++) {
            for (int second = minimumShips.get(secondFaction); second <= maximumShipsPerStart; second++) {
                LinkedHashMap<String, Integer> cap = new LinkedHashMap<>();
                cap.put(firstFaction, first);
                cap.put(secondFaction, second);
                var assessment = Stage20CommodityFrontierSharedProducerBound.assess(
                        topology,
                        placement,
                        supply,
                        water,
                        cap,
                        routes::assessWithAllocatedFreighters);
                totalVectors++;
                if (assessment.status() == Status.PROVED_INFEASIBLE) {
                    provedInfeasible++;
                } else {
                    possiblyFeasible++;
                    possibleByCap[first][second] = true;
                    possibleVectors.add(Map.copyOf(cap));
                }
            }
        }

        assertEquals(totalVectors, provedInfeasible + possiblyFeasible);
        for (int first = minimumShips.get(firstFaction); first <= maximumShipsPerStart; first++) {
            for (int second = minimumShips.get(secondFaction); second <= maximumShipsPerStart; second++) {
                if (!possibleByCap[first][second]) {
                    continue;
                }
                for (int largerFirst = first; largerFirst <= maximumShipsPerStart; largerFirst++) {
                    for (int largerSecond = second; largerSecond <= maximumShipsPerStart; largerSecond++) {
                        assertTrue(possibleByCap[largerFirst][largerSecond],
                                "optimistic cap feasibility must be monotone under larger fleet caps");
                    }
                }
            }
        }

        System.out.println("STAGE20E_SEED8_WATER_SHARED_PRODUCER_BOUND_BEGIN");
        System.out.println("boundVersion=" + Stage20CommodityFrontierSharedProducerBound.CURRENT_VERSION);
        System.out.println("rootSeed=" + ROOT_SEED);
        System.out.println("maximumShipsPerStart=" + maximumShipsPerStart);
        System.out.println("minimumShipsByFaction=" + minimumShips);
        System.out.println("capVectorCount=" + totalVectors);
        System.out.println("provedInfeasibleCapVectorCount=" + provedInfeasible);
        System.out.println("possiblyFeasibleCapVectorCount=" + possiblyFeasible);
        System.out.println("possiblyFeasibleCapVectors=" + possibleVectors);
        System.out.println("STAGE20E_SEED8_WATER_SHARED_PRODUCER_BOUND_END");
    }
}
