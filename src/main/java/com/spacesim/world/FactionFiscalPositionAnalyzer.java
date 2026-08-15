package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Computes read-only fiscal consequences from the ordinary authoritative economy.
 *
 * <p>The analyzer observes existing wallets/projects only. It does not settle taxes, grant subsidies,
 * reserve money in a second account, modify market prices or apply synthetic efficiency modifiers.</p>
 */
public final class FactionFiscalPositionAnalyzer {
    private FactionFiscalPositionAnalyzer() {
        throw new AssertionError("Utility class");
    }

    /**
     * Measures one faction's current treasury/liquidity/construction position.
     *
     * @param world authoritative world runtime
     * @param factionContentId authored or world-defined stable faction ID
     * @return immutable diagnostics derived from real wallet state
     */
    public static FactionFiscalPositionDiagnostics analyze(
            WorldSimulation world,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }

        FactionEconomicState economy = checkedWorld.findFactionEconomicState(factionId).orElseThrow(
                () -> new IllegalArgumentException("Faction has no economic state: " + factionId));
        FactionFiscalPolicyState policy = checkedWorld.findFactionFiscalPolicy(factionId).orElseThrow(
                () -> new IllegalArgumentException("Faction has no fiscal policy: " + factionId));
        int runtimeFactionId = checkedWorld.findFactionRuntimeId(factionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown faction identity: " + factionId));

        Set<SiteKey> constructionSites = new HashSet<>();
        int activeConstructionProjects = 0;
        long activeConstructionWallet = 0L;
        for (ConstructionProjectState project : checkedWorld.getConstructionProjects()) {
            EntityId siteId = project.constructionSiteEntityId();
            if (siteId != null) {
                constructionSites.add(new SiteKey(project.systemId(), siteId));
            }
            if (project.settlementKind() == ConstructionSettlementKind.FACTION_TREASURY
                    && factionId.equals(project.ownerFactionContentId())
                    && !isTerminal(project.status())) {
                activeConstructionProjects++;
                activeConstructionWallet = safeAdd(
                        activeConstructionWallet,
                        project.projectWalletMilliCredits());
            }
        }

        int ownedMarkets = 0;
        int belowReserve = 0;
        long ownedMarketLiquidity = 0L;
        long reserveTarget = 0L;
        long shortfall = 0L;
        for (StarSystemNode system : checkedWorld.getTopology().systems()) {
            SimulationSession session = checkedWorld.findSession(system.id()).orElseThrow(
                    () -> new IllegalStateException("World topology lost SimulationSession: " + system.id()));
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                MarketComponent market = entity.getComponent(MarketComponent.class);
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                EntityIdComponent entityId = entity.getComponent(EntityIdComponent.class);
                if (faction == null
                        || faction.factionId != runtimeFactionId
                        || market == null
                        || wallet == null
                        || entityId == null
                        || constructionSites.contains(new SiteKey(system.id(), entityId.id))) {
                    continue;
                }

                ownedMarkets++;
                long balance = wallet.getBalanceMilliCredits();
                ownedMarketLiquidity = safeAdd(ownedMarketLiquidity, balance);
                reserveTarget = safeAdd(reserveTarget, policy.stationLiquidityReserveMilliCredits());
                if (balance < policy.stationLiquidityReserveMilliCredits()) {
                    belowReserve++;
                    shortfall = safeAdd(
                            shortfall,
                            policy.stationLiquidityReserveMilliCredits() - balance);
                }
            }
        }

        return new FactionFiscalPositionDiagnostics(
                factionId,
                policy,
                economy.treasuryMilliCredits(),
                policy.spendableTreasuryMilliCredits(economy.treasuryMilliCredits()),
                ownedMarkets,
                belowReserve,
                ownedMarketLiquidity,
                reserveTarget,
                shortfall,
                activeConstructionProjects,
                activeConstructionWallet);
    }

    private static boolean isTerminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Fiscal diagnostics money total overflow", exception);
        }
    }

    private record SiteKey(StarSystemId systemId, EntityId entityId) {
        private SiteKey {
            Objects.requireNonNull(systemId, "Fiscal diagnostic system ID not set");
            Objects.requireNonNull(entityId, "Fiscal diagnostic entity ID not set");
        }
    }
}
