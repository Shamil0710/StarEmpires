package com.spacesim.content;

import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.world.*;

import java.util.List;

/** B16 core-identity command/persistence contract; trade-volume recovery is a separate campaign gate. */
final class Stage22CorePairTreatyProbe {
    private Stage22CorePairTreatyProbe() { }

    static Result run(long seed, Permutation permutation) {
        String owner = permutation == Permutation.DEFAULT ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        String visitor = permutation == Permutation.DEFAULT ? Stage22CorePairBalanceEvidence.UNION_FACTION_ID
                : Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;
        var world = Stage22CorePairWorldFixture.create(seed);
        var before = access(world, owner, visitor);
        int beforeTariff = tariff(world, owner, visitor).basisPoints();
        var offered = world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(owner, visitor,
                List.of(new DiplomaticTreatyClauseState(DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                DiplomaticTreatyClauseState.Direction.MUTUAL, null),
                        new DiplomaticTreatyClauseState(DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                                DiplomaticTreatyClauseState.Direction.MUTUAL, null)), -1L)).treaty();
        world = Stage22CorePairWorldFixture.roundTrip(world);
        boolean pendingDidNotGrant = !access(world, owner, visitor).allowed()
                && tariff(world, owner, visitor).basisPoints() == beforeTariff;
        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(visitor, offered.treatyId()));
        world = Stage22CorePairWorldFixture.roundTrip(world);
        var active = access(world, owner, visitor);
        var activeTariff = tariff(world, owner, visitor);
        boolean mutual = access(world, visitor, owner).allowed() && tariff(world, visitor, owner).basisPoints() == 0;
        var replay = Stage22CorePairWorldFixture.roundTrip(world);
        var breach = new DiplomaticTreatyCommand.Breach(owner, offered.treatyId(), "core-pair-B16-access-shock");
        world.applyDiplomaticTreatyCommand(breach);
        replay.applyDiplomaticTreatyCommand(breach);
        boolean continuation = world.snapshot().equals(replay.snapshot());
        world = Stage22CorePairWorldFixture.roundTrip(world);
        var after = access(world, owner, visitor);
        var afterTariff = tariff(world, owner, visitor);
        return new Result(owner, visitor, before.allowed(), beforeTariff, pendingDidNotGrant,
                active.allowed(), active.reason().name(), activeTariff.basisPoints(), mutual,
                after.allowed(), afterTariff.basisPoints(), continuation);
    }

    private static DiplomaticMarketAccessResolver.Decision access(WorldSimulation world, String owner, String visitor) {
        var state = world.snapshot();
        return DiplomaticMarketAccessResolver.evaluate(state.factionStrategies(), state.factionDiplomacyStates(),
                owner, visitor, world.getAuthoritativeWorldTick());
    }

    private static CustomsTariffResolver.Decision tariff(WorldSimulation world, String owner, String visitor) {
        return CustomsTariffResolver.evaluate(world.snapshot().factionDiplomacyStates(), owner, visitor,
                world.getAuthoritativeWorldTick());
    }

    record Result(String ownerFactionId, String visitingFactionId, boolean initiallyAllowed, int initialTariffBps,
            boolean pendingDidNotGrant, boolean activeAllowed, String activeAccessReason, int activeTariffBps,
            boolean mutualAccess, boolean afterBreachAllowed, int afterBreachTariffBps, boolean continuationEqual) {
        boolean valid() {
            return !initiallyAllowed && initialTariffBps == 750 && pendingDidNotGrant && activeAllowed
                    && activeAccessReason.equals("EXPLICIT_TREATY_RIGHT") && activeTariffBps == 0 && mutualAccess
                    && !afterBreachAllowed && afterBreachTariffBps == 750 && continuationEqual;
        }
    }

    public static void main(String[] args) {
        for (var permutation : Permutation.values()) {
            var result = run(Stage22CorePairExperimentProtocol.FIRST_SEED, permutation);
            System.out.println(result);
            if (!result.valid()) throw new AssertionError(result);
        }
    }
}
