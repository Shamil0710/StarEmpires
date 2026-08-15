package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable runtime for the persistent Stage-17D territorial process.
 *
 * <p>All evidence is derived from physical ECS state. The runtime never creates stations, fleets,
 * money or cargo. A political claim merely opens a stabilization clock; control is produced only
 * after continuous qualifying infrastructure, and is lost only after a persistent unsupported
 * grace period. Stable faction IDs are used at this layer and converted through the unified
 * {@link FactionIdentityResolver} only while reading local ECS ownership.</p>
 */
final class TerritorialControlRuntime {
    /** Required uninterrupted qualifying evidence before an unclaimed system can become controlled. */
    static final long REQUIRED_STABILIZATION_TICKS = 600L;
    /** Unsupported interval tolerated before established control is relinquished. */
    static final long CONTROL_LOSS_GRACE_TICKS = 3_600L;

    private static final int STATION_ANCHOR_SCORE = 100;
    private static final int OPERATIONAL_ANCHOR_SCORE = 25;
    private static final int LOCAL_FLEET_SCORE = 40;
    private static final int QUALIFYING_CONTROL_SCORE = 125;
    private static final int CONTEST_MARGIN = 40;

    private final GalaxyTopology topology;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final FactionIdentityResolver identities;
    private final ConstructionProjectService constructionProjects;
    private List<FactionStrategicState> strategies;
    private Map<String, FactionStrategicState> strategiesById;
    private Map<StarSystemId, String> controllerBySystem;

