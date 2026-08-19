package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ui.ScaledTacticalDebugSnapshot.CombatantDebug;
import com.spacesim.ui.ShipInspectionSnapshot.ShieldSummary;
import com.spacesim.ui.ShipInspectionSnapshot.TrackSummary;
import com.spacesim.ui.ShipInspectionSnapshot.WeaponFeed;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Read-only projection that joins current selected-ship presentation and authoritative diagnostics. */
public final class ShipInspectionProjection {
    /**
     * Projects one selected entity without advancing or mutating the tactical runtime.
     *
     * @param runtime current production tactical runtime
     * @param visual current immutable visual snapshot
     * @param debug current immutable debug snapshot
     * @param entityId stable selected entity id
     * @return current inspection snapshot, or empty when the entity is no longer represented
     */
    public Optional<ShipInspectionSnapshot> project(
            LiveTacticalBattleDeceptionRuntime runtime,
            TacticalPrototypeVisualSnapshot visual,
            ScaledTacticalDebugSnapshot debug,
            long entityId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(debug, "debug");
        if (entityId <= 0L) {
            return Optional.empty();
        }

        ShipGlyph glyph = visual.ships().stream()
                .filter(ship -> ship.entityId() == entityId)
                .findFirst()
                .orElse(null);
        CombatantDebug row = debug.combatants().stream()
                .filter(combatant -> combatant.entityId() == entityId)
                .findFirst()
                .orElse(null);
        if (glyph == null || row == null) {
            return Optional.empty();
        }

        CombatantRuntime combatant;
        try {
            combatant = runtime.battleState().requireCombatant(entityId);
        } catch (IllegalArgumentException missing) {
            return Optional.empty();
        }

        double vx = combatant.transform().velocity.x;
        double vy = combatant.transform().velocity.y;
        double speed = Math.hypot(vx, vy);
        var instance = combatant.engineering().instanceState;

        List<WeaponFeed> feeds = instance.weaponLoadout().feeds().stream()
                .map(feed -> new WeaponFeed(feed.mountId(), feed.interfaceId(), feed.ammunitionContentId()))
                .toList();
        List<TrackSummary> tracks = row.tracks().stream()
                .map(track -> new TrackSummary(track.targetId(), track.informationState(), track.positionKnown()))
                .toList();

        return Optional.of(new ShipInspectionSnapshot(
                entityId,
                combatant.spec().side(),
                glyph.role(),
                combatant.hull().id(),
                combatant.doctrine().fitId(),
                combatant.spec().doctrineId(),
                glyph.wreck(),
                row.meanCompartmentIntegrity(),
                row.minimumModuleIntegrity(),
                shieldSummary(instance.shieldStatesByMount()),
                row.sharedBusEnergyJ(),
                row.shipHeatStoredJ(),
                row.localHeatStoredJ(),
                row.reactionMassKg(),
                row.ammunitionCount(),
                combatant.transform().position.x,
                combatant.transform().position.y,
                vx,
                vy,
                speed,
                glyph.headingRad(),
                row.selectedTargetId(),
                row.fireRequested(),
                row.fireAuthorized(),
                feeds,
                tracks,
                row.survivalAction().name(),
                row.survivalReason().name(),
                formationText(row),
                "N/A — no authoritative acceleration field",
                "N/A — no selected-ship ECM/ECCM inspection field"));
    }

    private static ShieldSummary shieldSummary(Map<String, State> states) {
        int count = states.size();
        int collapsed = 0;
        double reserve = 0d;
        double heat = 0d;
        double minimumIntegrity = count == 0 ? 0d : 1d;
        for (State state : states.values()) {
            if (state.collapsed()) {
                collapsed++;
            }
            reserve += state.reserveJ();
            heat += state.accumulatedHeatJ();
            minimumIntegrity = Math.min(minimumIntegrity, state.emitterIntegrity());
        }
        return new ShieldSummary(count, collapsed, reserve, heat, minimumIntegrity);
    }

    private static String formationText(CombatantDebug row) {
        var formation = row.formation();
        if (!formation.objectiveKnown()) {
            return "NONE";
        }
        return String.format(Locale.ROOT,
                "%s %s/%s slot %d/%d err %.1f m",
                formation.mode(),
                formation.status(),
                formation.reason(),
                formation.slotIndex() + 1,
                formation.slotCount(),
                formation.errorM());
    }
}
