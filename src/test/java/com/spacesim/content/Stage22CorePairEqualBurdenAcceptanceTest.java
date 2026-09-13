package com.spacesim.content;

import com.spacesim.ship.LiveTacticalBattleWeaponRuntime.TargetProtectionFingerprint;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 Gate C / B07 multidimensional equal-burden acceptance.
 *
 * <p>The patrol is normalized by one declared authorization envelope, never by a scalar power score.
 * Both exact Stage-22 destroyers must fit the same independent mass, crew, power, ammunition and
 * reaction-mass ceilings. Outcome trade-offs are then read from the ordinary Stage-19 tactical
 * runtime and the existing Stage-21G paid replacement authority. This class is evidence only and
 * owns no gameplay state or faction modifier.</p>
 */
class Stage22CorePairEqualBurdenAcceptanceTest {
    private static final PatrolAuthorization AUTHORIZATION = new PatrolAuthorization(
            33_000_000d,
            34_000_000d,
            200,
            3_000_000_000d,
            20_000d,
            1_000_000d,
            120L);

    @Test
    void b07UsesOneRawDimensionEnvelopeAndExposesTwoSidedNonParetoTradeoffs() {
        for (var coordinate : Stage22CorePairExperimentProtocol.pairedSchedule(1)) {
            var evidence = Stage22CorePairTacticalProbe.run(
                    Stage22CorePairTacticalProbe.Variant.PATROL, coordinate, false);
            assertTrue(evidence.valid(), evidence.toString());

            Map<String, Stage22CorePairTacticalProbe.StartingBurden> burden = evidence.startingBurden().stream()
                    .collect(Collectors.toMap(Stage22CorePairTacticalProbe.StartingBurden::factionId, Function.identity()));
            var empire = burden.get(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID);
            var union = burden.get(Stage22CorePairBalanceEvidence.UNION_FACTION_ID);
            assertFitsAuthorization(empire);
            assertFitsAuthorization(union);

            Map<Long, TargetProtectionFingerprint> protection = evidence.last().protection().stream()
                    .collect(Collectors.toMap(TargetProtectionFingerprint::entityId, Function.identity()));
            var empireProtection = protection.get(com.spacesim.ship.Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
            var unionProtection = protection.get(com.spacesim.ship.Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
            assertTrue(empireProtection.impactsResolved() > 0L,
                    "B07 must observe actual exchanged fire, not compare pristine stat cards");
            assertEquals(empireProtection.impactsResolved(), unionProtection.impactsResolved(),
                    "resilience comparison requires the same observed impact count");

            EnumSet<Dimension> empireAdvantages = EnumSet.noneOf(Dimension.class);
            EnumSet<Dimension> unionAdvantages = EnumSet.noneOf(Dimension.class);
            preferHigher(empire.accelerationMps2(), union.accelerationMps2(),
                    Dimension.ACCELERATION, empireAdvantages, unionAdvantages);
            preferLower(empire.dryMassKg(), union.dryMassKg(),
                    Dimension.DRY_MASS, empireAdvantages, unionAdvantages);
            preferLower(empire.crew(), union.crew(),
                    Dimension.CREW_BURDEN, empireAdvantages, unionAdvantages);
            preferLower(empire.continuousPowerW(), union.continuousPowerW(),
                    Dimension.CONTINUOUS_POWER, empireAdvantages, unionAdvantages);
            preferLower(empire.ammunitionMassKg(), union.ammunitionMassKg(),
                    Dimension.AMMUNITION_MASS, empireAdvantages, unionAdvantages);
            preferHigher(empireProtection.totalShieldReserveJ(), unionProtection.totalShieldReserveJ(),
                    Dimension.SURVIVING_SHIELD_RESERVE, empireAdvantages, unionAdvantages);
            preferHigher(empireProtection.meanCompartmentIntegrity(), unionProtection.meanCompartmentIntegrity(),
                    Dimension.SURVIVING_COMPARTMENT_INTEGRITY, empireAdvantages, unionAdvantages);

            assertTrue(empireAdvantages.size() >= 2,
                    "Empire needs at least two independent observed strengths after B07 normalization: " + empireAdvantages);
            assertTrue(unionAdvantages.size() >= 2,
                    "Industrial Union needs at least two independent observed strengths after B07 normalization: " + unionAdvantages);
            assertTrue(unionAdvantages.contains(Dimension.DRY_MASS));
            assertTrue(unionAdvantages.contains(Dimension.CREW_BURDEN));
            assertTrue(empireAdvantages.contains(Dimension.SURVIVING_SHIELD_RESERVE));
            assertTrue(empireAdvantages.contains(Dimension.SURVIVING_COMPARTMENT_INTEGRITY));

            EnumSet<Dimension> empireVulnerabilities = EnumSet.copyOf(unionAdvantages);
            EnumSet<Dimension> unionVulnerabilities = EnumSet.copyOf(empireAdvantages);
            assertTrue(empireVulnerabilities.size() >= 2,
                    "Empire needs at least two independent vulnerabilities/costs: " + empireVulnerabilities);
            assertTrue(unionVulnerabilities.size() >= 2,
                    "Industrial Union needs at least two independent vulnerabilities/costs: " + unionVulnerabilities);
            assertFalse(empireAdvantages.isEmpty() || unionAdvantages.isEmpty(),
                    "a one-sided Pareto result cannot pass Gate C");
        }
    }

    @Test
    void b07EconomicContinuationKeepsReplacementBurdenRawAndTwoSided() {
        var empire = Stage22CorePairReplacementProbe.run(true);
        var union = Stage22CorePairReplacementProbe.run(false);
        assertTrue(empire.valid(), empire.toString());
        assertTrue(union.valid(), union.toString());

        assertTrue(empire.buildSeconds() > union.buildSeconds(),
                "Union replacement-tempo advantage must remain a paid work-time difference");
        assertTrue(empire.moduleInputMassKg() > union.moduleInputMassKg(),
                "Union replacement advantage must remain visible in physical module stock burden");
        assertEquals(empire.hullInputMassKg(), union.hullInputMassKg(), 0d,
                "equal hull-material burden is preserved as its own raw dimension");

        var pair = Stage22CorePairBalanceEvidence.deriveCurrent();
        assertTrue(pair.unionDisruption().retoolWorkSeconds() > 0L);
        assertTrue(pair.unionDisruption().retoolEnergyJ() > 0L);
        assertTrue(pair.unionDisruption().correlatedDisruption());
        assertTrue(pair.unionDisruption().correlatedThroughputDegradation()
                        > pair.unionDisruption().isolatedThroughputDegradation(),
                "Union throughput advantage must retain its independent commonality/retool counter-cost");
    }

    private static void assertFitsAuthorization(Stage22CorePairTacticalProbe.StartingBurden burden) {
        assertTrue(burden.dryMassKg() <= AUTHORIZATION.maxDryMassKg());
        assertTrue(burden.loadedMassKg() <= AUTHORIZATION.maxLoadedMassKg());
        assertTrue(burden.crew() <= AUTHORIZATION.maxCrew());
        assertTrue(burden.continuousPowerW() <= AUTHORIZATION.maxContinuousPowerW());
        assertTrue(burden.ammunitionMassKg() <= AUTHORIZATION.maxAmmunitionMassKg());
        assertEquals(AUTHORIZATION.reactionMassKg(), burden.reactionMassKg(), 0d,
                "both patrols receive the same physical reaction-mass authorization");
        assertEquals(AUTHORIZATION.rounds(), burden.rounds(),
                "both patrols receive the same round-count authorization");
    }

    private static void preferLower(
            double empire,
            double union,
            Dimension dimension,
            EnumSet<Dimension> empireAdvantages,
            EnumSet<Dimension> unionAdvantages) {
        if (empire < union) empireAdvantages.add(dimension);
        else if (union < empire) unionAdvantages.add(dimension);
    }

    private static void preferHigher(
            double empire,
            double union,
            Dimension dimension,
            EnumSet<Dimension> empireAdvantages,
            EnumSet<Dimension> unionAdvantages) {
        if (empire > union) empireAdvantages.add(dimension);
        else if (union > empire) unionAdvantages.add(dimension);
    }

    private enum Dimension {
        DRY_MASS,
        CREW_BURDEN,
        CONTINUOUS_POWER,
        AMMUNITION_MASS,
        ACCELERATION,
        SURVIVING_SHIELD_RESERVE,
        SURVIVING_COMPARTMENT_INTEGRITY
    }

    private record PatrolAuthorization(
            double maxDryMassKg,
            double maxLoadedMassKg,
            int maxCrew,
            double maxContinuousPowerW,
            double maxAmmunitionMassKg,
            double reactionMassKg,
            long rounds) { }
}
