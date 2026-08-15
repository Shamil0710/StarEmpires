package com.spacesim.persistence;

import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.TerritorialClaimState;
import com.spacesim.world.TerritorialConstructionRightState;
import com.spacesim.world.TerritorialControlState;
import com.spacesim.world.TerritorialRecognitionState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17D file-format trailer for claims, control maintenance and territorial legal rights. */
final class WorldTerritoryBinary {
    private static final int MAX_FACTIONS = 10_000;
    private static final int MAX_STATES_PER_FACTION = 100_000;

    private WorldTerritoryBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionStrategicState> strategies) throws IOException {
        WorldIoSupport.writeCount(out, strategies.size(), MAX_FACTIONS, "territorialFactions");
        for (FactionStrategicState strategy : strategies) {
            WorldIoSupport.writeString(out, strategy.factionContentId());

            WorldIoSupport.writeCount(
                    out, strategy.territorialClaims().size(), MAX_STATES_PER_FACTION, "territorialClaims");
            for (TerritorialClaimState claim : strategy.territorialClaims()) {
                out.writeLong(claim.systemId().value());
                out.writeLong(claim.declaredTick());
                out.writeLong(claim.lastEvaluatedTick());
                out.writeLong(claim.stabilizationTicks());
                WorldIoSupport.writeString(out, claim.status().name());
            }

            WorldIoSupport.writeCount(
                    out,
                    strategy.territorialControlStates().size(),
                    MAX_STATES_PER_FACTION,
                    "territorialControlStates");
            for (TerritorialControlState control : strategy.territorialControlStates()) {
                out.writeLong(control.systemId().value());
                out.writeLong(control.establishedTick());
                out.writeLong(control.lastEvaluatedTick());
                out.writeLong(control.unsupportedTicks());
            }

            WorldIoSupport.writeCount(
                    out,
                    strategy.territorialRecognitions().size(),
                    MAX_STATES_PER_FACTION,
                    "territorialRecognitions");
            for (TerritorialRecognitionState recognition : strategy.territorialRecognitions()) {
                WorldIoSupport.writeString(out, recognition.targetFactionContentId());
                out.writeLong(recognition.systemId().value());
                WorldIoSupport.writeString(out, recognition.kind().name());
            }

            WorldIoSupport.writeCount(
                    out,
                    strategy.constructionRightsGranted().size(),
                    MAX_STATES_PER_FACTION,
                    "territorialConstructionRights");
            for (TerritorialConstructionRightState right : strategy.constructionRightsGranted()) {
                WorldIoSupport.writeString(out, right.granteeFactionContentId());
                out.writeLong(right.systemId().value());
                out.writeLong(right.grantedTick());
                out.writeLong(right.expiresTick());
            }
        }
    }

    static List<FactionStrategicState> readAndAttach(
            DataInputStream in,
            List<FactionStrategicState> strategies) throws IOException {
        int factionCount = WorldIoSupport.readCount(in, MAX_FACTIONS, "territorialFactions");
        Map<String, TerritoryPayload> payloads = new HashMap<>();
        for (int factionIndex = 0; factionIndex < factionCount; factionIndex++) {
            String factionId = WorldIoSupport.readString(in);

            int claimCount = WorldIoSupport.readCount(in, MAX_STATES_PER_FACTION, "territorialClaims");
            List<TerritorialClaimState> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(new TerritorialClaimState(
                        new StarSystemId(in.readLong()),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        readClaimStatus(in)));
            }

            int controlCount = WorldIoSupport.readCount(
                    in, MAX_STATES_PER_FACTION, "territorialControlStates");
            List<TerritorialControlState> controls = new ArrayList<>(controlCount);
            for (int index = 0; index < controlCount; index++) {
                controls.add(new TerritorialControlState(
                        new StarSystemId(in.readLong()),
                        in.readLong(),
                        in.readLong(),
                        in.readLong()));
            }

            int recognitionCount = WorldIoSupport.readCount(
                    in, MAX_STATES_PER_FACTION, "territorialRecognitions");
            List<TerritorialRecognitionState> recognitions = new ArrayList<>(recognitionCount);
            for (int index = 0; index < recognitionCount; index++) {
                recognitions.add(new TerritorialRecognitionState(
                        WorldIoSupport.readString(in),
                        new StarSystemId(in.readLong()),
                        readRecognitionKind(in)));
            }

            int rightCount = WorldIoSupport.readCount(
                    in, MAX_STATES_PER_FACTION, "territorialConstructionRights");
            List<TerritorialConstructionRightState> rights = new ArrayList<>(rightCount);
            for (int index = 0; index < rightCount; index++) {
                rights.add(new TerritorialConstructionRightState(
                        WorldIoSupport.readString(in),
                        new StarSystemId(in.readLong()),
                        in.readLong(),
                        in.readLong()));
            }

            TerritoryPayload payload = new TerritoryPayload(
                    List.copyOf(claims),
                    List.copyOf(controls),
                    List.copyOf(recognitions),
                    List.copyOf(rights));
            if (payloads.putIfAbsent(factionId, payload) != null) {
                throw new IllegalArgumentException("Duplicate territorial faction trailer: " + factionId);
            }
        }

        if (payloads.size() != strategies.size()) {
            throw new IllegalArgumentException("Territorial trailer does not cover every faction strategy");
        }
        List<FactionStrategicState> result = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            TerritoryPayload payload = payloads.remove(strategy.factionContentId());
            if (payload == null) {
                throw new IllegalArgumentException(
                        "Territorial trailer missing faction strategy: " + strategy.factionContentId());
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
                    payload.claims,
                    payload.controls,
                    payload.recognitions,
                    payload.rights,
                    strategy.doctrine()));
        }
        if (!payloads.isEmpty()) {
            throw new IllegalArgumentException("Territorial trailer references unknown factions");
        }
        return List.copyOf(result);
    }

    private static TerritorialClaimState.Status readClaimStatus(DataInputStream in) throws IOException {
        try {
            return TerritorialClaimState.Status.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown territorial claim status", exception);
        }
    }

    private static TerritorialRecognitionState.Kind readRecognitionKind(DataInputStream in) throws IOException {
        try {
            return TerritorialRecognitionState.Kind.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown territorial recognition kind", exception);
        }
    }

    private static final class TerritoryPayload {
        private final List<TerritorialClaimState> claims;
        private final List<TerritorialControlState> controls;
        private final List<TerritorialRecognitionState> recognitions;
        private final List<TerritorialConstructionRightState> rights;

        private TerritoryPayload(
                List<TerritorialClaimState> claims,
                List<TerritorialControlState> controls,
                List<TerritorialRecognitionState> recognitions,
                List<TerritorialConstructionRightState> rights) {
            this.claims = claims;
            this.controls = controls;
            this.recognitions = recognitions;
            this.rights = rights;
        }
    }
}
