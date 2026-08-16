package com.spacesim.world;

import java.util.Objects;

/**
 * Executes common faction-policy commands through existing authoritative world boundaries.
 *
 * <p>This class contains no player-only or AI-only economic rules. Both callers submit the same
 * command values and receive the same deterministic mutation for identical world state.</p>
 */
public final class FactionPolicyCommandExecutor {
    private FactionPolicyCommandExecutor() {
        throw new AssertionError("Utility class");
    }

    /**
     * Executes one common faction-policy command.
     *
     * <p>Authoring commands only replace persistent policy state. Physical market targets and
     * production recipes change only for {@link FactionPolicyCommand.ApplyStrategicPolicy}; even that
     * ordinary apply never creates cargo, money or assets.</p>
     *
     * @param world authoritative world runtime
     * @param factionContentId stable target faction ID
     * @param command common policy command
     * @return immutable execution report
     */
    public static ExecutionResult execute(
            WorldSimulation world,
            String factionContentId,
            FactionPolicyCommand command) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        FactionPolicyCommand checkedCommand = Objects.requireNonNull(command, "Faction policy command not set");

        if (checkedCommand instanceof FactionPolicyCommand.UpdateDoctrine update) {
            checkedWorld.updateFactionDoctrine(factionId, update.doctrine());
            return ExecutionResult.authored(checkedCommand.kind());
        }
        if (checkedCommand instanceof FactionPolicyCommand.UpdateFiscalPolicy update) {
            checkedWorld.updateFactionFiscalPolicy(factionId, update.policy());
            return ExecutionResult.authored(checkedCommand.kind());
        }
        if (checkedCommand instanceof FactionPolicyCommand.UpdateStockProductionPolicy update) {
            checkedWorld.updateFactionStockProductionPolicy(factionId, update.policy());
            return ExecutionResult.authored(checkedCommand.kind());
        }
        if (checkedCommand instanceof FactionPolicyCommand.ApplyStrategicPolicy) {
            FactionStrategicPolicyEngine.ApplicationReport report =
                    checkedWorld.applyFactionStrategicPolicy(factionId);
            return new ExecutionResult(
                    checkedCommand.kind(),
                    report.marketsAdjusted(),
                    report.productionStationsRetooled(),
                    report.activeStrategicGoals());
        }
        throw new IllegalArgumentException("Unsupported faction policy command: " + checkedCommand.getClass());
    }

    /**
     * Result of executing one common faction-policy command.
     *
     * @param kind executed command family
     * @param marketsAdjusted ordinary market entities whose effective targets changed
     * @param productionStationsRetooled ordinary production entities whose recipe configuration changed
     * @param activeStrategicGoals active strategic goals observed by explicit strategic apply
     */
    public record ExecutionResult(
            FactionPolicyCommand.Kind kind,
            int marketsAdjusted,
            int productionStationsRetooled,
            int activeStrategicGoals) {
        /**
         * Validates one immutable command result.
         *
         * @param kind executed command family
         * @param marketsAdjusted non-negative ordinary market adjustment count
         * @param productionStationsRetooled non-negative ordinary retool count
         * @param activeStrategicGoals non-negative active strategic-goal count
         */
        public ExecutionResult {
            Objects.requireNonNull(kind, "Faction policy command kind not set");
            if (marketsAdjusted < 0 || productionStationsRetooled < 0 || activeStrategicGoals < 0) {
                throw new IllegalArgumentException("Faction policy command result counters cannot be negative");
            }
            if (kind != FactionPolicyCommand.Kind.APPLY_STRATEGIC_POLICY
                    && (marketsAdjusted != 0 || productionStationsRetooled != 0 || activeStrategicGoals != 0)) {
                throw new IllegalArgumentException("Authoring commands cannot report physical application");
            }
        }

        private static ExecutionResult authored(FactionPolicyCommand.Kind kind) {
            return new ExecutionResult(kind, 0, 0, 0);
        }
    }
}