    TerritorialControlRuntime(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FactionIdentityResolver identities,
            ConstructionProjectService constructionProjects,
            List<FactionStrategicState> initialStrategies) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology not set");
        this.sessionsById = Objects.requireNonNull(sessionsById, "Simulation sessions not set");
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        this.constructionProjects = Objects.requireNonNull(constructionProjects, "Construction projects not set");
        install(Objects.requireNonNull(initialStrategies, "Faction strategies not set"));
    }

    List<FactionStrategicState> snapshots() {
        return strategies;
    }

    FactionStrategicState find(String factionContentId) {
        return factionContentId == null ? null : strategiesById.get(factionContentId);
    }

    String controller(StarSystemId systemId) {
        return systemId == null ? null : controllerBySystem.get(systemId);
    }

    boolean isContested(StarSystemId systemId) {
        StarSystemId system = requireSystem(systemId);
        for (FactionStrategicState strategy : strategies) {
            TerritorialClaimState claim = strategy.claimFor(system);
            if (claim != null && claim.status() == TerritorialClaimState.Status.CONTESTED) {
                return true;
            }
        }
        return false;
    }

    TerritorialClaimState declareClaim(String factionContentId, StarSystemId systemId, long worldTick) {
        String factionId = requireFaction(factionContentId);
        StarSystemId system = requireSystem(systemId);
        requireWorldTick(worldTick);
        FactionStrategicState strategy = strategiesById.get(factionId);
        TerritorialClaimState existing = strategy.claimFor(system);
        if (existing != null) {
            return existing;
        }
        TerritorialClaimState claim = new TerritorialClaimState(
                system,
                worldTick,
                worldTick,
                strategy.controls(system) ? REQUIRED_STABILIZATION_TICKS : 0L,
                strategy.controls(system)
                        ? TerritorialClaimState.Status.ESTABLISHED
                        : TerritorialClaimState.Status.ACTIVE);
        List<TerritorialClaimState> claims = new ArrayList<>(strategy.territorialClaims());
        claims.add(claim);
        replaceStrategy(copy(strategy, strategy.controlledSystems(), claims,
                strategy.territorialControlStates(), strategy.territorialRecognitions(),
                strategy.constructionRightsGranted()));
        return claim;
    }

    boolean withdrawClaim(String factionContentId, StarSystemId systemId) {
        String factionId = requireFaction(factionContentId);
        StarSystemId system = requireSystem(systemId);
        FactionStrategicState strategy = strategiesById.get(factionId);
        if (strategy.controls(system)) {
            throw new IllegalStateException("Established controller must relinquish control before withdrawing its claim");
        }
        List<TerritorialClaimState> claims = new ArrayList<>(strategy.territorialClaims());
        boolean removed = claims.removeIf(claim -> claim.systemId().equals(system));
        if (!removed) {
            return false;
        }
        replaceStrategy(copy(strategy, strategy.controlledSystems(), claims,
                strategy.territorialControlStates(), strategy.territorialRecognitions(),
                strategy.constructionRightsGranted()));
        return true;
    }

    TerritorialRecognitionState recognize(
            String recognizingFactionContentId,
            String targetFactionContentId,
            StarSystemId systemId,
            TerritorialRecognitionState.Kind kind) {
        String recognizerId = requireFaction(recognizingFactionContentId);
        String targetId = requireFaction(targetFactionContentId);
        StarSystemId system = requireSystem(systemId);
        TerritorialRecognitionState.Kind recognitionKind = Objects.requireNonNull(
                kind, "Territorial recognition kind not set");
        if (recognizerId.equals(targetId)) {
            throw new IllegalArgumentException("Faction cannot recognize itself");
        }
        FactionStrategicState target = strategiesById.get(targetId);
        if (recognitionKind == TerritorialRecognitionState.Kind.CLAIM && target.claimFor(system) == null) {
            throw new IllegalStateException("Target faction has no claim to recognize");
        }
        if (recognitionKind == TerritorialRecognitionState.Kind.CONTROL && !target.controls(system)) {
            throw new IllegalStateException("Target faction does not control the recognized system");
        }

        FactionStrategicState recognizer = strategiesById.get(recognizerId);
        TerritorialRecognitionState recognition = new TerritorialRecognitionState(targetId, system, recognitionKind);
        if (recognizer.territorialRecognitions().contains(recognition)) {
            return recognition;
        }
        List<TerritorialRecognitionState> recognitions = new ArrayList<>(recognizer.territorialRecognitions());
        recognitions.add(recognition);
        replaceStrategy(copy(recognizer, recognizer.controlledSystems(), recognizer.territorialClaims(),
                recognizer.territorialControlStates(), recognitions, recognizer.constructionRightsGranted()));
        return recognition;
    }

    TerritorialConstructionRightState grantConstructionRight(
            String grantorFactionContentId,
            String granteeFactionContentId,
            StarSystemId systemId,
            long worldTick,
            long expiresTick) {
        String grantorId = requireFaction(grantorFactionContentId);
        String granteeId = requireFaction(granteeFactionContentId);
        StarSystemId system = requireSystem(systemId);
        requireWorldTick(worldTick);
        if (grantorId.equals(granteeId)) {
            throw new IllegalArgumentException("Domestic construction does not require a concession");
        }
        FactionStrategicState grantor = strategiesById.get(grantorId);
        if (!grantor.controls(system)) {
            throw new IllegalStateException("Only the current territorial controller can grant construction rights");
        }
        TerritorialConstructionRightState right = new TerritorialConstructionRightState(
                granteeId, system, worldTick, expiresTick);
        List<TerritorialConstructionRightState> rights = new ArrayList<>(grantor.constructionRightsGranted());
        rights.removeIf(existing -> existing.systemId().equals(system)
                && existing.granteeFactionContentId().equals(granteeId));
        rights.add(right);
        replaceStrategy(copy(grantor, grantor.controlledSystems(), grantor.territorialClaims(),
                grantor.territorialControlStates(), grantor.territorialRecognitions(), rights));
        return right;
    }

    boolean revokeConstructionRight(
            String grantorFactionContentId,
            String granteeFactionContentId,
            StarSystemId systemId) {
        String grantorId = requireFaction(grantorFactionContentId);
        String granteeId = requireFaction(granteeFactionContentId);
        StarSystemId system = requireSystem(systemId);
        FactionStrategicState grantor = strategiesById.get(grantorId);
        List<TerritorialConstructionRightState> rights = new ArrayList<>(grantor.constructionRightsGranted());
        boolean removed = rights.removeIf(existing -> existing.systemId().equals(system)
                && existing.granteeFactionContentId().equals(granteeId));
        if (!removed) {
            return false;
        }
        replaceStrategy(copy(grantor, grantor.controlledSystems(), grantor.territorialClaims(),
                grantor.territorialControlStates(), grantor.territorialRecognitions(), rights));
        return true;
    }

    boolean relinquishControl(String factionContentId, StarSystemId systemId, long worldTick) {
        String factionId = requireFaction(factionContentId);
        StarSystemId system = requireSystem(systemId);
        requireWorldTick(worldTick);
        FactionStrategicState strategy = strategiesById.get(factionId);
        if (!strategy.controls(system)) {
            return false;
        }
        removeControl(factionId, system, worldTick);
        pruneInvalidLegalState();
        return true;
    }

    UpdateReport advance(long worldTick) {
        requireWorldTick(worldTick);
        Map<EvidenceKey, Evidence> evidence = collectEvidence();
        updateClaimProgress(worldTick, evidence);
        int lost = updateControlMaintenance(worldTick, evidence);
        int acquired = acquireStabilizedUnclaimedSystems(worldTick, evidence);
        markCurrentContests(evidence);
        pruneInvalidLegalState();
        int contestedClaims = 0;
        for (FactionStrategicState strategy : strategies) {
            for (TerritorialClaimState claim : strategy.territorialClaims()) {
                if (claim.status() == TerritorialClaimState.Status.CONTESTED) {
                    contestedClaims++;
                }
            }
        }
        return new UpdateReport(acquired, lost, contestedClaims);
    }

    Evidence evidenceFor(String factionContentId, StarSystemId systemId) {
        String factionId = requireFaction(factionContentId);
        StarSystemId system = requireSystem(systemId);
        return collectEvidence().getOrDefault(new EvidenceKey(factionId, system), Evidence.NONE);
    }

    private void updateClaimProgress(long worldTick, Map<EvidenceKey, Evidence> evidence) {
        List<FactionStrategicState> updatedStrategies = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            List<TerritorialClaimState> claims = new ArrayList<>(strategy.territorialClaims().size());
            for (TerritorialClaimState claim : strategy.territorialClaims()) {
                long elapsed = elapsed(claim.lastEvaluatedTick(), worldTick, false);
                Evidence own = evidence.getOrDefault(
                        new EvidenceKey(strategy.factionContentId(), claim.systemId()), Evidence.NONE);
                boolean controls = strategy.controls(claim.systemId());
                boolean contested = !controls && isMateriallyContested(
                        strategy.factionContentId(), claim.systemId(), evidence);
                long stabilization = claim.stabilizationTicks();
                TerritorialClaimState.Status status;
                if (controls) {
                    stabilization = Math.max(stabilization, REQUIRED_STABILIZATION_TICKS);
                    status = TerritorialClaimState.Status.ESTABLISHED;
                } else if (contested) {
                    status = TerritorialClaimState.Status.CONTESTED;
                } else if (own.qualifiesForControl()) {
                    stabilization = Math.min(
                            REQUIRED_STABILIZATION_TICKS,
                            safeAdd(stabilization, elapsed));
                    status = TerritorialClaimState.Status.STABILIZING;
                } else {
                    stabilization = Math.max(0L, stabilization - Math.min(stabilization, elapsed));
                    status = TerritorialClaimState.Status.ACTIVE;
                }
                claims.add(new TerritorialClaimState(
                        claim.systemId(),
                        claim.declaredTick(),
                        worldTick,
                        stabilization,
                        status));
            }
            updatedStrategies.add(copy(strategy, strategy.controlledSystems(), claims,
                    strategy.territorialControlStates(), strategy.territorialRecognitions(),
                    strategy.constructionRightsGranted()));
        }
        install(updatedStrategies);
    }

    private int updateControlMaintenance(long worldTick, Map<EvidenceKey, Evidence> evidence) {
        List<ControlLoss> losses = new ArrayList<>();
        List<FactionStrategicState> updatedStrategies = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            List<TerritorialControlState> controls = new ArrayList<>();
            for (TerritorialControlState control : strategy.territorialControlStates()) {
                Evidence own = evidence.getOrDefault(
                        new EvidenceKey(strategy.factionContentId(), control.systemId()), Evidence.NONE);
                boolean rivalThreat = hasRivalControlThreat(
                        strategy.factionContentId(), control.systemId(), own, evidence);
                boolean unsupported = !own.qualifiesForControl() || rivalThreat;
                boolean bootstrapClock = control.establishedTick() == 0L && control.lastEvaluatedTick() == 0L;
                long elapsed = elapsed(control.lastEvaluatedTick(), worldTick, bootstrapClock);
                long unsupportedTicks = unsupported
                        ? safeAdd(control.unsupportedTicks(), elapsed)
                        : 0L;
                if (unsupportedTicks >= CONTROL_LOSS_GRACE_TICKS) {
                    losses.add(new ControlLoss(strategy.factionContentId(), control.systemId()));
                    continue;
                }
                controls.add(new TerritorialControlState(
                        control.systemId(),
                        control.establishedTick(),
                        worldTick,
                        unsupportedTicks));
            }
            List<StarSystemId> remainingSystems = new ArrayList<>(strategy.controlledSystems());
            for (ControlLoss loss : losses) {
                if (loss.factionId.equals(strategy.factionContentId())) {
                    remainingSystems.remove(loss.systemId);
                }
            }
            updatedStrategies.add(copy(strategy, remainingSystems, strategy.territorialClaims(), controls,
                    strategy.territorialRecognitions(), strategy.constructionRightsGranted()));
        }
        install(updatedStrategies);
        for (ControlLoss loss : losses) {
            resetClaimAfterControlLoss(loss.factionId, loss.systemId, worldTick);
        }
        return losses.size();
    }

    private int acquireStabilizedUnclaimedSystems(long worldTick, Map<EvidenceKey, Evidence> evidence) {
        int acquired = 0;
        for (StarSystemNode node : topology.systems()) {
            StarSystemId systemId = node.id();
            if (controllerBySystem.containsKey(systemId)) {
                continue;
            }
            List<String> eligible = new ArrayList<>();
            for (FactionStrategicState strategy : strategies) {
                TerritorialClaimState claim = strategy.claimFor(systemId);
                Evidence own = evidence.getOrDefault(
                        new EvidenceKey(strategy.factionContentId(), systemId), Evidence.NONE);
                if (claim != null
                        && claim.stabilizationTicks() >= REQUIRED_STABILIZATION_TICKS
                        && own.qualifiesForControl()) {
                    eligible.add(strategy.factionContentId());
                }
            }
            eligible.sort(String::compareTo);
            if (eligible.size() != 1) {
                continue;
            }
            establishControl(eligible.get(0), systemId, worldTick);
            acquired++;
        }
        return acquired;
    }

    private void establishControl(String factionId, StarSystemId systemId, long worldTick) {
        FactionStrategicState strategy = strategiesById.get(factionId);
        if (strategy.controls(systemId)) {
            return;
        }
        List<StarSystemId> controlled = new ArrayList<>(strategy.controlledSystems());
        controlled.add(systemId);
        List<TerritorialControlState> controls = new ArrayList<>(strategy.territorialControlStates());
        controls.add(new TerritorialControlState(systemId, worldTick, worldTick, 0L));
        List<TerritorialClaimState> claims = replaceClaimStatus(
                strategy.territorialClaims(),
                systemId,
                TerritorialClaimState.Status.ESTABLISHED,
                REQUIRED_STABILIZATION_TICKS,
                worldTick);
        replaceStrategy(copy(strategy, controlled, claims, controls,
                strategy.territorialRecognitions(), strategy.constructionRightsGranted()));
    }

    private void removeControl(String factionId, StarSystemId systemId, long worldTick) {
        FactionStrategicState strategy = strategiesById.get(factionId);
        List<StarSystemId> controlled = new ArrayList<>(strategy.controlledSystems());
        controlled.remove(systemId);
        List<TerritorialControlState> controls = new ArrayList<>(strategy.territorialControlStates());
        controls.removeIf(state -> state.systemId().equals(systemId));
        List<TerritorialClaimState> claims = replaceClaimStatus(
                strategy.territorialClaims(),
                systemId,
                TerritorialClaimState.Status.ACTIVE,
                0L,
                worldTick);
        replaceStrategy(copy(strategy, controlled, claims, controls,
                strategy.territorialRecognitions(), strategy.constructionRightsGranted()));
    }

    private void resetClaimAfterControlLoss(String factionId, StarSystemId systemId, long worldTick) {
        FactionStrategicState strategy = strategiesById.get(factionId);
        List<TerritorialClaimState> claims = replaceClaimStatus(
                strategy.territorialClaims(),
                systemId,
                TerritorialClaimState.Status.ACTIVE,
                0L,
                worldTick);
        replaceStrategy(copy(strategy, strategy.controlledSystems(), claims,
                strategy.territorialControlStates(), strategy.territorialRecognitions(),
                strategy.constructionRightsGranted()));
    }

    private void markCurrentContests(Map<EvidenceKey, Evidence> evidence) {
        List<FactionStrategicState> updated = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            List<TerritorialClaimState> claims = new ArrayList<>(strategy.territorialClaims().size());
            for (TerritorialClaimState claim : strategy.territorialClaims()) {
                if (strategy.controls(claim.systemId())) {
                    claims.add(claim.status() == TerritorialClaimState.Status.ESTABLISHED
                            ? claim
                            : new TerritorialClaimState(
                                    claim.systemId(), claim.declaredTick(), claim.lastEvaluatedTick(),
                                    Math.max(claim.stabilizationTicks(), REQUIRED_STABILIZATION_TICKS),
                                    TerritorialClaimState.Status.ESTABLISHED));
                    continue;
                }
                if (isMateriallyContested(strategy.factionContentId(), claim.systemId(), evidence)
                        && claim.status() != TerritorialClaimState.Status.CONTESTED) {
                    claims.add(new TerritorialClaimState(
                            claim.systemId(), claim.declaredTick(), claim.lastEvaluatedTick(),
                            claim.stabilizationTicks(), TerritorialClaimState.Status.CONTESTED));
                } else {
                    claims.add(claim);
                }
            }
            updated.add(copy(strategy, strategy.controlledSystems(), claims,
                    strategy.territorialControlStates(), strategy.territorialRecognitions(),
                    strategy.constructionRightsGranted()));
        }
        install(updated);
    }

    private void pruneInvalidLegalState() {
        List<FactionStrategicState> updated = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            List<TerritorialRecognitionState> recognitions = new ArrayList<>();
            for (TerritorialRecognitionState recognition : strategy.territorialRecognitions()) {
                FactionStrategicState target = strategiesById.get(recognition.targetFactionContentId());
                boolean valid = recognition.kind() == TerritorialRecognitionState.Kind.CLAIM
                        ? target != null && target.claimFor(recognition.systemId()) != null
                        : target != null && target.controls(recognition.systemId());
                if (valid) {
                    recognitions.add(recognition);
                }
            }
            List<TerritorialConstructionRightState> rights = new ArrayList<>();
            for (TerritorialConstructionRightState right : strategy.constructionRightsGranted()) {
                if (strategy.controls(right.systemId())) {
                    rights.add(right);
                }
            }
            updated.add(copy(strategy, strategy.controlledSystems(), strategy.territorialClaims(),
                    strategy.territorialControlStates(), recognitions, rights));
        }
        install(updated);
    }

    private Map<EvidenceKey, Evidence> collectEvidence() {
        Map<EvidenceKey, MutableEvidence> mutable = new HashMap<>();
        for (StarSystemNode node : topology.systems()) {
            SimulationSession session = sessionsById.get(node.id());
            if (session == null) {
                continue;
            }
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                IdentityComponent identity = entity.getComponent(IdentityComponent.class);
                if (faction == null || identity == null) {
                    continue;
                }
                String stableFactionId = identities.stableId(faction.factionId).orElse(null);
                if (stableFactionId == null || !strategiesById.containsKey(stableFactionId)) {
                    continue;
                }
                MutableEvidence value = mutable.computeIfAbsent(
                        new EvidenceKey(stableFactionId, node.id()), ignored -> new MutableEvidence());
                value.physicalAssets++;
                if (identity.kind == IdentityComponent.Kind.FLEET) {
                    value.localFleets++;
                    continue;
                }
                if (identity.kind != IdentityComponent.Kind.STATION || isConstructionSite(node.id(), entity)) {
                    continue;
                }
                value.stationAnchors++;
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (entity.getComponent(MarketComponent.class) != null
                        || wallet != null && wallet.getBalanceMilliCredits() > 0L
                        || inventory != null && inventory.getTotalStock() > 0) {
                    value.operationalAnchors++;
                }
            }
        }

        Map<EvidenceKey, Evidence> result = new HashMap<>();
        for (Map.Entry<EvidenceKey, MutableEvidence> entry : mutable.entrySet()) {
            MutableEvidence value = entry.getValue();
            int score = Math.addExact(
                    Math.addExact(
                            Math.multiplyExact(value.stationAnchors, STATION_ANCHOR_SCORE),
                            Math.multiplyExact(value.operationalAnchors, OPERATIONAL_ANCHOR_SCORE)),
                    Math.multiplyExact(value.localFleets, LOCAL_FLEET_SCORE));
            result.put(entry.getKey(), new Evidence(
                    value.physicalAssets,
                    value.stationAnchors,
                    value.operationalAnchors,
                    value.localFleets,
                    score));
        }
        return Map.copyOf(result);
    }

    private boolean isConstructionSite(StarSystemId systemId, Entity entity) {
        EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
        return id != null && constructionProjects.isConstructionSite(systemId, id.id);
    }

    private boolean isMateriallyContested(
            String claimantFactionId,
            StarSystemId systemId,
            Map<EvidenceKey, Evidence> evidence) {
        String controller = controllerBySystem.get(systemId);
        if (controller != null && !controller.equals(claimantFactionId)) {
            Evidence controllerEvidence = evidence.getOrDefault(
                    new EvidenceKey(controller, systemId), Evidence.NONE);
            if (controllerEvidence.qualifiesForControl()) {
                return true;
            }
        }
        for (FactionStrategicState rival : strategies) {
            if (rival.factionContentId().equals(claimantFactionId) || rival.claimFor(systemId) == null) {
                continue;
            }
            Evidence rivalEvidence = evidence.getOrDefault(
                    new EvidenceKey(rival.factionContentId(), systemId), Evidence.NONE);
            if (rivalEvidence.qualifiesForControl()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRivalControlThreat(
            String controllerFactionId,
            StarSystemId systemId,
            Evidence controllerEvidence,
            Map<EvidenceKey, Evidence> evidence) {
        for (FactionStrategicState rival : strategies) {
            if (rival.factionContentId().equals(controllerFactionId)
                    || rival.claimFor(systemId) == null) {
                continue;
            }
            Evidence rivalEvidence = evidence.getOrDefault(
                    new EvidenceKey(rival.factionContentId(), systemId), Evidence.NONE);
            if (rivalEvidence.qualifiesForControl()
                    && rivalEvidence.controlScore() + CONTEST_MARGIN >= controllerEvidence.controlScore()) {
                return true;
            }
        }
        return false;
    }

    private void replaceStrategy(FactionStrategicState replacement) {
        List<FactionStrategicState> updated = new ArrayList<>(strategies.size());
        boolean found = false;
        for (FactionStrategicState strategy : strategies) {
            if (strategy.factionContentId().equals(replacement.factionContentId())) {
                updated.add(replacement);
                found = true;
            } else {
                updated.add(strategy);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown faction strategy: " + replacement.factionContentId());
        }
        install(updated);
    }

    private void install(List<FactionStrategicState> source) {
        List<FactionStrategicState> canonical = new ArrayList<>(source.size());
        Map<String, FactionStrategicState> byId = new HashMap<>();
        Map<StarSystemId, String> controllers = new HashMap<>();
        for (FactionStrategicState strategy : source) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "Faction strategy not set");
            if (identities.runtimeId(value.factionContentId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown faction strategy identity: " + value.factionContentId());
            }
            validateTerritorialReferences(value);
            if (byId.putIfAbsent(value.factionContentId(), value) != null) {
                throw new IllegalArgumentException("Duplicate faction strategy: " + value.factionContentId());
            }
            for (StarSystemId controlled : value.controlledSystems()) {
                if (controllers.putIfAbsent(controlled, value.factionContentId()) != null) {
                    throw new IllegalArgumentException("StarSystem has multiple controllers: " + controlled);
                }
            }
            canonical.add(value);
        }
        canonical.sort(Comparator.naturalOrder());
        strategies = List.copyOf(canonical);
        strategiesById = Map.copyOf(byId);
        controllerBySystem = Map.copyOf(controllers);
    }

    private void validateTerritorialReferences(FactionStrategicState strategy) {
        for (TerritorialClaimState claim : strategy.territorialClaims()) {
            requireSystem(claim.systemId());
        }
        for (TerritorialControlState control : strategy.territorialControlStates()) {
            requireSystem(control.systemId());
        }
        for (TerritorialRecognitionState recognition : strategy.territorialRecognitions()) {
            requireSystem(recognition.systemId());
            requireKnownIdentity(recognition.targetFactionContentId());
        }
        for (TerritorialConstructionRightState right : strategy.constructionRightsGranted()) {
            requireSystem(right.systemId());
            requireKnownIdentity(right.granteeFactionContentId());
        }
    }

    private String requireFaction(String factionContentId) {
        String factionId = requireKnownIdentity(factionContentId);
        if (!strategiesById.containsKey(factionId)) {
            throw new IllegalArgumentException("Faction has no strategic state: " + factionId);
        }
        return factionId;
    }

    private String requireKnownIdentity(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty() || identities.runtimeId(factionId).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction identity: " + factionId);
        }
        return factionId;
    }

    private StarSystemId requireSystem(StarSystemId systemId) {
        StarSystemId system = Objects.requireNonNull(systemId, "StarSystemId not set");
        if (topology.findSystem(system).isEmpty()) {
            throw new IllegalArgumentException("Unknown StarSystem: " + system);
        }
        return system;
    }

    private static void requireWorldTick(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
    }

    private static long elapsed(long previousTick, long worldTick, boolean bootstrapClock) {
        if (worldTick < previousTick) {
            throw new IllegalStateException("Territorial clock moved backwards");
        }
        return bootstrapClock ? 0L : worldTick - previousTick;
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Territorial timer overflow", exception);
        }
    }

    private static List<TerritorialClaimState> replaceClaimStatus(
            List<TerritorialClaimState> source,
            StarSystemId systemId,
            TerritorialClaimState.Status status,
            long stabilizationTicks,
            long worldTick) {
        List<TerritorialClaimState> result = new ArrayList<>(source.size());
        for (TerritorialClaimState claim : source) {
            if (claim.systemId().equals(systemId)) {
                result.add(new TerritorialClaimState(
                        claim.systemId(), claim.declaredTick(), worldTick, stabilizationTicks, status));
            } else {
                result.add(claim);
            }
        }
        return List.copyOf(result);
    }

    private static FactionStrategicState copy(
            FactionStrategicState state,
            List<StarSystemId> controlledSystems,
            List<TerritorialClaimState> claims,
            List<TerritorialControlState> controls,
            List<TerritorialRecognitionState> recognitions,
            List<TerritorialConstructionRightState> rights) {
        return new FactionStrategicState(
                state.factionContentId(),
                state.minimumMarketAccessRelation(),
                state.relations(),
                controlledSystems,
                state.stationTaxBasisPoints(),
                state.foreignTerritoryTariffBasisPoints(),
                state.stockPolicies(),
                state.productionPolicies(),
                state.strategicGoals(),
                claims,
                controls,
                recognitions,
                rights,
                state.doctrine());
    }

    /**
     * Immutable physical territorial evidence for one faction/system pair.
     *
     * @param physicalAssets total faction-owned local physical entities
     * @param stationAnchors completed stationary anchors, excluding construction sites
     * @param operationalAnchors completed anchors with market/liquidity/stock capability
     * @param localFleets local faction fleet/security presence
     * @param controlScore deterministic aggregate evidence score
     */
    record Evidence(
            int physicalAssets,
            int stationAnchors,
            int operationalAnchors,
            int localFleets,
            int controlScore) {
        private static final Evidence NONE = new Evidence(0, 0, 0, 0, 0);

        Evidence {
            if (physicalAssets < 0 || stationAnchors < 0 || operationalAnchors < 0
                    || localFleets < 0 || controlScore < 0) {
                throw new IllegalArgumentException("Territorial evidence cannot be negative");
            }
        }

        boolean qualifiesForControl() {
            return stationAnchors > 0 && controlScore >= QUALIFYING_CONTROL_SCORE;
        }
    }

    /**
     * Result of one deterministic territorial update.
     *
     * @param controlsAcquired systems that became controlled this update
     * @param controlsLost systems that lost established control this update
     * @param contestedClaims claims currently marked contested
     */
    record UpdateReport(int controlsAcquired, int controlsLost, int contestedClaims) {
        UpdateReport {
            if (controlsAcquired < 0 || controlsLost < 0 || contestedClaims < 0) {
                throw new IllegalArgumentException("Territorial update counters cannot be negative");
            }
        }
    }

    private record EvidenceKey(String factionId, StarSystemId systemId) {
    }

    private record ControlLoss(String factionId, StarSystemId systemId) {
    }

    private static final class MutableEvidence {
        private int physicalAssets;
        private int stationAnchors;
        private int operationalAnchors;
        private int localFleets;
    }
}
