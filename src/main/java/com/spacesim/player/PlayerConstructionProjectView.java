package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Authoritative read-only management projection of one player-owned construction project.
 *
 * <p>All values are derived from persistent project state plus the current live site/session. UI
 * code therefore does not calculate funding, material fulfillment, build progress or ETA itself.</p>
 *
 * @param projectId stable construction project ID
 * @param stationArchetypeContentId target station archetype content ID
 * @param stationDisplayName station display name from the authoritative content catalog
 * @param systemId physical construction system
 * @param siteEntityId live construction-site entity ID
 * @param x physical local-system X coordinate
 * @param y physical local-system Y coordinate
 * @param status current authoritative project status
 * @param minimumFundingMilliCredits minimum site liquidity required by the project
 * @param siteWalletMilliCredits current live site wallet balance
 * @param fundingShortfallMilliCredits remaining liquidity needed to meet minimum funding
 * @param materials canonical material fulfillment rows
 * @param buildDurationTicks persisted construction duration fixed at project creation
 * @param elapsedBuildTicks authoritative elapsed BUILDING ticks, otherwise zero
 * @param remainingBuildTicks remaining assembly ticks once/while BUILDING
 * @param buildProgress normalized assembly progress in [0,1]
 * @param legalFactionContentId optional legal/faction affiliation of the resulting station
 * @param territorialAccessCurrentlyAllowed whether current strategic access policy still permits construction in the system
 * @param cancellation current authoritative cancellation decision
 * @param supplyFleetIds owned fleets currently assigned SUPPLY_PROJECT to this site
 */
public record PlayerConstructionProjectView(
        ConstructionProjectId projectId,
        String stationArchetypeContentId,
        String stationDisplayName,
        StarSystemId systemId,
        EntityId siteEntityId,
        float x,
        float y,
        ConstructionProjectStatus status,
        long minimumFundingMilliCredits,
        long siteWalletMilliCredits,
        long fundingShortfallMilliCredits,
        List<PlayerConstructionMaterialView> materials,
        long buildDurationTicks,
        long elapsedBuildTicks,
        long remainingBuildTicks,
        double buildProgress,
        String legalFactionContentId,
        boolean territorialAccessCurrentlyAllowed,
        PlayerConstructionCancellationView cancellation,
        List<FleetId> supplyFleetIds) implements Comparable<PlayerConstructionProjectView> {

    /**
     * Validates and canonicalizes one immutable project management row.
     *
     * @param projectId stable project ID
     * @param stationArchetypeContentId station content ID
     * @param stationDisplayName station display name
     * @param systemId construction system
     * @param siteEntityId live site entity ID
     * @param x local X
     * @param y local Y
     * @param status current project status
     * @param minimumFundingMilliCredits positive minimum funding
     * @param siteWalletMilliCredits non-negative live site balance
     * @param fundingShortfallMilliCredits non-negative minimum-funding shortfall
     * @param materials canonical material rows
     * @param buildDurationTicks positive persisted build duration
     * @param elapsedBuildTicks elapsed assembly ticks
     * @param remainingBuildTicks remaining assembly ticks
     * @param buildProgress normalized assembly progress
     * @param legalFactionContentId optional legal affiliation
     * @param territorialAccessCurrentlyAllowed current strategic access result
     * @param cancellation current cancellation decision
     * @param supplyFleetIds fleets currently supplying this project
     */
    public PlayerConstructionProjectView {
        Objects.requireNonNull(projectId, "Construction project ID not set");
        stationArchetypeContentId = requireText(stationArchetypeContentId, "Station archetype ID not set");
        stationDisplayName = requireText(stationDisplayName, "Station display name not set");
        Objects.requireNonNull(systemId, "Construction system not set");
        Objects.requireNonNull(siteEntityId, "Construction site EntityId not set");
        Objects.requireNonNull(status, "Construction status not set");
        Objects.requireNonNull(cancellation, "Construction cancellation view not set");
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || minimumFundingMilliCredits <= 0L
                || siteWalletMilliCredits < 0L
                || fundingShortfallMilliCredits < 0L
                || fundingShortfallMilliCredits != Math.max(0L, minimumFundingMilliCredits - siteWalletMilliCredits)
                || buildDurationTicks <= 0L
                || elapsedBuildTicks < 0L || elapsedBuildTicks > buildDurationTicks
                || remainingBuildTicks != buildDurationTicks - elapsedBuildTicks
                || !Double.isFinite(buildProgress) || buildProgress < 0d || buildProgress > 1d) {
            throw new IllegalArgumentException("Invalid construction project management values");
        }
        double expectedProgress = (double) elapsedBuildTicks / (double) buildDurationTicks;
        if (Math.abs(buildProgress - expectedProgress) > 1e-9d) {
            throw new IllegalArgumentException("Construction progress must match persisted duration and elapsed ticks");
        }
        if (legalFactionContentId != null) {
            legalFactionContentId = requireText(legalFactionContentId, "Legal faction ID cannot be blank");
        }
        List<PlayerConstructionMaterialView> materialCopy = new ArrayList<>(
                Objects.requireNonNull(materials, "Construction materials not set"));
        materialCopy.sort(PlayerConstructionMaterialView::compareTo);
        materials = List.copyOf(materialCopy);
        List<FleetId> fleets = new ArrayList<>(Objects.requireNonNull(supplyFleetIds, "Supply FleetIds not set"));
        fleets.sort(FleetId::compareTo);
        supplyFleetIds = List.copyOf(fleets);
    }

    /** @return total real required material units over all rows */
    public long totalRequiredUnits() {
        long result = 0L;
        for (PlayerConstructionMaterialView material : materials) {
            result += material.requiredUnits();
        }
        return result;
    }

    /** @return total real delivered material units over all rows */
    public long totalDeliveredUnits() {
        long result = 0L;
        for (PlayerConstructionMaterialView material : materials) {
            result += material.deliveredUnits();
        }
        return result;
    }

    /** @return total real missing material units over all rows */
    public long totalMissingUnits() {
        return totalRequiredUnits() - totalDeliveredUnits();
    }

    @Override
    public int compareTo(PlayerConstructionProjectView other) {
        return projectId.compareTo(Objects.requireNonNull(other, "Other project view not set").projectId);
    }

    private static String requireText(String value, String message) {
        String checked = Objects.requireNonNull(value, message).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return checked;
    }
}
