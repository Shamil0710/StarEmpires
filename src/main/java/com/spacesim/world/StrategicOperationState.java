package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent Stage-21E operation metadata layered over ordinary physical fleets.
 *
 * <p>The state never stores combat strength, production penalties, synthetic losses or duplicate
 * fleet placement. Every participant is an existing {@link FleetId}; physical damage, stores,
 * movement and destruction remain owned by the ordinary world/Stage-19 authorities.</p>
 *
 * @param nextOperationId next positive operation identity watermark
 * @param operations canonically ordered persistent operations
 */
public record StrategicOperationState(long nextOperationId, List<OperationState> operations) {

    /**
     * Validates allocator identity and canonicalizes operation order.
     *
     * @param nextOperationId next positive operation identity watermark
     * @param operations persistent operations to validate and canonicalize
     */
    public StrategicOperationState {
        if (nextOperationId <= 0L) {
            throw new IllegalArgumentException("nextOperationId must be positive");
        }
        Objects.requireNonNull(operations, "operations");
        ArrayList<OperationState> canonical = new ArrayList<>(operations.size());
        Set<Long> ids = new HashSet<>();
        Set<Long> activeGroups = new HashSet<>();
        long maximumId = 0L;
        for (OperationState operation : operations) {
            OperationState checked = Objects.requireNonNull(operation, "operation");
            if (!ids.add(checked.id())) {
                throw new IllegalArgumentException("duplicate strategic operation id: " + checked.id());
            }
            maximumId = Math.max(maximumId, checked.id());
            if (checked.status().active() && !activeGroups.add(checked.commandGroupId())) {
                throw new IllegalArgumentException(
                        "command group has multiple active strategic operations: " + checked.commandGroupId());
            }
            canonical.add(checked);
        }
        canonical.sort(Comparator.comparingLong(OperationState::id));
        if (nextOperationId <= maximumId) {
            throw new IllegalArgumentException("nextOperationId must be above every operation id");
        }
        operations = List.copyOf(canonical);
    }

    /** @return empty current operation registry */
    public static StrategicOperationState empty() {
        return new StrategicOperationState(1L, List.of());
    }

