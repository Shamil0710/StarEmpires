package com.spacesim.benchmark;

import com.spacesim.content.Stage228CarrierCounterplayEvidence;
import com.spacesim.content.Stage228CarrierCounterplayEvidence.CarrierBayVector;
import com.spacesim.content.Stage22CorePairBalanceEvidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.8L deterministic scale/performance evidence for persistent carrier small craft.
 *
 * <p>This class contains no scheduler and measures no wall-clock performance threshold. It derives
 * representative small/medium/dense scenario budgets from the physically bounded carrier capacities
 * already proven by M22.8K. Persistent craft count may grow with the world, while exact Stage-19
 * materialization remains bounded by the explicitly local carrier groups participating in an
 * encounter. Flight-deck concurrency remains bounded by installed physical bay count.</p>
 */
public final class Stage228CarrierScalePerformanceEvidence {
    /** Semantic version of the L deterministic scale evidence. */
    public static final String VERSION = "m22.8l.carrier_scale_performance_evidence.v1";

    private Stage228CarrierScalePerformanceEvidence() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives the three required representative scale scenarios.
     *
     * @return immutable scale/performance evidence
     */
    public static Evidence deriveCurrent() {
        var counterplay = Stage228CarrierCounterplayEvidence.deriveCurrent();
        CarrierBayVector empire = requireCarrier(
                counterplay.carrierBayVectors(),
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID);
        CarrierBayVector union = requireCarrier(
                counterplay.carrierBayVectors(),
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID);

        int empireCraftPerCarrier = minimumPositiveCapacity(empire);
        int unionCraftPerCarrier = minimumPositiveCapacity(union);

        return new Evidence(
                VERSION,
                counterplay.engineeringFingerprint(),
                empireCraftPerCarrier,
                unionCraftPerCarrier,
                List.of(
                        scenario(
                                ScenarioSize.SMALL,
                                1,
                                1,
                                empire,
                                union,
                                empireCraftPerCarrier,
                                unionCraftPerCarrier),
                        scenario(
                                ScenarioSize.MEDIUM,
                                8,
                                2,
                                empire,
                                union,
                                empireCraftPerCarrier,
                                unionCraftPerCarrier),
                        scenario(
                                ScenarioSize.DENSE,
                                32,
                                4,
                                empire,
                                union,
                                empireCraftPerCarrier,
                                unionCraftPerCarrier)),
                "SmallCraftRegistry / Stage228CampaignAuthority",
                "SmallCraftFlightDeckOperations",
                "SmallCraftTacticalEncounterService -> Stage19ExactTacticalEncounterResolver",
                "explicit local participant list; dormant persistent craft are not exact-materialized");
    }

    private static ScenarioBudget scenario(
            ScenarioSize size,
            int carriersPerFaction,
            int locallyEngagedCarriersPerFaction,
            CarrierBayVector empire,
            CarrierBayVector union,
            int empireCraftPerCarrier,
            int unionCraftPerCarrier) {
        int totalCarriers = Math.multiplyExact(carriersPerFaction, 2);
        int persistentCraftRows = Math.addExact(
                Math.multiplyExact(carriersPerFaction, empireCraftPerCarrier),
                Math.multiplyExact(carriersPerFaction, unionCraftPerCarrier));
        int exactLocalCraftUpperBound = Math.addExact(
                Math.multiplyExact(locallyEngagedCarriersPerFaction, empireCraftPerCarrier),
                Math.multiplyExact(locallyEngagedCarriersPerFaction, unionCraftPerCarrier));
        int dormantCraftLowerBound = persistentCraftRows - exactLocalCraftUpperBound;
        int physicalBayCount = Math.addExact(
                Math.multiplyExact(carriersPerFaction, empire.bayCount()),
                Math.multiplyExact(carriersPerFaction, union.bayCount()));
        int activeDeckOperationUpperBound = physicalBayCount;
        int queuedDeckRequestUpperBound = persistentCraftRows;
        double exactMaterializationFraction = persistentCraftRows == 0
                ? 0d
                : exactLocalCraftUpperBound / (double) persistentCraftRows;

        return new ScenarioBudget(
                size,
                carriersPerFaction,
                totalCarriers,
                locallyEngagedCarriersPerFaction,
                empireCraftPerCarrier,
                unionCraftPerCarrier,
                persistentCraftRows,
                exactLocalCraftUpperBound,
                dormantCraftLowerBound,
                physicalBayCount,
                activeDeckOperationUpperBound,
                queuedDeckRequestUpperBound,
                exactMaterializationFraction);
    }

    private static CarrierBayVector requireCarrier(
            Map<String, CarrierBayVector> carriers,
            String stableFactionId) {
        CarrierBayVector value = Objects.requireNonNull(
                carriers.get(stableFactionId),
                "carrier scale evidence missing faction " + stableFactionId);
        if (value.bayCount() <= 0) {
            throw new IllegalStateException(
                    "carrier scale evidence requires physical bay count: " + stableFactionId);
        }
        return value;
    }

    private static int minimumPositiveCapacity(CarrierBayVector carrier) {
        return carrier.boundedCapacityByRole().values().stream()
                .mapToInt(Integer::intValue)
                .filter(value -> value > 0)
                .min()
                .orElseThrow(() -> new IllegalStateException(
                        "carrier has no physically embarkable production role: "
                                + carrier.stableFactionId()));
    }

    /** Required deterministic scale scenario family. */
    public enum ScenarioSize {
        /** One carrier per side; all embarked craft may be locally active. */ SMALL,
        /** Several carrier groups with only a bounded local subset in exact combat. */ MEDIUM,
        /** Dense strategic population with local exact materialization remaining a subset. */ DENSE
    }

