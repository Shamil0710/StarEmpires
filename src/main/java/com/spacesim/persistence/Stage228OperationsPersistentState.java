package com.spacesim.persistence;

import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetId;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * M22.8M persistence sidecar for D/G/H carrier-operation continuity.
 *
 * <p>The sidecar stores only already-authoritative durable values: individual-craft mission intent,
 * produced-but-undelivered physical craft, and explicit carrier-to-wing strategic associations.
 * It creates no craft, ammunition, propellant, inventory, replacement, readiness or tactical state.
 * Stage-21 strategic operations remain in the embedded Stage-21 checkpoint and are deliberately not
 * duplicated here.</p>
 *
 * @param schemaVersion exact sidecar schema
 * @param runtimeVersion exact runtime contract
 * @param semanticContract no-grant persistence contract
 * @param nextMissionId next unused D mission identity
 * @param missions deterministic mission rows
 * @param pendingDeliveries G produced-but-undelivered physical craft
 * @param carrierWings H explicit carrier-wing associations
 */
public record Stage228OperationsPersistentState(
        int schemaVersion,
        String runtimeVersion,
        String semanticContract,
        long nextMissionId,
        List<MissionState> missions,
        List<PendingDeliveryState> pendingDeliveries,
        List<CarrierWingState> carrierWings) {

    /** Current M22.8M operations-sidecar schema. */
    public static final int CURRENT_VERSION = 1;
    /** Current M22.8M operations runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8m.operations.v1";
    /** Stable no-grant semantic contract. */
    public static final String CURRENT_SEMANTIC_CONTRACT =
            "mission-intent|pending-physical-delivery|carrier-wing-binding|no-free-craft|no-free-supply";

    /**
     * One persistent M22.8D mission row.
     *
     * @param missionId positive mission identity
     * @param craftId persistent individual craft identity
     * @param source PLAYER or AI command source
     * @param type mission family
     * @param targetKind actor-known target kind
     * @param targetReferenceId actor-visible target reference
     * @param submittedTick authoritative submission tick
     * @param status current mission lifecycle
     */
    public record MissionState(
            long missionId,
            SmallCraftId craftId,
            OrderSource source,
            MissionType type,
            TargetKind targetKind,
            String targetReferenceId,
            long submittedTick,
            MissionStatus status) implements Comparable<MissionState> {
        /**
         * Validates one exact mission row.
         *
         * @param missionId positive mission identity
         * @param craftId persistent craft identity
         * @param source command source
         * @param type mission family
         * @param targetKind actor-known target category
         * @param targetReferenceId actor-visible target reference
         * @param submittedTick authoritative submission tick
         * @param status mission lifecycle
         */
        public MissionState {
            if (missionId <= 0L) {
                throw new IllegalArgumentException("missionId must be positive");
            }
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(targetKind, "targetKind");
            targetReferenceId = requireText(targetReferenceId, "targetReferenceId");
            if (submittedTick < 0L) {
                throw new IllegalArgumentException("submittedTick cannot be negative");
            }
            Objects.requireNonNull(status, "status");
        }

        @Override
        public int compareTo(MissionState other) {
            return Long.compare(missionId, Objects.requireNonNull(other, "other").missionId);
        }
    }

    /**
     * One produced G craft waiting for ordinary physical delivery.
     *
     * @param craftId persistent produced craft identity
     * @param sourceStationId physical production origin
     * @param designId exact authored design/fit identity
     */
    public record PendingDeliveryState(
            SmallCraftId craftId,
            String sourceStationId,
            String designId) implements Comparable<PendingDeliveryState> {
        /**
         * Validates one pending-delivery row.
         *
         * @param craftId produced craft identity
         * @param sourceStationId production origin
         * @param designId exact design identity
         */
        public PendingDeliveryState {
            Objects.requireNonNull(craftId, "craftId");
            sourceStationId = requireText(sourceStationId, "sourceStationId");
            designId = requireText(designId, "designId");
        }

        @Override
        public int compareTo(PendingDeliveryState other) {
            return craftId.compareTo(Objects.requireNonNull(other, "other").craftId);
        }
    }

    /**
     * One explicit H carrier-wing association.
     *
     * <p>The row intentionally keeps lost craft IDs in the roster. Absence from the live craft registry
     * after restore is meaningful strategic loss evidence rather than a reason to synthesize a
     * replacement.</p>
     *
     * @param carrierFleetId ordinary Stage-21 carrier FleetId
     * @param hostStableId physical carrier hangar-host identity
     * @param stableFactionId governed owning faction identity
     * @param craftIds persistent wing roster, including lawfully lost identities
     */
    public record CarrierWingState(
            FleetId carrierFleetId,
            String hostStableId,
            String stableFactionId,
            List<SmallCraftId> craftIds) implements Comparable<CarrierWingState> {
        /**
         * Validates and canonicalizes one carrier-wing row.
         *
         * @param carrierFleetId ordinary carrier FleetId
         * @param hostStableId physical host identity
         * @param stableFactionId stable owning faction
         * @param craftIds individual craft roster
         */
        public CarrierWingState {
            Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            hostStableId = requireText(hostStableId, "hostStableId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(craftIds, "craftIds");
            TreeSet<SmallCraftId> canonical = new TreeSet<>();
            for (SmallCraftId id : craftIds) {
                if (!canonical.add(Objects.requireNonNull(id, "craftId"))) {
                    throw new IllegalArgumentException(
                            "duplicate craft in persisted carrier wing: " + id);
                }
            }
            if (canonical.isEmpty()) {
                throw new IllegalArgumentException("persisted carrier wing cannot be empty");
            }
            craftIds = List.copyOf(canonical);
        }

        @Override
        public int compareTo(CarrierWingState other) {
            return carrierFleetId.compareTo(
                    Objects.requireNonNull(other, "other").carrierFleetId);
        }

        /** @return runtime H association with identical durable identities */
        public CarrierWingAssignment toRuntime() {
            return new CarrierWingAssignment(
                    carrierFleetId, hostStableId, stableFactionId, craftIds);
        }
    }

    /**
     * Validates sidecar identity, deterministic ordering and no-grant cross-row constraints.
     *
     * @param schemaVersion exact schema
     * @param runtimeVersion exact runtime contract
     * @param semanticContract no-grant semantic contract
     * @param nextMissionId next unused mission ID
     * @param missions mission rows
     * @param pendingDeliveries produced but undelivered craft
     * @param carrierWings explicit strategic carrier associations
     */
    public Stage228OperationsPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8M operations schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8M operations runtime: " + runtimeVersion);
        }
        semanticContract = requireText(semanticContract, "semanticContract");
        if (!CURRENT_SEMANTIC_CONTRACT.equals(semanticContract)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8M operations semantic contract: " + semanticContract);
        }
        if (nextMissionId <= 0L) {
            throw new IllegalArgumentException("nextMissionId must be positive");
        }

        ArrayList<MissionState> missionCopy =
                new ArrayList<>(Objects.requireNonNull(missions, "missions"));
        if (missionCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("missions cannot contain null");
        }
        missionCopy.sort(Comparator.naturalOrder());
        Set<Long> missionIds = new HashSet<>();
        Set<SmallCraftId> activeMissionCraft = new HashSet<>();
        long maximumMissionId = 0L;
        for (MissionState mission : missionCopy) {
            if (!missionIds.add(mission.missionId())) {
                throw new IllegalArgumentException(
                        "duplicate persisted mission: " + mission.missionId());
            }
            maximumMissionId = Math.max(maximumMissionId, mission.missionId());
            if (mission.status().active()
                    && !activeMissionCraft.add(mission.craftId())) {
                throw new IllegalArgumentException(
                        "craft has multiple active persisted missions: " + mission.craftId());
            }
        }
        if (nextMissionId <= maximumMissionId) {
            throw new IllegalArgumentException(
                    "nextMissionId must exceed every persisted mission identity");
        }
        missions = List.copyOf(missionCopy);

        ArrayList<PendingDeliveryState> deliveryCopy =
                new ArrayList<>(Objects.requireNonNull(pendingDeliveries, "pendingDeliveries"));
        if (deliveryCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("pendingDeliveries cannot contain null");
        }
        deliveryCopy.sort(Comparator.naturalOrder());
        Set<SmallCraftId> pendingCraft = new HashSet<>();
        for (PendingDeliveryState delivery : deliveryCopy) {
            if (!pendingCraft.add(delivery.craftId())) {
                throw new IllegalArgumentException(
                        "duplicate persisted pending delivery: " + delivery.craftId());
            }
            if (activeMissionCraft.contains(delivery.craftId())) {
                throw new IllegalArgumentException(
                        "pending-delivery craft cannot also have an active mission: "
                                + delivery.craftId());
            }
        }
        pendingDeliveries = List.copyOf(deliveryCopy);

        ArrayList<CarrierWingState> wingCopy =
                new ArrayList<>(Objects.requireNonNull(carrierWings, "carrierWings"));
        if (wingCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("carrierWings cannot contain null");
        }
        wingCopy.sort(Comparator.naturalOrder());
        Set<FleetId> carriers = new HashSet<>();
        Set<SmallCraftId> assignedCraft = new HashSet<>();
        for (CarrierWingState wing : wingCopy) {
            if (!carriers.add(wing.carrierFleetId())) {
                throw new IllegalArgumentException(
                        "duplicate persisted carrier wing: " + wing.carrierFleetId());
            }
            for (SmallCraftId id : wing.craftIds()) {
                if (!assignedCraft.add(id)) {
                    throw new IllegalArgumentException(
                            "craft belongs to multiple persisted carrier wings: " + id);
                }
                if (pendingCraft.contains(id)) {
                    throw new IllegalArgumentException(
                            "pending-delivery craft cannot already belong to a carrier wing: " + id);
                }
            }
        }
        carrierWings = List.copyOf(wingCopy);
    }

    /** @return empty non-granting D/G/H sidecar */
    public static Stage228OperationsPersistentState empty() {
        return new Stage228OperationsPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                CURRENT_SEMANTIC_CONTRACT,
                1L,
                List.of(),
                List.of(),
                List.of());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
