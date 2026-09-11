package com.spacesim.campaign;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.simulation.GeneratedWorldFreightAutopilot;
import com.spacesim.simulation.GeneratedWorldFreightAutopilot.ActionReport;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;

import java.util.Objects;

/**
 * Ordinary headless lifecycle for one playable generated campaign.
 *
 * <p>This class is an orchestration boundary, not a second simulation authority. It composes the
 * accepted Stage-20/20.5 {@link LiveRuntime}, the existing freight command policy and the persistent
 * {@link SimulationClock} instances already owned by the ordinary world. New campaigns use the
 * accepted production factory; resumed campaigns restore the exact runtime checkpoint without
 * regeneration.</p>
 *
 * <p>Autonomous freight decisions are scheduled from authoritative simulation ticks. Presentation
 * frame cadence can therefore change without becoming the clock that decides gameplay actions.
 * Frame input is internally bounded to at most one fixed simulation tick so high time scales cannot
 * move an autonomous decision past its canonical tick boundary.</p>
 */
public final class GeneratedCampaignSession {
    /** Approximate legacy one-timescale freight cadence expressed as exact simulation time. */
    public static final double AUTONOMOUS_DECISION_PERIOD_SECONDS = 0.4d;

    private static final double PERIOD_ALIGNMENT_TOLERANCE_SECONDS = 0.000_001d;
    private static final ActionReport NO_AUTONOMOUS_ACTIONS = new ActionReport(0, 0, 0, 0, 0);

    private final long rootSeed;
    private final ContentCatalog content;
    private final LiveRuntime runtime;
    private final GeneratedWorldFreightAutopilot freightAutopilot;

    private GeneratedCampaignSession(long rootSeed, ContentCatalog content, LiveRuntime runtime) {
        this.rootSeed = rootSeed;
        this.content = Objects.requireNonNull(content, "content");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.freightAutopilot = new GeneratedWorldFreightAutopilot(runtime);
        autonomousDecisionPeriodTicks(activeClock());
        validateClockControlsAreUniform();
    }

    /**
     * Creates one new campaign through the accepted generated-world production bootstrap.
     *
     * @param rootSeed deterministic generated-world seed
     * @return ordinary long-lived campaign session
     */
    public static GeneratedCampaignSession create(long rootSeed) {
        var generated = Stage20PlayableGeneratedWorldFactory.create(rootSeed);
        return new GeneratedCampaignSession(generated.rootSeed(), generated.content(), generated.runtime());
    }

    /**
     * Restores one campaign from the exact atomic Stage-20.5 checkpoint without regeneration.
     *
     * @param checkpoint decoded generated-world runtime checkpoint
     * @return resumed ordinary campaign session
     */
    public static GeneratedCampaignSession restore(Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        Stage20GeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(checkpoint, "checkpoint");
        LiveRuntime restored = Stage20GeneratedWorldRuntimeBridge.restore(saved);
        return new GeneratedCampaignSession(
                saved.campaign().generationIdentity().worldSeed(),
                ContentCatalogLoader.loadDefault(),
                restored);
    }

    /** @return exact generated campaign seed */
    public long rootSeed() {
        return rootSeed;
    }

    /** @return installed content catalogue used by the ordinary client projection */
    public ContentCatalog content() {
        return content;
    }

    /** @return the single accepted mutable generated-world authority */
    public LiveRuntime runtime() {
        return runtime;
    }

    /** @return exact atomic checkpoint of the same runtime used by simulation and presentation */
    public Stage20GeneratedWorldRuntimePersistentState captureState() {
        return runtime.captureState();
    }

    /** @return whether campaign progression is paused */
    public boolean isPaused() {
        return activeClock().isPaused();
    }

    /** @return current simulation speed multiplier */
    public double timeScale() {
        return activeClock().getTimeScale();
    }

    /**
     * Applies one pause value to every existing system clock so changing the active system cannot
     * create a second time-control state.
     *
     * @param paused new campaign pause state
     */
    public void setPaused(boolean paused) {
        forEachClock(clock -> clock.setPaused(paused));
    }

    /**
     * Applies one time scale to every existing system clock.
     *
     * @param scale finite non-negative simulation speed multiplier
     */
    public void setTimeScale(double scale) {
        forEachClock(clock -> clock.setTimeScale(scale));
    }

