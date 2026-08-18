package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalOrdnanceObservationRuntime.ScanDiagnostics;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalOrdnanceElectronicWarfareTest {
    private static final long ATTACKER_ID = 199_501L;
    private static final long OBSERVER_ID = 199_502L;
    private static final long JAMMER_ID = 199_503L;

    @Test
    void physicalNoiseJammerSuppressesMissileMeasurementWhileEccmConsumesMoreBudget() {
        ObservationCase clear = runUntilRepresentativeScan(false);
        ObservationCase jammed = runUntilRepresentativeScan(true);

        assertTrue(clear.diagnostics().measurementsProduced() > 0,
                "clear production radar must measure the physically present guided threat");
        assertFalse(clear.diagnostics().eccmRequested());
        assertFalse(clear.diagnostics().eccmCommitted());

        assertTrue(jammed.diagnostics().noiseJammerCount() > 0,
                "fitted EW ship must appear as a physical receiver-local noise source");
        assertTrue(jammed.diagnostics().eccmRequested());
        assertTrue(jammed.diagnostics().eccmCommitted(),
                "available production engineering budget must physically admit ECCM processing");
        assertTrue(jammed.diagnostics().committedPowerW() > clear.diagnostics().committedPowerW(),
                "ECCM must consume additional admitted electrical power rather than being a free bonus");
        assertTrue(jammed.diagnostics().committedHeatW() > clear.diagnostics().committedHeatW(),
                "ECCM must create additional admitted local heat");
        assertTrue(jammed.diagnostics().measurementsProduced() < clear.diagnostics().measurementsProduced(),
                "sufficient physical interference must suppress measurements even after finite ECCM processing gain");
    }

    @Test
    void samePhysicalEwCaseReplaysDeterministically() {
        ObservationCase first = runUntilRepresentativeScan(true);
        ObservationCase second = runUntilRepresentativeScan(true);

        assertTrue(first.runtime().ordnanceRuntime().fingerprint()
                        .equals(second.runtime().ordnanceRuntime().fingerprint()),
                "same fixed-tick physical EW scenario must reproduce the same authoritative ordnance state");
        assertTrue(first.diagnostics().equals(second.diagnostics()),
                "receiver-local jammer/ECCM diagnostics must replay deterministically");
        assertTrue(first.runtime().tracksForObserver(OBSERVER_ID)
                        .equals(second.runtime().tracksForObserver(OBSERVER_ID)),
                "actor-bounded missile tracks must replay deterministically under EW");
    }

    private static ObservationCase runUntilRepresentativeScan(boolean includeJammer) {
        List<CombatantSpec> combatants = new ArrayList<>();
        combatants.add(new CombatantSpec(
                ATTACKER_ID, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 260d, 700d));
        combatants.add(new CombatantSpec(
                OBSERVER_ID, Side.BETA, DoctrineId.B_MISSILE_STRIKE, 1_690d, 700d));
        if (includeJammer) {
            combatants.add(new CombatantSpec(
                    JAMMER_ID, Side.ALPHA, DoctrineId.D_DEFENSIVE_EW, 1_050d, 1_050d));
        }
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(combatants));
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        LiveTacticalOrdnanceObservationRuntime observation =
                new LiveTacticalOrdnanceObservationRuntime(ordnance);

        ScanDiagnostics representative = null;
        for (int index = 0; index < 800; index++) {
            ordnance.advanceOneTick();
            observation.observeCurrentTick();
            ScanDiagnostics current = observation.lastScanDiagnostics(OBSERVER_ID);
            if (ordnance.guidedLaunches(ATTACKER_ID) > 0L
                    && current.committedPowerW() > 0d
                    && (!includeJammer || current.noiseJammerCount() > 0)) {
                representative = current;
                if (!includeJammer && current.measurementsProduced() > 0) {
                    break;
                }
                if (includeJammer && current.eccmCommitted()) {
                    break;
                }
            }
        }
        assertTrue(ordnance.guidedLaunches(ATTACKER_ID) > 0L,
                "acceptance fixture must materialize a real hostile guided body");
        assertNotNull(representative,
                "acceptance fixture must reach a physically admitted representative radar scan");
        assertTrue(representative.committedPowerW() > 0d);
        return new ObservationCase(observation, representative);
    }

    private record ObservationCase(
            LiveTacticalOrdnanceObservationRuntime runtime,
            ScanDiagnostics diagnostics) {
    }
}
