package com.spacesim.persistence;

import com.spacesim.world.FactionEconomicState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17F.2 bounded trailer for treasury reserve and construction authorization policy. */
final class WorldFiscalPolicyBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldFiscalPolicyBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionEconomicState> factions) throws IOException {
        WorldIoSupport.writeCount(out, factions.size(), MAX_FACTIONS, "fiscalPolicyFactions");
        for (FactionEconomicState faction : factions) {
            WorldIoSupport.writeString(out, faction.factionContentId());
            out.writeLong(faction.treasuryReserveFloorMilliCredits());
            out.writeLong(faction.maxConstructionInvestmentPerDecisionMilliCredits());
        }
    }

    static List<FactionEconomicState> readAndAttach(
            DataInputStream in,
            List<FactionEconomicState> factions) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "fiscalPolicyFactions");
        Map<String, FiscalExtension> extensions = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            FiscalExtension extension = new FiscalExtension(in.readLong(), in.readLong());
            if (extensions.putIfAbsent(factionId, extension) != null) {
                throw new IllegalArgumentException("Duplicate fiscal-policy faction: " + factionId);
            }
        }

        List<FactionEconomicState> result = new ArrayList<>(factions.size());
        for (FactionEconomicState faction : factions) {
            FiscalExtension extension = extensions.remove(faction.factionContentId());
            if (extension == null) {
                throw new IllegalArgumentException(
                        "Fiscal-policy trailer missing faction: " + faction.factionContentId());
            }
            result.add(new FactionEconomicState(
                    faction.factionContentId(),
                    faction.treasuryMilliCredits(),
                    faction.stationLiquidityReserveMilliCredits(),
                    faction.maxLiquiditySupportPerDecisionMilliCredits(),
                    extension.treasuryReserveFloorMilliCredits(),
                    extension.maxConstructionInvestmentPerDecisionMilliCredits()));
        }
        if (!extensions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Fiscal-policy trailer contains unknown factions: " + extensions.keySet());
        }
        return List.copyOf(result);
    }

    private record FiscalExtension(
            long treasuryReserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
        private FiscalExtension {
            if (treasuryReserveFloorMilliCredits < 0L
                    || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
                throw new IllegalArgumentException("Fiscal-policy extension values cannot be negative");
            }
        }
    }
}
