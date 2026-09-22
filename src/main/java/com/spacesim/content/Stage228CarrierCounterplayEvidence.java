package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection.DesignBinding;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.world.SmallCraftBayResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.8K read-only balance/counterplay evidence for carrier small craft.
 *
 * <p>This class is deliberately excluded from gameplay authority. It derives physical role vectors,
 * carrier bay limits and replacement burden from accepted engineering/Stage-18 content and records
 * which existing common systems provide counterplay for the required K scenario families. It never
 * grants a carrier/fighter damage, armor, accuracy, evasion, income or readiness multiplier.</p>
 */
public final class Stage228CarrierCounterplayEvidence {
    /** Semantic version of the K evidence surface. */
    public static final String VERSION = "m22.8k.carrier_counterplay_evidence.v1";

    private Stage228CarrierCounterplayEvidence() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives the current deterministic carrier/small-craft balance evidence.
     *
     * @return immutable evidence bundle
     */
    public static Evidence deriveCurrent() {
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();

        List<RoleVector> roles = Stage228SmallCraftProductionProjection.designBindings().stream()
                .sorted(Comparator.comparing(DesignBinding::stableFactionId)
                        .thenComparing(DesignBinding::roleId)
                        .thenComparing(DesignBinding::fitId))
                .map(binding -> roleVector(engineering, binding))
                .toList();

        Map<String, CarrierBayVector> carriers = Map.of(
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                carrierBayVector(
                        engineering,
                        Stage22EmpirePackageLoader.loadDefault().shipFamilies().stream()
                                .filter(value -> value.roleId().equals("role.military.carrier"))
                                .findFirst().orElseThrow().primaryFitId(),
                        Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                        roles),
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                carrierBayVector(
                        engineering,
                        Stage22IndustrialUnionPackageLoader.loadDefault().shipFamilies().stream()
                                .filter(value -> value.roleId().equals("role.military.carrier"))
                                .findFirst().orElseThrow().primaryFitId(),
                        Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                        roles));

        Map<String, ReplacementVector> replacement = Map.of(
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                replacementVector(
                        Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                        Stage228SmallCraftShipyardCatalogLoader.loadEmpireDefault(),
                        engineering,
                        Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID),
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                replacementVector(
                        Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                        Stage228SmallCraftShipyardCatalogLoader.loadIndustrialUnionDefault(),
                        engineering,
                        Stage228SmallCraftProductionProjection.UNION_HULL_ID));

        return new Evidence(
                VERSION,
                engineering.getFingerprint(),
                roles,
                carriers,
                replacement,
                scenarioMatrix());
    }

    private static RoleVector roleVector(
            ShipEngineeringCatalog engineering,
            DesignBinding binding) {
        DemonstratorFitDefinition fit = requireFit(engineering, binding.fitId());
        var hull = Objects.requireNonNull(
                engineering.findHull(fit.hullId()),
                "hull " + fit.hullId());

        double moduleMassKg = 0d;
        double thrustN = 0d;
        double sensorApertureM2 = 0d;
        double shieldReserveJ = 0d;
        double beamPowerW = 0d;
        double kineticProjectileMassKg = 0d;
        double ammunitionCapacity = 0d;
        double reactionMassCapacityKg = 0d;
        double maintenanceWorkSeconds = 0d;

        for (var installed : fit.installedModules()) {
            ModuleDefinition module = Objects.requireNonNull(
                    engineering.findModule(installed.moduleId()),
                    "module " + installed.moduleId());
            moduleMassKg += module.massKg();
            thrustN += module.capabilityParameters()
                    .getOrDefault(ShipEngineeringRuntime.THRUST_N, 0d);
            sensorApertureM2 += module.capabilityParameters()
                    .getOrDefault("aperture_area_m2", 0d);
            shieldReserveJ += module.capabilityParameters()
                    .getOrDefault("field_reserve_j",
                            module.capabilityParameters().getOrDefault("field_capacity_j", 0d));
            beamPowerW += module.capabilityParameters()
                    .getOrDefault("beam_power_w", 0d);
            kineticProjectileMassKg += module.capabilityParameters()
                    .getOrDefault("projectile_mass_kg", 0d);
            maintenanceWorkSeconds += module.maintenance().maintenanceWorkSeconds();
            for (var physicalInterface : module.interfaces()) {
                if (physicalInterface.kind() == InterfaceKind.AMMUNITION) {
                    ammunitionCapacity += physicalInterface.capacity();
                } else if (physicalInterface.kind() == InterfaceKind.REACTION_MASS) {
                    reactionMassCapacityKg += physicalInterface.capacity();
                }
            }
        }
        double fittedDryMassKg = hull.bareHullMassKg() + moduleMassKg;
        double accelerationMps2 = thrustN / fittedDryMassKg;
        return new RoleVector(
                binding.fitId(),
                binding.stableFactionId(),
                binding.roleId(),
                fittedDryMassKg,
                thrustN,
                accelerationMps2,
                sensorApertureM2,
                shieldReserveJ,
                beamPowerW,
                kineticProjectileMassKg,
                ammunitionCapacity,
                reactionMassCapacityKg,
                maintenanceWorkSeconds);
    }

