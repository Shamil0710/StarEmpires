package com.spacesim.ship;

import com.spacesim.ship.TacticalSurvivalPlanner.MissionDirective;
import com.spacesim.ship.TacticalSurvivalPlanner.SafePoint;

import java.util.Objects;

/**
 * Authored mission-level objective for one side of an exact-local tactical battle.
 *
 * <p>This object carries intent and own-side geometry only. It grants no combat statistics, does not
 * read enemy authority and does not move a ship. The shared control runtime translates the objective
 * into existing tactical/survival policy; physical execution remains engineering plus flight.</p>
 *
 * @param kind objective family
 * @param withdrawalPoint known withdrawal point for {@link Kind#WITHDRAW_TO_POINT}, otherwise unknown
 */
public record TacticalBattleObjective(Kind kind, SafePoint withdrawalPoint) {
    /** Mission-level tactical objective family. */
    public enum Kind {
        /** Continue normal engagement policy. */ ENGAGE,
        /** Leave the engagement toward an authored known safe point. */ WITHDRAW_TO_POINT
    }

    /**
     * Validates one authored battle objective.
     *
     * @param kind objective family
     * @param withdrawalPoint known withdrawal point for withdrawal, otherwise canonical unknown
     */
    public TacticalBattleObjective {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(withdrawalPoint, "withdrawalPoint");
        if (kind == Kind.ENGAGE && withdrawalPoint.known()) {
            throw new IllegalArgumentException("ENGAGE objective must not carry withdrawal geometry");
        }
        if (kind == Kind.WITHDRAW_TO_POINT && !withdrawalPoint.known()) {
            throw new IllegalArgumentException("WITHDRAW_TO_POINT requires a known safe point");
        }
    }

    /** @return canonical normal-engagement objective */
    public static TacticalBattleObjective engage() {
        return new TacticalBattleObjective(Kind.ENGAGE, SafePoint.unknown());
    }

    /**
     * Creates an authored withdrawal objective.
     *
     * @param xM known safe-point x coordinate in meters
     * @param yM known safe-point y coordinate in meters
     * @return withdrawal objective
     */
    public static TacticalBattleObjective withdrawTo(double xM, double yM) {
        return new TacticalBattleObjective(Kind.WITHDRAW_TO_POINT, new SafePoint(true, xM, yM));
    }

    /** @return survival-policy directive corresponding to this mission objective */
    public MissionDirective survivalDirective() {
        return kind == Kind.WITHDRAW_TO_POINT ? MissionDirective.WITHDRAW : MissionDirective.NORMAL;
    }
}
