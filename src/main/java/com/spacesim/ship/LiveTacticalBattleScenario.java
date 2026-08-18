package com.spacesim.ship;

import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic authored roster for one exact local Stage-19I live tactical battle.
 *
 * <p>The scenario owns only stable combatant identity, side, doctrine/content selection and initial
 * local position. It does not own ship statistics, ammunition, reaction mass, damage, sensors,
 * targeting or combat outcomes. Those remain production runtime state materialized from the
 * referenced doctrine/content.</p>
 */
public record LiveTacticalBattleScenario(List<CombatantSpec> combatants) {
    /**
     * Validates and canonically orders one live tactical scenario.
     *
     * @param combatants combatants participating in the exact local battle
     */
    public LiveTacticalBattleScenario {
        Objects.requireNonNull(combatants, "combatants");
        if (combatants.size() < 2) {
            throw new IllegalArgumentException("Live tactical scenario requires at least two combatants");
        }

        ArrayList<CombatantSpec> canonical = new ArrayList<>(combatants.size());
        Set<Long> entityIds = new HashSet<>();
        boolean alphaPresent = false;
        boolean betaPresent = false;
        for (CombatantSpec combatant : combatants) {
            CombatantSpec checked = Objects.requireNonNull(combatant, "combatant");
            if (!entityIds.add(checked.entityId())) {
                throw new IllegalArgumentException("Duplicate live tactical combatant entity id: "
                        + checked.entityId());
            }
            alphaPresent |= checked.side() == Side.ALPHA;
            betaPresent |= checked.side() == Side.BETA;
            canonical.add(checked);
        }
        if (!alphaPresent || !betaPresent) {
            throw new IllegalArgumentException("Live tactical scenario requires combatants on both sides");
        }
        canonical.sort(Comparator.comparingLong(CombatantSpec::entityId));
        combatants = List.copyOf(canonical);
    }

    /**
     * Returns the canonically ordered combatants belonging to one side.
     *
     * @param side requested battle side
     * @return immutable side roster ordered by stable entity id
     */
    public List<CombatantSpec> combatantsFor(Side side) {
        Side checked = Objects.requireNonNull(side, "side");
        return combatants.stream()
                .filter(value -> value.side() == checked)
                .toList();
    }

