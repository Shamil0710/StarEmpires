package com.spacesim.simulation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Retained request to advance the same campaign clock, never a second simulation.
 * @param mode requested stopping rule
 * @param startedTick actual beginning tick
 * @param targetTick duration boundary, or the counter limit for event mode
 * @param requestedMinutes original whole game minutes, zero for event mode
 * @param journalSequence last personal event known when starting
 * @param fleetId personally controlled fleet at start, or zero
 * @param jumpPhase last observed personal flight phase
 * @param resumeScale normal clock multiplier to restore on stopping
 * @param hullIntegrity personally observed initial hull integrity
 * @param moduleIntegrity last personally observed compartment and mount integrity
 * @param threatSignature initial personal danger-intelligence signature
 */
public record CampaignTimeSkipState(Mode mode, long startedTick, long targetTick, int requestedMinutes,
        long journalSequence, long fleetId, String jumpPhase, double resumeScale, double hullIntegrity,
        Map<String, Double> moduleIntegrity, int threatSignature) {
    /** Bounded user duration in game minutes. */
    public static final int MAX_MINUTES = 1_000_000;
    /** Maximum retained personal mount conditions. */
    public static final int MAX_MOUNTS = 512;
    /** Requested stopping rule. */
    public enum Mode {
        /** No outstanding request. */ NONE,
        /** Stop after the requested game minutes. */ MINUTES,
        /** Stop at the next personal event. */ EVENT
    }

    /**
     * Validates a bounded request and its original personally visible safety state.
     * @param mode stopping rule
     * @param startedTick beginning tick
     * @param targetTick stopping boundary
     * @param requestedMinutes requested game minutes
     * @param journalSequence starting personal event marker
     * @param fleetId controlled personal fleet or zero
     * @param jumpPhase last personal flight phase
     * @param resumeScale previous ordinary clock speed
     * @param hullIntegrity initial hull condition
     * @param moduleIntegrity last personally observed compartment and mount conditions
     * @param threatSignature initial personal danger signature
     */
    public CampaignTimeSkipState {
        Objects.requireNonNull(mode); Objects.requireNonNull(jumpPhase); Objects.requireNonNull(moduleIntegrity);
        if (startedTick < 0 || journalSequence < 0 || fleetId < 0 || !Double.isFinite(resumeScale) || resumeScale < 0
                || !Double.isFinite(hullIntegrity) || hullIntegrity < 0 || hullIntegrity > 1
                || moduleIntegrity.size() > MAX_MOUNTS
                || !java.util.Set.of("NONE", "MOVING_TO_JUMP", "JUMP_PENDING", "IN_TRANSIT", "ARRIVING").contains(jumpPhase))
            throw new IllegalArgumentException("Invalid campaign time skip request");
        var mounts = new TreeMap<String, Double>();
        for (var entry : moduleIntegrity.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 128
                    || entry.getValue() == null || !Double.isFinite(entry.getValue()) || entry.getValue() < 0 || entry.getValue() > 1)
                throw new IllegalArgumentException("Invalid time skip personal mount condition");
            mounts.put(entry.getKey(), entry.getValue());
        }
        moduleIntegrity = Collections.unmodifiableMap(mounts);
        if (mode == Mode.NONE) {
            if (startedTick != 0 || targetTick != 0 || requestedMinutes != 0 || journalSequence != 0 || fleetId != 0
                    || !jumpPhase.equals("NONE") || resumeScale != 1 || hullIntegrity != 1 || !mounts.isEmpty() || threatSignature != 0)
                throw new IllegalArgumentException("Idle time skip cannot retain a request");
        } else if (targetTick <= startedTick || mode == Mode.MINUTES && (requestedMinutes <= 0 || requestedMinutes > MAX_MINUTES)
                || mode == Mode.EVENT && (requestedMinutes != 0 || targetTick != Long.MAX_VALUE))
            throw new IllegalArgumentException("Invalid time skip duration or event boundary");
    }

    /** @return whether an outstanding request exists, including while paused */
    public boolean active() { return mode != Mode.NONE; }
    /** @return an idle, non-granting control state */
    public static CampaignTimeSkipState idle() { return new CampaignTimeSkipState(Mode.NONE, 0, 0, 0, 0, 0, "NONE", 1, 1, Map.of(), 0); }
    /**
     * Retains the same request while observing the first departure of a delegated personal order.
     * @param phase actual current personal jump phase
     * @return immutable updated control state
     */
    public CampaignTimeSkipState withJumpPhase(String phase) {
        return new CampaignTimeSkipState(mode, startedTick, targetTick, requestedMinutes, journalSequence, fleetId,
                phase, resumeScale, hullIntegrity, moduleIntegrity, threatSignature);
    }
}
