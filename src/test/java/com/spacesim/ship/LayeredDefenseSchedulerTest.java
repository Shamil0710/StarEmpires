package com.spacesim.ship;

import com.spacesim.ship.LayeredDefenseScheduler.Assignment;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredDefenseSchedulerTest {
    private static final DefendedZone ZONE = new DefendedZone(0d, 0d, 1_000d);

    @Test
    void deterministicPriorityUsesPredictedImpactTimeThenStableId() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        DefenseStation station = station(1L, 0d, 0d, 2, 2L, true);
        Threat later = new Threat(30L, 120_000d, 0d, -1_000d, 0d, 1_000d, true);
        Threat sameFirst = new Threat(20L, 100_000d, 0d, -1_000d, 0d, 1_000d, true);
        Threat sameSecond = new Threat(10L, 100_000d, 0d, -1_000d, 0d, 1_000d, true);

        List<Assignment> assignments = scheduler.schedule(
                ZONE,
                List.of(later, sameFirst, sameSecond),
                List.of(station));

        assertEquals(2, assignments.size());
        assertEquals(10L, assignments.get(0).threatId());
        assertEquals(20L, assignments.get(1).threatId());
        assertTrue(assignments.get(0).predictedImpactSeconds() <= assignments.get(1).predictedImpactSeconds());
    }

    @Test
    void guidanceKilledBodyRemainsDefenseThreatWhenBallisticPathStillImpacts() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        Threat ballisticResidual = new Threat(
                41L, 70_000d, 0d, -900d, 0d, 900d, false);

        List<Assignment> assignments = scheduler.schedule(
                ZONE,
                List.of(ballisticResidual),
                List.of(station(2L, 0d, 0d, 1, 1L, true)));

        assertEquals(1, assignments.size());
        assertEquals(41L, assignments.get(0).threatId());
    }

    @Test
    void formationSpacingChangesWhetherPhysicalInterceptorCanReachInTime() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        // At 2 km/s this body reaches the 5 km safe-intercept boundary in 12.5 s.
        // The central station cannot physically cover the distance in that time, while a forward
        // escort already near the inbound path can engage several seconds after launch.
        Threat fastThreat = new Threat(51L, 30_000d, 0d, -2_000d, 0d, 1_000d, true);
        DefenseStation central = station(3L, 0d, 0d, 1, 1L, true);
        DefenseStation forwardEscort = station(3L, 22_000d, 0d, 1, 1L, true);

        List<Assignment> centralAssignments = scheduler.schedule(ZONE, List.of(fastThreat), List.of(central));
        List<Assignment> forwardAssignments = scheduler.schedule(ZONE, List.of(fastThreat), List.of(forwardEscort));

        assertEquals(0, centralAssignments.size());
        assertEquals(1, forwardAssignments.size());
    }

    @Test
    void repeatedWaveExposesSupportChannelAmmoAndThermalEnduranceWithoutPdChance() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        List<Threat> wave = List.of(
                new Threat(61L, 100_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(62L, 102_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(63L, 104_000d, 0d, -1_000d, 0d, 1_000d, true));

        List<Assignment> oneChannelOneRound = scheduler.schedule(
                ZONE, wave, List.of(station(4L, 0d, 0d, 1, 1L, true)));
        List<Assignment> threeChannelsThreeRounds = scheduler.schedule(
                ZONE, wave, List.of(station(4L, 0d, 0d, 3, 3L, true)));
        List<Assignment> thermallyUnavailable = scheduler.schedule(
                ZONE, wave, List.of(station(4L, 0d, 0d, 3, 3L, false)));

        assertEquals(1, oneChannelOneRound.size());
        assertEquals(3, threeChannelsThreeRounds.size());
        assertEquals(0, thermallyUnavailable.size());
    }

    private static DefenseStation station(
            long id,
            double xM,
            double yM,
            int channels,
            long rounds,
            boolean thermalAvailable) {
        return new DefenseStation(
                id,
                xM,
                yM,
                0d,
                interceptor(),
                true,
                channels,
                rounds,
                thermalAvailable,
                5_000d);
    }

    private static GuidedWeapon interceptor() {
        return new GuidedWeapon(
                "ammo.pd_interceptor_test_v1",
                "seeker.radar_pd_v1",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                200d);
    }
}
