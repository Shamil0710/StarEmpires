package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175ICombatAcceptanceHarness.InformationPreset;
import com.spacesim.ship.Stage175ICombatAcceptanceHarness.Scenario;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Canonical Stage-17.5I deterministic scenario-variation catalog.
 *
 * <p>The catalog derives fleet mass from the same production fitting calculator used by runtime and
 * never converts doctrine labels into combat bonuses. It deliberately does not invent a scalar
 * industrial/reference cost: Stage 17.5 test construction inputs are heterogeneous component
 * quantities and Stage 18 owns the first complete comparable resource/facility cost basis.</p>
 */
public final class Stage175ICombatMatrixCatalog {
    private static final int MAX_EQUAL_MASS_SHIPS_PER_SIDE = 16;
    private static final double COMPACT_SPACING_M = 6_000d;
    private static final double DISPERSED_SPACING_M = 40_000d;

    private final ShipEngineeringCatalog engineering;
    private final DerivedShipCalculator calculator;

    /** Required deterministic variation categories beyond the eleven doctrine pairs. */
    public enum VariantKind {
        /** Same number of physical hulls on each side. */ EQUAL_COUNT,
        /** Integer fleet counts chosen to minimize fitted fleet-mass mismatch. */ APPROX_EQUAL_MASS,
        /** Small compact formation. */ SMALL_COMPACT_FORMATION,
        /** Larger dispersed formation. */ LARGE_DISPERSED_FORMATION,
        /** Physically depleted ammunition stores. */ PARTIAL_AMMUNITION,
        /** Existing compartment/module integrity below pristine. */ PRE_DAMAGED,
        /** Thermal availability constrained before layered-defense work. */ THERMALLY_STRESSED,
        /** Aged sensor information before fire-control use. */ DEGRADED_INFORMATION,
        /** Explicit vulnerable/logistics defended zone. */ PROTECTED_LOGISTICS
    }

    /** Why no scalar cost-equalization case is authored in Stage 17.5I. */
    public enum ReferenceCostCoverage {
        /** Stage 18 has not yet established one comparable industrial/resource cost basis. */
        DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS
    }

    /**
     * One named deterministic variation with production-derived fleet masses.
     *
     * @param kind required variation category
     * @param id stable acceptance-case ID
     * @param scenario exact combat harness inputs
     * @param leftFleetMassKg physical fitted mass times left ship count
     * @param rightFleetMassKg physical fitted mass times right ship count
     */
    public record MatrixCase(
            VariantKind kind,
            String id,
            Scenario scenario,
            double leftFleetMassKg,
            double rightFleetMassKg) {
        /** Validates one immutable matrix case. */
        public MatrixCase {
            Objects.requireNonNull(kind, "kind");
            requireNonBlank(id, "id");
            Objects.requireNonNull(scenario, "scenario");
            requirePositiveFinite(leftFleetMassKg, "leftFleetMassKg");
            requirePositiveFinite(rightFleetMassKg, "rightFleetMassKg");
        }

        /** @return relative fitted fleet-mass mismatch in [0,+inf) */
        public double relativeFleetMassMismatch() {
            return Math.abs(leftFleetMassKg - rightFleetMassKg)
                    / Math.max(leftFleetMassKg, rightFleetMassKg);
        }
    }

    /** Creates the catalog over the production-valid provisional Stage-17.5I doctrine content. */
    public Stage175ICombatMatrixCatalog() {
        this(Stage175ICombatTestContentPack.loadDoctrines());
    }

