package com.spacesim.world;

import java.util.Objects;

/**
 * Common Stage-17F policy command contract shared by player-facing UI/services and AI planners.
 *
 * <p>The command value contains policy intent only. It does not move money, cargo, territory or
 * assets by itself. Execution remains delegated to the ordinary authoritative world boundaries.
 */
public sealed interface FactionPolicyCommand
        permits FactionPolicyCommand.UpdateDoctrine,
        FactionPolicyCommand.UpdateFiscalPolicy,
        FactionPolicyCommand.UpdateStockProductionPolicy,
        FactionPolicyCommand.ApplyStrategicPolicy {

    /** @return stable command kind for diagnostics/UI */
    Kind kind();

    /** Supported common policy command families. */
    enum Kind {
        /** Replace persistent institutional doctrine. */
        UPDATE_DOCTRINE,
        /** Replace persistent fiscal policy. */
        UPDATE_FISCAL_POLICY,
        /** Replace persistent strategic stock/production authoring. */
        UPDATE_STOCK_PRODUCTION_POLICY,
        /** Explicitly materialize current strategic stock/production policy into ordinary ECS configuration. */
        APPLY_STRATEGIC_POLICY
    }

    /**
     * Replaces one faction's persistent doctrine profile.
     *
     * @param doctrine new bounded doctrine profile
     */
    record UpdateDoctrine(FactionDoctrineState doctrine) implements FactionPolicyCommand {
        /**
         * Validates the command payload.
         *
         * @param doctrine new bounded doctrine profile
         */
        public UpdateDoctrine {
            Objects.requireNonNull(doctrine, "Faction doctrine not set");
        }

        @Override
        public Kind kind() {
            return Kind.UPDATE_DOCTRINE;
        }
    }

    /**
     * Replaces one faction's persistent fiscal policy.
     *
     * @param policy new bounded fiscal policy
     */
    record UpdateFiscalPolicy(FactionFiscalPolicyState policy) implements FactionPolicyCommand {
        /**
         * Validates the command payload.
         *
         * @param policy new bounded fiscal policy
         */
        public UpdateFiscalPolicy {
            Objects.requireNonNull(policy, "Faction fiscal policy not set");
        }

        @Override
        public Kind kind() {
            return Kind.UPDATE_FISCAL_POLICY;
        }
    }

    /**
     * Replaces one faction's persistent strategic stock floors and production recipe preferences.
     *
     * @param policy new stock/production authoring state
     */
    record UpdateStockProductionPolicy(FactionStockProductionPolicyState policy)
            implements FactionPolicyCommand {
        /**
         * Validates the command payload.
         *
         * @param policy new stock/production authoring state
         */
        public UpdateStockProductionPolicy {
            Objects.requireNonNull(policy, "Faction stock/production policy not set");
        }

        @Override
        public Kind kind() {
            return Kind.UPDATE_STOCK_PRODUCTION_POLICY;
        }
    }

    /** Explicitly applies already-authored strategic stock/production policy. */
    record ApplyStrategicPolicy() implements FactionPolicyCommand {
        @Override
        public Kind kind() {
            return Kind.APPLY_STRATEGIC_POLICY;
        }
    }
}