    /**
     * Returns the legacy Stage-19I-A duel expressed through the generic scenario roster seam.
     *
     * @return deterministic one-versus-one scenario preserving the existing live-view coordinates
     */
    public static LiveTacticalBattleScenario legacyDuel() {
        return new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(
                        LiveTacticalSimulationSession.ATTACKER_ENTITY_ID,
                        Side.ALPHA,
                        DoctrineId.E_BALANCED_CONTROL,
                        260d,
                        700d),
                new CombatantSpec(
                        LiveTacticalSimulationSession.TARGET_ENTITY_ID,
                        Side.BETA,
                        DoctrineId.E_BALANCED_CONTROL,
                        1_690d,
                        700d)));
    }

    /**
     * Returns the first deterministic symmetric 4v4 roster required by the Stage-19I scale ladder.
     *
     * <p>This method authors identities and initial geometry only. Until the multi-combatant runtime
     * consumes this roster, it is not evidence that the 4v4 Stage-19I acceptance gate has passed.</p>
     *
     * @return deterministic eight-combatant balanced-control scenario
     */
    public static LiveTacticalBattleScenario balanced4v4() {
        ArrayList<CombatantSpec> roster = new ArrayList<>(8);
        double[] yPositions = {520d, 640d, 760d, 880d};
        for (int index = 0; index < yPositions.length; index++) {
            roster.add(new CombatantSpec(
                    191_100L + index,
                    Side.ALPHA,
                    DoctrineId.E_BALANCED_CONTROL,
                    260d,
                    yPositions[index]));
            roster.add(new CombatantSpec(
                    191_200L + index,
                    Side.BETA,
                    DoctrineId.E_BALANCED_CONTROL,
                    1_690d,
                    yPositions[index]));
        }
        return new LiveTacticalBattleScenario(roster);
    }

    /**
     * Returns the first deterministic mixed-doctrine 8v8 exact-local Stage-19I roster.
     *
     * <p>The roster deliberately mixes kinetic-line, missile-strike, defensive-EW and balanced
     * acceptance fits while keeping equal doctrine counts on both sides. Doctrine IDs select only
     * existing authored physical fits/stores; they grant no hidden combat multiplier. The two sides
     * use different vertical ordering so the fleet case is not eight duplicated mirror duels.</p>
     *
     * @return deterministic sixteen-combatant mixed-doctrine scenario
     */
    public static LiveTacticalBattleScenario mixed8v8() {
        ArrayList<CombatantSpec> roster = new ArrayList<>(16);
        double[] yPositions = {280d, 400d, 520d, 640d, 760d, 880d, 1_000d, 1_120d};
        DoctrineId[] alphaDoctrines = {
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL
        };
        DoctrineId[] betaDoctrines = {
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL
        };
        for (int index = 0; index < yPositions.length; index++) {
            roster.add(new CombatantSpec(
                    191_300L + index,
                    Side.ALPHA,
                    alphaDoctrines[index],
                    260d,
                    yPositions[index]));
            roster.add(new CombatantSpec(
                    191_400L + index,
                    Side.BETA,
                    betaDoctrines[index],
                    1_690d,
                    yPositions[index]));
        }
        return new LiveTacticalBattleScenario(roster);
    }

    /**
     * Returns the deterministic 16v16 exact-local roster for the Stage-19I >=32-combatant gate.
     *
     * <p>Each side contains four kinetic-line, four missile-strike, two defensive-EW and six balanced
     * acceptance fits. The counts are identical while vertical doctrine ordering differs. This scales
     * one authored physical battle to 32 ships without creating a fleet resolver or numerical doctrine
     * bonus. Saturation-specific feed overrides remain a later gate.</p>
     *
     * @return deterministic thirty-two-combatant mixed-doctrine scenario
     */
    public static LiveTacticalBattleScenario mixed16v16() {
        ArrayList<CombatantSpec> roster = new ArrayList<>(32);
        double[] yPositions = {
                -40d, 60d, 160d, 260d, 360d, 460d, 560d, 660d,
                760d, 860d, 960d, 1_060d, 1_160d, 1_260d, 1_360d, 1_460d
        };
        DoctrineId[] alphaDoctrines = {
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL
        };
        DoctrineId[] betaDoctrines = {
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.D_DEFENSIVE_EW,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.B_MISSILE_STRIKE,
                DoctrineId.E_BALANCED_CONTROL,
                DoctrineId.A_KINETIC_LINE
        };
        for (int index = 0; index < yPositions.length; index++) {
            roster.add(new CombatantSpec(
                    191_500L + index,
                    Side.ALPHA,
                    alphaDoctrines[index],
                    260d,
                    yPositions[index]));
            roster.add(new CombatantSpec(
                    191_600L + index,
                    Side.BETA,
                    betaDoctrines[index],
                    1_690d,
                    yPositions[index]));
        }
        return new LiveTacticalBattleScenario(roster);
    }

    /** Battle allegiance within one exact local tactical session. */
    public enum Side {
        /** First authored combat side. */
        ALPHA,
        /** Opposing authored combat side. */
        BETA
    }

    /**
     * Immutable authored identity and spawn information for one combatant.
     *
     * @param entityId stable unique physical entity identity
     * @param side authored battle allegiance
     * @param doctrineId doctrine/content selection; doctrine grants no hidden physical multiplier
     * @param xM initial local x coordinate in meters
     * @param yM initial local y coordinate in meters
     */
    public record CombatantSpec(
            long entityId,
            Side side,
            DoctrineId doctrineId,
            double xM,
            double yM) {
        /**
         * Validates one authored combatant specification.
         *
         * @param entityId stable unique physical entity identity
         * @param side authored battle allegiance
         * @param doctrineId doctrine/content selection
         * @param xM initial x coordinate in meters
         * @param yM initial y coordinate in meters
         */
        public CombatantSpec {
            if (entityId <= 0L) {
                throw new IllegalArgumentException("Live tactical entity id must be positive");
            }
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(doctrineId, "doctrineId");
            if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
                throw new IllegalArgumentException("Live tactical spawn coordinates must be finite");
            }
        }
    }
}