    /**
     * Creates the variation catalog over an explicit production engineering catalog.
     *
     * @param engineering production-valid catalog containing all doctrine fits
     */
    public Stage175ICombatMatrixCatalog(ShipEngineeringCatalog engineering) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.calculator = new DerivedShipCalculator(engineering);
    }

    /**
     * Returns one deterministic case for every required non-cost variation category.
     *
     * @return immutable variation catalog
     */
    public List<MatrixCase> requiredVariants() {
        List<MatrixCase> cases = new ArrayList<>();
        cases.add(caseOf(
                VariantKind.EQUAL_COUNT,
                "equal_count_a_vs_e",
                scenario(DoctrineId.A_KINETIC_LINE, DoctrineId.E_BALANCED_CONTROL,
                        4, 4, 12_000d, 1d, 1d, 0d, InformationPreset.NOMINAL, false)));

        int[] equalMassCounts = bestEqualMassCounts(
                DoctrineId.A_KINETIC_LINE,
                DoctrineId.C_HIGH_MOBILITY_BEAM,
                MAX_EQUAL_MASS_SHIPS_PER_SIDE);
        cases.add(caseOf(
                VariantKind.APPROX_EQUAL_MASS,
                "approx_equal_mass_a_vs_c",
                scenario(DoctrineId.A_KINETIC_LINE, DoctrineId.C_HIGH_MOBILITY_BEAM,
                        equalMassCounts[0], equalMassCounts[1], 12_000d,
                        1d, 1d, 0d, InformationPreset.NOMINAL, false)));

        cases.add(caseOf(
                VariantKind.SMALL_COMPACT_FORMATION,
                "small_compact_b_vs_d",
                scenario(DoctrineId.B_MISSILE_STRIKE, DoctrineId.D_DEFENSIVE_EW,
                        2, 2, COMPACT_SPACING_M, 1d, 1d, 0d, InformationPreset.NOMINAL, true)));
        cases.add(caseOf(
                VariantKind.LARGE_DISPERSED_FORMATION,
                "large_dispersed_b_vs_d",
                scenario(DoctrineId.B_MISSILE_STRIKE, DoctrineId.D_DEFENSIVE_EW,
                        8, 8, DISPERSED_SPACING_M, 1d, 1d, 0d, InformationPreset.NOMINAL, true)));
        cases.add(caseOf(
                VariantKind.PARTIAL_AMMUNITION,
                "partial_ammunition_a_vs_b",
                scenario(DoctrineId.A_KINETIC_LINE, DoctrineId.B_MISSILE_STRIKE,
                        4, 4, 12_000d, 0.35d, 1d, 0d, InformationPreset.NOMINAL, false)));
        cases.add(caseOf(
                VariantKind.PRE_DAMAGED,
                "predamaged_a_vs_e",
                scenario(DoctrineId.A_KINETIC_LINE, DoctrineId.E_BALANCED_CONTROL,
                        4, 4, 12_000d, 1d, 0.60d, 0d, InformationPreset.NOMINAL, false)));
        cases.add(caseOf(
                VariantKind.THERMALLY_STRESSED,
                "thermal_stress_b_vs_d",
                scenario(DoctrineId.B_MISSILE_STRIKE, DoctrineId.D_DEFENSIVE_EW,
                        4, 4, 12_000d, 1d, 1d, 1d, InformationPreset.NOMINAL, true)));
        cases.add(caseOf(
                VariantKind.DEGRADED_INFORMATION,
                "degraded_information_c_vs_e",
                scenario(DoctrineId.C_HIGH_MOBILITY_BEAM, DoctrineId.E_BALANCED_CONTROL,
                        4, 4, 12_000d, 1d, 1d, 0d, InformationPreset.DEGRADED, false)));
        cases.add(caseOf(
                VariantKind.PROTECTED_LOGISTICS,
                "protected_logistics_b_vs_e",
                scenario(DoctrineId.B_MISSILE_STRIKE, DoctrineId.E_BALANCED_CONTROL,
                        4, 4, 20_000d, 1d, 1d, 0d, InformationPreset.NOMINAL, true)));
        cases.sort(Comparator.comparing(value -> value.kind().name()));
        return List.copyOf(cases);
    }

    /** @return exactly the required variation kinds currently represented by {@link #requiredVariants()} */
    public EnumSet<VariantKind> representedVariantKinds() {
        EnumSet<VariantKind> result = EnumSet.noneOf(VariantKind.class);
        requiredVariants().forEach(value -> result.add(value.kind()));
        return result;
    }

    /**
     * Returns the explicit Stage-18 deferral rather than manufacturing a fake scalar cost.
     *
     * @return cost-comparison coverage state
     */
    public ReferenceCostCoverage referenceCostCoverage() {
        return ReferenceCostCoverage.DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS;
    }

    /**
     * Calculates physical total fleet mass for a homogeneous doctrine fixture.
     *
     * @param doctrineId doctrine fit
     * @param shipCount number of physical copies
     * @return fitted loaded mass times count
     */
    public double fleetMassKg(DoctrineId doctrineId, int shipCount) {
        if (shipCount <= 0) {
            throw new IllegalArgumentException("shipCount must be positive");
        }
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.get(Objects.requireNonNull(doctrineId, "doctrineId"));
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        var fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(doctrine.fitId()));
        double perShipMass = calculator.derive(
                hull, fit, doctrine.initialConsumables(), DamageState.pristine()).totalMassKg();
        return perShipMass * shipCount;
    }

    private int[] bestEqualMassCounts(DoctrineId left, DoctrineId right, int maxShips) {
        double leftPerShip = fleetMassKg(left, 1);
        double rightPerShip = fleetMassKg(right, 1);
        int bestLeft = 1;
        int bestRight = 1;
        double bestMismatch = Double.POSITIVE_INFINITY;
        int bestTotalShips = Integer.MAX_VALUE;
        for (int leftCount = 1; leftCount <= maxShips; leftCount++) {
            for (int rightCount = 1; rightCount <= maxShips; rightCount++) {
                double leftMass = leftPerShip * leftCount;
                double rightMass = rightPerShip * rightCount;
                double mismatch = Math.abs(leftMass - rightMass) / Math.max(leftMass, rightMass);
                int totalShips = leftCount + rightCount;
                if (mismatch < bestMismatch - 1e-12d
                        || (Math.abs(mismatch - bestMismatch) <= 1e-12d && totalShips < bestTotalShips)
                        || (Math.abs(mismatch - bestMismatch) <= 1e-12d
                        && totalShips == bestTotalShips && leftCount < bestLeft)) {
                    bestMismatch = mismatch;
                    bestTotalShips = totalShips;
                    bestLeft = leftCount;
                    bestRight = rightCount;
                }
            }
        }
        return new int[]{bestLeft, bestRight};
    }

    private MatrixCase caseOf(VariantKind kind, String id, Scenario scenario) {
        return new MatrixCase(
                kind,
                id,
                scenario,
                fleetMassKg(scenario.leftDoctrine(), scenario.leftCount()),
                fleetMassKg(scenario.rightDoctrine(), scenario.rightCount()));
    }

    private static Scenario scenario(
            DoctrineId left,
            DoctrineId right,
            int leftCount,
            int rightCount,
            double spacingM,
            double ammunitionFraction,
            double integrity,
            double thermalStress,
            InformationPreset information,
            boolean protectedLogistics) {
        return new Scenario(
                left, right, leftCount, rightCount, spacingM, ammunitionFraction,
                integrity, thermalStress, information, protectedLogistics);
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
