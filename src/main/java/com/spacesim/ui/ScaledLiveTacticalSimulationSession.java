package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.Stage19ScaledLiveTacticalFactory;

import java.util.Objects;

/**
 * Thin live/presentation session over the exact same scaled authoritative runtime used headlessly.
 *
 * <p>The session owns presentation scheduling state only: pause and fixed-tick batching. It never owns
 * movement, AI, sensing, weapons, ammunition, damage, power, heat or body state. Every authoritative
 * advance delegates to the same production runtime, while visual/debug reads are strictly read-only.</p>
 */
public final class ScaledLiveTacticalSimulationSession {
    /** Fixed-tick batches used only to change presentation-time simulation speed. */
    public enum SimulationSpeed {
        /** One fixed authoritative tick per scheduled batch. */ X1(1),
        /** Two fixed authoritative ticks per scheduled batch. */ X2(2),
        /** Four fixed authoritative ticks per scheduled batch. */ X4(4),
        /** Eight fixed authoritative ticks per scheduled batch. */ X8(8);

        private final int ticksPerBatch;

        SimulationSpeed(int ticksPerBatch) {
            this.ticksPerBatch = ticksPerBatch;
        }

        /** @return positive number of unchanged fixed simulation ticks in one scheduled batch */
        public int ticksPerBatch() {
            return ticksPerBatch;
        }
    }

    private LiveTacticalBattleDeceptionRuntime runtime;
    private final ScaledLiveTacticalSimulationProjection projection;
    private final ScaledTacticalDebugProjection debugProjection;
    private boolean paused;
    private SimulationSpeed simulationSpeed = SimulationSpeed.X1;

    /** Creates a fresh 32-ship saturation live session from the shared Stage-19 factory. */
    public ScaledLiveTacticalSimulationSession() {
        this(Stage19ScaledLiveTacticalFactory.createSaturation32(),
                new ScaledLiveTacticalSimulationProjection(),
                new ScaledTacticalDebugProjection());
    }

    ScaledLiveTacticalSimulationSession(
            LiveTacticalBattleDeceptionRuntime runtime,
            ScaledLiveTacticalSimulationProjection projection) {
        this(runtime, projection, new ScaledTacticalDebugProjection());
    }

    ScaledLiveTacticalSimulationSession(
            LiveTacticalBattleDeceptionRuntime runtime,
            ScaledLiveTacticalSimulationProjection projection,
            ScaledTacticalDebugProjection debugProjection) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.debugProjection = Objects.requireNonNull(debugProjection, "debugProjection");
    }

    /**
     * Advances exactly one authoritative fixed tactical tick regardless of pause state.
     *
     * <p>This preserves the existing programmatic single-step API. Presentation loops should use
     * {@link #advanceScheduledBatch()} for pause/speed-aware advancement.</p>
     */
    public void advanceOneTick() {
        stepOneTick();
    }

    /** Advances exactly one authoritative fixed tick, including while paused. */
    public void stepOneTick() {
        runtime.advanceOneTick();
    }

    /**
     * Advances the configured number of unchanged fixed ticks when not paused.
     *
     * @return number of authoritative fixed ticks actually executed
     */
    public int advanceScheduledBatch() {
        if (paused) {
            return 0;
        }
        int executed = simulationSpeed.ticksPerBatch();
        for (int index = 0; index < executed; index++) {
            runtime.advanceOneTick();
        }
        return executed;
    }

    /** Pauses scheduled presentation-time advancement without changing authoritative state. */
    public void pause() {
        paused = true;
    }

    /** Resumes scheduled presentation-time advancement without changing authoritative state. */
    public void resume() {
        paused = false;
    }

    /** @return whether scheduled presentation-time advancement is paused */
    public boolean paused() {
        return paused;
    }

    /**
     * Selects how many unchanged fixed ticks a scheduled batch executes.
     *
     * @param simulationSpeed presentation scheduling multiplier
     */
    public void setSimulationSpeed(SimulationSpeed simulationSpeed) {
        this.simulationSpeed = Objects.requireNonNull(simulationSpeed, "simulationSpeed");
    }

    /** @return current presentation scheduling multiplier */
    public SimulationSpeed simulationSpeed() {
        return simulationSpeed;
    }

    /**
     * Recreates the authoritative saturation scenario from the same shared factory.
     *
     * <p>Reset also restores canonical presentation scheduling state. It does not restore by copying
     * a previously mutated combat snapshot.</p>
     */
    public void reset() {
        runtime = Stage19ScaledLiveTacticalFactory.createSaturation32();
        paused = false;
        simulationSpeed = SimulationSpeed.X1;
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
     * Reads immutable tactical diagnostics without advancing simulation state.
     *
     * @return current actor/control/engineering debug snapshot
     */
    public ScaledTacticalDebugSnapshot debugSnapshot() {
        return debugProjection.project(runtime);
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