    /**
     * One representative deterministic scale budget.
     *
     * @param size scenario family
     * @param carriersPerFaction persistent carriers on each core side
     * @param totalCarriers total persistent carriers across both sides
     * @param locallyEngagedCarriersPerFaction carrier groups participating in one local exact encounter
     * @param empireCraftPerCarrier conservative physical craft capacity per Empire carrier
     * @param unionCraftPerCarrier conservative physical craft capacity per Union carrier
     * @param persistentCraftRows total persistent individual craft in the scenario
     * @param exactLocalCraftUpperBound craft that may enter this representative local exact encounter
     * @param dormantCraftLowerBound persistent craft outside that local exact encounter
     * @param physicalBayCount installed physical bay count across all scenario carriers
     * @param activeDeckOperationUpperBound one active handling sequence per physical bay
     * @param queuedDeckRequestUpperBound at most one queued/active request per persistent craft
     * @param exactMaterializationFraction fraction of persistent craft entering the representative exact encounter
     */
    public record ScenarioBudget(
            ScenarioSize size,
            int carriersPerFaction,
            int totalCarriers,
            int locallyEngagedCarriersPerFaction,
            int empireCraftPerCarrier,
            int unionCraftPerCarrier,
            int persistentCraftRows,
            int exactLocalCraftUpperBound,
            int dormantCraftLowerBound,
            int physicalBayCount,
            int activeDeckOperationUpperBound,
            int queuedDeckRequestUpperBound,
            double exactMaterializationFraction) {
        /**
         * Validates one deterministic scenario budget.
         *
         * @param size scenario family
         * @param carriersPerFaction persistent carriers per side
         * @param totalCarriers total carriers
         * @param locallyEngagedCarriersPerFaction local exact carrier groups per side
         * @param empireCraftPerCarrier Empire physical craft capacity
         * @param unionCraftPerCarrier Union physical craft capacity
         * @param persistentCraftRows persistent individual craft count
         * @param exactLocalCraftUpperBound local exact participant upper bound
         * @param dormantCraftLowerBound dormant/non-local persistent craft lower bound
         * @param physicalBayCount total physical bays
         * @param activeDeckOperationUpperBound concurrent deck-operation upper bound
         * @param queuedDeckRequestUpperBound queued/active request row upper bound
         * @param exactMaterializationFraction local exact fraction
         */
        public ScenarioBudget {
            Objects.requireNonNull(size, "size");
            if (carriersPerFaction <= 0
                    || totalCarriers != carriersPerFaction * 2
                    || locallyEngagedCarriersPerFaction <= 0
                    || locallyEngagedCarriersPerFaction > carriersPerFaction
                    || empireCraftPerCarrier <= 0
                    || unionCraftPerCarrier <= 0
                    || persistentCraftRows <= 0
                    || exactLocalCraftUpperBound <= 0
                    || exactLocalCraftUpperBound > persistentCraftRows
                    || dormantCraftLowerBound != persistentCraftRows - exactLocalCraftUpperBound
                    || physicalBayCount <= 0
                    || activeDeckOperationUpperBound != physicalBayCount
                    || queuedDeckRequestUpperBound != persistentCraftRows
                    || !Double.isFinite(exactMaterializationFraction)
                    || exactMaterializationFraction <= 0d
                    || exactMaterializationFraction > 1d) {
                throw new IllegalArgumentException("invalid M22.8L deterministic scenario budget");
            }
        }
    }

    /**
     * Complete M22.8L evidence surface.
     *
     * @param version evidence version
     * @param engineeringFingerprint exact physical-content fingerprint
     * @param empireCraftPerCarrier conservative Empire capacity
     * @param unionCraftPerCarrier conservative Union capacity
     * @param scenarios small/medium/dense deterministic budgets
     * @param persistenceAuthority persistent craft authority
     * @param deckSchedulingAuthority physical deck scheduling authority
     * @param localExactAuthority exact local combat materialization authority
     * @param dormantPolicy architecture statement for non-local craft
     */
    public record Evidence(
            String version,
            String engineeringFingerprint,
            int empireCraftPerCarrier,
            int unionCraftPerCarrier,
            List<ScenarioBudget> scenarios,
            String persistenceAuthority,
            String deckSchedulingAuthority,
            String localExactAuthority,
            String dormantPolicy) {
        /**
         * Validates immutable L evidence.
         *
         * @param version evidence version
         * @param engineeringFingerprint exact engineering fingerprint
         * @param empireCraftPerCarrier Empire capacity
         * @param unionCraftPerCarrier Union capacity
         * @param scenarios deterministic budgets
         * @param persistenceAuthority persistent authority label
         * @param deckSchedulingAuthority deck authority label
         * @param localExactAuthority local exact authority label
         * @param dormantPolicy dormant-craft architecture statement
         */
        public Evidence {
            version = requireText(version, "version");
            engineeringFingerprint = requireText(
                    engineeringFingerprint, "engineeringFingerprint");
            if (empireCraftPerCarrier <= 0 || unionCraftPerCarrier <= 0) {
                throw new IllegalArgumentException("carrier craft capacities must be positive");
            }
            scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
            if (scenarios.size() != ScenarioSize.values().length) {
                throw new IllegalArgumentException(
                        "M22.8L evidence must contain small, medium and dense scenarios");
            }
            persistenceAuthority = requireText(persistenceAuthority, "persistenceAuthority");
            deckSchedulingAuthority = requireText(
                    deckSchedulingAuthority, "deckSchedulingAuthority");
            localExactAuthority = requireText(localExactAuthority, "localExactAuthority");
            dormantPolicy = requireText(dormantPolicy, "dormantPolicy");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
