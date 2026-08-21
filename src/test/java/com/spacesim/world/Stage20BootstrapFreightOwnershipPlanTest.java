package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.FactionFleetOwnership;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipSlot;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.SelectedCommodityPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapFreightOwnershipPlanTest {
    private static final String FACTION = "faction.alpha";
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final StarSystemId START = new StarSystemId(10L);
    private static final StarSystemId OTHER_START = new StarSystemId(11L);
    private static final StarSystemId WATER_SOURCE = new StarSystemId(20L);
    private static final StarSystemId ORE_SOURCE = new StarSystemId(30L);

    @Test
    void finiteOwnedPoolSeparatesSelectedCommitmentsFromReserveWithoutAllocatingFleetIds() {
        PlanReport physical = physicalPlan(
                3,
                5,
                List.of(
                        remoteCommodity(WATER, WATER_SOURCE, 2, 20d, 5),
                        remoteCommodity(ORE, ORE_SOURCE, 1, 12d, 5)));

        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(
                placement(START),
                physical);

        assertEquals(1L, ownership.rootSeed());
        assertEquals("profile.v1", ownership.placementProfileVersion());
        assertEquals(physical, ownership.physicalPlan());
        assertEquals(5, ownership.totalOwnedFreighters());
        assertEquals(3, ownership.totalCommittedFreighters());
        FactionFleetOwnership faction = ownership.factions().get(0);
        assertEquals(FACTION, faction.stableFactionId());
        assertEquals(START, faction.homeStartSystemId());
        assertEquals(5, faction.ownedFreighterCount());
        assertEquals(3, faction.committedFreighterCount());
        assertEquals(2, faction.reserveFreighterCount());
        assertEquals(List.of(ORE, WATER), faction.remoteCommitments().stream()
                .map(value -> value.commitmentKey().commodityId())
                .toList());
        assertTrue(faction.remoteCommitments().stream()
                .allMatch(value -> value.commitmentKey().consumerStartSystemId()
                        .equals(START)));
        assertEquals(List.of(0, 1, 2, 3, 4), faction.materializationSlots().stream()
                .map(OwnershipSlot::ownershipOrdinal)
                .toList());
        assertEquals(3L, faction.materializationSlots().stream()
                .filter(value -> value.commitment().isPresent())
                .count());
        assertEquals(2L, faction.materializationSlots().stream()
                .filter(value -> value.commitment().isEmpty())
                .count());
        assertTrue(faction.materializationSlots().stream()
                .flatMap(value -> value.commitment().stream())
                .allMatch(value -> value.commitmentKey().frontierVersion().equals("frontier.v1")
                        && value.commitmentKey().optionId().startsWith("option.")));
    }

    @Test
    void localPhysicalServiceConsumesNoRemoteOwnershipSlots() {
        PlanReport physical = physicalPlan(
                0,
                5,
                List.of(localCommodity(WATER, 5)));

        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(
                placement(START),
                physical);

        FactionFleetOwnership faction = ownership.factions().get(0);
        assertEquals(0, faction.committedFreighterCount());
        assertEquals(5, faction.reserveFreighterCount());
        assertTrue(faction.remoteCommitments().isEmpty());
        assertTrue(faction.materializationSlots().stream()
                .allMatch(value -> value.commitment().isEmpty()));
    }

    @Test
    void physicalPlanBudgetIsTheOnlyOwnershipCapacityAuthority() {
        PlanReport physical = physicalPlan(
                2,
                5,
                List.of(remoteCommodity(WATER, WATER_SOURCE, 2, 20d, 5)));

        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(
                placement(START),
                physical);

        assertEquals(Map.of(FACTION, 5), ownership.physicalPlan().remoteFreighterBudgetByFaction());
        assertEquals(5, ownership.factions().get(0).ownedFreighterCount());
    }

    @Test
    void selectedPhysicalStartMustEqualAcceptedFactionPlacement() {
        PlanReport physical = physicalPlan(
                2,
                5,
                List.of(remoteCommodityAtStart(WATER, WATER_SOURCE, OTHER_START, 2, 20d, 5)));

        assertThrows(IllegalArgumentException.class, () -> Stage20BootstrapFreightOwnershipPlan.plan(
                placement(START),
                physical));
    }

    @Test
    void selectedPhysicalPlanMustRetainTheAcceptedPlacementVersion() {
        SelectedCommodityPlan water = remoteCommodity(WATER, WATER_SOURCE, 2, 20d, 5);
        PlanReport mismatched = new PlanReport(
                Stage20BootstrapFreightPhysicalPlan.CURRENT_VERSION,
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                "placement.other",
                "supply.v1",
                100,
                Map.of(FACTION, 5),
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, 2),
                List.of(water));

        assertThrows(IllegalArgumentException.class, () -> Stage20BootstrapFreightOwnershipPlan.plan(
                placement(START),
                mismatched));
    }

    private static PlanReport physicalPlan(
            int remoteFreightersUsed,
            int remoteFreighterBudget,
            List<SelectedCommodityPlan> commodities) {
        return new PlanReport(
                Stage20BootstrapFreightPhysicalPlan.CURRENT_VERSION,
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                "supply.v1",
                100,
                Map.of(FACTION, remoteFreighterBudget),
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, remoteFreightersUsed),
                commodities);
    }

    private static PlacementResult placement(StarSystemId systemId) {
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "profile.v1",
                PlacementStatus.ACCEPTED,
                List.of(new Assignment(FACTION, systemId, 0d)),
                1,
                Optional.empty());
    }

    private static SelectedCommodityPlan remoteCommodity(
            String commodityId,
            StarSystemId producer,
            int ships,
            double delivered,
            int budget) {
        return remoteCommodityAtStart(commodityId, producer, START, ships, delivered, budget);
    }

    private static SelectedCommodityPlan remoteCommodityAtStart(
            String commodityId,
            StarSystemId producer,
            StarSystemId startSystem,
            int ships,
            double delivered,
            int budget) {
        RouteAssessment route = new RouteAssessment(List.of(producer, startSystem), 100d, delivered);
        SupplierCommitment commitment = new SupplierCommitment(
                commodityId,
                producer,
                false,
                ships,
                delivered,
                Optional.of(route));
        DemandPlan demand = new DemandPlan(
                commodityId,
                delivered,
                delivered,
                ships,
                List.of(commitment));
        StartPlan start = new StartPlan(FACTION, startSystem, budget, ships, List.of(demand));
        ProducerUsage usage = new ProducerUsage(
                new SupplyKey(commodityId, producer), delivered * 2d, delivered);
        return new SelectedCommodityPlan(
                commodityId,
                "frontier.v1",
                "option." + commodityId,
                Map.of(FACTION, ships),
                List.of(start),
                List.of(usage));
    }

    private static SelectedCommodityPlan localCommodity(String commodityId, int budget) {
        double delivered = 20d;
        SupplierCommitment commitment = new SupplierCommitment(
                commodityId,
                START,
                true,
                0,
                delivered,
                Optional.empty());
        DemandPlan demand = new DemandPlan(
                commodityId,
                delivered,
                delivered,
                0,
                List.of(commitment));
        StartPlan start = new StartPlan(FACTION, START, budget, 0, List.of(demand));
        ProducerUsage usage = new ProducerUsage(
                new SupplyKey(commodityId, START), delivered * 2d, delivered);
        return new SelectedCommodityPlan(
                commodityId,
                "frontier.v1",
                "option.local." + commodityId,
                Map.of(FACTION, 0),
                List.of(start),
                List.of(usage));
    }
}
