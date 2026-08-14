package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Persistent Stage-11B domain model. */
public final class StrategicGrowthState {
    private StrategicGrowthState() {
        throw new AssertionError("Utility class");
    }

    /** Persisted strategic reason. */
    public enum Reason {
        /** Expansion primarily secures finite mineable resources. */
        RESOURCE_ACCESS,
        /** Expansion primarily serves unmet market demand. */
        MARKET_DEMAND,
        /** Expansion primarily extends an existing trade network. */
        TRADE_NETWORK,
        /** Expansion primarily extends spatial reach through jump topology. */
        STRATEGIC_REACH,
        /** Multiple physical signals contribute without one dominant reason. */
        BALANCED
    }

    /** Persistent lifecycle. */
    public enum Status {
        /** Plan exists but has not yet been approved for physical execution. */
        PLANNED,
        /** Budget/target checks passed and execution may start. */
        APPROVED,
        /** Linked Stage-9 construction and logistics are physically active. */
        EXECUTING,
        /** Anchor construction completed and the growth objective was established. */
        ESTABLISHED,
        /** Plan was deliberately cancelled before establishment. */
        CANCELLED,
        /** Plan terminated because authoritative execution became impossible. */
        FAILED;

        /** @return true for terminal states */
        public boolean terminal() {
            return this == ESTABLISHED || this == CANCELLED || this == FAILED;
        }
    }

    /**
     * Globally stable composite plan identity.
     *
     * @param ownerContentId stable owner content ID
     * @param sequence positive owner-local sequence
     */
    public record PlanId(String ownerContentId, long sequence) implements Comparable<PlanId> {
        /**
         * Validates identity.
         *
         * @param ownerContentId stable owner content ID
         * @param sequence positive owner-local sequence
         */
        public PlanId {
            ownerContentId = normalized(ownerContentId, "Owner content ID");
            if (sequence <= 0L) {
                throw new IllegalArgumentException("Plan sequence must be positive");
            }
        }

        @Override
        public int compareTo(PlanId other) {
            int owner = ownerContentId.compareTo(other.ownerContentId);
            return owner != 0 ? owner : Long.compare(sequence, other.sequence);
        }
    }

    /**
     * Initial physical stock target.
     *
     * @param itemContentId stable item content ID
     * @param targetAmount positive target units
     */
    public record StockTarget(String itemContentId, int targetAmount) implements Comparable<StockTarget> {
        /**
         * Validates target.
         *
         * @param itemContentId stable item content ID
         * @param targetAmount positive target units
         */
        public StockTarget {
            itemContentId = normalized(itemContentId, "Item content ID");
            if (targetAmount <= 0) {
                throw new IllegalArgumentException("Stock target must be positive");
            }
        }

        @Override
        public int compareTo(StockTarget other) {
            return itemContentId.compareTo(other.itemContentId);
        }
    }

    /**
     * Immutable persistent plan snapshot.
     *
     * @param id stable composite ID
     * @param sourceSystemId controlled source system
     * @param targetSystemId remote target system
     * @param reason persisted strategic reason
     * @param anchorArchetypeContentId selected station archetype
     * @param anchorProjectId linked Stage-9 project after execution starts, otherwise null
     * @param requiredSupportFleetCount support requirement
     * @param assignedSupportFleetIds assigned stable FleetIds
     * @param initialStockTargets initial physical stock targets
     * @param approvedBudgetMilliCredits positive approved budget ceiling
     * @param status persistent lifecycle
     * @param createdTick creation tick
     * @param stateChangedTick latest transition tick
     * @param terminalTick terminal tick or -1
     */
    public record Plan(
            PlanId id,
            StarSystemId sourceSystemId,
            StarSystemId targetSystemId,
            Reason reason,
            String anchorArchetypeContentId,
            ConstructionProjectId anchorProjectId,
            int requiredSupportFleetCount,
            List<FleetId> assignedSupportFleetIds,
            List<StockTarget> initialStockTargets,
            long approvedBudgetMilliCredits,
            Status status,
            long createdTick,
            long stateChangedTick,
            long terminalTick) implements Comparable<Plan> {

        /**
         * Validates and canonicalizes the snapshot.
         *
         * @param id stable composite ID
         * @param sourceSystemId source system
         * @param targetSystemId target system
         * @param reason strategic reason
         * @param anchorArchetypeContentId anchor archetype
         * @param anchorProjectId optional construction link
         * @param requiredSupportFleetCount support requirement
         * @param assignedSupportFleetIds assigned fleets
         * @param initialStockTargets stock targets
         * @param approvedBudgetMilliCredits budget ceiling
         * @param status lifecycle state
         * @param createdTick creation tick
         * @param stateChangedTick latest transition tick
         * @param terminalTick terminal tick or -1
         */
        public Plan {
            Objects.requireNonNull(id, "PlanId not set");
            Objects.requireNonNull(sourceSystemId, "Source system not set");
            Objects.requireNonNull(targetSystemId, "Target system not set");
            Objects.requireNonNull(reason, "Reason not set");
            anchorArchetypeContentId = normalized(anchorArchetypeContentId, "Anchor archetype");
            Objects.requireNonNull(status, "Status not set");
            if (sourceSystemId.equals(targetSystemId)) {
                throw new IllegalArgumentException("Source and target systems must differ");
            }
            if (requiredSupportFleetCount < 0 || approvedBudgetMilliCredits <= 0L) {
                throw new IllegalArgumentException("Support requirement/budget is invalid");
            }
            if (createdTick < 0L || stateChangedTick < createdTick) {
                throw new IllegalArgumentException("Plan timestamps are invalid");
            }

            List<FleetId> fleets = new ArrayList<>(Objects.requireNonNull(assignedSupportFleetIds));
            Set<FleetId> uniqueFleets = new HashSet<>();
            for (FleetId fleetId : fleets) {
                if (!uniqueFleets.add(Objects.requireNonNull(fleetId))) {
                    throw new IllegalArgumentException("Duplicate support FleetId");
                }
            }
            fleets.sort(FleetId::compareTo);
            if (fleets.size() > requiredSupportFleetCount) {
                throw new IllegalArgumentException("Assigned fleets exceed requirement");
            }
            assignedSupportFleetIds = List.copyOf(fleets);

            List<StockTarget> targets = new ArrayList<>(Objects.requireNonNull(initialStockTargets));
            Set<String> items = new HashSet<>();
            for (StockTarget target : targets) {
                if (!items.add(Objects.requireNonNull(target).itemContentId())) {
                    throw new IllegalArgumentException("Duplicate stock target");
                }
            }
            targets.sort(StockTarget::compareTo);
            initialStockTargets = List.copyOf(targets);

            if ((status == Status.PLANNED || status == Status.APPROVED) && anchorProjectId != null) {
                throw new IllegalArgumentException("Pre-execution plan cannot link construction");
            }
            if ((status == Status.EXECUTING || status == Status.ESTABLISHED) && anchorProjectId == null) {
                throw new IllegalArgumentException("Executing plan requires construction link");
            }
            if (status.terminal()) {
                if (terminalTick < stateChangedTick) {
                    throw new IllegalArgumentException("Terminal plan requires terminal tick");
                }
            } else if (terminalTick != -1L) {
                throw new IllegalArgumentException("Non-terminal plan cannot have terminal tick");
            }
        }

        /** @return true when every required support slot is assigned */
        public boolean supportRequirementSatisfied() {
            return assignedSupportFleetIds.size() >= requiredSupportFleetCount;
        }

        @Override
        public int compareTo(Plan other) {
            return id.compareTo(other.id);
        }
    }

    private static String normalized(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return normalized;
    }
}