    private static CarrierBayVector carrierBayVector(
            ShipEngineeringCatalog engineering,
            String carrierFitId,
            String stableFactionId,
            List<RoleVector> roleVectors) {
        DemonstratorFitDefinition carrier = requireFit(engineering, carrierFitId);
        int bayCount = 0;
        double totalSupportedMassKg = 0d;
        double totalUsableVolumeM3 = 0d;
        double maxEnvelopeLengthM = 0d;
        double maxEnvelopeWidthM = 0d;
        double maxEnvelopeHeightM = 0d;

        for (var installed : carrier.installedModules()) {
            ModuleDefinition module = Objects.requireNonNull(
                    engineering.findModule(installed.moduleId()),
                    "carrier module " + installed.moduleId());
            if (module.family() != ModuleFamily.HANGAR_SMALL_CRAFT) {
                continue;
            }
            bayCount++;
            Double supported = module.capabilityParameters()
                    .get(SmallCraftBayResolver.SUPPORTED_CRAFT_MASS_KG);
            if (supported == null || !Double.isFinite(supported) || supported <= 0d) {
                throw new IllegalStateException(
                        "carrier hangar lacks positive physical supported craft mass: " + module.id());
            }
            totalSupportedMassKg += supported;
            totalUsableVolumeM3 += module.occupiedVolumeM3();
            maxEnvelopeLengthM = Math.max(maxEnvelopeLengthM, module.physicalDimensionsM().lengthM());
            maxEnvelopeWidthM = Math.max(maxEnvelopeWidthM, module.physicalDimensionsM().widthM());
            maxEnvelopeHeightM = Math.max(maxEnvelopeHeightM, module.physicalDimensionsM().heightM());
        }
        if (bayCount == 0) {
            throw new IllegalStateException("production carrier exposes no physical small-craft bay: " + carrierFitId);
        }

        LinkedHashMap<String, Integer> boundedCapacityByRole = new LinkedHashMap<>();
        List<RoleVector> factionRoles = roleVectors.stream()
                .filter(value -> value.stableFactionId().equals(stableFactionId))
                .sorted(Comparator.comparing(RoleVector::roleId))
                .toList();
        for (RoleVector value : factionRoles) {
            var fit = requireFit(engineering, value.fitId());
            var hull = Objects.requireNonNull(engineering.findHull(fit.hullId()), fit.hullId());
            boolean envelopeFits =
                    hull.boundingDimensionsM().lengthM() <= maxEnvelopeLengthM
                            && hull.boundingDimensionsM().widthM() <= maxEnvelopeWidthM
                            && hull.boundingDimensionsM().heightM() <= maxEnvelopeHeightM;
            double craftVolumeM3 = hull.boundingDimensionsM().lengthM()
                    * hull.boundingDimensionsM().widthM()
                    * hull.boundingDimensionsM().heightM();
            int byMass = (int) Math.floor(totalSupportedMassKg / value.fittedDryMassKg());
            int byVolume = (int) Math.floor(totalUsableVolumeM3 / craftVolumeM3);
            boundedCapacityByRole.put(
                    value.roleId(),
                    envelopeFits ? Math.max(0, Math.min(byMass, byVolume)) : 0);
        }

        return new CarrierBayVector(
                stableFactionId,
                carrierFitId,
                bayCount,
                totalSupportedMassKg,
                totalUsableVolumeM3,
                maxEnvelopeLengthM,
                maxEnvelopeWidthM,
                maxEnvelopeHeightM,
                Map.copyOf(boundedCapacityByRole));
    }

