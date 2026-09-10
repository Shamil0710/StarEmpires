package com.spacesim.world;

import com.spacesim.persistence.EntityState;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stage-21E gate between actor-known strategic contact and Stage-19 exact-local materialization.
 *
 * <p>The service performs no combat. It verifies that the retained actor-bounded contact is current,
 * that both sides now physically occupy the same ordinary system, and then hands exact persisted
 * {@link EntityState} payloads to the injected Stage-19 authority. An implementation that cannot
 * materialize those exact fitted payloads must reject the request; substituting an acceptance fit or
 * an abstract strength value is outside this contract.</p>
 */
public final class Stage21ETacticalMaterializationService {

    /**
     * Materializes one exact-local encounter after contact and physical co-location are both proven.
     *
     * @param operation operation in CONTACT_CONFIRMED state
     * @param forces current ordinary physical force reconstruction
     * @param currentTick authoritative world tick
     * @param authority existing Stage-19 exact-local materialization authority
     * @return deterministic encounter reference to persist in Stage-21E state
     */
    public TacticalEncounterState materialize(
            OperationState operation,
            FleetForceRegistry forces,
            long currentTick,
            TacticalMaterializationAuthority authority) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(forces, "forces");
        Objects.requireNonNull(authority, "authority");
        if (currentTick < 0L) {
            throw new IllegalArgumentException("currentTick must be non-negative");
        }
        if (operation.status() != OperationStatus.CONTACT_CONFIRMED || operation.contact() == null) {
            throw new IllegalStateException("tactical materialization requires confirmed actor-bounded contact");
        }
        ContactState contact = operation.contact();
        if (!contact.currentAt(currentTick)) {
            throw new IllegalStateException("actor-bounded target contact is stale");
        }

        FleetForceRegistry.Entry target = forces.find(contact.targetFleetId())
                .orElseThrow(() -> new IllegalStateException("confirmed target no longer exists as an ordinary FleetId"));
        if (target.locationKind() != FleetLocationKind.IN_SYSTEM || target.systemId() == null) {
            throw new IllegalStateException("confirmed target is not physically materialized in a system");
        }
        if (!contact.observedSystemId().equals(target.systemId())) {
            throw new IllegalStateException("physical target moved away from the actor-known contact system");
        }
        if (target.factionId() == operation.factionId()) {
            throw new IllegalStateException("operation cannot materialize combat against its own physical fleet");
        }

        ArrayList<PhysicalCombatant> participants = new ArrayList<>();
        for (FleetId fleetId : operation.participantFleetIds()) {
            FleetForceRegistry.Entry attacker = forces.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException("operation participant no longer exists: " + fleetId));
            if (attacker.factionId() != operation.factionId()) {
                throw new IllegalStateException("operation participant owner changed: " + fleetId);
            }
            if (attacker.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !target.systemId().equals(attacker.systemId())) {
                throw new IllegalStateException("combatants have not physically met in one system");
            }
            participants.add(new PhysicalCombatant(
                    fleetId, CombatSide.OPERATION, attacker.factionId(), attacker.entityState()));
        }
        participants.add(new PhysicalCombatant(
                target.fleetId(), CombatSide.CONTACT, target.factionId(), target.entityState()));
        participants.sort(Comparator.comparing(PhysicalCombatant::fleetId));

        TacticalMaterializationRequest request = new TacticalMaterializationRequest(
                operation.id(), target.systemId(), currentTick, List.copyOf(participants));
        long encounterId = authority.materializeExact(request);
        if (encounterId <= 0L) {
            throw new IllegalStateException("Stage-19 materialization authority returned invalid encounter identity");
        }
        return new TacticalEncounterState(
                encounterId, target.fleetId(), target.systemId(), currentTick, -1L);
    }

    /** Side identity within one Stage-21E handoff; it grants no physical modifier. */
    public enum CombatSide {
        /** Fleets participating in the admitted strategic operation. */ OPERATION,
        /** Physically met fleet represented by the retained actor-known contact. */ CONTACT
    }

    /**
     * Exact ordinary physical fleet payload handed to Stage 19.
     *
     * @param fleetId stable ordinary fleet identity
     * @param side handoff side identity only
     * @param factionId current physical faction affiliation
     * @param entityState exact current persistent entity payload including fit/damage/stores
     */
    public record PhysicalCombatant(
            FleetId fleetId,
            CombatSide side,
            int factionId,
            EntityState entityState) {
        /**
         * Validates an exact physical handoff row.
         *
         * @param fleetId stable ordinary fleet identity
         * @param side tactical handoff side identity only
         * @param factionId current physical faction affiliation
         * @param entityState exact current persistent entity payload including engineering state
         */
        public PhysicalCombatant {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(entityState, "entityState");
            if (factionId < 0) {
                throw new IllegalArgumentException("factionId must be non-negative");
            }
            if (entityState.engineering() == null) {
                throw new IllegalArgumentException("tactical combatant requires persisted physical engineering state");
            }
        }
    }

    /**
     * Exact-local Stage-19 handoff request.
     *
     * @param operationId owning Stage-21E operation
     * @param systemId physical system where every combatant is currently materialized
     * @param materializedAtTick exact handoff tick
     * @param combatants exact physical payloads in canonical FleetId order
     */
    public record TacticalMaterializationRequest(
            long operationId,
            StarSystemId systemId,
            long materializedAtTick,
            List<PhysicalCombatant> combatants) {
        /**
         * Validates canonical exact-local request shape.
         *
         * @param operationId positive owning Stage-21E operation identity
         * @param systemId physical system containing every combatant
         * @param materializedAtTick exact non-negative handoff tick
         * @param combatants exact physical payloads in canonical FleetId order
         */
        public TacticalMaterializationRequest {
            if (operationId <= 0L || materializedAtTick < 0L) {
                throw new IllegalArgumentException("invalid operation/tick identity");
            }
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(combatants, "combatants");
            if (combatants.size() < 2 || combatants.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("tactical request requires at least two physical combatants");
            }
            ArrayList<PhysicalCombatant> canonical = new ArrayList<>(combatants);
            canonical.sort(Comparator.comparing(PhysicalCombatant::fleetId));
            if (!canonical.equals(combatants)) {
                throw new IllegalArgumentException("tactical combatants must be in canonical FleetId order");
            }
            if (canonical.stream().map(PhysicalCombatant::fleetId).distinct().count() != canonical.size()) {
                throw new IllegalArgumentException("tactical request contains a duplicate FleetId");
            }
            long operationCount = combatants.stream().filter(row -> row.side() == CombatSide.OPERATION).count();
            long contactCount = combatants.stream().filter(row -> row.side() == CombatSide.CONTACT).count();
            if (operationCount <= 0L || contactCount <= 0L) {
                throw new IllegalArgumentException("tactical request requires both physical sides");
            }
            combatants = List.copyOf(canonical);
        }
    }

    /**
     * Minimal extension seam to the existing Stage-19 exact-local combat authority.
     *
     * <p>The implementation MUST materialize the supplied exact {@link EntityState} engineering,
     * damage, ammunition, propellant and other physical state. It MUST fail closed when current
     * Stage-19 content/runtime cannot represent an imported fit. It MUST NOT substitute a doctrine
     * fixture, strength score or statistical resolver. Stage 21E intentionally does not own the
     * returned tactical runtime.</p>
     */
    @FunctionalInterface
    public interface TacticalMaterializationAuthority {
        /**
         * Performs the exact Stage-19 materialization or fails closed.
         *
         * @param request exact ordinary physical payload handoff
         * @return positive stable encounter identity
         */
        long materializeExact(TacticalMaterializationRequest request);
    }
}
