package com.spacesim.world;

import com.spacesim.world.FleetCommandState.OrderSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable M22.8D mission/command state for individual persistent small craft.
 *
 * <p>The state records mission intent only. Physical launch/recovery remains M22.8C authority and
 * local tactical movement/combat remains Stage-19/M22.8E authority. At most one active mission may
 * reference one {@link SmallCraftId}, preventing PLAYER or AI command paths from double-committing
 * the same physical craft.</p>
 *
 * @param nextMissionId next positive mission identity watermark
 * @param missions deterministic mission rows ordered by identity
 */
public record SmallCraftMissionState(long nextMissionId, List<MissionOrder> missions) {

    /** Validates allocator continuity, unique IDs and one active mission per physical craft.
     * @param nextMissionId next positive mission identity watermark
     * @param missions mission rows
     */
    public SmallCraftMissionState {
        if (nextMissionId <= 0L) {
            throw new IllegalArgumentException("nextMissionId must be positive");
        }
        Objects.requireNonNull(missions, "missions");
        ArrayList<MissionOrder> copy = new ArrayList<>(missions);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("missions cannot contain null");
        }
        copy.sort(Comparator.comparingLong(MissionOrder::id));
        Set<Long> ids = new HashSet<>();
        Set<SmallCraftId> activeCraft = new HashSet<>();
        long maxId = 0L;
        for (MissionOrder mission : copy) {
            if (!ids.add(mission.id())) {
                throw new IllegalArgumentException("duplicate small-craft mission id: " + mission.id());
            }
            maxId = Math.max(maxId, mission.id());
            if (mission.status().active() && !activeCraft.add(mission.craftId())) {
                throw new IllegalArgumentException(
                        "small craft has multiple active missions: " + mission.craftId());
            }
        }
        if (nextMissionId <= maxId) {
            throw new IllegalArgumentException(
                    "nextMissionId must exceed every persisted mission identity");
        }
        missions = List.copyOf(copy);
    }

    /** @return empty deterministic mission state */
    public static SmallCraftMissionState empty() {
        return new SmallCraftMissionState(1L, List.of());
    }

    /**
     * Resolves one mission or fails closed.
     *
     * @param missionId positive mission identity
     * @return matching mission
     */
    public MissionOrder requireMission(long missionId) {
        return missions.stream()
                .filter(value -> value.id() == missionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown small-craft mission: " + missionId));
    }

    /**
     * Finds the current active mission for one craft.
     *
     * @param craftId physical small-craft identity
     * @return current active mission when present
     */
    public Optional<MissionOrder> activeMissionFor(SmallCraftId craftId) {
        SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
        return missions.stream()
                .filter(value -> value.craftId().equals(checked) && value.status().active())
                .findFirst();
    }

    /**
     * Adds one accepted mission and advances the allocator watermark.
     *
     * @param mission accepted mission
     * @return immutable updated state
     */
    public SmallCraftMissionState add(MissionOrder mission) {
        MissionOrder checked = Objects.requireNonNull(mission, "mission");
        ArrayList<MissionOrder> next = new ArrayList<>(missions);
        next.add(checked);
        return new SmallCraftMissionState(
                Math.max(nextMissionId, Math.addExact(checked.id(), 1L)),
                next);
    }

    /**
     * Replaces an existing mission without changing identity.
     *
     * @param replacement replacement mission state
     * @return immutable updated state
     */
    public SmallCraftMissionState replace(MissionOrder replacement) {
        MissionOrder checked = Objects.requireNonNull(replacement, "replacement");
        ArrayList<MissionOrder> next = new ArrayList<>(missions.size());
        boolean replaced = false;
        for (MissionOrder current : missions) {
            if (current.id() == checked.id()) {
                next.add(checked);
                replaced = true;
            } else {
                next.add(current);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException(
                    "unknown small-craft mission: " + checked.id());
        }
        return new SmallCraftMissionState(nextMissionId, next);
    }

    /** Required reusable M22.8D mission vocabulary. */
    public enum MissionType {
        /** Defensive combat air patrol around an actor-known area or host. */ CAP,
        /** Emergency reaction launch against an actor-known contact. */ QRA,
        /** Intercept an actor-known track. */ INTERCEPTION,
        /** Accompany an actor-known owned/assigned asset. */ ESCORT,
        /** Anti-ship strike against an actor-known track. */ ANTI_SHIP_STRIKE,
        /** Reconnaissance or sensor-extension mission. */ RECONNAISSANCE,
        /** Electronic-warfare/support mission where fitted capability permits it. */ EW_SUPPORT,
        /** Return toward the assigned/known recovery host. */ RETURN,
        /** Enter recovery intent for a known host. */ RECOVER,
        /** Divert to another actor-known safe host/area. */ DIVERT;

        /** @return whether this mission normally commits a ready embarked craft to launch */
        public boolean deploymentMission() {
            return this != RETURN && this != RECOVER && this != DIVERT;
        }

        /** @return whether a weapon-capable fit is required by the mission family */
        public boolean requiresWeapons() {
            return this == CAP || this == QRA || this == INTERCEPTION
                    || this == ANTI_SHIP_STRIKE;
        }

        /** @return whether an authored sensor/EW family is required */
        public boolean requiresSensorEw() {
            return this == RECONNAISSANCE || this == EW_SUPPORT;
        }
    }

    /** Actor-known target category; never a hidden world-state pointer. */
    public enum TargetKind {
        /** Actor-known patrol/recon/divert area. */ AREA,
        /** Actor-known local tactical track. */ TRACK,
        /** Actor-known owned/protected asset. */ OWNED_ASSET,
        /** Actor-known carrier/station recovery host. */ HOST
    }

    /**
     * Opaque actor-known target reference.
     *
     * @param kind target category
     * @param referenceId stable actor-visible target/report identity
     */
    public record MissionTarget(TargetKind kind, String referenceId) {
        /** Validates one actor-visible target reference.
         * @param kind target category
         * @param referenceId stable actor-visible target/report identity
         */
        public MissionTarget {
            Objects.requireNonNull(kind, "kind");
            referenceId = requireText(referenceId, "referenceId");
        }
    }

    /** Persistent mission lifecycle without owning physical tactical execution. */
    public enum MissionStatus {
        /** Accepted for an embarked ready craft; M22.8C launch is queued/in progress. */ LAUNCH_QUEUED,
        /** Physical local-flight handoff has occurred; Stage-19/M22.8E owns execution. */ ACTIVE,
        /** Craft is executing return/divert intent in local flight. */ RETURNING,
        /** Physical recovery has been offered/queued through M22.8C. */ RECOVERY_PENDING,
        /** Mission completed after physical recovery/service boundary. */ COMPLETE,
        /** Mission cancelled before completion. */ CANCELLED,
        /** Mission failed due to physical/lifecycle failure. */ FAILED;

        /** @return whether this mission still occupies the craft's single active mission slot */
        public boolean active() {
            return this == LAUNCH_QUEUED || this == ACTIVE
                    || this == RETURNING || this == RECOVERY_PENDING;
        }
    }

    /**
     * One persistent mission intent for one physical craft.
     *
     * @param id positive mission identity
     * @param craftId physical craft identity
     * @param source PLAYER or AI submission source
     * @param type mission family
     * @param target actor-known target reference
     * @param submittedTick authoritative acceptance tick
     * @param status current mission lifecycle
     */
    public record MissionOrder(
            long id,
            SmallCraftId craftId,
            OrderSource source,
            MissionType type,
            MissionTarget target,
            long submittedTick,
            MissionStatus status) {
        /** Validates one mission row.
         * @param id positive mission identity
         * @param craftId physical craft identity
         * @param source PLAYER or AI source
         * @param type mission family
         * @param target actor-known target
         * @param submittedTick authoritative acceptance tick
         * @param status lifecycle state
         */
        public MissionOrder {
            if (id <= 0L) {
                throw new IllegalArgumentException("mission id must be positive");
            }
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(target, "target");
            if (submittedTick < 0L) {
                throw new IllegalArgumentException("submittedTick cannot be negative");
            }
            Objects.requireNonNull(status, "status");
        }

        /**
         * Returns this mission with a new lifecycle state.
         *
         * @param nextStatus replacement status
         * @return immutable mission copy
         */
        public MissionOrder withStatus(MissionStatus nextStatus) {
            return new MissionOrder(
                    id, craftId, source, type, target, submittedTick,
                    Objects.requireNonNull(nextStatus, "nextStatus"));
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
