package com.spacesim.persistence;

import com.spacesim.world.FactionDiplomacyState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17E.4 bounded file-format trailer for faction transaction/customs tariff rates. */
final class WorldCustomsBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldCustomsBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionDiplomacyState> states) throws IOException {
        WorldIoSupport.writeCount(out, states.size(), MAX_FACTIONS, "customsFactions");
        for (FactionDiplomacyState state : states) {
            WorldIoSupport.writeString(out, state.factionContentId());
            out.writeInt(state.customsTariffBasisPoints());
        }
    }

    static List<FactionDiplomacyState> readAndAttach(
            DataInputStream in,
            List<FactionDiplomacyState> states) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "customsFactions");
        Map<String, Integer> rates = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            int basisPoints = in.readInt();
            if (basisPoints < 0 || basisPoints > 10_000) {
                throw new IllegalArgumentException("Customs tariff must be in range 0..10000 bps");
            }
            if (rates.putIfAbsent(factionId, basisPoints) != null) {
                throw new IllegalArgumentException("Duplicate customs faction trailer: " + factionId);
            }
        }
        if (rates.size() != states.size()) {
            throw new IllegalArgumentException("Customs trailer does not cover every diplomacy faction");
        }
        List<FactionDiplomacyState> result = new ArrayList<>(states.size());
        for (FactionDiplomacyState state : states) {
            Integer rate = rates.remove(state.factionContentId());
            if (rate == null) {
                throw new IllegalArgumentException(
                        "Customs trailer missing diplomacy faction: " + state.factionContentId());
            }
            result.add(new FactionDiplomacyState(
                    state.factionContentId(),
                    state.standings(),
                    state.grievances(),
                    state.treaties(),
                    state.embargoes(),
                    rate));
        }
        if (!rates.isEmpty()) {
            throw new IllegalArgumentException("Customs trailer references unknown diplomacy factions");
        }
        result.sort(null);
        return List.copyOf(result);
    }
}
