package com.spacesim.content;

import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.AssetPathKind;
import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.CivilianAvailability;
import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.CivilianRole;
import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.EcosystemHook;
import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.HookKind;
import com.spacesim.content.Stage22CivilianMinorEcosystemCatalog.ScenarioBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Diagnostic-only M22.5 validator that resolves civilian/minor content through existing authorities.
 *
 * <p>No method mutates world, economy, diplomacy, production or fleet state. The validator proves that
 * concrete content references resolve, licensed support assets bind real core production manifests,
 * service providers are real constructible legacy archetypes, B08/B16 have core-pair mission bindings,
 * and any unfinished production path remains explicit in the report.</p>
 */
public final class Stage22CivilianMinorEcosystemValidator {
    private Stage22CivilianMinorEcosystemValidator() {
        throw new AssertionError("utility class");
    }

    /** Runs the canonical M22.5 diagnostic validation. */
    public static ValidationReport validateDefault() {
        Stage22CivilianMinorEcosystemCatalog ecosystem = Stage22CivilianMinorEcosystemCatalog.loadDefault();
        ContentCatalog legacy = ContentCatalogLoader.loadDefault();
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        Stage22IndustrialUnionPackageCatalog union = Stage22IndustrialUnionPackageLoader.loadDefault();

        int licensedPaths = 0;
        for (CivilianAvailability availability : ecosystem.civilianAvailability()) {
            requireClass(availability.operatingAuthority());
            if (availability.productionAuthority().startsWith("com.")) {
                requireClass(availability.productionAuthority());
            }
            if (availability.supportAuthority().startsWith("com.")) {
                requireClass(availability.supportAuthority());
            }
            if (availability.pathKind() == AssetPathKind.LEGACY_RUNTIME_ARCHETYPE) {
                if (legacy.findShipArchetype(availability.assetRef()) == null) {
                    throw new IllegalStateException("Unknown legacy civilian ship archetype: " + availability.assetRef());
                }
            } else {
                licensedPaths++;
                validateLicensedCoreAsset(availability, empire, union);
            }
        }

        for (Stage22CivilianMinorEcosystemCatalog.ServiceProviderPolicy provider : ecosystem.serviceProviders()) {
            ContentCatalog.StationArchetypeDefinition station = legacy.findStationArchetype(provider.providerRef());
            if (station == null) {
                throw new IllegalStateException("Unknown M22.5 service provider archetype: " + provider.providerRef());
            }
            if (!provider.ownerFactionId().equals(station.factionId())) {
                throw new IllegalStateException("M22.5 provider owner mismatch: " + provider.providerRef());
            }
            if (station.construction() == null) {
                throw new IllegalStateException("M22.5 service provider lacks physical construction path: " + provider.providerRef());
            }
            requireClass(provider.ownershipAuthority());
            requireClass(provider.accessAuthority());
            requireClass(provider.tariffAuthority());
            requireClass(provider.logisticsAuthority());
        }

        for (Stage22CivilianMinorEcosystemCatalog.MinorActorPolicy actor : ecosystem.minorActors()) {
            Stage22ContentGovernanceCatalog.FactionIdentityDefinition identity = governance.findFactionIdentity(actor.stableFactionId());
            if (identity == null || identity.canonicalPackageKey() != null || !actor.preserveStableId()
                    || actor.majorPackageFallbackAllowed()) {
                throw new IllegalStateException("Invalid M22.5 minor identity governance: " + actor.stableFactionId());
            }
        }

        boolean insuranceDeferred = false;
        for (EcosystemHook hook : ecosystem.hooks()) {
            if (hook.deferred()) {
                if (hook.authorityRef() != null) {
                    throw new IllegalStateException("Deferred ecosystem hook invented an authority: " + hook.kind());
                }
            } else {
                requireClass(hook.authorityRef());
            }
            if (hook.kind() == HookKind.INSURANCE) {
                insuranceDeferred = hook.deferred();
            }
        }
        if (!insuranceDeferred) {
            throw new IllegalStateException("M22.5 insurance integration must remain an explicit deferred content hook");
        }

        boolean b08Ready = validateScenario(ecosystem.scenario("B08"), empire, union, "CONVOY_ESCORT", null);
        boolean b16Ready = validateScenario(ecosystem.scenario("B16"), empire, union, null, "MARKET_ACCESS_ALLOWED");

        return new ValidationReport(
                ecosystem.fingerprint(),
                ecosystem.civilianAvailability().size(),
                licensedPaths,
                ecosystem.serviceProviders().size(),
                ecosystem.minorActors().size(),
                ecosystem.unresolvedProductionRoles(),
                b08Ready,
                b16Ready,
                insuranceDeferred);
    }

