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
 * <p>This class owns no mutable economy, diplomacy, ownership, logistics, extraction or fleet state.
 * It only binds Stage-22 civilian/minor content to authorities implemented by earlier stages and
 * validates that compatibility identities cannot silently inherit a major-faction package.</p>
 */
public final class Stage22CivilianMinorEcosystemCatalog {
    /** Shared civilian roles required by the M22.5 alpha slice. */
    public enum CivilianRole {
        /** Inter-system bulk freight. */
        FREIGHT,
        /** Fuel/consumables replenishment traffic. */
        TANKER,
        /** Resource extraction traffic. */
        MINING,
        /** Wreck recovery and salvage traffic. */
        SALVAGE,
        /** Low-value neutral/local traffic. */
        NEUTRAL_TRAFFIC
    }

    /**
     * One legal civilian availability path.
     *
     * @param role shared civilian role
     * @param contentRef existing archetype/role reference
     * @param defaultOperatorFactionId preserved default operator identity
     * @param productionAuthority authoritative construction/manufacturing seam
     * @param operatingAuthority authoritative runtime seam
     * @param supportAuthority supporting logistics/extraction seam
     */
    public record CivilianAvailability(
            CivilianRole role,
            String contentRef,
            String defaultOperatorFactionId,
            String productionAuthority,
            String operatingAuthority,
            String supportAuthority) {
        /** Validates one immutable availability row. */
        public CivilianAvailability {
            role = Objects.requireNonNull(role, "civilian role");
            contentRef = requireText(contentRef, "contentRef");
            defaultOperatorFactionId = requireFactionId(defaultOperatorFactionId);
            productionAuthority = requireText(productionAuthority, "productionAuthority");
            operatingAuthority = requireText(operatingAuthority, "operatingAuthority");
            supportAuthority = requireText(supportAuthority, "supportAuthority");
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
        /** Validates one minor-actor policy. */
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
     * @param providerRef existing station/service reference
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
        /** Validates one service-provider binding. */
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

    private final List<CivilianAvailability> civilianAvailability;
    private final List<MinorActorPolicy> minorActors;
    private final List<ServiceProviderPolicy> serviceProviders;
    private final Map<CivilianRole, CivilianAvailability> availabilityByRole;
    private final Map<String, MinorActorPolicy> minorById;
    private final String fingerprint;

    private Stage22CivilianMinorEcosystemCatalog(
            List<CivilianAvailability> civilianAvailability,
            List<MinorActorPolicy> minorActors,
            List<ServiceProviderPolicy> serviceProviders) {
        ArrayList<CivilianAvailability> availabilityCopy = new ArrayList<>(
                Objects.requireNonNull(civilianAvailability, "civilianAvailability"));
        availabilityCopy.sort(Comparator.comparing(value -> value.role().name()));
        this.civilianAvailability = List.copyOf(availabilityCopy);

        ArrayList<MinorActorPolicy> actorCopy = new ArrayList<>(Objects.requireNonNull(minorActors, "minorActors"));
        actorCopy.sort(Comparator.comparing(MinorActorPolicy::stableFactionId));
        this.minorActors = List.copyOf(actorCopy);

        ArrayList<ServiceProviderPolicy> providerCopy = new ArrayList<>(
                Objects.requireNonNull(serviceProviders, "serviceProviders"));
        providerCopy.sort(Comparator.comparing(ServiceProviderPolicy::providerRef));
        this.serviceProviders = List.copyOf(providerCopy);

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
        this.fingerprint = computeFingerprint();
    }

    /**
     * Builds and validates the canonical Stage-22.5 package against Stage-22.0 identity governance.
     *
     * @return immutable canonical package
     */
    public static Stage22CivilianMinorEcosystemCatalog loadDefault() {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Stage22CivilianMinorEcosystemCatalog catalog = new Stage22CivilianMinorEcosystemCatalog(
                List.of(
                        new CivilianAvailability(
                                CivilianRole.FREIGHT,
                                "bulk_freighter",
                                "faction.trade_league",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.trade.InterSystemTradeService",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new CivilianAvailability(
                                CivilianRole.TANKER,
                                "role.support.tanker_replenishment",
                                "faction.neutral",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.trade.InterSystemTradeService",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new CivilianAvailability(
                                CivilianRole.MINING,
                                "miner",
                                "faction.miners",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.economy.Stage18ExtractionRuntime",
                                "extraction.asteroid_excavation"),
                        new CivilianAvailability(
                                CivilianRole.SALVAGE,
                                "salvage_tug",
                                "faction.miners",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.economy.Stage18SalvageRuntime",
                                "extraction.salvage_recovery"),
                        new CivilianAvailability(
                                CivilianRole.NEUTRAL_TRAFFIC,
                                "cargo_lugger",
                                "faction.neutral",
                                "com.spacesim.content.Stage22AuthoredProductionBridge",
                                "com.spacesim.trade.InterSystemTradeService",
                                "com.spacesim.world.generation.Stage21EGeneratedWorldTrafficRuntime")),
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
                                "hub-neutral-alpha",
                                "faction.neutral",
                                "neutral market and service hub",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new ServiceProviderPolicy(
                                "exchange-trade-lane",
                                "faction.trade_league",
                                "civilian trade exchange",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime"),
                        new ServiceProviderPolicy(
                                "refinery-belt-3",
                                "faction.miners",
                                "extraction and refining support hub",
                                "com.spacesim.world.FactionTerritoryService",
                                "com.spacesim.world.DiplomaticMarketAccessResolver",
                                "com.spacesim.world.CustomsTariffResolver",
                                "com.spacesim.economy.Stage18LogisticsRuntime")));
        catalog.validateGovernance(governance);
        return catalog;
    }

    /** @return deterministic civilian availability rows */
    public List<CivilianAvailability> civilianAvailability() { return civilianAvailability; }

    /** @return deterministic preserved minor actor policies */
    public List<MinorActorPolicy> minorActors() { return minorActors; }

    /** @return deterministic neutral/minor provider policies */
    public List<ServiceProviderPolicy> serviceProviders() { return serviceProviders; }

    /** @return deterministic SHA-256 fingerprint of the package */
    public String fingerprint() { return fingerprint; }

    /** @return availability for a required role */
    public CivilianAvailability availability(CivilianRole role) { return availabilityByRole.get(role); }

    /** @return minor policy for a stable ID, or {@code null} */
    public MinorActorPolicy minorActor(String stableFactionId) { return minorById.get(stableFactionId); }

    private void validateGovernance(Stage22ContentGovernanceCatalog governance) {
        for (MinorActorPolicy actor : minorActors) {
            Stage22ContentGovernanceCatalog.FactionIdentityDefinition identity =
                    governance.findFactionIdentity(actor.stableFactionId());
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
        }
        for (ServiceProviderPolicy provider : serviceProviders) {
            if (!minorById.containsKey(provider.ownerFactionId())) {
                throw new IllegalStateException("Service provider owner is not a governed M22.5 minor: " + provider.providerRef());
            }
        }
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(4096);
        for (CivilianAvailability value : civilianAvailability) {
            canonical.append("availability|").append(value.role()).append('|').append(value.contentRef()).append('|')
                    .append(value.defaultOperatorFactionId()).append('|').append(value.productionAuthority()).append('|')
                    .append(value.operatingAuthority()).append('|').append(value.supportAuthority()).append('\n');
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
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

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
