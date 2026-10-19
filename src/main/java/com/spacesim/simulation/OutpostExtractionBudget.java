package com.spacesim.simulation;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;

/**
 * The largest gross source mass an outpost can extract in one interval and hand to a receiving
 * station: limited by the remaining deposit, the installed method's rate, the outpost's power, work
 * and maintenance, its storage, both handling rates and the receiver's room for the output.
 */
public final class OutpostExtractionBudget {
    private static final double MASS_EPSILON_KG = 1.0e-9d;

    private OutpostExtractionBudget() { }

    /**
     * @param outpost generated source outpost
     * @param receiver station that takes the output in the same interval
     * @param outputCapKg most output the receiver wants or can take
     * @param elapsedSimulationSeconds interval
     * @return gross source mass to request; output is this times {@link #recoveryFraction}
     */
    public static double feasibleSourceMassKg(MaterializedExtractionOutpost outpost, RuntimeEndpoint receiver,
            double outputCapKg, double elapsedSimulationSeconds) {
        var source = outpost.source().sourceState();
        var method = method(outpost);
        var commodity = Stage18ResourceOntologyLoader.loadDefault().findCommodity(source.outputCommodityId());
        if (commodity == null) {
            throw new IllegalStateException(
                    "generated extraction source references missing installed commodity: " + source.outputCommodityId());
        }
        double recoveryFraction = recoveryFraction(outpost);
        if (!Double.isFinite(recoveryFraction) || recoveryFraction <= MASS_EPSILON_KG) {
            return 0d;
        }
        var facility = outpost.facilityCapability();
        double maxSourceMassKg = source.remainingAccessibleMassKg();
        maxSourceMassKg = Math.min(maxSourceMassKg, method.maxSourceKgPerSecond() * elapsedSimulationSeconds);
        maxSourceMassKg = Math.min(maxSourceMassKg,
                facility.effectiveProcessPowerW() * elapsedSimulationSeconds / method.energyJPerSourceKg());
        maxSourceMassKg = Math.min(maxSourceMassKg,
                facility.effectiveEngineeringWorkRate() * elapsedSimulationSeconds / method.workSecondsPerSourceKg());
        maxSourceMassKg = Math.min(maxSourceMassKg,
                facility.effectiveMaintenanceWorkRate() * elapsedSimulationSeconds / method.maintenanceWorkSecondsPerSourceKg());
        double outpostStorageOutputKg = outpost.storage().remainingCapacityKg(commodity.storageClassId());
        double stagingOutputKg = Math.min(outpost.stationNode().handlingCapability().massRateKgPerSecond(),
                receiver.handlingCapability().massRateKgPerSecond()) * elapsedSimulationSeconds;
        double outputBudgetKg = Math.min(outputCapKg, Math.min(outpostStorageOutputKg, stagingOutputKg));
        maxSourceMassKg = Math.min(maxSourceMassKg, outputBudgetKg / recoveryFraction);
        return Math.max(0d, maxSourceMassKg);
    }

    /**
     * @param outpost generated source outpost
     * @return output mass per kilogram of gross source mass
     */
    public static double recoveryFraction(MaterializedExtractionOutpost outpost) {
        var source = outpost.source().sourceState();
        return source.gradeFraction() * source.sourceRecoveryFraction() * method(outpost).recoveryFraction();
    }

    private static com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition method(MaterializedExtractionOutpost outpost) {
        var method = Stage18ExtractionCatalogLoader.loadDefault().findMethod(outpost.site().extractionMethodId());
        if (method == null) {
            throw new IllegalStateException(
                    "generated extraction site references missing installed method: " + outpost.site().extractionMethodId());
        }
        return method;
    }
}