    private static void validateLicensedCoreAsset(
            CivilianAvailability availability,
            Stage22EmpirePackageCatalog empire,
            Stage22IndustrialUnionPackageCatalog union) {
        String roleId = roleId(availability.role());
        if (roleId == null) {
            throw new IllegalStateException("Role has no legal core-license mapping: " + availability.role());
        }
        Stage22EmpirePackageCatalog.ShipFamilyDefinition empireFamily = null;
        Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition unionFamily = null;
        if (Stage22EmpirePackageCatalog.PACKAGE_KEY.equals(availability.sourcePackageKey())) {
            empireFamily = empire.findShipForRole(roleId);
            if (empireFamily == null) {
                throw new IllegalStateException("Empire lacks licensed role: " + roleId);
            }
            requireFamilyBinding(
                    availability,
                    empireFamily.primaryFitId(),
                    empireFamily.refitFitId(),
                    empireFamily.productionManifestId());
        } else if (Stage22IndustrialUnionPackageCatalog.PACKAGE_KEY.equals(availability.sourcePackageKey())) {
            unionFamily = union.findShipForRole(roleId);
            if (unionFamily == null) {
                throw new IllegalStateException("Industrial Union lacks licensed role: " + roleId);
            }
            requireFamilyBinding(
                    availability,
                    unionFamily.primaryFitId(),
                    unionFamily.refitFitId(),
                    unionFamily.productionManifestId());
        } else {
            throw new IllegalStateException("Unknown licensed core package: " + availability.sourcePackageKey());
        }
    }

    private static void requireFamilyBinding(
            CivilianAvailability availability,
            String primaryFitId,
            String refitFitId,
            String productionManifestId) {
        if (!availability.assetRef().equals(primaryFitId) && !availability.assetRef().equals(refitFitId)) {
            throw new IllegalStateException("Licensed M22.5 asset is not a real core family fit: " + availability.assetRef());
        }
        if (!availability.productionManifestId().equals(productionManifestId)) {
            throw new IllegalStateException("Licensed M22.5 asset production manifest mismatch: " + availability.assetRef());
        }
    }

    private static boolean validateScenario(
            ScenarioBinding scenario,
            Stage22EmpirePackageCatalog empire,
            Stage22IndustrialUnionPackageCatalog union,
            String expectedRuntimeTemplate,
            String expectedObjectiveKind) {
        if (scenario == null) {
            throw new IllegalStateException("Missing required M22.5 scenario binding");
        }
        requireClass(scenario.primaryAuthority());
        boolean empireBound = false;
        boolean unionBound = false;
        for (String missionId : scenario.requiredCoreMissionIds()) {
            if (missionId.startsWith("mission.empire.")) {
                Stage22EmpirePackageCatalog.MissionTemplateDefinition mission = empire.findMission(missionId);
                if (mission == null) {
                    throw new IllegalStateException("Scenario references unknown Empire mission: " + missionId);
                }
                requireScenarioMission(mission.runtimeTemplate().name(), mission.objectiveKind().name(), expectedRuntimeTemplate, expectedObjectiveKind);
                empireBound = true;
            } else if (missionId.startsWith("mission.industrial_union.")) {
                Stage22IndustrialUnionPackageCatalog.MissionTemplateDefinition mission = union.findMission(missionId);
                if (mission == null) {
                    throw new IllegalStateException("Scenario references unknown Industrial Union mission: " + missionId);
                }
                requireScenarioMission(mission.runtimeTemplate().name(), mission.objectiveKind().name(), expectedRuntimeTemplate, expectedObjectiveKind);
                unionBound = true;
            } else {
                throw new IllegalStateException("Scenario binding must use core-pair mission content: " + missionId);
            }
        }
        if (!empireBound || !unionBound) {
            throw new IllegalStateException("Scenario binding must cover both core factions: " + scenario.scenarioId());
        }
        return true;
    }

    private static void requireScenarioMission(
            String runtimeTemplate,
            String objectiveKind,
            String expectedRuntimeTemplate,
            String expectedObjectiveKind) {
        if (expectedRuntimeTemplate != null && !expectedRuntimeTemplate.equals(runtimeTemplate)) {
            throw new IllegalStateException("Unexpected runtime template for M22.5 scenario: " + runtimeTemplate);
        }
        if (expectedObjectiveKind != null && !expectedObjectiveKind.equals(objectiveKind)) {
            throw new IllegalStateException("Unexpected objective kind for M22.5 scenario: " + objectiveKind);
        }
    }

    private static String roleId(CivilianRole role) {
        return switch (role) {
            case FREIGHT, NEUTRAL_TRAFFIC -> "role.support.freight";
            case TANKER -> "role.support.tanker_replenishment";
            case SALVAGE -> "role.support.fleet_logistics_repair_salvage";
            case MINING -> null;
        };
    }

    private static void requireClass(String className) {
        String checked = Objects.requireNonNull(className, "authority class").strip();
        try {
            Class.forName(checked, false, Stage22CivilianMinorEcosystemValidator.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("M22.5 authority class does not exist: " + checked, exception);
        }
    }

    /** Immutable validation evidence for M22.5 closure work. */
    public record ValidationReport(
            String ecosystemFingerprint,
            int civilianRoleCount,
            int licensedProductionPathCount,
            int serviceProviderCount,
            int preservedMinorActorCount,
            List<CivilianRole> unresolvedProductionRoles,
            boolean b08BindingReady,
            boolean b16BindingReady,
            boolean insuranceHookDeferred) {
        public ValidationReport {
            ecosystemFingerprint = Objects.requireNonNull(ecosystemFingerprint, "ecosystemFingerprint");
            unresolvedProductionRoles = List.copyOf(Objects.requireNonNull(unresolvedProductionRoles, "unresolvedProductionRoles"));
        }

        /** @return true only when every required M22.5 civilian role has a legal production path */
        public boolean productionClosureReady() {
            return unresolvedProductionRoles.isEmpty();
        }
    }
}
