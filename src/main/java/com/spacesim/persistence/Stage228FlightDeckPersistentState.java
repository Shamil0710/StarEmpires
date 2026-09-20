package com.spacesim.persistence;

import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftFlightDeckOperations.RecoveryFailureKind;
import com.spacesim.world.SmallCraftId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Versioned M22.8C persistence sidecar for physical flight-deck queue/operation continuity.
 *
 * <p>The sidecar stores only deterministic handling state. It does not create craft, bay occupancy,
 * fuel, ammunition, repair material or local-flight entities.</p>
 *
 * @param schemaVersion exact sidecar schema
 * @param runtimeVersion exact runtime contract
 * @param semanticContract explicit no-teleport queue contract
 * @param lastProcessedTick last authoritative tick consumed by deck operations, or -1
 * @param profiles physical deck handling profiles
 * @param queued queued launch/recovery requests
 * @param active active per-bay operations
 */
public record Stage228FlightDeckPersistentState(
        int schemaVersion,
        String runtimeVersion,
        String semanticContract,
        long lastProcessedTick,
        List<DeckProfileState> profiles,
        List<RequestState> queued,
        List<ActiveState> active) {

    /** Current M22.8C flight-deck sidecar schema. */
    public static final int CURRENT_VERSION = 1;
    /** Current M22.8C runtime identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8c.flight-deck.v1";
    /** Stable semantic contract for deterministic physical handling continuity. */
    public static final String CURRENT_SEMANTIC_CONTRACT =
            "per-bay-queue|fixed-tick-work|physical-handoff|failed-recovery-blocks|no-teleport";

    /** Persistent physical handling profile for one bay. */
    public record DeckProfileState(
            String hostStableId,
            String bayStableId,
            double launchWorkSeconds,
            double recoveryWorkSeconds) implements Comparable<DeckProfileState> {
        /** Validates one profile row.
         * @param hostStableId stable host identity
         * @param bayStableId host-local bay identity
         * @param launchWorkSeconds positive launch work
         * @param recoveryWorkSeconds positive recovery work
         */
        public DeckProfileState {
            hostStableId = requireText(hostStableId, "hostStableId");
            bayStableId = requireText(bayStableId, "bayStableId");
            requirePositive(launchWorkSeconds, "launchWorkSeconds");
            requirePositive(recoveryWorkSeconds, "recoveryWorkSeconds");
        }

        @Override
        public int compareTo(DeckProfileState other) {
            int host = hostStableId.compareTo(Objects.requireNonNull(other, "other").hostStableId);
            return host != 0 ? host : bayStableId.compareTo(other.bayStableId);
        }
    }

    /** Persistent queued request. */
    public record RequestState(
            SmallCraftId craftId,
            String hostStableId,
            String bayStableId,
            OperationKind kind,
            long requestedTick) implements Comparable<RequestState> {
        /** Validates one queue row.
         * @param craftId individual craft
         * @param hostStableId stable host identity
         * @param bayStableId host-local bay identity
         * @param kind operation kind
         * @param requestedTick authoritative request tick
         */
        public RequestState {
            Objects.requireNonNull(craftId, "craftId");
            hostStableId = requireText(hostStableId, "hostStableId");
            bayStableId = requireText(bayStableId, "bayStableId");
            Objects.requireNonNull(kind, "kind");
            if (requestedTick < 0L) {
                throw new IllegalArgumentException("requestedTick cannot be negative");
            }
        }

        @Override
        public int compareTo(RequestState other) {
            RequestState checked = Objects.requireNonNull(other, "other");
            int tick = Long.compare(requestedTick, checked.requestedTick);
            if (tick != 0) {
                return tick;
            }
            int host = hostStableId.compareTo(checked.hostStableId);
            if (host != 0) {
                return host;
            }
            int bay = bayStableId.compareTo(checked.bayStableId);
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

    /** Persistent active per-bay handling operation. */
    public record ActiveState(
            RequestState request,
            OperationPhase phase,
            double remainingWorkSeconds,
            RecoveryFailureKind failureKind) implements Comparable<ActiveState> {
        /** Validates one active operation row.
         * @param request source request
         * @param phase finite current phase
         * @param remainingWorkSeconds non-negative remaining work
         * @param failureKind failure only for FAILED_BLOCKED
         */
        public ActiveState {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(phase, "phase");
            requireNonNegative(remainingWorkSeconds, "remainingWorkSeconds");
            if (phase == OperationPhase.QUEUED) {
                throw new IllegalArgumentException(
                        "Active flight-deck operation cannot use QUEUED phase");
            }
            if (phase == OperationPhase.CYCLING && remainingWorkSeconds <= 1e-9d) {
                throw new IllegalArgumentException(
                        "Persisted CYCLING operation must retain positive work");
            }
            if (phase == OperationPhase.AWAITING_HANDOFF) {
                if (request.kind() != OperationKind.LAUNCH) {
                    throw new IllegalArgumentException(
                            "Only persisted launch may await physical handoff");
                }
                if (remainingWorkSeconds > 1e-9d) {
                    throw new IllegalArgumentException(
                            "Persisted handoff-ready launch cannot retain deck work");
                }
            }
            if (phase == OperationPhase.FAILED_BLOCKED
                    && request.kind() != OperationKind.RECOVERY) {
                throw new IllegalArgumentException(
                        "Only persisted recovery may block after failure");
            }
            if ((phase == OperationPhase.FAILED_BLOCKED) != (failureKind != null)) {
                throw new IllegalArgumentException(
                        "failureKind presence must match FAILED_BLOCKED");
            }
        }

        @Override
        public int compareTo(ActiveState other) {
            ActiveState checked = Objects.requireNonNull(other, "other");
            int host = request.hostStableId().compareTo(checked.request.hostStableId());
            return host != 0
                    ? host
                    : request.bayStableId().compareTo(checked.request.bayStableId());
        }
    }

    /**
     * Compatibility constructor for pre-watermark call sites inside the unmerged C slice.
     *
     * @param schemaVersion exact schema
     * @param runtimeVersion exact runtime ID
     * @param semanticContract semantic contract
     * @param profiles physical profiles
     * @param queued queued requests
     * @param active active operations
     */
    public Stage228FlightDeckPersistentState(
            int schemaVersion,
            String runtimeVersion,
            String semanticContract,
            List<DeckProfileState> profiles,
            List<RequestState> queued,
            List<ActiveState> active) {
        this(
                schemaVersion,
                runtimeVersion,
                semanticContract,
                -1L,
                profiles,
                queued,
                active);
    }

    /** Validates version, uniqueness and deterministic ordering.
     * @param schemaVersion exact schema
     * @param runtimeVersion exact runtime ID
     * @param semanticContract semantic contract
     * @param lastProcessedTick last consumed authoritative tick, or -1
     * @param profiles physical profiles
     * @param queued queued requests
     * @param active active operations
     */
    public Stage228FlightDeckPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8C flight-deck schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8C flight-deck runtime: " + runtimeVersion);
        }
        semanticContract = requireText(semanticContract, "semanticContract");
        if (!CURRENT_SEMANTIC_CONTRACT.equals(semanticContract)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8C flight-deck semantic contract: " + semanticContract);
        }
        if (lastProcessedTick < -1L) {
            throw new IllegalArgumentException("lastProcessedTick cannot be below -1");
        }

        ArrayList<DeckProfileState> profileCopy =
                new ArrayList<>(Objects.requireNonNull(profiles, "profiles"));
        if (profileCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("profiles cannot contain null");
        }
        profileCopy.sort(Comparator.naturalOrder());
        for (int index = 1; index < profileCopy.size(); index++) {
            if (profileCopy.get(index - 1).compareTo(profileCopy.get(index)) == 0) {
                throw new IllegalArgumentException("Duplicate flight-deck profile");
            }
        }
        profiles = List.copyOf(profileCopy);

        ArrayList<RequestState> queueCopy =
                new ArrayList<>(Objects.requireNonNull(queued, "queued"));
        if (queueCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("queued cannot contain null");
        }
        queueCopy.sort(Comparator.naturalOrder());
        Set<SmallCraftId> operationCraft = new HashSet<>();
        for (RequestState request : queueCopy) {
            if (!operationCraft.add(request.craftId())) {
                throw new IllegalArgumentException(
                        "Duplicate queued flight-deck craft: " + request.craftId());
            }
        }
        queued = List.copyOf(queueCopy);

        ArrayList<ActiveState> activeCopy =
                new ArrayList<>(Objects.requireNonNull(active, "active"));
        if (activeCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("active cannot contain null");
        }
        activeCopy.sort(Comparator.naturalOrder());
        TreeSet<String> activeBays = new TreeSet<>();
        for (ActiveState operation : activeCopy) {
            if (lastProcessedTick < 0L
                    || operation.request().requestedTick() > lastProcessedTick) {
                throw new IllegalArgumentException(
                        "Active flight-deck operation cannot originate after processed watermark");
            }
            if (!operationCraft.add(operation.request().craftId())) {
                throw new IllegalArgumentException(
                        "Craft appears in queued and active flight-deck state: "
                                + operation.request().craftId());
            }
            String bayKey = operation.request().hostStableId()
                    + "\u0000" + operation.request().bayStableId();
            if (!activeBays.add(bayKey)) {
                throw new IllegalArgumentException(
                        "Multiple active operations for one persisted bay");
            }
        }
        active = List.copyOf(activeCopy);
    }

    /** @return empty non-granting C sidecar */
    public static Stage228FlightDeckPersistentState empty() {
        return new Stage228FlightDeckPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                CURRENT_SEMANTIC_CONTRACT,
                -1L,
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
