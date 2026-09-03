package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Stage-22.5 governed contract for shared civilian traffic and preserved minor actors.
 *
 * <p>This class owns no mutable economy, diplomacy, ownership, logistics, extraction, production or
 * fleet state. It binds M22.5 content to existing authorities, allows individual reviewed core support
 * assets to be exposed through ordinary licensed-market access without granting the operator a core
 * faction doctrine/profile, and keeps incomplete legacy production paths explicit rather than faking
 * closure.</p>
 */
public final class Stage22CivilianMinorEcosystemCatalog {
    /** Shared civilian roles required by the M22.5 alpha slice. */
    public enum CivilianRole {
        /** Inter-system freight and cargo transport. */
        FREIGHT,
        /** Fuel, propellant and replenishment support. */
        TANKER,
        /** Civilian resource extraction. */
        MINING,
        /** Wreck recovery and salvage support. */
        SALVAGE,
        /** Background neutral traffic using ordinary logistics authority. */
        NEUTRAL_TRAFFIC
    }

    /** Provenance of one civilian asset path. */
    public enum AssetPathKind {
        /** Existing compatibility-era runtime archetype retained for supported saves/worlds. */
        LEGACY_RUNTIME_ARCHETYPE,
        /** One concrete reviewed core asset exposed individually through ordinary market licensing. */
        LICENSED_CORE_FIT
    }

    /** Optional ecosystem integrations that must not create a parallel economy. */
    public enum HookKind {
        /** Existing inter-system trade integration. */
        TRADE,
        /** Existing mission/contract integration. */
        CONTRACT,
        /** Reserved insurance integration that remains explicitly deferred in M22.5. */
        INSURANCE
    }

    /**
     * One legal or explicitly-unfinished civilian availability path.
     *
     * @param role shared civilian role
     * @param pathKind provenance of the asset path
     * @param assetRef concrete legacy archetype or production fit ID
     * @param sourcePackageKey core package key for licensed fits, otherwise {@code null}
     * @param productionManifestId exact core production manifest for licensed fits, otherwise {@code null}
     * @param defaultOperatorFactionId preserved default operator identity
     * @param productionAuthority immutable composition/production seam
     * @param operatingAuthority authoritative runtime seam
     * @param supportAuthority supporting logistics/extraction seam
     * @param legalProductionPath whether this row currently closes a real production path
     */
    public record CivilianAvailability(
            CivilianRole role,
            AssetPathKind pathKind,
            String assetRef,
            String sourcePackageKey,
            String productionManifestId,
            String defaultOperatorFactionId,
            String productionAuthority,
            String operatingAuthority,
            String supportAuthority,
            boolean legalProductionPath) {
        /**
         * Validates and normalizes one civilian availability contract.
         *
         * @param role shared civilian role
         * @param pathKind provenance of the asset path
         * @param assetRef concrete legacy archetype or production fit ID
         * @param sourcePackageKey core package key for licensed fits, otherwise {@code null}
         * @param productionManifestId exact core production manifest for licensed fits, otherwise {@code null}
         * @param defaultOperatorFactionId preserved default operator identity
         * @param productionAuthority immutable composition/production seam
         * @param operatingAuthority authoritative runtime seam
         * @param supportAuthority supporting logistics/extraction seam
         * @param legalProductionPath whether this row currently closes a real production path
         */
        public CivilianAvailability {
            role = Objects.requireNonNull(role, "civilian role");
            pathKind = Objects.requireNonNull(pathKind, "asset path kind");
            assetRef = requireText(assetRef, "assetRef");
            defaultOperatorFactionId = requireFactionId(defaultOperatorFactionId);
            productionAuthority = requireText(productionAuthority, "productionAuthority");
            operatingAuthority = requireText(operatingAuthority, "operatingAuthority");
            supportAuthority = requireText(supportAuthority, "supportAuthority");
            if (pathKind == AssetPathKind.LICENSED_CORE_FIT) {
                sourcePackageKey = requireText(sourcePackageKey, "sourcePackageKey");
                productionManifestId = requireText(productionManifestId, "productionManifestId");
                if (!legalProductionPath) {
                    throw new IllegalArgumentException("Licensed core asset must bind a legal production path");
                }
            } else if (sourcePackageKey != null || productionManifestId != null) {
                throw new IllegalArgumentException("Legacy archetype cannot masquerade as a core production manifest");
            }
        }
    }

