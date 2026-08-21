package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.SelectedCommodityPlan;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.SelectedOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierOption;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20ResolvedFreightAcceptance.AcceptanceReport;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage20BootstrapFreightPhysicalPlanTest {
    private static final String FACTION = "faction.alpha";
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final StarSystemId START = new StarSystemId(1L);
    private static final StarSystemId WATER_SOURCE = new StarSystemId(2L);
    private static final StarSystemId ORE_SOURCE = new StarSystemId(3L);

    @Test
    void acceptedCombinerSelectionRestoresRoutesProducerReservationsAndExactShipUsage() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        FrontierReport ore = frontier(ORE, "option.ore", ORE_SOURCE, 3, 12d);
        AcceptanceReport acceptance = accepted(List.of(water, ore));

        PlanReport plan = Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance);

        assertEquals(Status.ACCEPTED, acceptance.combination().status());
        assertEquals(Stage20ResolvedFreightAcceptance.CURRENT_VERSION, plan.acceptanceVersion());
        assertEquals(1L, plan.rootSeed());
        assertEquals("placement.v1", plan.placementVersion());
        assertEquals("supply.v1", plan.supplyProfileVersion());
        assertEquals(100, plan.searchNodeBudgetPerCommodity());
        assertEquals(Map.of(FACTION, 5), plan.remoteFreighterBudgetByFaction());
        assertEquals(Map.of(FACTION, 5), plan.remoteFreightersByFaction());
        assertEquals(List.of(ORE, WATER), plan.commodities().stream()
                .map(SelectedCommodityPlan::commodityId).toList());

        SelectedCommodityPlan selectedWater = plan.commodities().stream()
                .filter(value -> value.commodityId().equals(WATER))
                .findFirst().orElseThrow();
        SupplierCommitment commitment = selectedWater.starts().get(0).demands().get(0).commitments().get(0);
        assertEquals(WATER_SOURCE, commitment.producerSystemId());
        assertEquals(List.of(WATER_SOURCE, START), commitment.route().orElseThrow().orderedSystems());
        assertEquals(2, commitment.allocatedFreighters());
        assertEquals(20d, commitment.deliveredKgPerSecond(), 0d);
        assertEquals(new SupplyKey(WATER, WATER_SOURCE), selectedWater.producerUsage().get(0).supplyKey());
        assertEquals(20d, selectedWater.producerUsage().get(0).reservedKgPerSecond(), 0d);
    }

    @Test
    void inputFrontierOrderingDoesNotChangeReconstructedPlan() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        FrontierReport ore = frontier(ORE, "option.ore", ORE_SOURCE, 3, 12d);
        PlanReport forward = Stage20BootstrapFreightPhysicalPlan.reconstruct(accepted(List.of(water, ore)));
        PlanReport reversed = Stage20BootstrapFreightPhysicalPlan.reconstruct(accepted(List.of(ore, water)));

        assertEquals(forward, reversed);
    }

    @Test
    void selectedShipVectorMustStillMatchRichPhysicalOption() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        FrontierReport ore = frontier(ORE, "option.ore", ORE_SOURCE, 3, 12d);
        CombinationReport tampered = new CombinationReport(
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, 5),
                Status.ACCEPTED,
                Optional.empty(),
                Map.of(FACTION, 4),
                List.of(
                        new SelectedOption(WATER, water.version(), "option.water", Map.of(FACTION, 1)),
                        new SelectedOption(ORE, ore.version(), "option.ore", Map.of(FACTION, 3))));

        AcceptanceReport acceptance = acceptance(List.of(water, ore), tampered);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance));
    }

    @Test
    void nonAcceptedCombinationCannotBecomeAnOwnershipPhysicalPlan() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        CombinationReport infeasible = new CombinationReport(
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, 5),
                Status.INFEASIBLE,
                Optional.of(Stage20CommodityFreightFrontierCombiner.FailureReason.SHARED_FLEET_COMBINATION_INFEASIBLE),
                Map.of(),
                List.of());

        AcceptanceReport acceptance = acceptance(List.of(water), infeasible);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance));
    }

    @Test
    void selectedOptionMustExistInTheSuppliedRichFrontier() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        CombinationReport missing = new CombinationReport(
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, 5),
                Status.ACCEPTED,
                Optional.empty(),
                Map.of(FACTION, 2),
                List.of(new SelectedOption(
                        WATER,
                        water.version(),
                        "option.absent",
                        Map.of(FACTION, 2))));

        AcceptanceReport acceptance = acceptance(List.of(water), missing);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance));
    }

    @Test
    void reconstructedPlanCannotReplaceTheAcceptedFiniteFleetBudget() {
        FrontierReport water = frontier(WATER, "option.water", WATER_SOURCE, 2, 20d);
        PlanReport plan = Stage20BootstrapFreightPhysicalPlan.reconstruct(
                accepted(List.of(water)));

        assertThrows(IllegalArgumentException.class, () -> new PlanReport(
                plan.version(),
                plan.acceptanceVersion(),
                plan.rootSeed(),
                plan.placementVersion(),
                plan.supplyProfileVersion(),
                plan.searchNodeBudgetPerCommodity(),
                Map.of(FACTION, 6),
                plan.combinerVersion(),
                plan.remoteFreightersByFaction(),
                plan.commodities()));
    }

    private static AcceptanceReport accepted(List<FrontierReport> frontiers) {
        CombinationReport combination = Stage20CommodityFreightFrontierCombiner.combine(
                frontiers.stream().map(FrontierReport::toCombinerFrontier).toList(),
                Map.of(FACTION, 5));
        return acceptance(frontiers, combination);
    }

    private static AcceptanceReport acceptance(
            List<FrontierReport> frontiers,
            CombinationReport combination) {
        return new AcceptanceReport(
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                1L,
                "placement.v1",
                "supply.v1",
                100,
                Map.of(FACTION, 5),
                frontiers,
                combination);
    }

    private static FrontierReport frontier(
            String commodityId,
            String optionId,
            StarSystemId producer,
            int ships,
            double deliveredKgPerSecond) {
        RouteAssessment route = new RouteAssessment(
                List.of(producer, START),
                100d,
                deliveredKgPerSecond);
        SupplierCommitment commitment = new SupplierCommitment(
                commodityId,
                producer,
                false,
                ships,
                deliveredKgPerSecond,
                Optional.of(route));
        DemandPlan demand = new DemandPlan(
                commodityId,
                deliveredKgPerSecond,
                deliveredKgPerSecond,
                ships,
                List.of(commitment));
        StartPlan start = new StartPlan(
                FACTION,
                START,
                5,
                ships,
                List.of(demand));
        ProducerUsage producerUsage = new ProducerUsage(
                new SupplyKey(commodityId, producer),
                deliveredKgPerSecond * 2d,
                deliveredKgPerSecond);
        FrontierOption option = new FrontierOption(
                optionId,
                commodityId,
                Map.of(FACTION, ships),
                List.of(start),
                List.of(producerUsage));
        return new FrontierReport(
                "frontier." + commodityId,
                "placement.v1",
                "supply.v1",
                commodityId,
                100,
                1,
                FrontierStatus.COMPLETE,
                Map.of(FACTION, 5),
                List.of(option));
    }
}
