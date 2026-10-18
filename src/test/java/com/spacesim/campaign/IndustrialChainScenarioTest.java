package com.spacesim.campaign;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Ordinary first industrial chain: earn money by mining, buy the local industrial station, mine the
 * three ores (one of them in a neighbouring system), unload them, refine the three basic alloys and
 * build and commission the starter batch-processing facility. Only pilot inputs; nothing is granted.
 */
@Tag("scenario")
@Tag("soak")
class IndustrialChainScenarioTest {
    static final long SEED = 2L;
    static final String STATION = "industry.1";
    /** Checkpoint after commissioning, the starting point of longer industrial scenarios. */
    static final String COMPLETE_CHECKPOINT = "industrial-chain-complete.s25";

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void ordinaryPilotBuildsAndCommissionsTheStarterProcessingFacility() throws java.io.IOException {
        var industry = IndustrialPilot.start(SEED, STATION, 6_000_000L);
        run(industry);
        industry.pilot.saveAndReload();
        industry.save(COMPLETE_CHECKPOINT);
    }

    /** The whole first chain on an independent pilot start; reused by longer scenarios. */
    static void run(IndustrialPilot industry) throws java.io.IOException {
        long t0 = System.nanoTime();
        industry.buyMinerAndMaps();
        industry.phase("miner and maps", t0);
        long t = System.nanoTime();
        industry.earn(industry.pilot.campaign().stationSalePriceMilliCredits(STATION));
        industry.phase("earned station price", t);
        t = System.nanoTime();
        industry.buyStation();
        industry.phase("bought " + STATION, t);
        industry.buildStarterFacility();
        industry.phase("total", t0);
    }
}
