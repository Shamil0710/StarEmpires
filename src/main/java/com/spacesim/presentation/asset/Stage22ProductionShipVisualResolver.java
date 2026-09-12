package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.Stage22EmpireProductionCatalogs;
import com.spacesim.content.Stage22FactionProfileCatalog;
import com.spacesim.content.Stage22FactionProfileCatalog.SystemicProfileDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.VisualProfileDefinition;
import com.spacesim.content.Stage22FactionProfileLoader;
import com.spacesim.content.Stage22FitFingerprint;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageLoader;
import com.spacesim.content.Stage22IndustrialUnionProductionCatalogs;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fail-closed M22.7C resolver for authored production ship visuals.
 *
 * <p>The resolver projects the accepted Stage-22 faction profile, authored package, exact engineering
 * fit and production visual binding into one immutable presentation result. It never mutates simulation
 * state and deliberately provides no Stage-20.5 sprite fallback: a production faction/role/fit with a
 * missing, stale or non-production exact binding is an explicit error. Broader faction-profile maturity
 * remains visible in the key and is intentionally separate from exact asset legality/human approval.</p>
 */
public final class Stage22ProductionShipVisualResolver {
    private static final Stage22FactionProfileCatalog FACTION_PROFILES = Stage22FactionProfileLoader.loadDefault();
    private static final Map<String, FactionVisualAuthority> AUTHORITIES = loadAuthorities();

    private Stage22ProductionShipVisualResolver() {
        throw new AssertionError("utility class");
    }

    /** Runtime presentation state included in the binding key even when the current base layer is shared. */
    public enum RuntimeVisualState {
        /** Ship is present without commanded thrust. */ IDLE,
        /** Ship is under ordinary commanded thrust. */ THRUSTING,
        /** Ship remains operational but has visible damage state. */ DAMAGED
    }