    /**
     * Advances the existing authoritative runtime from one presentation-frame delta.
     *
     * <p>The method never uses wall-clock timestamps. Autonomous actions are emitted only after
     * exact fixed ticks and therefore continue on the same cadence after save/load.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return deterministic orchestration diagnostics for this call
     */
    public AdvanceReport advanceFrame(float realDeltaSeconds) {
        if (!Float.isFinite(realDeltaSeconds) || realDeltaSeconds < 0f) {
            throw new IllegalArgumentException("Frame delta must be finite and non-negative");
        }
        if (realDeltaSeconds == 0f || isPaused() || timeScale() == 0d) {
            return new AdvanceReport(0L, 0, NO_AUTONOMOUS_ACTIONS);
        }

        double remaining = realDeltaSeconds;
        long fixedTicks = 0L;
        int autonomousDecisions = 0;
        ActionReport lastAction = NO_AUTONOMOUS_ACTIONS;
        while (remaining > 0d) {
            SimulationClock clock = activeClock();
            double scale = clock.getTimeScale();
            if (clock.isPaused() || scale == 0d) {
                break;
            }
            double maxPresentationSlice = clock.getFixedStepSeconds() / scale;
            float slice = (float) Math.min(remaining, maxPresentationSlice);
            if (!Float.isFinite(slice) || slice <= 0f) {
                throw new IllegalStateException("Time scale is too large for fixed-step presentation input");
            }

            long beforeTick = runtime.world().getAuthoritativeWorldTick();
            runtime.advanceFrame(slice);
            long afterTick = runtime.world().getAuthoritativeWorldTick();
            long executed = afterTick - beforeTick;
            if (executed < 0L || executed > 1L) {
                throw new IllegalStateException(
                        "Campaign frame slice crossed more than one authoritative fixed tick");
            }
            fixedTicks += executed;
            if (executed == 1L
                    && afterTick % autonomousDecisionPeriodTicks(activeClock()) == 0L) {
                lastAction = freightAutopilot.advance();
                autonomousDecisions++;
            }

            remaining -= slice;
            if (remaining < 1e-12d) {
                remaining = 0d;
            }
        }
        return new AdvanceReport(fixedTicks, autonomousDecisions, lastAction);
    }

    private SimulationClock activeClock() {
        var world = runtime.world();
        return world.findSession(world.getActiveSystemId()).orElseThrow().getClock();
    }

    private long autonomousDecisionPeriodTicks(SimulationClock clock) {
        double fixedStep = clock.getFixedStepSeconds();
        long ticks = Math.round(AUTONOMOUS_DECISION_PERIOD_SECONDS / fixedStep);
        if (ticks <= 0L
                || Math.abs(ticks * fixedStep - AUTONOMOUS_DECISION_PERIOD_SECONDS)
                > PERIOD_ALIGNMENT_TOLERANCE_SECONDS) {
            throw new IllegalStateException(
                    "Autonomous decision period must align to authoritative fixed ticks");
        }
        return ticks;
    }

    private void validateClockControlsAreUniform() {
        SimulationClock reference = activeClock();
        forEachClock(clock -> {
            if (Float.floatToIntBits(clock.getFixedStepSeconds())
                    != Float.floatToIntBits(reference.getFixedStepSeconds())
                    || Double.doubleToLongBits(clock.getTimeScale())
                    != Double.doubleToLongBits(reference.getTimeScale())
                    || clock.isPaused() != reference.isPaused()) {
                throw new IllegalStateException("Generated campaign system clocks disagree");
            }
        });
    }

    private void forEachClock(java.util.function.Consumer<SimulationClock> action) {
        Objects.requireNonNull(action, "action");
        for (var system : runtime.world().getTopology().systems()) {
            action.accept(runtime.world().findSession(system.id()).orElseThrow().getClock());
        }
    }

    /**
     * Per-frame lifecycle diagnostics.
     *
     * @param fixedTicks authoritative local fixed ticks executed
     * @param autonomousDecisions freight-policy decisions executed at canonical tick boundaries
     * @param lastAutonomousAction diagnostics from the final autonomous decision in this call
     */
    public record AdvanceReport(
            long fixedTicks,
            int autonomousDecisions,
            ActionReport lastAutonomousAction) {
        /** Validates non-negative counters and a present diagnostic report. */
        public AdvanceReport {
            if (fixedTicks < 0L || autonomousDecisions < 0) {
                throw new IllegalArgumentException("Campaign advance counters cannot be negative");
            }
            Objects.requireNonNull(lastAutonomousAction, "lastAutonomousAction");
        }
    }
}