    /**
     * One preserved minor identity and its deterministic provenance/migration policy.
     *
     * @param stableFactionId stable runtime/save ID
     * @param provenance source/provenance statement
     * @param spawnPolicy deterministic spawn/provenance rule
     * @param preserveStableId whether save/load must preserve this ID exactly
     * @param majorPackageFallbackAllowed must remain false for M22.5 actors
     */
    public record MinorActorPolicy(
            String stableFactionId,
            String provenance,
            String spawnPolicy,
            boolean preserveStableId,
            boolean majorPackageFallbackAllowed) {
        /**
         * Validates stable identity preservation and rejects major-package fallback.
         *
         * @param stableFactionId stable runtime/save ID
         * @param provenance source/provenance statement
         * @param spawnPolicy deterministic spawn/provenance rule
         * @param preserveStableId whether save/load must preserve this ID exactly
         * @param majorPackageFallbackAllowed must remain false for M22.5 actors
         */
        public MinorActorPolicy {
            stableFactionId = requireFactionId(stableFactionId);
            provenance = requireText(provenance, "provenance");
            spawnPolicy = requireText(spawnPolicy, "spawnPolicy");
            if (!preserveStableId) {
                throw new IllegalArgumentException("M22.5 preserved minor actors must keep stable IDs");
            }
            if (majorPackageFallbackAllowed) {
                throw new IllegalArgumentException("Minor actors cannot inherit a major-faction package by fallback");
            }
        }
    }

    /**
     * One neutral/minor service provider bound to canonical authority seams.
     *
     * @param providerRef existing station archetype reference
     * @param ownerFactionId stable owner identity
     * @param serviceKind concise provider purpose
     * @param ownershipAuthority canonical territorial/ownership authority
     * @param accessAuthority canonical market-access authority
     * @param tariffAuthority canonical customs/tariff authority
     * @param logisticsAuthority canonical logistics authority
     */
    public record ServiceProviderPolicy(
            String providerRef,
            String ownerFactionId,
            String serviceKind,
            String ownershipAuthority,
            String accessAuthority,
            String tariffAuthority,
            String logisticsAuthority) {
        /**
         * Validates one service-provider policy and its authority references.
         *
         * @param providerRef existing station archetype reference
         * @param ownerFactionId stable owner identity
         * @param serviceKind concise provider purpose
         * @param ownershipAuthority canonical territorial/ownership authority
         * @param accessAuthority canonical market-access authority
         * @param tariffAuthority canonical customs/tariff authority
         * @param logisticsAuthority canonical logistics authority
         */
        public ServiceProviderPolicy {
            providerRef = requireText(providerRef, "providerRef");
            ownerFactionId = requireFactionId(ownerFactionId);
            serviceKind = requireText(serviceKind, "serviceKind");
            ownershipAuthority = requireText(ownershipAuthority, "ownershipAuthority");
            accessAuthority = requireText(accessAuthority, "accessAuthority");
            tariffAuthority = requireText(tariffAuthority, "tariffAuthority");
            logisticsAuthority = requireText(logisticsAuthority, "logisticsAuthority");
        }
    }

    /**
     * One integration hook. Deferred hooks are deliberately non-authoritative placeholders for later
     * content and may not name a mutable authority that does not exist yet.
     *
     * @param kind integration category
     * @param authorityRef existing authority class, or {@code null} only for deferred hooks
     * @param deferred whether implementation remains intentionally deferred
     * @param semanticIntent bounded purpose of the integration
     */
    public record EcosystemHook(HookKind kind, String authorityRef, boolean deferred, String semanticIntent) {
        /**
         * Validates one integration hook without inventing a deferred mutable authority.
         *
         * @param kind integration category
         * @param authorityRef existing authority class, or {@code null} only for deferred hooks
         * @param deferred whether implementation remains intentionally deferred
         * @param semanticIntent bounded purpose of the integration
         */
        public EcosystemHook {
            kind = Objects.requireNonNull(kind, "hook kind");
            semanticIntent = requireText(semanticIntent, "semanticIntent");
            if (deferred) {
                if (authorityRef != null) {
                    throw new IllegalArgumentException("Deferred hook must not invent an authority reference");
                }
            } else {
                authorityRef = requireText(authorityRef, "authorityRef");
            }
        }
    }