    private static ReplacementVector replacementVector(
            String stableFactionId,
            Stage18ShipyardCatalog shipyard,
            ShipEngineeringCatalog engineering,
            String hullId) {
        var physical = Objects.requireNonNull(
                shipyard.findHullProfile(hullId),
                "physical hull profile " + hullId);
        double hullBuildMassKg = physical.buildInputsKg().stream()
                .mapToDouble(Stage18ShipyardCatalog.PhysicalInputDefinition::massKg)
                .sum();

        List<DesignBinding> factionDesigns =
                Stage228SmallCraftProductionProjection.designBindings().stream()
                        .filter(value -> value.stableFactionId().equals(stableFactionId))
                        .toList();
        int minimumModuleCount = factionDesigns.stream()
                .map(value -> requireFit(engineering, value.fitId()))
                .mapToInt(value -> value.installedModules().size())
                .min().orElseThrow();
        int maximumModuleCount = factionDesigns.stream()
                .map(value -> requireFit(engineering, value.fitId()))
                .mapToInt(value -> value.installedModules().size())
                .max().orElseThrow();

        return new ReplacementVector(
                stableFactionId,
                hullId,
                hullBuildMassKg,
                minimumModuleCount,
                maximumModuleCount,
                physical.buildInputsKg().size());
    }

    private static List<ScenarioEvidence> scenarioMatrix() {
        return List.of(
                scenario(
                        ScenarioKind.CONVENTIONAL_SURFACE_COMBATANTS,
                        EnumSet.of(
                                CounterplaySurface.PHYSICAL_FIT_MASS_AND_PROTECTION,
                                CounterplaySurface.KINEMATICS_AND_RANGE,
                                CounterplaySurface.CARRIER_HOST_LOSS),
                        List.of(
                                "ShipFittingValidator",
                                "Stage19ExactTacticalEncounterResolver",
                                "CarrierStrategicTacticalEncounterService"),
                        List.of(
                                "SmallCraftTacticalEncounterServiceTest.productionBridgeUsesRealStage19ResolverForSmallCraftAndExternalCombatant",
                                "CarrierStrategicTacticalEncounterServiceTest.strategicHandoffCommitsIndividualLossAndLeavesDetachedCarrierAndBayStateUntouched")),
                scenario(
                        ScenarioKind.MISSILE_HEAVY_FORCES,
                        EnumSet.of(
                                CounterplaySurface.FINITE_GUIDED_ORDNANCE,
                                CounterplaySurface.POINT_DEFENSE_CHANNELS_AMMO_THERMAL,
                                CounterplaySurface.SENSOR_TRACK_QUALITY),
                        List.of(
                                "WeaponAmmunitionCatalog",
                                "LayeredDefenseScheduler",
                                "ShipSensorRuntime"),
                        List.of(
                                "Stage228CarrierCounterplayPhysicalSystemsTest.pointDefenseScreenIsEffectiveButFiniteInChannelsAmmoAndThermalDuty",
                                "LiveTacticalBattleGuidedImpactAcceptanceTest")),
                scenario(
                        ScenarioKind.STRONG_POINT_DEFENSE_OR_INTERCEPTOR_SCREEN,
                        EnumSet.of(
                                CounterplaySurface.POINT_DEFENSE_CHANNELS_AMMO_THERMAL,
                                CounterplaySurface.KINEMATICS_AND_RANGE,
                                CounterplaySurface.FINITE_SORTIE_GENERATION),
                        List.of(
                                "LayeredDefenseScheduler",
                                "SmallCraftTacticalEncounterService",
                                "SmallCraftFlightDeckOperations"),
                        List.of(
                                "Stage228CarrierCounterplayPhysicalSystemsTest.pointDefenseScreenIsEffectiveButFiniteInChannelsAmmoAndThermalDuty",
                                "SmallCraftFlightDeckOperationsTest")),
                scenario(
                        ScenarioKind.EW_AND_DECEPTION_PRESSURE,
                        EnumSet.of(
                                CounterplaySurface.SENSOR_TRACK_QUALITY,
                                CounterplaySurface.ELECTRONIC_WARFARE,
                                CounterplaySurface.KNOWLEDGE_FRESHNESS),
                        List.of(
                                "ShipSensorRuntime",
                                "ShipElectronicWarfareEngineeringAdapter",
                                "FactionActorObservationSnapshot"),
                        List.of(
                                "Stage228CarrierCounterplayPhysicalSystemsTest.electronicWarfareCanSuppressTrackingAndEccmRecoversAtExplicitPowerCost",
                                "LiveTacticalOrdnanceElectronicWarfareTest")),
                scenario(
                        ScenarioKind.DEGRADED_LOGISTICS_AND_REPLACEMENT,
                        EnumSet.of(
                                CounterplaySurface.FINITE_PROPellant_AND_AMMUNITION,
                                CounterplaySurface.FINITE_SORTIE_GENERATION,
                                CounterplaySurface.STAGE18_REPAIR_AND_REPLACEMENT,
                                CounterplaySurface.CARRIER_HOST_LOSS),
                        List.of(
                                "SmallCraftTurnaroundService",
                                "SmallCraftFlightDeckOperations",
                                "SmallCraftPhysicalLogisticsService",
                                "Stage18ShipyardRuntime"),
                        List.of(
                                "SmallCraftTurnaroundServiceTest.finiteFuelAndAmmoRequireExactDeliveryAndHandlingWorkBeforeReady",
                                "SmallCraftPhysicalLogisticsServiceTest",
                                "CarrierPostBattleRecoveryServiceTest")));
    }

