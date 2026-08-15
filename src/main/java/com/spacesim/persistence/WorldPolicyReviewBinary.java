package com.spacesim.persistence;

import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionPolicyReviewState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17F.6 bounded trailer for the common per-faction policy-review watermark. */
final class WorldPolicyReviewBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldPolicyReviewBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionEconomicState> factions) throws IOException {
        WorldIoSupport.writeCount(out, factions.size(), MAX_FACTIONS, "policyReviewFactions");
        for (FactionEconomicState faction : factions) {
            WorldIoSupport.writeString(out, faction.factionContentId());
            out.writeLong(faction.policyReviewState().lastPolicyReviewTick());
        }
    }

    static List<FactionEconomicState> readAndAttach(
            DataInputStream in,
            List<FactionEconomicState> factions) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "policyReviewFactions");
        Map<String, FactionPolicyReviewState> extensions = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            FactionPolicyReviewState state = new FactionPolicyReviewState(in.readLong());
            if (extensions.putIfAbsent(factionId, state) != null) {
                throw new IllegalArgumentException("Duplicate policy-review faction: " + factionId);
            }
        }

        List<FactionEconomicState> result = new ArrayList<>(factions.size());
        for (FactionEconomicState faction : factions) {
            FactionPolicyReviewState review = extensions.remove(faction.factionContentId());
            if (review == null) {
                throw new IllegalArgumentException(
                        "Policy-review trailer missing faction: " + faction.factionContentId());
            }
            result.add(new FactionEconomicState(
                    faction.factionContentId(),
                    faction.treasuryMilliCredits(),
                    faction.stationLiquidityReserveMilliCredits(),
                    faction.maxLiquiditySupportPerDecisionMilliCredits(),
                    faction.treasuryReserveFloorMilliCredits(),
                    faction.maxConstructionInvestmentPerDecisionMilliCredits(),
                    review));
        }
        if (!extensions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Policy-review trailer contains unknown factions: " + extensions.keySet());
        }
        return List.copyOf(result);
    }
}
