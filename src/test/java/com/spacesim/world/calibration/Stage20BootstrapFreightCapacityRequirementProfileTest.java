package com.spacesim.world.calibration;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapFreightCapacityRequirementProfileTest {
    @Test
    void derivesFinitePerStartFleetRequirementFromAcceptedPhysicalAuthorities() {
        Stage20BootstrapFreightCapacityRequirementProfile profile =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        var bootstrap = Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();
        var service = bootstrap.serviceCadence();
        var regional = Stage20IntersystemCadenceCalibrationProfile.deriveCurrent().samples().stream()
                .filter(value -> value.representativeId().equals(service.freightReferenceClass()))
                .filter(value -> value.hopCount() == service.regionalHopCount())
                .findFirst()
                .orElseThrow();

        double expectedDemand = bootstrap.bootstrapRequirements().essentialCommodities().stream()
                .mapToDouble(CommodityRequirement::minSupplierThroughputKgPerSecond)
                .sum();
        double expectedCycle = 2d * service.oneEndpointHandlingSeconds()
                + 2d * service.maximumSourceLocalAccessSeconds()
                + 4d * service.maximumJumpAccessSeconds()
                + 2d * regional.readyAgainTimeS();
        double expectedOneFreighterThroughput = Math.min(
                service.payloadMassKg() / expectedCycle,
                service.hubTransferMassRateKgPerSecond());
        int expectedCount = (int) Math.ceil(expectedDemand / expectedOneFreighterThroughput);

        assertEquals(Stage20BootstrapFreightCapacityRequirementProfile.CURRENT_VERSION, profile.version());
        assertEquals(bootstrap.version(), profile.bootstrapRequirementVersion());
        assertEquals(service.version(), profile.serviceCadenceVersion());
        assertEquals(expectedDemand, profile.totalEssentialDemandKgPerSecond(), 1e-9);
        assertEquals(expectedCycle, profile.referenceRoundTripCycleSeconds(), 1e-9);
        assertEquals(expectedOneFreighterThroughput,
                profile.oneFreighterSustainableThroughputKgPerSecond(), 1e-9);
        assertEquals(expectedCount, profile.requiredFreighterCountPerFactionStart());
        assertTrue(profile.requiredFreighterCountPerFactionStart() > 0);
        assertTrue(profile.stage22ReviewRequired());
        assertTrue(profile.evidenceIds().stream().anyMatch(value -> value.startsWith("ship:")));
        assertTrue(profile.evidenceIds().stream().anyMatch(value -> value.startsWith("ftl:")));

        System.out.println("STAGE20E_BOOTSTRAP_FREIGHT_CAPACITY_REQUIREMENT_BEGIN");
        System.out.println("version=" + profile.version());
        System.out.println("bootstrapRequirementVersion=" + profile.bootstrapRequirementVersion());
        System.out.println("serviceCadenceVersion=" + profile.serviceCadenceVersion());
        System.out.println("intersystemCadenceVersion=" + profile.intersystemCadenceVersion());
        System.out.println("freightReferenceClass=" + profile.freightReferenceClass());
        System.out.println("regionalHopCount=" + profile.regionalHopCount());
        System.out.println("totalEssentialDemandKgS=" + profile.totalEssentialDemandKgPerSecond());
        System.out.println("payloadMassKg=" + profile.payloadMassKg());
        System.out.println("regionalFtlReadyAgainSeconds=" + profile.regionalFtlReadyAgainSeconds());
        System.out.println("referenceRoundTripCycleSeconds=" + profile.referenceRoundTripCycleSeconds());
        System.out.println("oneFreighterSustainableThroughputKgS="
                + profile.oneFreighterSustainableThroughputKgPerSecond());
        System.out.println("requiredFreighterCountPerFactionStart="
                + profile.requiredFreighterCountPerFactionStart());
        System.out.println("STAGE20E_BOOTSTRAP_FREIGHT_CAPACITY_REQUIREMENT_END");
    }
}
