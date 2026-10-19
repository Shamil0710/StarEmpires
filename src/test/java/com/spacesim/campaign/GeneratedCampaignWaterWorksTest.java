package com.spacesim.campaign;

import com.spacesim.economy.MarketConsumption;
import com.spacesim.economy.WaterWorks;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit fixture: the depots' opening water is removed so their works run; everything else is ordinary. */
@Tag("slow")
class GeneratedCampaignWaterWorksTest {
    private static final String UNION_DEPOT = "industry.23";
    private static final String UNOPERATED_DEPOT = "industry.26";

    @Test void anOperatedDepotPurifiesIceFromItsOwnDepositsEachHour() {
        var c = Stage228CampaignAuthority.create(2);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        drain(c, UNION_DEPOT); drain(c, UNOPERATED_DEPOT);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
        double deposits = iceInSystem(c, 23);
        long hour = Math.round(3600d / c.coordinator().session().fixedStepSeconds());

        advance(c, hour - 1);
        assertEquals(0, water(c, UNION_DEPOT), 1e-9, "nothing before the hour completes");
        advance(c, 1);

        double made = water(c, UNION_DEPOT);
        System.out.printf("WATERWORKS made %.0f kg; deposits used %.0f kg%n", made, deposits - iceInSystem(c, 23));
        assertTrue(made > 0 && made <= WaterWorks.WATER_PER_HOUR_KG + 1e-6, "made " + made);
        assertEquals(0, ice(c, UNION_DEPOT), 1e-6, "all delivered ice is purified");
        assertTrue(deposits - iceInSystem(c, 23) >= made / WaterWorks.waterPerIceKg() - 1e-3,
                "the ice came out of finite deposits in the depot's system");
        assertEquals(0, water(c, UNOPERATED_DEPOT), 1e-9, "a depot without an operator does not run");

        advance(restored, hour);
        assertEquals(c.captureState(), restored.captureState(), "the works are a pure function of the saved state and the tick");
    }

    private static void drain(Stage228CampaignAuthority c, String station) {
        var storage = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        double kg = storage.commodityMassKg(WaterWorks.WATER);
        if (kg > 0) MarketConsumption.consume(storage, WaterWorks.WATER, kg);
    }

    private static void advance(Stage228CampaignAuthority c, long ticks) {
        c.coordinator().setPaused(false);
        for (long i = 0; i < ticks; i++) c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
    }

    private static double water(Stage228CampaignAuthority c, String station) {
        return c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(WaterWorks.WATER);
    }

    private static double ice(Stage228CampaignAuthority c, String station) {
        return c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(WaterWorks.ICE);
    }

    private static double iceInSystem(Stage228CampaignAuthority c, long system) {
        return c.coordinator().runtime().industry().sourceOutposts().outposts().stream()
                .filter(o -> o.site().systemId().value() == system && o.source().sourceState().outputCommodityId().equals(WaterWorks.ICE))
                .mapToDouble(o -> o.source().sourceState().remainingAccessibleMassKg()).sum();
    }
}
