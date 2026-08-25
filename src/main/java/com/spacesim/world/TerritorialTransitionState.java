package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent Stage-21F occupation-transition metadata layered over Stage-17 territorial authority.
 *
 * <p>This state never owns claims or sovereignty. Claim/stabilization/control remain in
 * {@link FactionStrategicState} and {@link TerritorialControlRuntime}. The only durable facts kept
 * here are the physical occupation attempt, its exact sustained-security progress, the deadline of
 * an unsupported interval, whether this occupation created its Stage-17 claim, and whether Stage-17
 * control was ever actually established. This lets save/load resume an invasion transition and
 * distinguish later liberation without inventing a second territorial authority.</p>
 *
 * @param occupations canonical occupation attempts, unique by faction/system
 */
public record TerritorialTransitionState(List<OccupationState> occupations) {

    /** Validates and canonicalizes persistent occupation attempts. */
    public TerritorialTransitionState {
        Objects.requireNonNull(occupations, "occupations");
        ArrayList<OccupationState> canonical = new ArrayList<>(occupations.size());
        Set<String> keys = new HashSet<>();
        for (OccupationState occupation : occupations) {
            OccupationState checked = Objects.requireNonNull(occupation, "occupation");
            String key = checked.factionContentId() + "\u0000" + checked.systemId().value();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate occupation transition: " + key);
            }
            canonical.add(checked);
        }
        canonical.sort(Comparator
                .comparing(OccupationState::factionContentId)
                .thenComparing(OccupationState::systemId));
        occupations = List.copyOf(canonical);
    }

    /** @return empty Stage-21F transition state */
    public static TerritorialTransitionState empty() {
        return new TerritorialTransitionState(List.of());
    }

    /**
     * Finds one faction/system occupation attempt.
     *
     * @param factionContentId stable faction ID
     * @param systemId objective system
     * @return matching occupation when present
     */
    public Optional<OccupationState> occupationFor(String factionContentId, StarSystemId systemId) {
        if (factionContentId == null || systemId == null) return Optional.empty();
        String faction = factionContentId.strip();
        return occupations.stream()
                .filter(value -> value.factionContentId().equals(faction) && value.systemId().equals(systemId))
                .findFirst();
    }

    /** Returns an immutable registry with the supplied faction/system attempt inserted or replaced. */
    public TerritorialTransitionState upsert(OccupationState replacement) {
        OccupationState checked = Objects.requireNonNull(replacement, "replacement");
        ArrayList<OccupationState> next = new ArrayList<>(occupations.size() + 1);
        boolean replaced = false;
        for (OccupationState current : occupations) {
            if (current.factionContentId().equals(checked.factionContentId())
                    && current.systemId().equals(checked.systemId())) {
                next.add(checked);
                replaced = true;
            } else {
                next.add(current);
            }
        }
        if (!replaced) next.add(checked);
        return new TerritorialTransitionState(next);
    }

    /** Physical occupation lifecycle; none of these values is legal sovereignty. */
    public enum OccupationStatus {
        /** Physical invasion exists but has not yet accumulated the sustained occupation threshold. */ OCCUPYING,
        /** Rival ordinary fleet presence currently prevents a secure occupation clock from advancing. */ CONTESTED,
        /** Sustained supplied physical security threshold was reached; Stage-17 claim may stabilize. */ SECURED,
        /** Surviving participants are leaving through ordinary movement/order authority. */ WITHDRAWING,
        /** Physical support failed for long enough that occupation progress collapsed. */ COLLAPSED,
        /** Stage-17 control once existed for the occupier and has since passed to another controller. */ LIBERATED
    }

    /**
     * One durable physical occupation attempt.
     *
     * @param factionContentId stable occupying faction identity
     * @param systemId objective system
     * @param operationId Stage-21E INVASION operation providing the physical participants
     * @param startedTick first authoritative occupation evaluation tick
     * @param lastEvaluatedTick latest authoritative occupation evaluation tick
     * @param securedTicks cumulative continuous/surviving secure-presence progress
     * @param unsupportedSinceTick first current unsupported tick, or -1 when supported/contested
     * @param claimCreatedByOccupation whether this occupation created the current Stage-17 claim
     * @param controlEverEstablished whether Stage-17 authority ever established control for this occupier
     * @param status current physical occupation lifecycle
     */
    public record OccupationState(
            String factionContentId,
            StarSystemId systemId,
            long operationId,
            long startedTick,
            long lastEvaluatedTick,
            long securedTicks,
            long unsupportedSinceTick,
            boolean claimCreatedByOccupation,
            boolean controlEverEstablished,
            OccupationStatus status) {

        /** Validates one persistent occupation attempt. */
        public OccupationState {
            factionContentId = requireId(factionContentId);
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(status, "status");
            if (operationId <= 0L) throw new IllegalArgumentException("operationId must be positive");
            if (startedTick < 0L || lastEvaluatedTick < startedTick) {
                throw new IllegalArgumentException("invalid occupation evaluation ticks");
            }
            if (securedTicks < 0L) throw new IllegalArgumentException("securedTicks must be non-negative");
            if (unsupportedSinceTick < -1L
                    || (unsupportedSinceTick >= 0L && unsupportedSinceTick < startedTick)
                    || (unsupportedSinceTick > lastEvaluatedTick)) {
                throw new IllegalArgumentException("invalid unsupportedSinceTick");
            }
            if ((status == OccupationStatus.SECURED || status == OccupationStatus.CONTESTED
                    || status == OccupationStatus.LIBERATED)
                    && unsupportedSinceTick >= 0L) {
                throw new IllegalArgumentException("secure/contested/liberated occupation cannot retain unsupported deadline");
            }
            if (status == OccupationStatus.LIBERATED && !controlEverEstablished) {
                throw new IllegalArgumentException("liberated occupation requires prior established Stage-17 control");
            }
        }
    }

    private static String requireId(String value) {
        String checked = Objects.requireNonNull(value, "factionContentId").strip();
        if (checked.isEmpty()) throw new IllegalArgumentException("factionContentId cannot be blank");
        return checked;
    }
}
