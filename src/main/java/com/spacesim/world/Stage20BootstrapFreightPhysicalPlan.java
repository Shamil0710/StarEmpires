package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.SelectedOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierOption;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20ResolvedFreightAcceptance.AcceptanceReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reconstructs the rich physical Stage-20E freight plan selected by the exact commodity combiner.
 *
 * <p>The exact combiner intentionally carries only per-start ship-count vectors. Before bootstrap
 * freight can receive persistent ownership or materialized fleet identities, the selected option IDs
 * must be joined back to the rich per-commodity frontier evidence containing producer reservations,
 * demand plans, explicit routes and delivered throughput. This class performs only that deterministic
 * identity join; it creates no ships, cargo, inventory, money, producer capacity or transport.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20BootstrapFreightPhysicalPlan {
    /** Stable selected-physical-plan reconstruction version. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-freight-physical-plan.v1";

    private Stage20BootstrapFreightPhysicalPlan() {
        throw new AssertionError("No instances");
    }

    /**
     * One combiner-selected commodity option with its original physical commitments restored.
     *
     * @param commodityId authoritative Stage-18 commodity identifier
     * @param frontierVersion exact source frontier version selected by the combiner
     * @param optionId stable selected option identifier
     * @param remoteFreightersByFaction exact per-start ship-count vector
     * @param starts complete physical service plans for every placed start
     * @param producerUsage authoritative producer-capacity reservations for this commodity
     */
    public record SelectedCommodityPlan(
            String commodityId,
            String frontierVersion,
            String optionId,
            Map<String, Integer> remoteFreightersByFaction,
            List<StartPlan> starts,
            List<ProducerUsage> producerUsage) {
        /**
         * Validates and canonicalizes one selected rich commodity plan.
         *
         * @param commodityId authoritative Stage-18 commodity identifier
         * @param frontierVersion exact source frontier version selected by the combiner
         * @param optionId stable selected option identifier
         * @param remoteFreightersByFaction exact per-start ship-count vector
         * @param starts complete physical service plans for every placed start
         * @param producerUsage authoritative producer-capacity reservations for this commodity
         */
        public SelectedCommodityPlan {
            commodityId = requireText(commodityId, "commodityId");
            frontierVersion = requireText(frontierVersion, "frontierVersion");
            optionId = requireText(optionId, "optionId");
            remoteFreightersByFaction = canonicalFreighterMap(
                    remoteFreightersByFaction, "remoteFreightersByFaction");

            ArrayList<StartPlan> startCopy = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
            ArrayList<ProducerUsage> producerCopy = new ArrayList<>(
                    Objects.requireNonNull(producerUsage, "producerUsage"));
            if (startCopy.isEmpty() || producerCopy.isEmpty()
                    || startCopy.stream().anyMatch(Objects::isNull)
                    || producerCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selected physical plan requires non-empty start/producer evidence");
            }
            startCopy.sort(Comparator.comparing(StartPlan::stableFactionId));
            producerCopy.sort(Comparator.comparing(ProducerUsage::supplyKey));
            starts = List.copyOf(startCopy);
            producerUsage = List.copyOf(producerCopy);

            TreeMap<String, Integer> usageFromStarts = new TreeMap<>();
            for (StartPlan start : starts) {
                if (usageFromStarts.put(start.stableFactionId(), start.remoteFreightersUsed()) != null) {
                    throw new IllegalArgumentException("selected start plans must be unique by faction");
                }
                if (start.demands().size() != 1
                        || !commodityId.equals(start.demands().get(0).commodityId())) {
                    throw new IllegalArgumentException("selected start must contain exactly one matching commodity demand");
                }
            }
            if (!usageFromStarts.equals(remoteFreightersByFaction)) {
                throw new IllegalArgumentException("selected physical start usage must equal the combiner ship vector");
            }

            Set<Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey> producerKeys = new HashSet<>();
            for (ProducerUsage usage : producerUsage) {
                if (!commodityId.equals(usage.supplyKey().commodityId())
                        || !producerKeys.add(usage.supplyKey())) {
                    throw new IllegalArgumentException("producer evidence must be unique and match selected commodity");
                }
            }
        }
    }

    /**
     * Complete rich physical plan corresponding to one accepted exact cross-commodity combination.
     *
     * @param version reconstruction contract version
     * @param acceptanceVersion exact resolved-freight acceptance version
     * @param placementVersion exact accepted faction-start placement version
     * @param supplyProfileVersion exact physical supply-profile version
     * @param searchNodeBudgetPerCommodity bounded search authority applied to every source frontier
     * @param remoteFreighterBudgetByFaction authoritative finite freight capacity at every start
     * @param combinerVersion exact source combiner version
     * @param remoteFreightersByFaction aggregate selected remote-freighter usage at every start
     * @param commodities selected rich physical option for every required commodity
     */
    public record PlanReport(
            String version,
            String acceptanceVersion,
            String placementVersion,
            String supplyProfileVersion,
            int searchNodeBudgetPerCommodity,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            String combinerVersion,
            Map<String, Integer> remoteFreightersByFaction,
            List<SelectedCommodityPlan> commodities) {
        /**
         * Validates aggregate selected-plan consistency.
         *
         * @param version reconstruction contract version
         * @param acceptanceVersion exact resolved-freight acceptance version
         * @param placementVersion exact accepted faction-start placement version
         * @param supplyProfileVersion exact physical supply-profile version
         * @param searchNodeBudgetPerCommodity bounded search authority applied to every source frontier
         * @param remoteFreighterBudgetByFaction authoritative finite freight capacity at every start
         * @param combinerVersion exact source combiner version
         * @param remoteFreightersByFaction aggregate selected remote-freighter usage at every start
         * @param commodities selected rich physical option for every required commodity
         */
        public PlanReport {
            version = requireText(version, "version");
            acceptanceVersion = requireText(acceptanceVersion, "acceptanceVersion");
            placementVersion = requireText(placementVersion, "placementVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            if (searchNodeBudgetPerCommodity <= 0) {
                throw new IllegalArgumentException("searchNodeBudgetPerCommodity must be positive");
            }
            remoteFreighterBudgetByFaction = canonicalPositiveFreighterMap(
                    remoteFreighterBudgetByFaction, "remoteFreighterBudgetByFaction");
            combinerVersion = requireText(combinerVersion, "combinerVersion");
            remoteFreightersByFaction = canonicalFreighterMap(
                    remoteFreightersByFaction, "remoteFreightersByFaction");
            if (!remoteFreightersByFaction.keySet().equals(remoteFreighterBudgetByFaction.keySet())) {
                throw new IllegalArgumentException("selected usage and accepted budget must cover the same factions");
            }
            for (Map.Entry<String, Integer> entry : remoteFreightersByFaction.entrySet()) {
                if (entry.getValue() > remoteFreighterBudgetByFaction.get(entry.getKey())) {
                    throw new IllegalArgumentException("selected usage cannot exceed accepted freight capacity");
                }
            }
            ArrayList<SelectedCommodityPlan> copy = new ArrayList<>(
                    Objects.requireNonNull(commodities, "commodities"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selected physical plan must contain commodities");
            }
            copy.sort(Comparator.comparing(SelectedCommodityPlan::commodityId));
            HashSet<String> commodityIds = new HashSet<>();
            TreeMap<String, Integer> aggregate = zeroUsage(remoteFreightersByFaction.keySet());
            for (SelectedCommodityPlan commodity : copy) {
                if (!commodityIds.add(commodity.commodityId())
                        || !commodity.remoteFreightersByFaction().keySet()
                        .equals(remoteFreightersByFaction.keySet())) {
                    throw new IllegalArgumentException("commodity plans must be unique and cover the exact faction set");
                }
                for (StartPlan start : commodity.starts()) {
                    Integer budget = remoteFreighterBudgetByFaction.get(start.stableFactionId());
                    if (budget == null || start.remoteFreighterBudget() != budget) {
                        throw new IllegalArgumentException(
                                "selected physical starts must retain the accepted freight capacity");
                    }
                }
                for (Map.Entry<String, Integer> entry : commodity.remoteFreightersByFaction().entrySet()) {
                    aggregate.put(entry.getKey(), Math.addExact(aggregate.get(entry.getKey()), entry.getValue()));
                }
            }
            if (!aggregate.equals(remoteFreightersByFaction)) {
                throw new IllegalArgumentException("selected commodity usage must sum to the accepted combiner usage");
            }
            commodities = List.copyOf(copy);
        }
    }

    /**
     * Reconstructs the exact combiner selection from one resolved-freight acceptance authority.
     *
     * @param acceptance resolved freight report retaining the rich frontiers and exact combination
     * @return deterministic rich physical plan containing the selected producer/route commitments
     * @throws IllegalArgumentException when evidence is incomplete, mismatched or not accepted
     */
    public static PlanReport reconstruct(AcceptanceReport acceptance) {
        AcceptanceReport resolved = Objects.requireNonNull(acceptance, "acceptance");
        CombinationReport selected = resolved.combination();
        if (selected.status() != Status.ACCEPTED) {
            throw new IllegalArgumentException("physical freight plan requires an accepted exact combination");
        }

        TreeMap<String, FrontierReport> frontiers = new TreeMap<>();
        for (FrontierReport frontier : resolved.commodityFrontiers()) {
            FrontierReport value = Objects.requireNonNull(frontier, "frontier");
            if (frontiers.putIfAbsent(value.commodityId(), value) != null) {
                throw new IllegalArgumentException("rich frontier reports must be unique by commodity");
            }
        }
        if (frontiers.isEmpty() || frontiers.size() != selected.selectedOptions().size()) {
            throw new IllegalArgumentException("accepted selection must cover every supplied rich commodity frontier");
        }

        ArrayList<SelectedCommodityPlan> restored = new ArrayList<>();
        for (SelectedOption chosen : selected.selectedOptions()) {
            FrontierReport frontier = frontiers.get(chosen.commodityId());
            if (frontier == null || !frontier.version().equals(chosen.frontierVersion())) {
                throw new IllegalArgumentException("selected option does not match a supplied frontier version");
            }
            FrontierOption rich = frontier.options().stream()
                    .filter(option -> option.optionId().equals(chosen.optionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "selected option is absent from rich frontier evidence: " + chosen.commodityId()
                                    + "/" + chosen.optionId()));
            if (!rich.remoteFreightersByFaction().equals(chosen.remoteFreightersByFaction())) {
                throw new IllegalArgumentException("selected combiner vector differs from rich physical option");
            }
            restored.add(new SelectedCommodityPlan(
                    chosen.commodityId(),
                    chosen.frontierVersion(),
                    chosen.optionId(),
                    chosen.remoteFreightersByFaction(),
                    rich.starts(),
                    rich.producerUsage()));
        }

        return new PlanReport(
                CURRENT_VERSION,
                resolved.version(),
                resolved.placementVersion(),
                resolved.supplyProfileVersion(),
                resolved.searchNodeBudgetPerCommodity(),
                resolved.remoteFreighterBudgetByFaction(),
                selected.version(),
                selected.remoteFreightersUsedByFaction(),
                restored);
    }

    private static Map<String, Integer> canonicalFreighterMap(
            Map<String, Integer> input,
            String field) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(input, field).entrySet()) {
            String faction = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), field + " count");
            if (count < 0 || result.putIfAbsent(faction, count) != null) {
                throw new IllegalArgumentException(field + " must contain unique factions and non-negative counts");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-empty");
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Integer> canonicalPositiveFreighterMap(
            Map<String, Integer> input,
            String field) {
        Map<String, Integer> result = canonicalFreighterMap(input, field);
        if (result.values().stream().anyMatch(value -> value <= 0)) {
            throw new IllegalArgumentException(field + " counts must be positive");
        }
        return result;
    }

    private static TreeMap<String, Integer> zeroUsage(Set<String> factions) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (String faction : factions) {
            result.put(faction, 0);
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
