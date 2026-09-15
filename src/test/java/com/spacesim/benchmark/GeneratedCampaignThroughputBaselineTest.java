package com.spacesim.benchmark;

import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignThroughputBaselineTest {
    private static final int PROBE_FRAMES = 100;
    private static final float FRAME_SECONDS = 0.1f;

    @Test
    void recordsDenseCivilianTrafficAndOneTimesEightTimesThroughput() {
        GeneratedCampaignCoordinator bootstrap = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var checkpoint = bootstrap.captureState();
        var freight = bootstrap.session().captureState().freight();
        assertTrue(freight.freighters().size() >= 2,
                "throughput probe requires non-trivial civilian freight traffic");
        assertTrue(freight.orders().size() >= 2,
                "throughput probe requires multiple persistent transport orders");

        Probe oneTimes = measure(checkpoint, 1d);
        Probe eightTimes = measure(checkpoint, 8d);

        assertEquals(PROBE_FRAMES, oneTimes.fixedTicks());
        assertEquals(PROBE_FRAMES * 8L, eightTimes.fixedTicks());
        assertTrue(oneTimes.elapsedNanos() > 0L && eightTimes.elapsedNanos() > 0L);

        System.out.printf(Locale.ROOT,
                "M22.7_THROUGHPUT_BASELINE freightFleets=%d orders=%d "
                        + "oneX_ticks=%d oneX_ms=%.3f oneX_ticksPerSecond=%.1f "
                        + "eightX_ticks=%d eightX_ms=%.3f eightX_ticksPerSecond=%.1f%n",
                freight.freighters().size(),
                freight.orders().size(),
                oneTimes.fixedTicks(),
                oneTimes.elapsedNanos() / 1_000_000d,
                oneTimes.ticksPerSecond(),
                eightTimes.fixedTicks(),
                eightTimes.elapsedNanos() / 1_000_000d,
                eightTimes.ticksPerSecond());
    }

    private static Probe measure(
            com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState checkpoint,
            double timeScale) {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.restore(checkpoint);
        campaign.setTimeScale(timeScale);
        long fixedTicks = 0L;
        long started = System.nanoTime();
        for (int frame = 0; frame < PROBE_FRAMES; frame++) {
            fixedTicks += campaign.advanceFrame(FRAME_SECONDS).fixedTicks();
        }
        long elapsed = System.nanoTime() - started;
        return new Probe(fixedTicks, elapsed);
    }

    private record Probe(long fixedTicks, long elapsedNanos) {
        private double ticksPerSecond() {
            return fixedTicks * 1_000_000_000d / elapsedNanos;
        }
    }
}