    private static ScenarioEvidence scenario(
            ScenarioKind kind,
            EnumSet<CounterplaySurface> surfaces,
            List<String> authorities,
            List<String> acceptanceFixtures) {
        return new ScenarioEvidence(
                kind,
                List.copyOf(surfaces),
                List.copyOf(authorities),
                List.copyOf(acceptanceFixtures));
    }

    private static DemonstratorFitDefinition requireFit(
            ShipEngineeringCatalog engineering,
            String fitId) {
        DemonstratorFitDefinition fit = engineering.findDemonstratorFit(fitId);
        if (fit == null) {
            throw new IllegalStateException("M22.8K evidence references absent fit: " + fitId);
        }
        return fit;
    }

    /** Required K matchup family. */
    public enum ScenarioKind {
        /** Carrier group against conventional gun/beam/kinetic combatants. */
        CONVENTIONAL_SURFACE_COMBATANTS,
        /** Carrier group under guided-ordnance pressure. */
        MISSILE_HEAVY_FORCES,
        /** Carrier group opposed by strong PD or interceptor screens. */
        STRONG_POINT_DEFENSE_OR_INTERCEPTOR_SCREEN,
        /** Carrier group under sensor/EW/deception pressure. */
        EW_AND_DECEPTION_PRESSURE,
        /** Carrier group with damaged supply, turnaround or replacement chain. */
        DEGRADED_LOGISTICS_AND_REPLACEMENT
    }

    /** Existing common physical/systemic counterplay surfaces consumed by K evidence. */
    public enum CounterplaySurface {
        /** Hull/module mass, topology and ordinary protection/damage capacity. */
        PHYSICAL_FIT_MASS_AND_PROTECTION,
        /** Ordinary thrust, mass, range geometry and engagement timing. */
        KINEMATICS_AND_RANGE,
        /** Guided bodies are finite physical ammunition with real flight envelopes. */
        FINITE_GUIDED_ORDNANCE,
        /** Defense is limited by support channels, ammunition and thermal availability. */
        POINT_DEFENSE_CHANNELS_AMMO_THERMAL,
        /** Detection/tracking/fire-control quality follows physical sensor equations. */
        SENSOR_TRACK_QUALITY,
        /** Jamming/deception act through the common EW/sensor authority. */
        ELECTRONIC_WARFARE,
        /** Decisions are bounded by actor-known observations and freshness. */
        KNOWLEDGE_FRESHNESS,
        /** Launch/recovery throughput is finite physical deck work. */
        FINITE_SORTIE_GENERATION,
        /** Craft expend finite reaction mass and ammunition. */
        FINITE_PROPellant_AND_AMMUNITION,
        /** Repair/replacement consumes ordinary Stage-18 resources/work/delivery. */
        STAGE18_REPAIR_AND_REPLACEMENT,
        /** Destroying a carrier removes its remaining physically embarked craft. */
        CARRIER_HOST_LOSS
    }