    /**
     * Resolves one operation or fails closed.
     *
     * @param operationId positive persistent operation identity
     * @return matching persistent operation
     */
    public OperationState requireOperation(long operationId) {
        return operations.stream()
                .filter(operation -> operation.id() == operationId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown strategic operation: " + operationId));
    }

    /**
     * Finds the single active operation for one command group.
     *
     * @param commandGroupId Stage-21D command group identity
     * @return active operation when present
     */
    public Optional<OperationState> activeForCommandGroup(long commandGroupId) {
        return operations.stream()
                .filter(operation -> operation.commandGroupId() == commandGroupId && operation.status().active())
                .findFirst();
    }

    /**
     * Adds one newly allocated operation.
     *
     * @param operation operation whose identity equals the allocator watermark
     * @return immutable state containing the new operation
     */
    public StrategicOperationState add(OperationState operation) {
        OperationState checked = Objects.requireNonNull(operation, "operation");
        if (checked.id() != nextOperationId) {
            throw new IllegalArgumentException("operation id must equal allocator watermark");
        }
        ArrayList<OperationState> next = new ArrayList<>(operations);
        next.add(checked);
        return new StrategicOperationState(Math.addExact(nextOperationId, 1L), next);
    }

    /**
     * Replaces an existing operation without changing its identity.
     *
     * @param replacement replacement lifecycle state for an existing operation
     * @return immutable state containing the replacement
     */
    public StrategicOperationState replace(OperationState replacement) {
        OperationState checked = Objects.requireNonNull(replacement, "replacement");
        ArrayList<OperationState> next = new ArrayList<>(operations.size());
        boolean replaced = false;
        for (OperationState operation : operations) {
            if (operation.id() == checked.id()) {
                next.add(checked);
                replaced = true;
            } else {
                next.add(operation);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("unknown strategic operation: " + checked.id());
        }
        return new StrategicOperationState(nextOperationId, next);
    }

    /** Stage-21E physical operation families. */
    public enum OperationType {
        /** Protect ordinary traffic or another commanded force. */ ESCORT,
        /** Acquire and physically intercept an actor-known contact. */ INTERCEPTION,
        /** Attack one concrete physical target or traffic flow. */ RAID,
        /** Deny traffic through physically present blockade forces. */ BLOCKADE,
        /** Defend a concrete system or physical asset. */ DEFENSE,
        /** Move against and physically contest a territorial objective. */ INVASION
    }

    /** Persistent operation lifecycle. */
    public enum OperationStatus {
        /** Participants are assembling through ordinary movement authority. */ STAGING,
        /** Participants reached the operation area and are executing the mission. */ ACTIVE,
        /** Actor-bounded evidence identified a target contact. */ CONTACT_CONFIRMED,
        /** A Stage-19 exact-local tactical encounter has been materialized. */ ENGAGED,
        /** Ordinary fleet orders are withdrawing surviving participants. */ WITHDRAWING,
        /** Mission ended without further active effects. */ COMPLETED,
        /** Mission became impossible without inventing replacement forces/effects. */ FAILED;

        /** @return whether this status still owns the command group's active operation slot */
        public boolean active() {
            return this != COMPLETED && this != FAILED;
        }
    }

    /** Rules of engagement for one operation. */
    public enum RulesOfEngagement {
        /** Force may answer only an actual physical attack. */ SELF_DEFENSE_ONLY,
        /** Force may engage a positively identified hostile contact. */ IDENTIFIED_HOSTILES,
        /** Declared-hostile physical assets may be engaged after contact acquisition. */ DECLARED_HOSTILES
    }

    /**
     * Explicit physical supply policy; values are decision thresholds, never resource grants.
     *
     * @param minimumMissionReadinessBps minimum derived readiness required to continue
     * @param minimumSupplyAccessBps minimum observed Stage-18/logistics access required to continue
     * @param maximumUnsupportedTicks maximum continuous ticks without required supply access
     */
    public record SupplyPolicy(
            int minimumMissionReadinessBps,
            int minimumSupplyAccessBps,
            long maximumUnsupportedTicks) {
        /**
         * Validates bounded decision thresholds.
         *
         * @param minimumMissionReadinessBps minimum derived mission readiness in basis points
         * @param minimumSupplyAccessBps minimum observed supply access in basis points
         * @param maximumUnsupportedTicks maximum continuous unsupported duration
         */
        public SupplyPolicy {
            requireBps(minimumMissionReadinessBps, "minimumMissionReadinessBps");
            requireBps(minimumSupplyAccessBps, "minimumSupplyAccessBps");
            if (maximumUnsupportedTicks < 0L) {
                throw new IllegalArgumentException("maximumUnsupportedTicks must be non-negative");
            }
        }
    }

    /**
     * Explicit withdrawal policy routed through ordinary Stage-21D movement/order authority.
     *
     * @param fallbackSystemId lawful physical fallback destination
     * @param withdrawBelowReadinessBps readiness threshold that triggers withdrawal
     * @param withdrawWhenOutOfAmmunition whether zero ammunition forces withdrawal
     * @param withdrawWhenOutOfPropellant whether zero propellant makes the operation fail closed
     */
    public record WithdrawalPolicy(
            StarSystemId fallbackSystemId,
            int withdrawBelowReadinessBps,
            boolean withdrawWhenOutOfAmmunition,
            boolean withdrawWhenOutOfPropellant) {
        /**
         * Validates one physical withdrawal decision policy.
         *
         * @param fallbackSystemId lawful physical fallback destination
         * @param withdrawBelowReadinessBps readiness threshold that triggers withdrawal
         * @param withdrawWhenOutOfAmmunition whether zero ammunition forces withdrawal
         * @param withdrawWhenOutOfPropellant whether zero propellant makes the operation fail closed
         */
        public WithdrawalPolicy {
            Objects.requireNonNull(fallbackSystemId, "fallbackSystemId");
            requireBps(withdrawBelowReadinessBps, "withdrawBelowReadinessBps");
        }
    }

    /**
     * Actor-bounded target evidence retained for deterministic mid-operation continuation.
     *
     * @param targetFleetId ordinary physical fleet identity reported by the actor's observation layer
     * @param observedSystemId system in which the actor-known report located the target
     * @param channel allowed observation provenance channel
     * @param provenanceId stable source report identity
     * @param observedAtTick tick at which the actor received the report
     * @param freshUntilTick inclusive freshness horizon, or -1 for durable evidence
     */
    public record ContactState(
            FleetId targetFleetId,
            StarSystemId observedSystemId,
            ObservationChannel channel,
            String provenanceId,
            long observedAtTick,
            long freshUntilTick) {
        /**
         * Validates contact identity and provenance without consulting hidden world truth.
         *
         * @param targetFleetId ordinary physical fleet identity reported to the actor
         * @param observedSystemId actor-known system in which the target was observed
         * @param channel allowed actor-bounded observation channel
         * @param provenanceId stable source report identity
         * @param observedAtTick tick at which the actor received the report
         * @param freshUntilTick inclusive freshness horizon, or -1 for durable evidence
         */
        public ContactState {
            Objects.requireNonNull(targetFleetId, "targetFleetId");
            Objects.requireNonNull(observedSystemId, "observedSystemId");
            Objects.requireNonNull(channel, "channel");
            provenanceId = requireText(provenanceId, "provenanceId");
            if (observedAtTick < 0L) {
                throw new IllegalArgumentException("observedAtTick must be non-negative");
            }
            if (freshUntilTick < -1L || (freshUntilTick >= 0L && freshUntilTick < observedAtTick)) {
                throw new IllegalArgumentException("invalid contact freshness horizon");
            }
            if (channel != ObservationChannel.LOCAL_SENSOR_REPORT
                    && channel != ObservationChannel.INTELLIGENCE_REPORT
                    && channel != ObservationChannel.OWNED_ASSET_REPORT
                    && channel != ObservationChannel.DISCOVERY_KNOWLEDGE) {
                throw new IllegalArgumentException("contact must originate from an actor-bounded security channel");
            }
        }

        /**
         * Tests whether the retained actor-known report is current at a tick.
         *
         * @param tick authoritative non-negative review tick
         * @return true when the report exists by that tick and has not exceeded its freshness horizon
         */
        public boolean currentAt(long tick) {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            return tick >= observedAtTick && (freshUntilTick < 0L || tick <= freshUntilTick);
        }
    }

    /**
     * Persistent Stage-19 tactical encounter reference; combat state itself remains Stage-19 owned.
     *
     * @param encounterId deterministic positive encounter identity within the operation
     * @param targetFleetId actor-known opposing ordinary fleet
     * @param systemId exact local system where both sides physically met
     * @param materializedAtTick tick at which Stage-19 materialization occurred
     * @param resolvedAtTick resolution tick, or -1 while the encounter remains active
     */
    public record TacticalEncounterState(
            long encounterId,
            FleetId targetFleetId,
            StarSystemId systemId,
            long materializedAtTick,
            long resolvedAtTick) {
        /**
         * Validates a tactical reference while leaving battle contents in Stage 19.
         *
         * @param encounterId deterministic positive encounter identity
         * @param targetFleetId opposing ordinary FleetId
         * @param systemId exact physical encounter system
         * @param materializedAtTick non-negative tactical materialization tick
         * @param resolvedAtTick resolution tick, or -1 while active
         */
        public TacticalEncounterState {
            if (encounterId <= 0L) {
                throw new IllegalArgumentException("encounterId must be positive");
            }
            Objects.requireNonNull(targetFleetId, "targetFleetId");
            Objects.requireNonNull(systemId, "systemId");
            if (materializedAtTick < 0L) {
                throw new IllegalArgumentException("materializedAtTick must be non-negative");
            }
            if (resolvedAtTick < -1L || (resolvedAtTick >= 0L && resolvedAtTick < materializedAtTick)) {
                throw new IllegalArgumentException("invalid tactical resolution tick");
            }
        }

        /** @return true while the Stage-19 encounter has not returned a physical result */
        public boolean active() {
            return resolvedAtTick < 0L;
        }
    }

    /**
     * One persistent Stage-21E operation referencing only ordinary fleet/system identities.
     *
     * @param id stable positive operation identity
     * @param type physical operation family
     * @param commandGroupId Stage-21D command group that owns the operation
     * @param sourceOrderId Stage-21D order from which the operation was admitted
     * @param factionId owning faction runtime identifier
     * @param participantFleetIds non-empty ordinary participating fleets
     * @param stagingSystemId physical staging system at operation admission
     * @param objectiveSystemId physical objective system
     * @param objectiveId stable concrete objective identity, such as system or physical asset id
     * @param rulesOfEngagement explicit engagement policy
     * @param supplyPolicy explicit readiness/supply continuation policy
     * @param withdrawalPolicy explicit fallback/withdrawal policy
     * @param status current lifecycle state
     * @param createdAtTick operation creation tick
     * @param lastTransitionTick most recent lifecycle transition tick
     * @param unsupportedSinceTick first tick supply policy was unsatisfied, or -1
     * @param contact actor-bounded target contact, or null
     * @param encounter Stage-19 tactical encounter reference, or null
     */
    public record OperationState(
            long id,
            OperationType type,
            long commandGroupId,
            long sourceOrderId,
            int factionId,
            List<FleetId> participantFleetIds,
            StarSystemId stagingSystemId,
            StarSystemId objectiveSystemId,
            String objectiveId,
            RulesOfEngagement rulesOfEngagement,
            SupplyPolicy supplyPolicy,
            WithdrawalPolicy withdrawalPolicy,
            OperationStatus status,
            long createdAtTick,
            long lastTransitionTick,
            long unsupportedSinceTick,
            ContactState contact,
            TacticalEncounterState encounter) {

        /**
         * Validates and canonicalizes one operation.
         *
         * @param id stable positive operation identity
         * @param type physical operation family
         * @param commandGroupId Stage-21D command group identity
         * @param sourceOrderId accepted Stage-21D source order identity
         * @param factionId owning faction runtime identifier
         * @param participantFleetIds non-empty ordinary participating FleetIds
         * @param stagingSystemId physical staging system at admission
         * @param objectiveSystemId physical objective system
         * @param objectiveId stable concrete objective identity
         * @param rulesOfEngagement explicit engagement policy
         * @param supplyPolicy explicit physical readiness/supply policy
         * @param withdrawalPolicy explicit physical fallback/withdrawal policy
         * @param status current operation lifecycle state
         * @param createdAtTick non-negative creation tick
         * @param lastTransitionTick latest lifecycle transition tick
         * @param unsupportedSinceTick first unsupported tick, or -1
         * @param contact retained actor-bounded target contact, or null
         * @param encounter Stage-19 tactical encounter reference, or null
         */
        public OperationState {
            if (id <= 0L || commandGroupId <= 0L || sourceOrderId <= 0L) {
                throw new IllegalArgumentException("operation/group/order identities must be positive");
            }
            if (factionId < 0) {
                throw new IllegalArgumentException("factionId must be non-negative");
            }
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(stagingSystemId, "stagingSystemId");
            Objects.requireNonNull(objectiveSystemId, "objectiveSystemId");
            objectiveId = requireText(objectiveId, "objectiveId");
            Objects.requireNonNull(rulesOfEngagement, "rulesOfEngagement");
            Objects.requireNonNull(supplyPolicy, "supplyPolicy");
            Objects.requireNonNull(withdrawalPolicy, "withdrawalPolicy");
            Objects.requireNonNull(status, "status");
            if (createdAtTick < 0L || lastTransitionTick < createdAtTick) {
                throw new IllegalArgumentException("invalid operation lifecycle ticks");
            }
            if (unsupportedSinceTick < -1L || (unsupportedSinceTick >= 0L && unsupportedSinceTick < createdAtTick)) {
                throw new IllegalArgumentException("invalid unsupportedSinceTick");
            }
            Objects.requireNonNull(participantFleetIds, "participantFleetIds");
            ArrayList<FleetId> participants = new ArrayList<>(participantFleetIds);
            participants.sort(Comparator.naturalOrder());
            if (participants.isEmpty() || participants.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("operation requires physical participant fleets");
            }
            if (new HashSet<>(participants).size() != participants.size()) {
                throw new IllegalArgumentException("duplicate participant FleetId");
            }
            participantFleetIds = List.copyOf(participants);
            if (contact != null && status == OperationStatus.STAGING) {
                throw new IllegalArgumentException("staging operation cannot already own a contact");
            }
            if (encounter != null && contact == null) {
                throw new IllegalArgumentException("tactical encounter requires retained actor-bounded contact");
            }
            if (encounter != null && !encounter.targetFleetId().equals(contact.targetFleetId())) {
                throw new IllegalArgumentException("encounter target must match retained contact");
            }
            if (status == OperationStatus.ENGAGED && (encounter == null || !encounter.active())) {
                throw new IllegalArgumentException("ENGAGED operation requires an active tactical encounter");
            }
        }

        /**
         * Returns an immutable lifecycle replacement.
         *
         * @param nextStatus next operation lifecycle state
         * @param tick non-decreasing lifecycle transition tick
         * @param nextUnsupportedSinceTick next unsupported-supply watermark, or -1
         * @param nextContact retained actor-bounded contact, or null
         * @param nextEncounter retained Stage-19 tactical reference, or null
         * @return immutable operation retaining identity and physical participants
         */
        public OperationState withLifecycle(
                OperationStatus nextStatus,
                long tick,
                long nextUnsupportedSinceTick,
                ContactState nextContact,
                TacticalEncounterState nextEncounter) {
            if (tick < lastTransitionTick) {
                throw new IllegalArgumentException("operation transition cannot move backwards in time");
            }
            return new OperationState(
                    id, type, commandGroupId, sourceOrderId, factionId, participantFleetIds,
                    stagingSystemId, objectiveSystemId, objectiveId, rulesOfEngagement, supplyPolicy,
                    withdrawalPolicy, nextStatus, createdAtTick, tick, nextUnsupportedSinceTick,
                    nextContact, nextEncounter);
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireBps(int value, String label) {
        if (value < 0 || value > FleetReadinessState.FULL) {
            throw new IllegalArgumentException(label + " must be in 0..10000");
        }
    }
}