    /**
     * One M22.5 balance-scenario binding to already-existing authorities and core-pair mission content.
     *
     * @param scenarioId canonical balance-scenario identifier
     * @param primaryAuthority existing runtime authority exercised by the scenario
     * @param requiredCoreMissionIds exactly two core-pair mission identifiers
     * @param semanticIntent expected scenario consequence without introducing a parallel authority
     */
    public record ScenarioBinding(
            String scenarioId,
            String primaryAuthority,
            List<String> requiredCoreMissionIds,
            String semanticIntent) {
        /**
         * Validates deterministic two-faction scenario binding metadata.
         *
         * @param scenarioId canonical balance-scenario identifier
         * @param primaryAuthority existing runtime authority exercised by the scenario
         * @param requiredCoreMissionIds exactly two core-pair mission identifiers
         * @param semanticIntent expected scenario consequence without introducing a parallel authority
         */
        public ScenarioBinding {
            scenarioId = requireText(scenarioId, "scenarioId");
            primaryAuthority = requireText(primaryAuthority, "primaryAuthority");
            ArrayList<String> missions = new ArrayList<>(Objects.requireNonNull(requiredCoreMissionIds, "requiredCoreMissionIds"));
            missions.replaceAll(value -> requireText(value, "requiredCoreMissionId"));
            if (missions.size() != 2) {
                throw new IllegalArgumentException("M22.5 core-pair scenario binding requires exactly two mission IDs");
            }
            missions.sort(String::compareTo);
            requiredCoreMissionIds = List.copyOf(missions);
            semanticIntent = requireText(semanticIntent, "semanticIntent");
        }
    }

    private final List<CivilianAvailability> civilianAvailability;
    private final List<MinorActorPolicy> minorActors;
    private final List<ServiceProviderPolicy> serviceProviders;
    private final List<EcosystemHook> hooks;
    private final List<ScenarioBinding> scenarioBindings;
    private final Map<CivilianRole, CivilianAvailability> availabilityByRole;
    private final Map<String, MinorActorPolicy> minorById;
    private final Map<String, ScenarioBinding> scenariosById;
    private final String fingerprint;

    private Stage22CivilianMinorEcosystemCatalog(
            List<CivilianAvailability> civilianAvailability,
            List<MinorActorPolicy> minorActors,
            List<ServiceProviderPolicy> serviceProviders,
            List<EcosystemHook> hooks,
            List<ScenarioBinding> scenarioBindings) {
        ArrayList<CivilianAvailability> availabilityCopy = new ArrayList<>(Objects.requireNonNull(civilianAvailability, "civilianAvailability"));
        availabilityCopy.sort(Comparator.comparing(value -> value.role().name()));
        this.civilianAvailability = List.copyOf(availabilityCopy);

        ArrayList<MinorActorPolicy> actorCopy = new ArrayList<>(Objects.requireNonNull(minorActors, "minorActors"));
        actorCopy.sort(Comparator.comparing(MinorActorPolicy::stableFactionId));
        this.minorActors = List.copyOf(actorCopy);

        ArrayList<ServiceProviderPolicy> providerCopy = new ArrayList<>(Objects.requireNonNull(serviceProviders, "serviceProviders"));
        providerCopy.sort(Comparator.comparing(ServiceProviderPolicy::providerRef));
        this.serviceProviders = List.copyOf(providerCopy);

        ArrayList<EcosystemHook> hookCopy = new ArrayList<>(Objects.requireNonNull(hooks, "hooks"));
        hookCopy.sort(Comparator.comparing(value -> value.kind().name()));
        this.hooks = List.copyOf(hookCopy);

        ArrayList<ScenarioBinding> scenarioCopy = new ArrayList<>(Objects.requireNonNull(scenarioBindings, "scenarioBindings"));
        scenarioCopy.sort(Comparator.comparing(ScenarioBinding::scenarioId));
        this.scenarioBindings = List.copyOf(scenarioCopy);

        EnumMap<CivilianRole, CivilianAvailability> byRole = new EnumMap<>(CivilianRole.class);
        for (CivilianAvailability value : this.civilianAvailability) {
            if (byRole.putIfAbsent(value.role(), value) != null) {
                throw new IllegalArgumentException("Duplicate civilian role: " + value.role());
            }
        }
        if (byRole.size() != CivilianRole.values().length) {
            throw new IllegalArgumentException("Every M22.5 civilian role must have an availability path");
        }
        this.availabilityByRole = Map.copyOf(byRole);

        LinkedHashMap<String, MinorActorPolicy> actors = new LinkedHashMap<>();
        for (MinorActorPolicy actor : this.minorActors) {
            if (actors.putIfAbsent(actor.stableFactionId(), actor) != null) {
                throw new IllegalArgumentException("Duplicate minor actor: " + actor.stableFactionId());
            }
        }
        this.minorById = Map.copyOf(actors);

        LinkedHashMap<String, ScenarioBinding> scenarios = new LinkedHashMap<>();
        for (ScenarioBinding scenario : this.scenarioBindings) {
            if (scenarios.putIfAbsent(scenario.scenarioId(), scenario) != null) {
                throw new IllegalArgumentException("Duplicate scenario binding: " + scenario.scenarioId());
            }
        }
        this.scenariosById = Map.copyOf(scenarios);
        this.fingerprint = computeFingerprint();
    }

