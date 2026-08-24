package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Deterministic Stage-21E reconciliation of operation consequences from ordinary physical fleets.
 *
 * <p>The service applies no damage and removes no assets. It compares two authoritative
 * {@link FleetForceRegistry} reconstructions and reports only what the ordinary world actually
 * contains. A fleet loss is therefore reportable only when the exact {@link FleetId} disappeared;
 * damage/ammunition/propellant/crew changes are the existing Stage-21D readiness projections of
 * before/after physical payloads rather than invented combat percentages.</p>
 */
public final class Stage21EPhysicalConsequenceService {

    /**
     * Reconciles every operation participant plus the retained hostile contact across before/after state.
     *
     * <p>The contact target is included when present so a physically destroyed opposing FleetId is
     * represented by the same ordinary-world evidence rule as an operation participant. No target is
     * inferred from hidden world truth.</p>
     *
     * @param operation operation whose explicit participant/contact identities bound the comparison
     * @param before physical force registry captured before tactical/ordinary effects
     * @param after physical force registry captured after those effects
     * @return canonical physical consequence report
     */
    public ConsequenceReport reconcile(
            StrategicOperationState.OperationState operation,
            FleetForceRegistry before,
            FleetForceRegistry after) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        TreeSet<FleetId> combatants = new TreeSet<>(operation.participantFleetIds());
        if (operation.contact() != null) {
            combatants.add(operation.contact().targetFleetId());
        }
        ArrayList<FleetConsequence> rows = new ArrayList<>();
        for (FleetId fleetId : combatants) {
            FleetForceRegistry.Entry previous = before.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException(
                            "operation/contact fleet was absent before physical reconciliation: " + fleetId));
            FleetForceRegistry.Entry current = after.find(fleetId).orElse(null);
            if (current == null) {
                rows.add(new FleetConsequence(
                        fleetId,
                        previous.entityState().id(),
                        null,
                        previous.readiness(),
                        null,
                        true,
                        true));
                continue;
            }
            if (current.factionId() != previous.factionId()) {
                throw new IllegalStateException(
                        "ordinary fleet ownership changed during consequence reconciliation: " + fleetId);
            }
            rows.add(new FleetConsequence(
                    fleetId,
                    previous.entityState().id(),
                    current.entityState().id(),
                    previous.readiness(),
                    current.readiness(),
                    false,
                    !previous.entityState().equals(current.entityState())));
        }
        return new ConsequenceReport(operation.id(), List.copyOf(rows));
    }

    /**
     * One exact fleet outcome derived from ordinary physical state.
     *
     * @param fleetId stable ordinary fleet identity
     * @param beforeEntityId physical entity id before the outcome
     * @param afterEntityId physical entity id after the outcome, or null for a destroyed/removed fleet
     * @param beforeReadiness readiness derived from the before physical payload
     * @param afterReadiness readiness derived from the after physical payload, or null for a loss
     * @param destroyed whether the ordinary fleet/entity is absent afterwards
     * @param physicalPayloadChanged whether exact ordinary entity state changed or disappeared
     */
    public record FleetConsequence(
            FleetId fleetId,
            EntityId beforeEntityId,
            EntityId afterEntityId,
            FleetReadinessState beforeReadiness,
            FleetReadinessState afterReadiness,
            boolean destroyed,
            boolean physicalPayloadChanged) {
        /**
         * Validates a consequence row and forbids synthetic loss reports.
         *
         * @param fleetId stable ordinary fleet identity
         * @param beforeEntityId physical entity identity before the outcome
         * @param afterEntityId physical entity identity after the outcome, or null for loss
         * @param beforeReadiness readiness derived from the before physical payload
         * @param afterReadiness readiness derived from the after physical payload, or null for loss
         * @param destroyed whether the ordinary FleetId is absent afterwards
         * @param physicalPayloadChanged whether the exact ordinary payload changed or disappeared
         */
        public FleetConsequence {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(beforeEntityId, "beforeEntityId");
            Objects.requireNonNull(beforeReadiness, "beforeReadiness");
            if (destroyed) {
                if (afterEntityId != null || afterReadiness != null || !physicalPayloadChanged) {
                    throw new IllegalArgumentException("destroyed fleet must be absent from after physical state");
                }
            } else {
                Objects.requireNonNull(afterEntityId, "afterEntityId");
                Objects.requireNonNull(afterReadiness, "afterReadiness");
            }
        }

        /** @return physical structural change in basis points, negative when damaged */
        public int structuralDeltaBps() {
            return destroyed ? -beforeReadiness.structuralBps()
                    : afterReadiness.structuralBps() - beforeReadiness.structuralBps();
        }

        /** @return physical ammunition change in basis points, negative when ammunition was consumed */
        public int ammunitionDeltaBps() {
            return destroyed ? -beforeReadiness.ammunitionBps()
                    : afterReadiness.ammunitionBps() - beforeReadiness.ammunitionBps();
        }

        /** @return physical reaction-mass change in basis points, negative when propellant was consumed */
        public int propellantDeltaBps() {
            return destroyed ? -beforeReadiness.propellantBps()
                    : afterReadiness.propellantBps() - beforeReadiness.propellantBps();
        }

        /** @return physical crew-availability change in basis points, negative when availability fell */
        public int crewDeltaBps() {
            return destroyed ? -beforeReadiness.crewBps()
                    : afterReadiness.crewBps() - beforeReadiness.crewBps();
        }
    }

    /**
     * Canonical consequence set for one operation encounter.
     *
     * @param operationId operation identity
     * @param fleets explicit participant/contact rows in stable FleetId order
     */
    public record ConsequenceReport(long operationId, List<FleetConsequence> fleets) {
        /**
         * Validates and canonicalizes a consequence report.
         *
         * @param operationId positive strategic operation identity
         * @param fleets physical participant/contact outcome rows
         */
        public ConsequenceReport {
            if (operationId <= 0L) {
                throw new IllegalArgumentException("operationId must be positive");
            }
            Objects.requireNonNull(fleets, "fleets");
            ArrayList<FleetConsequence> canonical = new ArrayList<>(fleets);
            canonical.sort(java.util.Comparator.comparing(FleetConsequence::fleetId));
            if (canonical.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("consequence row cannot be null");
            }
            fleets = List.copyOf(canonical);
        }

        /** @return surviving ordinary fleet identities */
        public List<FleetId> survivors() {
            return fleets.stream().filter(row -> !row.destroyed()).map(FleetConsequence::fleetId).toList();
        }

        /** @return ordinary fleet identities actually absent after reconciliation */
        public List<FleetId> losses() {
            return fleets.stream().filter(FleetConsequence::destroyed).map(FleetConsequence::fleetId).toList();
        }
    }

    /**
     * Exact ordinary entity payload pair useful to callers that must audit material conservation.
     *
     * @param before pre-effect physical entity payload
     * @param after post-effect physical entity payload, or null when the ordinary entity was destroyed
     */
    public record PhysicalPayloadPair(EntityState before, EntityState after) {
        /**
         * Validates the required pre-effect authority payload.
         *
         * @param before pre-effect physical entity payload
         * @param after post-effect physical entity payload, or null when destroyed
         */
        public PhysicalPayloadPair {
            Objects.requireNonNull(before, "before");
        }
    }
}
