package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.controllers.TradeTransactionPolicy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Live world-backed customs settlement policy for ordinary {@link com.spacesim.controllers.TradeController}. */
final class WorldTradeTransactionPolicy implements TradeTransactionPolicy {
    private static final long BASIS_POINTS_DENOMINATOR = 10_000L;

    private final FactionIdentityResolver identities;
    private final Map<String, FactionEconomicAccount> accounts;
    private final Supplier<List<FactionDiplomacyState>> diplomacyStates;
    private final LongSupplier worldTick;

    WorldTradeTransactionPolicy(
            FactionIdentityResolver identities,
            Map<String, FactionEconomicAccount> accounts,
            Supplier<List<FactionDiplomacyState>> diplomacyStates,
            LongSupplier worldTick) {
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        this.accounts = Objects.requireNonNull(accounts, "Faction accounts not set");
        this.diplomacyStates = Objects.requireNonNull(diplomacyStates, "Diplomacy supplier not set");
        this.worldTick = Objects.requireNonNull(worldTick, "World tick supplier not set");
    }

    @Override
    public Charge quote(Entity station, Entity participant, Direction direction, long tradeValueMilliCredits) {
        Objects.requireNonNull(station, "Trade station not set");
        Objects.requireNonNull(participant, "Trade participant not set");
        Objects.requireNonNull(direction, "Trade direction not set");
        if (tradeValueMilliCredits <= 0L) {
            throw new IllegalArgumentException("Trade value must be positive");
        }
        FactionComponent stationFaction = station.getComponent(FactionComponent.class);
        if (stationFaction == null) {
            return Charge.none();
        }
        String ownerId = identities.stableId(stationFaction.factionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown market runtime faction: " + stationFaction.factionId));
        FactionComponent participantFaction = participant.getComponent(FactionComponent.class);
        String participantId = participantFaction == null ? null : identities.stableId(participantFaction.factionId).orElseThrow(
                () -> new IllegalArgumentException("Unknown trader runtime faction: " + participantFaction.factionId));
        CustomsTariffResolver.Decision decision = CustomsTariffResolver.evaluate(
                diplomacyStates.get(), ownerId, participantId, worldTick.getAsLong());
        long duty = basisPointCeil(tradeValueMilliCredits, decision.basisPoints());
        if (duty == 0L) {
            return Charge.none();
        }
        FactionEconomicAccount account = accounts.get(ownerId);
        if (account == null) {
            throw new IllegalStateException("Customs collector has no faction treasury: " + ownerId);
        }
        return new Charge(
                duty,
                account.treasury(),
                "faction:" + ownerId + ":treasury",
                "customs-tariff");
    }

    static long basisPointCeil(long value, int basisPoints) {
        if (value <= 0L || basisPoints <= 0) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / BASIS_POINTS_DENOMINATOR, basisPoints);
        long remainderProduct = (value % BASIS_POINTS_DENOMINATOR) * basisPoints;
        long remainder = remainderProduct / BASIS_POINTS_DENOMINATOR;
        if (remainderProduct % BASIS_POINTS_DENOMINATOR != 0L) {
            remainder = Math.addExact(remainder, 1L);
        }
        return Math.addExact(whole, remainder);
    }
}