    /**
     * One exact production-role physical vector.
     *
     * @param fitId exact production fit
     * @param stableFactionId governed owner
     * @param roleId authored role label
     * @param fittedDryMassKg hull plus installed module mass
     * @param thrustN fitted ordinary thrust
     * @param accelerationMps2 dry-mass acceleration proxy
     * @param sensorApertureM2 fitted physical sensor aperture
     * @param shieldReserveJ fitted shield reserve, zero when absent
     * @param beamPowerW fitted beam power, zero when absent
     * @param kineticProjectileMassKg fitted kinetic projectile mass, zero when absent
     * @param ammunitionCapacity finite ammunition interface capacity
     * @param reactionMassCapacityKg finite reaction-mass interface capacity
     * @param maintenanceWorkSeconds summed authored maintenance work
     */
    public record RoleVector(
            String fitId,
            String stableFactionId,
            String roleId,
            double fittedDryMassKg,
            double thrustN,
            double accelerationMps2,
            double sensorApertureM2,
            double shieldReserveJ,
            double beamPowerW,
            double kineticProjectileMassKg,
            double ammunitionCapacity,
            double reactionMassCapacityKg,
            double maintenanceWorkSeconds) { }

    /**
     * Physical carrier bay envelope and bounded role capacities.
     *
     * @param stableFactionId governed owner
     * @param carrierFitId exact production carrier fit
     * @param bayCount installed physical bay count
     * @param totalSupportedMassKg aggregate supported embarked-craft mass
     * @param totalUsableVolumeM3 aggregate physical hangar volume
     * @param maxSingleCraftLengthM largest accepted per-bay envelope length
     * @param maxSingleCraftWidthM largest accepted per-bay envelope width
     * @param maxSingleCraftHeightM largest accepted per-bay envelope height
     * @param boundedCapacityByRole conservative mass/volume bounded craft count per role
     */
    public record CarrierBayVector(
            String stableFactionId,
            String carrierFitId,
            int bayCount,
            double totalSupportedMassKg,
            double totalUsableVolumeM3,
            double maxSingleCraftLengthM,
            double maxSingleCraftWidthM,
            double maxSingleCraftHeightM,
            Map<String, Integer> boundedCapacityByRole) { }

    /**
     * Ordinary Stage-18 replacement burden for one faction's small-craft hull family.
     *
     * @param stableFactionId governed owner
     * @param hullId production small-craft hull
     * @param hullBuildMassKg finite Stage-18 bare-hull input mass
     * @param minimumInstalledModuleCount least complex role fit
     * @param maximumInstalledModuleCount most complex role fit
     * @param distinctHullInputCount number of physical input commodity families
     */
    public record ReplacementVector(
            String stableFactionId,
            String hullId,
            double hullBuildMassKg,
            int minimumInstalledModuleCount,
            int maximumInstalledModuleCount,
            int distinctHullInputCount) { }

    /**
     * One mandatory K matchup mapped to existing common counterplay authorities.
     *
     * @param kind matchup family
     * @param counterplaySurfaces physically actionable surfaces
     * @param commonAuthorities existing authorities that own those surfaces
     * @param acceptanceFixtures concrete deterministic regression fixtures exercising the matchup surfaces
     */
    public record ScenarioEvidence(
            ScenarioKind kind,
            List<CounterplaySurface> counterplaySurfaces,
            List<String> commonAuthorities,
            List<String> acceptanceFixtures) { }

    /**
     * Complete deterministic K evidence bundle.
     *
     * @param version evidence schema/version
     * @param engineeringFingerprint exact J physical-content fingerprint
     * @param roleVectors all six production role vectors
     * @param carrierBayVectors carrier physical capacity evidence by faction
     * @param replacementVectors Stage-18 replacement burden by faction
     * @param scenarios all five required matchup families
     */
    public record Evidence(
            String version,
            String engineeringFingerprint,
            List<RoleVector> roleVectors,
            Map<String, CarrierBayVector> carrierBayVectors,
            Map<String, ReplacementVector> replacementVectors,
            List<ScenarioEvidence> scenarios) { }
}
