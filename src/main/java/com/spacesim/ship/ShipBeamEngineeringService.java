package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.ship.BeamWeaponRuntime.BeamSolution;
import com.spacesim.ship.ShipEngineeringGrantService.IntervalBudget;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;

import java.util.Objects;

/**
 * Stage-17.5H beam-operation facade that commits Stage-17.5E electrical demand and local heat.
 *
 * <p>The physical beam solver remains authoritative for geometry, dwell and target exposure. This
 * facade only closes the engineering seam: an otherwise valid beam solution executes when the common
 * fitted/damaged power and thermal layer grants the required operation load. Denied grants cannot
 * create beam energy or mutate the target.</p>
 */
public final class ShipBeamEngineeringService {
    private final BeamWeaponRuntime beams = new BeamWeaponRuntime();
    private final ShipEngineeringGrantService grants;

    /**
     * Creates a beam-operation facade over the production engineering catalog.
     *
     * @param catalog production engineering catalog
     */
    public ShipBeamEngineeringService(ShipEngineeringCatalog catalog) {
        this.grants = new ShipEngineeringGrantService(Objects.requireNonNull(catalog, "catalog"));
    }

    /**
     * Plans a physical non-overlapping beam dwell and atomically commits its electrical/thermal cost.
     *
     * @param engineering authoritative firing ship engineering component
     * @param mountId fitted emitter mount receiving power/heat accounting
     * @param weapon physical beam definition associated with that fitted emitter
     * @param track current fire-control track
     * @param emitterXM emitter x position in meters
     * @param emitterYM emitter y position in meters
     * @param dwellSeconds requested dwell duration
     * @return admitted beam solution, or {@code null} when engineering power/thermal admission fails
     */
    public BeamSolution planAndCommit(
            EngineeringComponent engineering,
            String mountId,
            BeamWeapon weapon,
            TrackState track,
            double emitterXM,
            double emitterYM,
            double dwellSeconds) {
        return planAndCommitInternal(
                engineering,
                mountId,
                weapon,
                track,
                emitterXM,
                emitterYM,
                dwellSeconds,
                null);
    }

    /**
     * Plans a physical beam dwell against a shared same-interval engineering reservation budget.
     *
     * @param engineering authoritative firing ship engineering component
     * @param mountId fitted emitter mount receiving power/heat accounting
     * @param weapon physical beam definition associated with that fitted emitter
     * @param track current fire-control track
     * @param emitterXM emitter x position in meters
     * @param emitterYM emitter y position in meters
     * @param dwellSeconds requested dwell duration
     * @param intervalBudget shared reservation ledger for overlapping ship operations
     * @return admitted beam solution, or {@code null} when engineering power/thermal admission fails
     */
    public BeamSolution planAndCommit(
            EngineeringComponent engineering,
            String mountId,
            BeamWeapon weapon,
            TrackState track,
            double emitterXM,
            double emitterYM,
            double dwellSeconds,
            IntervalBudget intervalBudget) {
        return planAndCommitInternal(
                engineering,
                mountId,
                weapon,
                track,
                emitterXM,
                emitterYM,
                dwellSeconds,
                Objects.requireNonNull(intervalBudget, "intervalBudget"));
    }

    private BeamSolution planAndCommitInternal(
            EngineeringComponent engineering,
            String mountId,
            BeamWeapon weapon,
            TrackState track,
            double emitterXM,
            double emitterYM,
            double dwellSeconds,
            IntervalBudget intervalBudget) {
        BeamSolution solution = beams.plan(
                Objects.requireNonNull(weapon, "weapon"),
                Objects.requireNonNull(track, "track"),
                emitterXM,
                emitterYM,
                dwellSeconds);
        if (!solution.allowed()) {
            return solution;
        }
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        ShipEngineeringGrantService.GrantResult grant = intervalBudget == null
                ? grants.grantAndCommit(
                        component,
                        mountId,
                        solution.electricalEnergyDemandJ() / dwellSeconds,
                        solution.wasteHeatJ() / dwellSeconds,
                        dwellSeconds)
                : grants.grantAndCommit(
                        component,
                        mountId,
                        solution.electricalEnergyDemandJ() / dwellSeconds,
                        solution.wasteHeatJ() / dwellSeconds,
                        dwellSeconds,
                        intervalBudget);
        return grant.committed() ? solution : null;
    }
}