    /**
     * Builds the canonical in-progress M22.5 contract.
     *
     * @return immutable governed civilian/minor ecosystem catalog
     */
    public static Stage22CivilianMinorEcosystemCatalog loadDefault() {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Stage22CivilianMinorEcosystemCatalog catalog = new Stage22CivilianMinorEcosystemCatalog(
                List.of(
                        new CivilianAvailability(
                                CivilianRole.FREIGHT,
                                AssetPathKind.LICENSED_CORE_FIT,
                                "fit.industrial_union.freight.bulk_v1",
                                Stage22IndustrialUnionPackageCatalog.PACKAGE_KEY,
                                "production_manifest.industrial_union.freight_v1",
                                "faction.trade_league",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.trade.InterSystemTradeService",
                                "com.spacesim.economy.Stage18LogisticsRuntime",
                                true),
                        new CivilianAvailability(
                                CivilianRole.TANKER,
                                AssetPathKind.LICENSED_CORE_FIT,
                                "fit.empire.tanker.fleet_v1",
                                Stage22EmpirePackageCatalog.PACKAGE_KEY,
                                "production_manifest.empire.tanker_v1",
                                "faction.neutral",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.trade.InterSystemTradeService",
                                "com.spacesim.economy.Stage18LogisticsRuntime",
                                true),
                        new CivilianAvailability(
                                CivilianRole.MINING,
                                AssetPathKind.LEGACY_RUNTIME_ARCHETYPE,
                                "ship.basic_miner",
                                null,
                                null,
                                "faction.miners",
                                "data/content/catalog-v1.json",
                                "com.spacesim.economy.Stage18ExtractionRuntime",
                                "extraction.asteroid_excavation",
                                false),
                        new CivilianAvailability(
                                CivilianRole.SALVAGE,
                                AssetPathKind.LICENSED_CORE_FIT,
                                "fit.industrial_union.fleet_support.salvage_refit_v1",
                                Stage22IndustrialUnionPackageCatalog.PACKAGE_KEY,
                                "production_manifest.industrial_union.fleet_support_v1",
                                "faction.miners",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.economy.Stage18SalvageRuntime",
                                "extraction.salvage_recovery",
                                true),
                        new CivilianAvailability(
                                CivilianRole.NEUTRAL_TRAFFIC,
                                AssetPathKind.LICENSED_CORE_FIT,
                                "fit.empire.freight.bulk_v1",
                                Stage22EmpirePackageCatalog.PACKAGE_KEY,
                                "production_manifest.empire.freight_v1",
                                "faction.neutral",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.world.generation.Stage21EGeneratedWorldTrafficRuntime",
                                "com.spacesim.economy.Stage18LogisticsRuntime",
                                true)),
                List.of(
                        new MinorActorPolicy(
                                "faction.neutral",
                                "legacy catalog-v1 neutral-services compatibility identity",
                                "spawn only from governed neutral service/traffic definitions; never sovereign-major fallback",
                                true,
                                false),
                        new MinorActorPolicy(
                                "faction.trade_league",
                                "legacy catalog-v1 trade-network compatibility identity",
                                "spawn from governed freight/market-provider definitions and lawful route availability",
                                true,
                                false),
                        new MinorActorPolicy(
                                "faction.miners",
                                "legacy catalog-v1 extraction-network compatibility identity",
                                "spawn from governed extraction/salvage definitions and available industrial support",
                                true,
                                false)),
                List.of(
                        new ServiceProviderPolicy(
                                "station.colony",
                                "faction.neutral",
                                "neutral market and logistics hub",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new ServiceProviderPolicy(
                                "station.agrodome",
                                "faction.trade_league",
                                "civilian trade and provisioning node",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new ServiceProviderPolicy(
                                "station.mining_base",
                                "faction.miners",
                                "extraction support and ore market node",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime")),
                List.of(
                        new EcosystemHook(
                                HookKind.TRADE,
                                "com.spacesim.trade.InterSystemTradeService",
                                false,
                                "Use ordinary finite freight and market flow; no civilian shadow ledger."),
                        new EcosystemHook(
                                HookKind.CONTRACT,
                                "com.spacesim.world.Stage21HNpcMissionService",
                                false,
                                "Use existing mission/contract objective authorities for civilian work requests."),
                        new EcosystemHook(
                                HookKind.INSURANCE,
                                null,
                                true,
                                "Reserve provenance/risk metadata only; M22.5 does not create an insurance treasury, debt model or post-core League mechanic.")),
                List.of(
                        new ScenarioBinding(
                                "B08",
                                "com.spacesim.world.Stage21EOperationTrafficPolicy",
                                List.of("mission.empire.convoy_guard", "mission.industrial_union.corridor_escort"),
                                "Convoy escort/interdiction must protect or threaten real civilian/logistics traffic through existing fleet/operation authority."),
                        new ScenarioBinding(
                                "B16",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                List.of("mission.empire.formal_market_access", "mission.industrial_union.access_contract"),
                                "Treaty/market-access shock must change legal access through existing diplomacy state rather than a scripted faction modifier.")));
        catalog.validateGovernance(governance);
        return catalog;
    }

    /** @return immutable civilian availability rows ordered by role */
    public List<CivilianAvailability> civilianAvailability() { return civilianAvailability; }

    /** @return immutable preserved minor-actor policies ordered by stable faction ID */
    public List<MinorActorPolicy> minorActors() { return minorActors; }

    /** @return immutable neutral/minor service-provider policies */
    public List<ServiceProviderPolicy> serviceProviders() { return serviceProviders; }

    /** @return immutable ecosystem integration hooks */
    public List<EcosystemHook> hooks() { return hooks; }

    /** @return immutable B08/B16 scenario bindings */
    public List<ScenarioBinding> scenarioBindings() { return scenarioBindings; }

    /** @return deterministic SHA-256 fingerprint of the complete M22.5 catalog */
    public String fingerprint() { return fingerprint; }

    /**
     * Resolves the availability contract for one civilian role.
     *
     * @param role civilian role to resolve
     * @return matching availability row, or {@code null} when absent
     */
    public CivilianAvailability availability(CivilianRole role) { return availabilityByRole.get(role); }

    /**
     * Resolves one preserved minor actor by stable faction ID.
     *
     * @param stableFactionId stable faction identity
     * @return matching actor policy, or {@code null} when absent
     */
    public MinorActorPolicy minorActor(String stableFactionId) { return minorById.get(stableFactionId); }

    /**
     * Resolves one representative balance scenario.
     *
     * @param scenarioId canonical scenario identifier
     * @return matching scenario binding, or {@code null} when absent
     */
    public ScenarioBinding scenario(String scenarioId) { return scenariosById.get(scenarioId); }

    /** @return deterministic list of roles whose physical production path is not closed yet */
    public List<CivilianRole> unresolvedProductionRoles() {
        ArrayList<CivilianRole> unresolved = new ArrayList<>();
        for (CivilianAvailability value : civilianAvailability) {
            if (!value.legalProductionPath()) {
                unresolved.add(value.role());
            }
        }
        unresolved.sort(Comparator.comparing(Enum::name));
        return List.copyOf(unresolved);
    }

    private void validateGovernance(Stage22ContentGovernanceCatalog governance) {
        for (MinorActorPolicy actor : minorActors) {
            Stage22ContentGovernanceCatalog.FactionIdentityDefinition identity = governance.findFactionIdentity(actor.stableFactionId());
            if (identity == null) {
                throw new IllegalStateException("M22.5 minor actor lacks Stage-22 governance: " + actor.stableFactionId());
            }
            if (identity.canonicalPackageKey() != null) {
                throw new IllegalStateException("M22.5 minor actor cannot bind a major package: " + actor.stableFactionId());
            }
            if (identity.disposition() != Stage22ContentGovernanceCatalog.IdentityDisposition.PRESERVE) {
                throw new IllegalStateException("M22.5 compatibility actor must preserve its stable ID: " + actor.stableFactionId());
            }
        }
        for (CivilianAvailability value : civilianAvailability) {
            if (!minorById.containsKey(value.defaultOperatorFactionId())) {
                throw new IllegalStateException("Civilian role uses an ungoverned M22.5 operator: " + value.role());
            }
            if (value.sourcePackageKey() != null
                    && !Stage22EmpirePackageCatalog.PACKAGE_KEY.equals(value.sourcePackageKey())
                    && !Stage22IndustrialUnionPackageCatalog.PACKAGE_KEY.equals(value.sourcePackageKey())) {
                throw new IllegalStateException("Licensed civilian path references a non-core package: " + value.sourcePackageKey());
            }
        }
        for (ServiceProviderPolicy provider : serviceProviders) {
            if (!minorById.containsKey(provider.ownerFactionId())) {
                throw new IllegalStateException("Service provider owner is not a governed M22.5 minor: " + provider.providerRef());
            }
        }
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(8192);
        for (CivilianAvailability value : civilianAvailability) {
            canonical.append("availability|").append(value.role()).append('|').append(value.pathKind()).append('|')
                    .append(value.assetRef()).append('|').append(nullToEmpty(value.sourcePackageKey())).append('|')
                    .append(nullToEmpty(value.productionManifestId())).append('|').append(value.defaultOperatorFactionId()).append('|')
                    .append(value.productionAuthority()).append('|').append(value.operatingAuthority()).append('|')
                    .append(value.supportAuthority()).append('|').append(value.legalProductionPath()).append('\n');
        }
        for (MinorActorPolicy actor : minorActors) {
            canonical.append("minor|").append(actor.stableFactionId()).append('|').append(actor.provenance()).append('|')
                    .append(actor.spawnPolicy()).append('|').append(actor.preserveStableId()).append('|')
                    .append(actor.majorPackageFallbackAllowed()).append('\n');
        }
        for (ServiceProviderPolicy provider : serviceProviders) {
            canonical.append("provider|").append(provider.providerRef()).append('|').append(provider.ownerFactionId()).append('|')
                    .append(provider.serviceKind()).append('|').append(provider.ownershipAuthority()).append('|')
                    .append(provider.accessAuthority()).append('|').append(provider.tariffAuthority()).append('|')
                    .append(provider.logisticsAuthority()).append('\n');
        }
        for (EcosystemHook hook : hooks) {
            canonical.append("hook|").append(hook.kind()).append('|').append(nullToEmpty(hook.authorityRef())).append('|')
                    .append(hook.deferred()).append('|').append(hook.semanticIntent()).append('\n');
        }
        for (ScenarioBinding scenario : scenarioBindings) {
            canonical.append("scenario|").append(scenario.scenarioId()).append('|').append(scenario.primaryAuthority()).append('|')
                    .append(String.join(",", scenario.requiredCoreMissionIds())).append('|').append(scenario.semanticIntent()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }

    private static String requireFactionId(String value) {
        String checked = requireText(value, "stable faction ID");
        if (!checked.startsWith("faction.") || checked.length() <= "faction.".length()) {
            throw new IllegalArgumentException("Stable faction ID must use faction.* syntax: " + checked);
        }
        return checked;
    }
}
