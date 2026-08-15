package com.spacesim.persistence;

import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionStrategicState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17F.1 bounded file-format trailer for persistent faction doctrine profiles. */
final class WorldDoctrineBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldDoctrineBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionStrategicState> strategies) throws IOException {
        WorldIoSupport.writeCount(out, strategies.size(), MAX_FACTIONS, "doctrineFactions");
        for (FactionStrategicState strategy : strategies) {
            WorldIoSupport.writeString(out, strategy.factionContentId());
            FactionDoctrineState doctrine = strategy.doctrine();
            out.writeInt(doctrine.tradeOpenness());
            out.writeInt(doctrine.securityPosture());
            out.writeInt(doctrine.expansionPreference());
            out.writeInt(doctrine.sovereigntySensitivity());
            out.writeInt(doctrine.treatyLegalism());
            out.writeInt(doctrine.interventionism());
            out.writeInt(doctrine.economicResiliencePriority());
        }
    }

    static List<FactionStrategicState> readAndAttach(
            DataInputStream in,
            List<FactionStrategicState> strategies) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "doctrineFactions");
        Map<String, FactionDoctrineState> profiles = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            FactionDoctrineState doctrine = new FactionDoctrineState(
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readInt());
            if (profiles.putIfAbsent(factionId, doctrine) != null) {
                throw new IllegalArgumentException("Duplicate doctrine faction trailer: " + factionId);
            }
        }
        if (profiles.size() != strategies.size()) {
            throw new IllegalArgumentException("Doctrine trailer does not cover every faction strategy");
        }
        List<FactionStrategicState> result = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            FactionDoctrineState doctrine = profiles.remove(strategy.factionContentId());
            if (doctrine == null) {
                throw new IllegalArgumentException(
                        "Doctrine trailer missing faction strategy: " + strategy.factionContentId());
            }
            result.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    strategy.controlledSystems(),
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals(),
                    strategy.territorialClaims(),
                    strategy.territorialControlStates(),
                    strategy.territorialRecognitions(),
                    strategy.constructionRightsGranted(),
                    doctrine));
        }
        if (!profiles.isEmpty()) {
            throw new IllegalArgumentException("Doctrine trailer references unknown factions");
        }
        result.sort(null);
        return List.copyOf(result);
    }
}