    /**
     * Complete deterministic key for one production visual decision.
     *
     * @param stableEntityId stable runtime/save identity of the presented ship or fleet
     * @param stableFactionId authoritative stable faction identity
     * @param systemicProfileId exact Stage-22 systemic faction profile
     * @param shipVisualProfileId exact faction ship visual profile
     * @param shipVisualProfileStatus broader governed profile maturity, distinct from exact binding approval
     * @param familyId authored Stage-22 ship family
     * @param roleId common Stage-22 role taxonomy ID
     * @param hullId exact hull belonging to the visual fit
     * @param fitId exact legal engineering fit represented by the asset
     * @param fitFingerprint exact semantic fit fingerprint pinned by the binding
     * @param visualBindingId exact Stage-22 production visual binding
     * @param packageFingerprint authored faction-package fingerprint
     * @param factionProfileCatalogFingerprint Stage-22 systemic-profile catalog fingerprint
     * @param runtimeState runtime presentation state
     */
    public record BindingKey(
            String stableEntityId,
            String stableFactionId,
            String systemicProfileId,
            String shipVisualProfileId,
            AssetStatus shipVisualProfileStatus,
            String familyId,
            String roleId,
            String hullId,
            String fitId,
            String fitFingerprint,
            String visualBindingId,
            String packageFingerprint,
            String factionProfileCatalogFingerprint,
            RuntimeVisualState runtimeState) {
        public BindingKey {
            stableEntityId = requireText(stableEntityId, "stableEntityId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            systemicProfileId = requireText(systemicProfileId, "systemicProfileId");
            shipVisualProfileId = requireText(shipVisualProfileId, "shipVisualProfileId");
            shipVisualProfileStatus = Objects.requireNonNull(shipVisualProfileStatus, "shipVisualProfileStatus");
            familyId = requireText(familyId, "familyId");
            roleId = requireText(roleId, "roleId");
            hullId = requireText(hullId, "hullId");
            fitId = requireText(fitId, "fitId");
            fitFingerprint = requireText(fitFingerprint, "fitFingerprint");
            visualBindingId = requireText(visualBindingId, "visualBindingId");
            packageFingerprint = requireText(packageFingerprint, "packageFingerprint");
            factionProfileCatalogFingerprint = requireText(
                    factionProfileCatalogFingerprint, "factionProfileCatalogFingerprint");
            runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
        }
    }

    /**
     * One validated production visual and its exact authored physical envelope.
     *
     * @param key complete deterministic binding key
     * @param assetRef classpath production PNG
     * @param authorityDocument canonical faction visual authority document
     * @param worldLengthM exact hull length from the faction engineering catalog
     * @param worldWidthM exact hull width from the faction engineering catalog
     */
    public record ResolvedVisual(
            BindingKey key,
            String assetRef,
            String authorityDocument,
            double worldLengthM,
            double worldWidthM) {
        public ResolvedVisual {
            key = Objects.requireNonNull(key, "key");
            assetRef = requireText(assetRef, "assetRef");
            authorityDocument = requireText(authorityDocument, "authorityDocument");
            requirePositiveFinite(worldLengthM, "worldLengthM");
            requirePositiveFinite(worldWidthM, "worldWidthM");
        }
    }

    /**
     * Resolves the authored primary fit for one common production role.
     *
     * @param stableEntityId stable runtime/save entity or fleet identity
     * @param stableFactionId authoritative stable faction identity
     * @param roleId common Stage-22 role ID
     * @param runtimeState current presentation state
     * @return fail-closed exact production visual
     */
    public static ResolvedVisual resolveRole(
            String stableEntityId,
            String stableFactionId,
            String roleId,
            RuntimeVisualState runtimeState) {
        FactionVisualAuthority authority = authority(stableFactionId);
        Family family = authority.familyByRole().get(requireText(roleId, "roleId"));
        if (family == null) {
            throw new IllegalArgumentException(
                    "production faction has no authored ship family for role: "
                            + stableFactionId + " -> " + roleId);
        }
        return resolve(authority, stableEntityId, family, family.primaryFitId(), runtimeState);
    }

    /**
     * Resolves one exact legal primary/refit definition without role fallback.
     *
     * @param stableEntityId stable runtime/save entity or fleet identity
     * @param stableFactionId authoritative stable faction identity
     * @param fitId exact Stage-22 faction fit ID
     * @param runtimeState current presentation state
     * @return fail-closed exact production visual
     */
    public static ResolvedVisual resolveExactFit(
            String stableEntityId,
            String stableFactionId,
            String fitId,
            RuntimeVisualState runtimeState) {
        FactionVisualAuthority authority = authority(stableFactionId);
        String checkedFit = requireText(fitId, "fitId");
        Family family = authority.familyByFit().get(checkedFit);
        if (family == null) {
            throw new IllegalArgumentException(
                    "production faction has no authored visual family for fit: "
                            + stableFactionId + " -> " + checkedFit);
        }
        return resolve(authority, stableEntityId, family, checkedFit, runtimeState);
    }

    /**
     * Resolves one installed engineering fit by semantic equality against the governed faction catalog.
     * Surfaces that already hold authoritative {@link InstalledFit} state therefore do not need their
     * own string-ID lookup or role heuristic.
     *
     * @param stableEntityId stable runtime/save entity or fleet identity
     * @param stableFactionId authoritative stable faction identity
     * @param installedFit exact installed engineering payload
     * @param runtimeState current presentation state
     * @return fail-closed exact production visual
     */
    public static ResolvedVisual resolveInstalledFit(
            String stableEntityId,
            String stableFactionId,
            InstalledFit installedFit,
            RuntimeVisualState runtimeState) {
        FactionVisualAuthority authority = authority(stableFactionId);
        InstalledFit checked = Objects.requireNonNull(installedFit, "installedFit");
        List<String> matches = authority.engineering().getDemonstratorFits().stream()
                .filter(definition -> InstalledFit.fromDemonstrator(definition).equals(checked))
                .map(DemonstratorFitDefinition::id)
                .filter(authority.familyByFit()::containsKey)
                .sorted()
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                    "installed fit must match exactly one governed production definition for "
                            + stableFactionId + ": matches=" + matches);
        }
        String fitId = matches.get(0);
        return resolve(
                authority,
                stableEntityId,
                authority.familyByFit().get(fitId),
                fitId,
                runtimeState);
    }

    private static ResolvedVisual resolve(
            FactionVisualAuthority authority,
            String stableEntityId,
            Family family,
            String fitId,
            RuntimeVisualState runtimeState) {
        VisualBindingDefinition binding = authority.visualByFit().get(fitId);
        if (binding == null) {
            throw new IllegalStateException("missing production visual binding for exact fit: " + fitId);
        }
        if (binding.status() != AssetStatus.PRODUCTION) {
            throw new IllegalStateException("ship visual is not production-approved: " + binding.id());
        }
        String actualFingerprint = Stage22FitFingerprint.compute(authority.engineering(), fitId);
        if (!actualFingerprint.equals(binding.expectedFitFingerprint())) {
            throw new IllegalStateException(
                    "stale production visual fingerprint: " + binding.id()
                            + " expected=" + binding.expectedFitFingerprint()
                            + " actual=" + actualFingerprint);
        }
        DemonstratorFitDefinition fit = authority.engineering().findDemonstratorFit(fitId);
        if (fit == null) {
            throw new IllegalStateException("production visual references absent engineering fit: " + fitId);
        }
        HullDefinition hull = authority.engineering().findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalStateException("production visual fit references absent hull: " + fit.hullId());
        }
        URL asset = Stage22ProductionShipVisualResolver.class.getClassLoader().getResource(binding.assetRef());
        if (asset == null) {
            throw new IllegalStateException("production visual asset is missing: " + binding.assetRef());
        }

        SystemicProfileDefinition systemic = Objects.requireNonNull(
                FACTION_PROFILES.findProfileForFaction(authority.stableFactionId()),
                "systemic profile for " + authority.stableFactionId());
        if (!systemic.packageKey().equals(authority.packageKey())) {
            throw new IllegalStateException("production visual package/profile mismatch: " + authority.stableFactionId());
        }
        VisualProfileDefinition visualProfile = Objects.requireNonNull(
                FACTION_PROFILES.findVisual(systemic.shipVisualProfileRef()),
                "ship visual profile " + systemic.shipVisualProfileRef());
        if (visualProfile.kind() != Stage22FactionProfileCatalog.VisualKind.SHIP
                || !visualProfile.packageKey().equals(authority.packageKey())) {
            throw new IllegalStateException("invalid production ship visual profile: " + visualProfile.id());
        }

        BindingKey key = new BindingKey(
                stableEntityId,
                authority.stableFactionId(),
                systemic.profileId(),
                visualProfile.id(),
                visualProfile.status(),
                family.familyId(),
                family.roleId(),
                hull.id(),
                fit.id(),
                actualFingerprint,
                binding.id(),
                authority.packageFingerprint(),
                FACTION_PROFILES.fingerprint(),
                runtimeState);
        return new ResolvedVisual(
                key,
                binding.assetRef(),
                visualProfile.authorityDocument(),
                hull.boundingDimensionsM().lengthM(),
                hull.boundingDimensionsM().widthM());
    }

    private static FactionVisualAuthority authority(String stableFactionId) {
        String id = requireText(stableFactionId, "stableFactionId");
        FactionVisualAuthority authority = AUTHORITIES.get(id);
        if (authority == null) {
            throw new IllegalArgumentException(
                    "no Stage-22 production ship visual authority for faction: " + id);
        }
        return authority;
    }

    private static Map<String, FactionVisualAuthority> loadAuthorities() {
        LinkedHashMap<String, FactionVisualAuthority> result = new LinkedHashMap<>();

        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog empireEngineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        putAuthority(result, new FactionVisualAuthority(
                empire.stableFactionId(),
                empire.packageKey(),
                empire.fingerprint(),
                empireEngineering,
                empire.shipFamilies().stream().map(value -> new Family(
                        value.familyId(), value.roleId(), value.primaryFitId(), value.refitFitId())).toList(),
                Stage22EmpireProductionCatalogs.loadVisualBindings()));

        Stage22IndustrialUnionPackageCatalog union = Stage22IndustrialUnionPackageLoader.loadDefault();
        ShipEngineeringCatalog unionEngineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        putAuthority(result, new FactionVisualAuthority(
                union.stableFactionId(),
                union.packageKey(),
                union.fingerprint(),
                unionEngineering,
                union.shipFamilies().stream().map(value -> new Family(
                        value.familyId(), value.roleId(), value.primaryFitId(), value.refitFitId())).toList(),
                Stage22IndustrialUnionProductionCatalogs.loadVisualBindings()));

        return Map.copyOf(result);
    }

    private static void putAuthority(
            Map<String, FactionVisualAuthority> authorities,
            FactionVisualAuthority authority) {
        if (authorities.putIfAbsent(authority.stableFactionId(), authority) != null) {
            throw new IllegalStateException("duplicate production visual faction: " + authority.stableFactionId());
        }
    }

    private record Family(String familyId, String roleId, String primaryFitId, String refitFitId) {
        private Family {
            familyId = requireText(familyId, "familyId");
            roleId = requireText(roleId, "roleId");
            primaryFitId = requireText(primaryFitId, "primaryFitId");
            refitFitId = requireText(refitFitId, "refitFitId");
            if (primaryFitId.equals(refitFitId)) {
                throw new IllegalArgumentException("production family primary/refit fit must differ: " + familyId);
            }
        }
    }

    private record FactionVisualAuthority(
            String stableFactionId,
            String packageKey,
            String packageFingerprint,
            ShipEngineeringCatalog engineering,
            Map<String, Family> familyByRole,
            Map<String, Family> familyByFit,
            Map<String, VisualBindingDefinition> visualByFit) {
        private FactionVisualAuthority(
                String stableFactionId,
                String packageKey,
                String packageFingerprint,
                ShipEngineeringCatalog engineering,
                List<Family> families,
                List<VisualBindingDefinition> visuals) {
            this(
                    requireText(stableFactionId, "stableFactionId"),
                    requireText(packageKey, "packageKey"),
                    requireText(packageFingerprint, "packageFingerprint"),
                    Objects.requireNonNull(engineering, "engineering"),
                    indexFamiliesByRole(families),
                    indexFamiliesByFit(families),
                    indexVisuals(visuals));
        }
    }

    private static Map<String, Family> indexFamiliesByRole(List<Family> families) {
        LinkedHashMap<String, Family> result = new LinkedHashMap<>();
        for (Family family : Objects.requireNonNull(families, "families")) {
            if (result.putIfAbsent(family.roleId(), family) != null) {
                throw new IllegalStateException("duplicate production role: " + family.roleId());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Family> indexFamiliesByFit(List<Family> families) {
        LinkedHashMap<String, Family> result = new LinkedHashMap<>();
        for (Family family : Objects.requireNonNull(families, "families")) {
            if (result.putIfAbsent(family.primaryFitId(), family) != null
                    || result.putIfAbsent(family.refitFitId(), family) != null) {
                throw new IllegalStateException("duplicate production family fit: " + family.familyId());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, VisualBindingDefinition> indexVisuals(List<VisualBindingDefinition> visuals) {
        LinkedHashMap<String, VisualBindingDefinition> result = new LinkedHashMap<>();
        for (VisualBindingDefinition visual : Objects.requireNonNull(visuals, "visuals")) {
            if (result.putIfAbsent(visual.fitId(), visual) != null) {
                throw new IllegalStateException("duplicate production visual fit: " + visual.fitId());
            }
        }
        return Map.copyOf(result);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be positive and finite");
        }
    }
}
