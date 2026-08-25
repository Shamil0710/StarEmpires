package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-19 exact-local encounter resolver for detached imported physical ships.
 *
 * <p>The resolver runs the accepted production control, kinetic, guided, defense, deception and beam
 * stack. It never produces a statistical combat score. The encounter is a bounded tactical exchange:
 * it ends at the first catastrophic physical ship destruction or at an explicit simulation horizon.
 * Any ammunition/propellant already consumed remains consumed. In-flight bodies leaving the bounded
 * encounter are not returned to a fleet inventory, so the boundary cannot recreate spent ordnance.</p>
 *
 * <p>The resolver mutates only detached tactical copies. A caller such as Stage 21E must explicitly
 * validate and commit returned physical engineering state and encounter-local kinematics to its
 * ordinary world authority.</p>
 */
public final class Stage19ExactTacticalEncounterResolver {
    /** Provisional Stage-19 encounter horizon: 120 exact simulation seconds. */
    public static final long DEFAULT_MAXIMUM_TICKS = Math.round(120d / LiveTacticalBattleControlRuntime.TICK_SECONDS);

    /**
     * Resolves one bounded exact-local encounter through the complete current Stage-19 runtime stack.
     *
     * @param imported exact detached physical combatants on both sides
     * @param maximumTicks positive hard simulation horizon in Stage-19 fixed ticks
     * @return immutable deterministic final physical state for every imported combatant
     */
    public Result resolve(List<ImportedCombatantState> imported, long maximumTicks) {
        Objects.requireNonNull(imported, "imported");
        if (maximumTicks <= 0L) {
            throw new IllegalArgumentException("maximumTicks must be positive");
        }
        LiveTacticalBattleRuntimeState battle = LiveTacticalBattleRuntimeState.importExact(imported);
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(battle);
        LiveTacticalBattleWeaponRuntime weapon = new LiveTacticalBattleWeaponRuntime(control);
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(weapon);
        LiveTacticalBattleDeceptionRuntime runtime = new LiveTacticalBattleDeceptionRuntime(ordnance);

        Termination termination = destroyedCombatants(battle).isEmpty()
                ? null
                : Termination.PHYSICAL_DESTRUCTION;
        long executed = 0L;
        while (termination == null && executed < maximumTicks) {
            runtime.advanceOneTick();
            executed++;
            if (!destroyedCombatants(battle).isEmpty()) {
                termination = Termination.PHYSICAL_DESTRUCTION;
            }
        }
        if (termination == null) {
            termination = Termination.ENCOUNTER_HORIZON;
        }

        ArrayList<CombatantResult> results = new ArrayList<>();
        for (CombatantRuntime combatant : battle.combatants()) {
            results.add(new CombatantResult(
                    combatant.spec().entityId(),
                    combatant.spec().side(),
                    combatant.engineering().fit,
                    combatant.engineering().runtimeState,
                    combatant.engineering().instanceState,
                    combatant.transform().position.x,
                    combatant.transform().position.y,
                    combatant.transform().velocity.x,
                    combatant.transform().velocity.y,
                    combatant.fullyDestroyed()));
        }
        results.sort(Comparator.comparingLong(CombatantResult::entityId));
        return new Result(executed, termination, List.copyOf(results));
    }

    /**
     * Resolves one encounter with the current provisional bounded Stage-19 horizon.
     *
     * @param imported exact detached physical combatants on both sides
     * @return immutable deterministic final physical state
     */
    public Result resolve(List<ImportedCombatantState> imported) {
        return resolve(imported, DEFAULT_MAXIMUM_TICKS);
    }

    private static List<Long> destroyedCombatants(LiveTacticalBattleRuntimeState battle) {
        return battle.combatants().stream()
                .filter(CombatantRuntime::fullyDestroyed)
                .map(value -> value.spec().entityId())
                .toList();
    }

    /** Why one bounded exact-local encounter returned control to the strategic layer. */
    public enum Termination {
        /** At least one imported physical ship reached catastrophic local destruction. */ PHYSICAL_DESTRUCTION,
        /** The bounded exact-local engagement horizon elapsed without catastrophic destruction. */ ENCOUNTER_HORIZON
    }

    /**
     * Final detached physical state of one imported combatant.
     *
     * @param entityId stable tactical identity supplied at import
     * @param side local battle allegiance
     * @param fit unchanged exact installed fit
     * @param runtimeState final physical consumables/power/thermal/propulsion state
     * @param instanceState final damage/shield/maintenance/weapon-continuity state
     * @param xM final encounter-frame x coordinate in meters
     * @param yM final encounter-frame y coordinate in meters
     * @param velocityXMps final encounter-frame x velocity in meters per second
     * @param velocityYMps final encounter-frame y velocity in meters per second
     * @param destroyed whether catastrophic physical destruction was reached
     */
    public record CombatantResult(
            long entityId,
            Side side,
            InstalledFit fit,
            RuntimeState runtimeState,
            ShipInstanceRuntimeState instanceState,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            boolean destroyed) {
        /**
         * Validates one final detached combatant result.
         *
         * @param entityId stable positive tactical identity
         * @param side local battle allegiance
         * @param fit unchanged exact installed fit
         * @param runtimeState final physical operating state
         * @param instanceState final physical local continuity state
         * @param xM final finite encounter-frame x coordinate in meters
         * @param yM final finite encounter-frame y coordinate in meters
         * @param velocityXMps final finite x velocity in meters per second
         * @param velocityYMps final finite y velocity in meters per second
         * @param destroyed catastrophic physical destruction flag
         */
        public CombatantResult {
            if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(runtimeState, "runtimeState");
            Objects.requireNonNull(instanceState, "instanceState");
            if (!Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)) {
                throw new IllegalArgumentException("final tactical kinematics must be finite");
            }
        }
    }

    /**
     * Whole bounded encounter result.
     *
     * @param ticksExecuted exact Stage-19 fixed ticks actually executed
     * @param termination physical destruction or bounded horizon
     * @param combatants final combatant state in canonical tactical-identity order
     */
    public record Result(long ticksExecuted, Termination termination, List<CombatantResult> combatants) {
        /**
         * Validates and freezes one encounter result.
         *
         * @param ticksExecuted non-negative Stage-19 fixed ticks executed
         * @param termination physical encounter termination reason
         * @param combatants non-empty canonical final combatant state
         */
        public Result {
            if (ticksExecuted < 0L) throw new IllegalArgumentException("ticksExecuted must be non-negative");
            Objects.requireNonNull(termination, "termination");
            Objects.requireNonNull(combatants, "combatants");
            if (combatants.size() < 2 || combatants.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("encounter result requires at least two combatants");
            }
            ArrayList<CombatantResult> canonical = new ArrayList<>(combatants);
            canonical.sort(Comparator.comparingLong(CombatantResult::entityId));
            if (!canonical.equals(combatants)) {
                throw new IllegalArgumentException("encounter results must be in canonical identity order");
            }
            combatants = List.copyOf(canonical);
        }

        /**
         * Resolves one final combatant by tactical identity.
         *
         * @param entityId stable tactical identity supplied at import
         * @return matching immutable final state
         */
        public CombatantResult require(long entityId) {
            return combatants.stream().filter(value -> value.entityId() == entityId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown encounter combatant: " + entityId));
        }
    }
}
