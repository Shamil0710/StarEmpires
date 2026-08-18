package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.Stage19ScaledLiveTacticalFactory;

import java.util.Objects;

/**
 * Thin live/presentation session over the exact same scaled authoritative runtime used headlessly.
 *
 * <p>The session owns no combat state beyond the production runtime. Advancing delegates exactly once
 * to that runtime; snapshot reads delegate only to the read-only projection. A real-time viewer may
 * call {@link #snapshot()} any number of times between fixed ticks without changing simulation truth.</p>
 */
public final class ScaledLiveTacticalSimulationSession {
    private final LiveTacticalBattleDeceptionRuntime runtime;
    private final ScaledLiveTacticalSimulationProjection projection;

    /** Creates a fresh 32-ship saturation live session from the shared Stage-19 factory. */
    public ScaledLiveTacticalSimulationSession() {
        this(Stage19ScaledLiveTacticalFactory.createSaturation32(),
                new ScaledLiveTacticalSimulationProjection());
    }

    ScaledLiveTacticalSimulationSession(
            LiveTacticalBattleDeceptionRuntime runtime,
            ScaledLiveTacticalSimulationProjection projection) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    /** Advances exactly one authoritative fixed tactical tick. */
    public void advanceOneTick() {
        runtime.advanceOneTick();
    }

    /** @return authoritative shared tactical tick */
    public long tick() {
        return runtime.tick();
    }

    /**
     * Reads an immutable presentation snapshot without advancing simulation state.
     *
     * @return current scaled tactical visual snapshot
     */
    public TacticalPrototypeVisualSnapshot snapshot() {
        return projection.project(runtime);
    }

    /**
     * Returns the authoritative whole-runtime fingerprint for parity/replay validation.
     *
     * @return current deterministic production fingerprint
     */
    public LiveTacticalBattleDeceptionRuntime.DeceptionFingerprint fingerprint() {
        return runtime.fingerprint();
    }

    /** @return underlying authoritative production runtime for validation integration */
    public LiveTacticalBattleDeceptionRuntime runtime() {
        return runtime;
    }
}
