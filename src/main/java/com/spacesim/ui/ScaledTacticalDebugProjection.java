package com.spacesim.ui;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringState;
import com.spacesim.ui.ScaledTacticalDebugSnapshot.BodyCounts;
import com.spacesim.ui.ScaledTacticalDebugSnapshot.CombatantDebug;
import com.spacesim.ui.ScaledTacticalDebugSnapshot.FormationDebug;
import com.spacesim.ui.ScaledTacticalDebugSnapshot.TrackDebug;

import java.util.List;
import java.util.Objects;

/** Strict read-only diagnostic projection for the scaled Stage-19I tactical runtime. */
public final class ScaledTacticalDebugProjection {
    /**
     * Projects current actor/control/engineering diagnostics without advancing or mutating combat.
     *
     * @param runtime authoritative scaled tactical runtime
     * @return immutable diagnostic snapshot
     */
    public ScaledTacticalDebugSnapshot project(LiveTacticalBattleDeceptionRuntime runtime) {
        LiveTacticalBattleDeceptionRuntime checked = Objects.requireNonNull(runtime, "runtime");
        var controlRuntime = checked.ordnanceRuntime().weaponRuntime().controlRuntime();
        List<CombatantDebug> combatants = checked.battleState().combatants().stream()
                .map(combatant -> combatantDebug(checked, controlRuntime, combatant))
                .toList();
        return new ScaledTacticalDebugSnapshot(
                checked.tick(),
                combatants,
                new BodyCounts(
                        checked.ordnanceRuntime().weaponRuntime().projectiles().size(),
                        checked.ordnanceRuntime().guidedBodies().size(),
                        checked.defenseRuntime().interceptorBodies().size(),
                        checked.decoyRuntime().decoyBodies().size()));
    }

    private static CombatantDebug combatantDebug(
            LiveTacticalBattleDeceptionRuntime runtime,
            com.spacesim.ship.LiveTacticalBattleControlRuntime controlRuntime,
            CombatantRuntime combatant) {
        long entityId = combatant.spec().entityId();
        var control = controlRuntime.controlState(entityId);
        var formation = controlRuntime.formationState(entityId);
        var engineering = combatant.engineering();
        var consumables = engineering.runtimeState.consumables();
        List<TrackDebug> tracks = runtime.battleState().visibleContacts(entityId).stream()
                .map(value -> new TrackDebug(
                        value.track().targetId(),
                        value.track().informationState(),
                        value.track().positionKnown()))
                .toList();
        return new CombatantDebug(
                entityId,
                combatant.spec().side(),
                tracks,
                control.intent().targetSelected() ? control.intent().targetId() : 0L,
                control.intent().fireRequested(),
                control.fireAuthorized(),
                control.movementAxisX(),
                control.movementAxisY(),
                control.survivalDecision().action(),
                control.survivalDecision().reason(),
                new FormationDebug(
                        formation.objectiveKnown(),
                        formation.mode(),
                        formation.slotIndex(),
                        formation.slotCount(),
                        formation.desiredYM(),
                        formation.errorM(),
                        formation.status(),
                        formation.reason()),
                ammunitionCount(consumables),
                reactionMassKg(consumables),
                engineering.runtimeState.sharedBusEnergyJ(),
                engineering.runtimeState.shipHeatStoredJ(),
                engineering.runtimeState.localHeatJByMount().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                meanIntegrity(combatant),
                minimumModuleIntegrity(combatant));
    }

    private static long ammunitionCount(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static double reactionMassKg(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }

    private static double meanIntegrity(CombatantRuntime combatant) {
        return combatant.hull().compartments().stream()
                .mapToDouble(value -> combatant.engineering().instanceState.damage()
                        .compartmentIntegrityById().getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
    }

    private static double minimumModuleIntegrity(CombatantRuntime combatant) {
        return combatant.engineering().fit.installedModules().stream()
                .mapToDouble(value -> combatant.engineering().instanceState.damage()
                        .moduleDamage().moduleIntegrityByMount().getOrDefault(value.mountId(), 1d))
                .min()
                .orElse(1d);
    }
}
