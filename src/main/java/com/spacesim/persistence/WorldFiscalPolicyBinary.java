package com.spacesim.persistence;

import com.spacesim.world.FactionEconomicState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17F.2 bounded v7 trailer for treasury reserve and construction spending authorization. */
final class WorldFiscalPolicyBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldFiscalPolicyBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionEconomicState> states) throws IOException {
        WorldIoSupport.writeCount(out, states.size(), MAX_FACTIONS, "fiscalPolicyFactions");
        for (FactionEconomicState state : states) {
            WorldIoSupport.writeString(out, state.factionContentId());
            out.writeLong(state.treasuryReserveFloorMilliCredits());
            out.writeLong(state.maxConstructionInvestmentPerDecisionMilliCredits());
        }
    }

    static List<FactionEconomicState> readAndAttach(
            DataInputStream in,
            List<FactionEconomicState> states) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "fiscalPolicyFactions");
        Map<String, Payload> payloads = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            long reserveFloor = in.readLong();
            long constructionBudget = in.readLong();
            if (reserveFloor < 0L || constructionBudget < 0L) {
                throw new IllegalArgumentException("Fiscal policy trailer values cannot be negative");
            }
            if (payloads.putIfAbsent(factionId, new Payload(reserveFloor, constructionBudget)) != null) {
                throw new IllegalArgumentException("Duplicate fiscal policy faction trailer: " + factionId);
            }
        }
        if (payloads.size() != states.size()) {
            throw new IllegalArgumentException("Fiscal policy trailer does not cover every faction economy");
        }
        List<FactionEconomicState> result = new ArrayList<>(states.size());
        for (FactionEconomicState state : states) {
            Payload payload = payloads.remove(state.factionContentId());
            if (payload == null) {
                throw new IllegalArgumentException(
                        "Fiscal policy trailer missing faction economy: " + state.factionContentId());
            }
            result.add(new FactionEconomicState(
                    state.factionContentId(),
                    state.treasuryMilliCredits(),
                    state.stationLiquidityReserveMilliCredits(),
                    state.maxLiquiditySupportPerDecisionMilliCredits(),
                    payload.reserveFloorMilliCredits(),
                    payload.maxConstructionInvestmentPerDecisionMilliCredits()));
        }
        if (!payloads.isEmpty()) {
            throw new IllegalArgumentException("Fiscal policy trailer references unknown factions");
        }
        result.sort(null);
        return List.copyOf(result);
    }

    private record Payload(
            long reserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
    }
}
