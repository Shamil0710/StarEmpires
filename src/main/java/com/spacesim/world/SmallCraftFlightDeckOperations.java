package com.spacesim.world;

import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * M22.8C deterministic launch/recovery sequencer over the physical M22.8B hangar authority.
 *
 * <p>The service owns no clock and advances only when supplied exact completed authoritative ticks.
 * A completed launch never removes a craft from its bay by itself: it enters
 * {@link OperationPhase#AWAITING_HANDOFF} until the later physical local-flight integration confirms
 * the handoff. Recovery is offered only by the physical world seam and consumes bay capacity only
 * when its safe recovery cycle actually starts.</p>
 */
public final class SmallCraftFlightDeckOperations {
    private static final double EPSILON = 1e-9d;

    private final SmallCraftHangarRegistry hangars;
    private final TreeMap<BayId, DeckProfile> profiles = new TreeMap<>();
    private final TreeSet<Request> queue = new TreeSet<>();
    private final TreeMap<BayId, ActiveOperation> activeByBay = new TreeMap<>();

    /**
     * Creates one sequencer over existing physical hangar occupancy.
     *
     * @param hangars M22.8B individual physical occupancy authority
     * @param profiles explicit physical handling profiles by bay
     */
    public SmallCraftFlightDeckOperations(
            SmallCraftHangarRegistry hangars,
            Collection<DeckProfile> profiles) {
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        Objects.requireNonNull(profiles, "profiles");
        for (DeckProfile profile : profiles) {
            DeckProfile checked = Objects.requireNonNull(profile, "profile");
            if (this.profiles.putIfAbsent(checked.bayId(), checked) != null) {
                throw new IllegalArgumentException(
                        "Duplicate flight-deck profile for " + checked.bayId());
            }
        }
    }

    /** Physical operation kind sharing one safe bay sequence. */
    public enum OperationKind {
        /** Returning craft receives safety priority at equal request tick. */ RECOVERY,
        /** Embarked ready craft leaves only after physical handoff confirmation. */ LAUNCH
    }

    /** Finite operation state for one bay. */
    public enum OperationPhase {
        /** Waiting for the bay's single handling sequence. */ QUEUED,
        /** Physical launch/recovery handling work is progressing. */ CYCLING,
        /** Launch cycle completed but craft remains embarked until physical handoff. */ AWAITING_HANDOFF,
        /** Failed recovery blocks the bay until a physical-world resolution is supplied. */ FAILED_BLOCKED
    }

    /** Stable deterministic recovery failure classifications. */
    public enum RecoveryFailureKind {
        /** Craft missed or aborted the physical capture approach. */ MISSED_APPROACH,
        /** Mechanical/deck capture system failed while the craft was in the handling envelope. */ CAPTURE_SYSTEM_FAILURE,
        /** Physical contact/collision occurred during recovery. */ DECK_CONTACT
    }

    /**
     * Authored physical handling throughput for one bay.
     *
     * <p>Work seconds are pristine-condition handling work, not wall-clock delays. Each authoritative
     * tick completes {@code fixedStepSeconds * conditionFraction} work, so real bay damage slows
     * handling and zero integrity stops it without a class-name modifier.</p>
     *
     * @param bayId stable physical bay identity
     * @param launchWorkSeconds pristine launch handling work
     * @param recoveryWorkSeconds pristine recovery handling work
     */
    public record DeckProfile(
            BayId bayId,
            double launchWorkSeconds,
            double recoveryWorkSeconds) implements Comparable<DeckProfile> {
        /** Validates one positive finite handling profile.
         * @param bayId stable physical bay identity
         * @param launchWorkSeconds pristine launch work
         * @param recoveryWorkSeconds pristine recovery work
         */
        public DeckProfile {
            Objects.requireNonNull(bayId, "bayId");
            requirePositive(launchWorkSeconds, "launchWorkSeconds");
            requirePositive(recoveryWorkSeconds, "recoveryWorkSeconds");
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(DeckProfile other) {
            return bayId.compareTo(Objects.requireNonNull(other, "other").bayId);
        }

        double workSeconds(OperationKind kind) {
            return kind == OperationKind.LAUNCH ? launchWorkSeconds : recoveryWorkSeconds;
        }
    }

    /**
     * Deterministic queued physical operation.
     *
     * @param craftId individual persistent craft
     * @param bayId target physical bay
     * @param kind launch or recovery
     * @param requestedTick authoritative request tick
     */
    public record Request(
            SmallCraftId craftId,
            BayId bayId,
            OperationKind kind,
            long requestedTick) implements Comparable<Request> {
        /** Validates one deterministic request.
         * @param craftId individual craft
         * @param bayId physical bay
         * @param kind operation kind
         * @param requestedTick authoritative request tick
         */
        public Request {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(bayId, "bayId");
            Objects.requireNonNull(kind, "kind");
            if (requestedTick < 0L) {
                throw new IllegalArgumentException("requestedTick cannot be negative");
            }
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(Request other) {
            Request checked = Objects.requireNonNull(other, "other");
            int tick = Long.compare(requestedTick, checked.requestedTick);
            if (tick != 0) {
                return tick;
            }
            int bay = bayId.compareTo(checked.bayId);
            if (bay != 0) {
                return bay;
            }
            int kindOrder = Integer.compare(kind.ordinal(), checked.kind.ordinal());
            if (kindOrder != 0) {
                return kindOrder;
            }
            return craftId.compareTo(checked.craftId);
        }
    }

    /**
     * Current physical operation occupying one bay handling sequence.
     *
     * @param request source request
     * @param phase current finite phase
     * @param remainingWorkSeconds remaining pristine-equivalent handling work
     * @param failureKind recovery failure when blocked, otherwise null
     */
    public record ActiveOperation(
            Request request,
            OperationPhase phase,
            double remainingWorkSeconds,
            RecoveryFailureKind failureKind) {
        /** Validates one active operation.
         * @param request source request
         * @param phase finite operation phase
         * @param remainingWorkSeconds non-negative handling work
         * @param failureKind recovery failure only for FAILED_BLOCKED
         */
        public ActiveOperation {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(phase, "phase");
            requireNonNegative(remainingWorkSeconds, "remainingWorkSeconds");
            if ((phase == OperationPhase.FAILED_BLOCKED) != (failureKind != null)) {
                throw new IllegalArgumentException(
                        "failureKind presence must match FAILED_BLOCKED phase");
            }
            if (phase == OperationPhase.AWAITING_HANDOFF
                    && request.kind() != OperationKind.LAUNCH) {
                throw new IllegalArgumentException(
                        "Only launch may await local-flight handoff");
            }
            if (phase == OperationPhase.FAILED_BLOCKED
                    && request.kind() != OperationKind.RECOVERY) {
                throw new IllegalArgumentException(
                        "Only recovery may enter failed-blocked state");
            }
        }
    }

    /**
     * Queues one already embarked ready craft for launch.
     *
     * @param craftId individual craft identity
     * @param bayId current occupied bay
     * @param requestedTick exact authoritative request tick
     */
    public void requestLaunch(SmallCraftId craftId, BayId bayId, long requestedTick) {
        SmallCraftId checkedCraft = Objects.requireNonNull(craftId, "craftId");
        BayId checkedBay = requireProfile(bayId).bayId();
        var assignment = hangars.find(checkedCraft).orElseThrow(
                () -> new IllegalArgumentException("Launch craft is not embarked: " + checkedCraft));
        if (!assignment.bayId().equals(checkedBay)
                || assignment.state() != OccupancyState.READY) {
            throw new IllegalArgumentException(
                    "Launch requires READY craft in requested bay: " + checkedCraft);
        }
        enqueueUnique(new Request(
                checkedCraft, checkedBay, OperationKind.LAUNCH, requestedTick));
    }

    /**
     * Cancels a launch that has not crossed the physical handoff boundary.
     *
     * @param craftId launch craft identity
     * @return whether a queued/cycling launch was cancelled
     */
    public boolean cancelLaunch(SmallCraftId craftId) {
        SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
        Request queued = queue.stream()
                .filter(value -> value.craftId().equals(checked)
                        && value.kind() == OperationKind.LAUNCH)
                .findFirst().orElse(null);
        if (queued != null) {
            queue.remove(queued);
            return true;
        }
        ActiveOperation active = activeByBay.values().stream()
                .filter(value -> value.request().craftId().equals(checked)
                        && value.request().kind() == OperationKind.LAUNCH)
                .findFirst().orElse(null);
        if (active == null || active.phase() == OperationPhase.AWAITING_HANDOFF) {
            return false;
        }
        hangars.transition(checked, OccupancyState.READY);
        activeByBay.remove(active.request().bayId());
        return true;
    }

    /**
     * Offers a craft that the physical local-flight authority has brought to a recovery gate.
     *
     * <p>The craft is not teleported into the hangar here. It remains outside occupancy while queued;
     * capacity and envelope are rechecked when its recovery cycle actually starts.</p>
     *
     * @param craftId physical craft at the recovery gate
     * @param bay current target bay projection
     * @param requestedTick exact authoritative request tick
     */
    void offerPhysicalRecovery(
            SmallCraftId craftId,
            BayDefinition bay,
            long requestedTick) {
        SmallCraftId checkedCraft = Objects.requireNonNull(craftId, "craftId");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        requireProfile(checkedBay.id());
        if (hangars.find(checkedCraft).isPresent()) {
            throw new IllegalArgumentException(
                    "Recovery craft is already embarked: " + checkedCraft);
        }
        if (!hangars.canAccept(checkedCraft, checkedBay)) {
            throw new IllegalArgumentException(
                    "Recovery craft cannot fit current bay capacity: " + checkedCraft);
        }
        enqueueUnique(new Request(
                checkedCraft, checkedBay.id(), OperationKind.RECOVERY, requestedTick));
    }

    /**
     * Advances physical deck work after exactly one authoritative fixed tick.
     *
     * @param authoritativeTick completed world tick
     * @param fixedStepSeconds accepted simulation fixed-step seconds
     * @param bayDefinitions current physical bay projections including damage
     */
    public void advanceFixedTick(
            long authoritativeTick,
            double fixedStepSeconds,
            Map<BayId, BayDefinition> bayDefinitions) {
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick cannot be negative");
        }
        requirePositive(fixedStepSeconds, "fixedStepSeconds");
        Map<BayId, BayDefinition> checkedBays = immutableBayMap(bayDefinitions);

        for (DeckProfile profile : profiles.values()) {
            BayDefinition bay = checkedBays.get(profile.bayId());
            if (bay == null) {
                continue;
            }
            ActiveOperation active = activeByBay.get(profile.bayId());
            if (active == null) {
                Request next = nextRequest(profile.bayId(), authoritativeTick);
                if (next == null || bay.conditionFraction() <= EPSILON) {
                    continue;
                }
                start(next, bay, profile);
                active = activeByBay.get(profile.bayId());
            }
            if (active == null
                    || active.phase() == OperationPhase.AWAITING_HANDOFF
                    || active.phase() == OperationPhase.FAILED_BLOCKED
                    || bay.conditionFraction() <= EPSILON) {
                continue;
            }
            double completedWork = fixedStepSeconds * bay.conditionFraction();
            double remaining = Math.max(0d, active.remainingWorkSeconds() - completedWork);
            if (remaining > EPSILON) {
                activeByBay.put(profile.bayId(), new ActiveOperation(
                        active.request(), OperationPhase.CYCLING, remaining, null));
            } else {
                completeCycle(active);
            }
        }
    }

    /**
     * @return deterministic immutable queued operations
     */
    public List<Request> queued() {
        return List.copyOf(queue);
    }

    /**
     * @return deterministic immutable active operations ordered by bay ID
     */
    public List<ActiveOperation> active() {
        return List.copyOf(activeByBay.values());
    }

    /**
     * @param craftId individual craft identity
     * @return current active operation for that craft
     */
    public Optional<ActiveOperation> activeFor(SmallCraftId craftId) {
        SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
        return activeByBay.values().stream()
                .filter(value -> value.request().craftId().equals(checked))
                .findFirst();
    }

    /**
     * Confirms Stage-19/local-flight materialization after a launch cycle completed.
     *
     * <p>This package-level handoff is intentionally unavailable to command/UI code. Only after the
     * physical-world integration has created the same craft in local flight may occupancy be removed.</p>
     *
     * @param craftId launched craft identity
     */
    void confirmLaunchHandoff(SmallCraftId craftId) {
        ActiveOperation active = requireActive(craftId, OperationKind.LAUNCH);
        if (active.phase() != OperationPhase.AWAITING_HANDOFF) {
            throw new IllegalStateException(
                    "Launch handoff requested before physical cycle completion");
        }
        hangars.release(active.request().craftId());
        activeByBay.remove(active.request().bayId());
    }

    /**
     * Reports a physical recovery failure without teleporting the craft to PARKED/READY.
     *
     * <p>The craft remains in RECOVERING occupancy and the bay remains blocked until the physical
     * world resolves the consequence. Later Stage-19 integration may apply actual damage/loss or
     * confirm departure/diversion.</p>
     *
     * @param craftId recovering craft identity
     * @param failureKind physical failure class
     */
    void reportRecoveryFailure(
            SmallCraftId craftId,
            RecoveryFailureKind failureKind) {
        ActiveOperation active = requireActive(craftId, OperationKind.RECOVERY);
        if (active.phase() != OperationPhase.CYCLING) {
            throw new IllegalStateException("Recovery failure requires active cycling");
        }
        activeByBay.put(active.request().bayId(), new ActiveOperation(
                active.request(),
                OperationPhase.FAILED_BLOCKED,
                active.remainingWorkSeconds(),
                Objects.requireNonNull(failureKind, "failureKind")));
    }

    /**
     * Confirms that a failed recovery physically departed/diverted from the bay handling envelope.
     *
     * @param craftId recovering craft identity
     */
    void confirmFailedRecoveryDeparture(SmallCraftId craftId) {
        ActiveOperation active = requireActive(craftId, OperationKind.RECOVERY);
        if (active.phase() != OperationPhase.FAILED_BLOCKED) {
            throw new IllegalStateException(
                    "Failed recovery departure requires FAILED_BLOCKED state");
        }
        hangars.release(active.request().craftId());
        activeByBay.remove(active.request().bayId());
    }

    private void start(Request request, BayDefinition bay, DeckProfile profile) {
        if (request.kind() == OperationKind.LAUNCH) {
            var assignment = hangars.find(request.craftId()).orElseThrow(
                    () -> new IllegalStateException(
                            "Queued launch craft is no longer embarked: " + request.craftId()));
            if (!assignment.bayId().equals(request.bayId())
                    || assignment.state() != OccupancyState.READY) {
                throw new IllegalStateException(
                        "Queued launch no longer references READY craft in bay");
            }
            hangars.transition(request.craftId(), OccupancyState.LAUNCHING);
        } else {
            if (!hangars.canAccept(request.craftId(), bay)) {
                return;
            }
            hangars.assign(request.craftId(), bay, OccupancyState.RECOVERING);
        }
        queue.remove(request);
        activeByBay.put(request.bayId(), new ActiveOperation(
                request,
                OperationPhase.CYCLING,
                profile.workSeconds(request.kind()),
                null));
    }

    private void completeCycle(ActiveOperation active) {
        Request request = active.request();
        if (request.kind() == OperationKind.LAUNCH) {
            activeByBay.put(request.bayId(), new ActiveOperation(
                    request, OperationPhase.AWAITING_HANDOFF, 0d, null));
        } else {
            hangars.transition(request.craftId(), OccupancyState.SERVICING);
            activeByBay.remove(request.bayId());
        }
    }

    private Request nextRequest(BayId bayId, long authoritativeTick) {
        return queue.stream()
                .filter(value -> value.bayId().equals(bayId)
                        && value.requestedTick() <= authoritativeTick)
                .findFirst().orElse(null);
    }

    private void enqueueUnique(Request request) {
        boolean duplicate = queue.stream().anyMatch(value -> value.craftId().equals(request.craftId()))
                || activeByBay.values().stream()
                        .anyMatch(value -> value.request().craftId().equals(request.craftId()));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Craft already has launch/recovery operation: " + request.craftId());
        }
        queue.add(request);
    }

    private ActiveOperation requireActive(SmallCraftId craftId, OperationKind kind) {
        SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
        return activeByBay.values().stream()
                .filter(value -> value.request().craftId().equals(checked)
                        && value.request().kind() == kind)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Craft has no active " + kind + " operation: " + checked));
    }

    private DeckProfile requireProfile(BayId bayId) {
        BayId checked = Objects.requireNonNull(bayId, "bayId");
        DeckProfile profile = profiles.get(checked);
        if (profile == null) {
            throw new IllegalArgumentException("No flight-deck profile for " + checked);
        }
        return profile;
    }

    private static Map<BayId, BayDefinition> immutableBayMap(
            Map<BayId, BayDefinition> bayDefinitions) {
        Objects.requireNonNull(bayDefinitions, "bayDefinitions");
        TreeMap<BayId, BayDefinition> result = new TreeMap<>();
        for (Map.Entry<BayId, BayDefinition> entry : bayDefinitions.entrySet()) {
            BayId id = Objects.requireNonNull(entry.getKey(), "bay ID");
            BayDefinition bay = Objects.requireNonNull(entry.getValue(), "bay definition");
            if (!id.equals(bay.id())) {
                throw new IllegalArgumentException(
                        "Bay map key disagrees with definition identity");
            }
            result.put(id, bay);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
