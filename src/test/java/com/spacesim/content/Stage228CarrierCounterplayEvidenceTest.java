package com.spacesim.content;

import com.spacesim.content.Stage228CarrierCounterplayEvidence.CounterplaySurface;
import com.spacesim.content.Stage228CarrierCounterplayEvidence.RoleVector;
import com.spacesim.content.Stage228CarrierCounterplayEvidence.ScenarioKind;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228CarrierCounterplayEvidenceTest {

    @Test
    void allRequiredMatchupsExposeExistingPhysicalCounterplayInsteadOfOutcomeModifiers() {
        var evidence = Stage228CarrierCounterplayEvidence.deriveCurrent();

        assertEquals(
                EnumSet.allOf(ScenarioKind.class),
                EnumSet.copyOf(evidence.scenarios().stream()
                        .map(value -> value.kind()).toList()));
        assertEquals(5, evidence.scenarios().size());
        evidence.scenarios().forEach(row -> {
            assertFalse(row.counterplaySurfaces().isEmpty());
            assertFalse(row.commonAuthorities().isEmpty());
            assertFalse(row.acceptanceFixtures().isEmpty(),
                    "every K matchup must name concrete deterministic regression evidence");
            assertTrue(row.commonAuthorities().stream()
                    .noneMatch(value -> value.toLowerCase().contains("modifier")));
        });

        assertTrue(evidence.scenarios().stream()
                .filter(value -> value.kind() == ScenarioKind.MISSILE_HEAVY_FORCES)
                .flatMap(value -> value.counterplaySurfaces().stream())
                .anyMatch(value -> value
                        == CounterplaySurface.POINT_DEFENSE_CHANNELS_AMMO_THERMAL));
        assertTrue(evidence.scenarios().stream()
                .filter(value -> value.kind() == ScenarioKind.EW_AND_DECEPTION_PRESSURE)
                .flatMap(value -> value.counterplaySurfaces().stream())
                .anyMatch(value -> value == CounterplaySurface.ELECTRONIC_WARFARE));
        assertTrue(evidence.scenarios().stream()
                .filter(value -> value.kind() == ScenarioKind.DEGRADED_LOGISTICS_AND_REPLACEMENT)
                .flatMap(value -> value.counterplaySurfaces().stream())
                .anyMatch(value -> value == CounterplaySurface.STAGE18_REPAIR_AND_REPLACEMENT));
    }

    @Test
    void interceptorDefenseAndStrikeHaveRealTradeoffsAndNoUniversallyDominantFit() {
        var evidence = Stage228CarrierCounterplayEvidence.deriveCurrent();

        for (String faction : List.of(
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID)) {
            RoleVector interceptor = role(
                    evidence.roleVectors(), faction,
                    Stage228SmallCraftProductionProjection.ROLE_INTERCEPTION);
            RoleVector defence = role(
                    evidence.roleVectors(), faction,
                    Stage228SmallCraftProductionProjection.ROLE_DEFENCE);
            RoleVector strike = role(
                    evidence.roleVectors(), faction,
                    Stage228SmallCraftProductionProjection.ROLE_STRIKE);

            assertTrue(interceptor.accelerationMps2() > defence.accelerationMps2(),
                    "shield mass must cost defensive craft acceleration");
            assertEquals(0d, interceptor.shieldReserveJ(), 0d);
            assertTrue(defence.shieldReserveJ() > 0d);

            assertTrue(interceptor.beamPowerW() > 0d);
            assertTrue(defence.beamPowerW() > 0d);
            assertEquals(0d, strike.beamPowerW(), 0d);
            assertTrue(strike.kineticProjectileMassKg() > 0d);
            assertTrue(strike.ammunitionCapacity() > 0d);

            assertEquals(0d, interceptor.kineticProjectileMassKg(), 0d);
            assertEquals(0d, defence.kineticProjectileMassKg(), 0d);
            assertTrue(interceptor.reactionMassCapacityKg() > 0d);
            assertTrue(defence.reactionMassCapacityKg() > 0d);
            assertTrue(strike.reactionMassCapacityKg() > 0d);
            assertTrue(interceptor.maintenanceWorkSeconds() > 0d);
            assertTrue(defence.maintenanceWorkSeconds() > interceptor.maintenanceWorkSeconds(),
                    "defensive field adds real maintenance burden");
            assertTrue(strike.maintenanceWorkSeconds() > 0d);
        }
    }

    @Test
    void carrierCapacityAndReplacementBurdenRemainFiniteAndPhysical() {
        var evidence = Stage228CarrierCounterplayEvidence.deriveCurrent();

        assertEquals(2, evidence.carrierBayVectors().size());
        evidence.carrierBayVectors().values().forEach(carrier -> {
            assertTrue(carrier.bayCount() > 0);
            assertTrue(carrier.totalSupportedMassKg() > 0d);
            assertTrue(carrier.totalUsableVolumeM3() > 0d);
            assertEquals(3, carrier.boundedCapacityByRole().size());
            carrier.boundedCapacityByRole().values().forEach(count -> {
                assertTrue(count > 0, "production carrier must physically embark each J role");
                assertTrue(count < 10_000, "capacity must be finite rather than a virtual pool");
            });
        });

        assertEquals(2, evidence.replacementVectors().size());
        evidence.replacementVectors().values().forEach(replacement -> {
            assertTrue(replacement.hullBuildMassKg() > 0d);
            assertTrue(replacement.distinctHullInputCount() > 1);
            assertTrue(replacement.minimumInstalledModuleCount() > 0);
            assertTrue(replacement.maximumInstalledModuleCount()
                    >= replacement.minimumInstalledModuleCount());
        });
    }

    private static RoleVector role(
            List<RoleVector> roles,
            String faction,
            String roleId) {
        return roles.stream()
                .filter(value -> value.stableFactionId().equals(faction))
                .filter(value -> value.roleId().equals(roleId))
                .findFirst()
                .orElseThrow();
    }
}
