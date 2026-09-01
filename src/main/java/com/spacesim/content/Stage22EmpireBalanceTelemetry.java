package com.spacesim.content;

import com.spacesim.content.Stage22EmpirePackageValidator.FamilyMetrics;

import java.util.Map;
import java.util.Objects;

/**
 * Diagnostic-only M22.3 telemetry projection for declared Empire strengths and weaknesses.
 *
 * <p>No value in this class is consumed as a gameplay modifier. Every metric is derived from the
 * already legal engineering/production package and exists only to make the package's burden and
 * dependencies observable before paired M22.6 tuning.</p>
 */
public final class Stage22EmpireBalanceTelemetry {
    private Stage22EmpireBalanceTelemetry() {
        throw new AssertionError("utility class");
    }

    /** Derives deterministic package-level diagnostics from the cross-authority validation report. */
    public static Report deriveCurrent() {
        Stage22EmpirePackageValidator.ValidationReport validation = Stage22EmpirePackageValidator.validateDefault();
        Map<String, FamilyMetrics> metrics = validation.familyMetrics();
        FamilyMetrics corvette = require(metrics, "role.military.corvette");
        FamilyMetrics cruiser = require(metrics, "role.military.cruiser");
        FamilyMetrics battleship = require(metrics, "role.military.battleship");
        FamilyMetrics carrier = require(metrics, "role.military.carrier");
        FamilyMetrics freight = require(metrics, "role.support.freight");
        FamilyMetrics tanker = require(metrics, "role.support.tanker_replenishment");
        FamilyMetrics support = require(metrics, "role.support.fleet_logistics_repair_salvage");

        double totalMass = metrics.values().stream().mapToDouble(FamilyMetrics::fittedDryMassKg).sum();
        double capitalMass = cruiser.fittedDryMassKg() + battleship.fittedDryMassKg() + carrier.fittedDryMassKg();
        double supportMass = freight.fittedDryMassKg() + tanker.fittedDryMassKg() + support.fittedDryMassKg();
        double projectionBundleMass = carrier.fittedDryMassKg() + tanker.fittedDryMassKg() + support.fittedDryMassKg();
        double averageCrew = metrics.values().stream().mapToInt(FamilyMetrics::staffedCrewBurden).average().orElseThrow();

        Report report = new Report(
                validation.packageFingerprint(),
                totalMass,
                capitalMass / totalMass,
                supportMass / totalMass,
                projectionBundleMass,
                carrier.fittedDryMassKg(),
                battleship.fittedDryMassKg() / corvette.fittedDryMassKg(),
                averageCrew,
                metrics.size(),
                Stage22EmpireShipyardCatalogLoader.loadDefault().getYards().size(),
                true);
        validateStructuralSignals(report);
        return report;
    }

    private static void validateStructuralSignals(Report report) {
        if (report.familyCount() != Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES) {
            throw new IllegalStateException("Empire telemetry must cover the exact required family floor");
        }
        if (report.repairCoveredFamilyCount() != report.familyCount()) {
            throw new IllegalStateException("Every Empire family must retain a physical repair path");
        }
        if (report.productionYardCount() <= 0) {
            throw new IllegalStateException("Empire telemetry requires at least one real production/service yard");
        }
        if (report.supportMassShare() <= 0d || report.capitalMassShare() <= 0d) {
            throw new IllegalStateException("Empire support and capital burdens must be physically visible");
        }
        if (report.projectionBundleMassKg() <= report.carrierMassKg()) {
            throw new IllegalStateException("Remote carrier projection must expose non-zero tanker/support mass burden");
        }
        if (report.battleshipToCorvetteMassRatio() <= 1d) {
            throw new IllegalStateException("Capital-heavy hierarchy is absent from physical hull burden");
        }
    }

    private static FamilyMetrics require(Map<String, FamilyMetrics> metrics, String roleId) {
        return Objects.requireNonNull(metrics.get(roleId), "Missing Empire telemetry role " + roleId);
    }

    /**
     * Immutable diagnostic result vector for M22.3 package review.
     *
     * @param packageFingerprint exact package semantic fingerprint
     * @param totalPrimaryFittedMassKg total dry fitted mass across one primary fit per role
     * @param capitalMassShare share of total mass in cruiser/battleship/carrier primaries
     * @param supportMassShare share of total mass in freight/tanker/fleet-support primaries
     * @param projectionBundleMassKg carrier+tanker+fleet-support dry fitted mass
     * @param carrierMassKg carrier dry fitted mass alone
     * @param battleshipToCorvetteMassRatio physical hierarchy indicator
     * @param averageStaffedCrewBurden average authored staffed crew burden
     * @param familyCount exact role-family coverage
     * @param productionYardCount authored Empire construction/service yards
     * @param repairCoveredFamilyCount count of families with validated repair coverage
     */
    public record Report(
            String packageFingerprint,
            double totalPrimaryFittedMassKg,
            double capitalMassShare,
            double supportMassShare,
            double projectionBundleMassKg,
            double carrierMassKg,
            double battleshipToCorvetteMassRatio,
            double averageStaffedCrewBurden,
            int familyCount,
            int productionYardCount,
            int repairCoveredFamilyCount) {
        /** Creates a report where every validated family has repair coverage. */
        public Report(
                String packageFingerprint,
                double totalPrimaryFittedMassKg,
                double capitalMassShare,
                double supportMassShare,
                double projectionBundleMassKg,
                double carrierMassKg,
                double battleshipToCorvetteMassRatio,
                double averageStaffedCrewBurden,
                int familyCount,
                int productionYardCount,
                boolean allFamiliesRepairCovered) {
            this(
                    packageFingerprint,
                    totalPrimaryFittedMassKg,
                    capitalMassShare,
                    supportMassShare,
                    projectionBundleMassKg,
                    carrierMassKg,
                    battleshipToCorvetteMassRatio,
                    averageStaffedCrewBurden,
                    familyCount,
                    productionYardCount,
                    allFamiliesRepairCovered ? familyCount : 0);
        }
    }
}
