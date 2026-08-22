package com.spacesim.presentation.asset;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.model.ShipType;
import com.spacesim.ui.ShipVisualRole;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Stage-20.5E binding from stable content roles to the minimum playable sprite pack.
 *
 * <p>The catalogue is presentation-only. It cannot mutate entity state and its asset IDs, paths,
 * atlas regions, pivots or nominal fallback scale never become simulation identity, collision,
 * fitting, sensor or economy authority. Exact production hull/station bindings consume their
 * existing physical dimensions; role fallbacks retain an explicit non-authoritative scale.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20MinimumPlayableSpriteCatalog {
    /** Stable sprite-pack and binding contract. */
    public static final String CURRENT_VERSION = "stage20_5.minimum-playable-sprite-pack.v1";
    /** Explicit fallback law for content outside the accepted minimum identity set. */
    public static final String FALLBACK_VERSION = "stage20_5.sprite-role-fallback.v1";
    /** Physical-to-authored-sprite hardpoint axis convention. */
    public static final String HARDPOINT_AXIS_VERSION =
            "stage20_5.physical-plus-y-to-sprite-plus-x.v1";

    private static final String ROOT = "assets/stage20_5/";
    private static final double HARDPOINT_TOLERANCE = 1.0e-4d;

    /** Presentation roles covered by the minimum pack. */
    public enum VisualRole {
        /** Light player/service utility hull. */ UTILITY_SHIP,
        /** Physical cargo transport. */ CARGO_TRANSPORT_SHIP,
        /** Mining and industrial work hull. */ MINING_INDUSTRIAL_SHIP,
        /** Light combat/escort hull. */ LIGHT_COMBAT_ESCORT_SHIP,
        /** Medium combat hull. */ MEDIUM_COMBAT_SHIP,
        /** Generic station core and trade dock. */ TRADE_DOCK_STATION,
        /** Extraction, refinery and fabrication station. */ INDUSTRIAL_STATION,
        /** Major construction/shipyard station. */ SHIPYARD_STATION,
        /** Carbonaceous resource-body atlas region. */ RESOURCE_CARBONACEOUS,
        /** Water/ice resource-body atlas region. */ RESOURCE_WATER_ICE,
        /** Metallic resource-body atlas region. */ RESOURCE_METALLIC,
        /** Silicate/conductor/fissile resource-body atlas region. */ RESOURCE_MINERAL,
        /** Finite salvage derelict. */ DERELICT
    }

    /** Caller-visible fallback role for a ship whose exact content ID is outside the minimum set. */
    public enum ShipRole {
        /** General service/player craft. */ UTILITY,
        /** Cargo transport. */ CARGO_TRANSPORT,
        /** Mining/industrial craft. */ MINING_INDUSTRIAL,
        /** Light combat escort. */ LIGHT_COMBAT_ESCORT,
        /** Medium combat craft. */ MEDIUM_COMBAT
    }

    /** Why a resolved scale may be treated as exact or presentation-only. */
    public enum ScaleAuthority {
        /** Dimensions came from the matching ordinary hull/station physical authority. */ EXACT_PHYSICAL_CONTENT,
        /** Dimensions are a versioned readability fallback and must not feed simulation. */ PRESENTATION_FALLBACK
    }

    /**
     * Pixel region inside a texture, measured from the authored image's top-left corner.
     *
     * @param pixelX left coordinate
     * @param pixelY top coordinate
     * @param pixelWidth positive region width
     * @param pixelHeight positive region height
     */
    public record AtlasRegion(int pixelX, int pixelY, int pixelWidth, int pixelHeight) {
        /**
         * Validates a non-negative, positive-size atlas rectangle.
         *
         * @param pixelX left coordinate
         * @param pixelY top coordinate
         * @param pixelWidth positive region width
         * @param pixelHeight positive region height
         */
        public AtlasRegion {
            if (pixelX < 0 || pixelY < 0 || pixelWidth <= 0 || pixelHeight <= 0) {
                throw new IllegalArgumentException("sprite atlas region is invalid");
            }
        }

        /** @return full 768 by 512 sprite region */
        public static AtlasRegion fullLandscape() {
            return new AtlasRegion(0, 0, 768, 512);
        }

        /** @return full 640 by 640 sprite region */
        public static AtlasRegion fullSquare() {
            return new AtlasRegion(0, 0, 640, 640);
        }
    }

    /**
     * Immutable authored presentation asset metadata.
     *
     * @param assetId stable presentation identity, never simulation identity
     * @param role visual role
     * @param texturePath classpath PNG path
     * @param region authored atlas region
     * @param pivotX normalized horizontal pivot
     * @param pivotY normalized vertical pivot
     * @param sourceFacing authored facing convention
     * @param nominalLengthM non-authoritative fallback display length
     * @param nominalWidthM non-authoritative fallback display width
     * @param hardpoints stable presentation anchors
     */
    public record SpriteBinding(
            String assetId,
            VisualRole role,
            String texturePath,
            AtlasRegion region,
            float pivotX,
            float pivotY,
            SourceFacing sourceFacing,
            double nominalLengthM,
            double nominalWidthM,
            List<VisualHardpoint> hardpoints) {
        /**
         * Validates one immutable sprite binding.
         *
         * @param assetId stable presentation identity
         * @param role visual role
         * @param texturePath classpath PNG path
         * @param region authored atlas region
         * @param pivotX normalized horizontal pivot
         * @param pivotY normalized vertical pivot
         * @param sourceFacing authored facing convention
         * @param nominalLengthM positive fallback display length
         * @param nominalWidthM positive fallback display width
         * @param hardpoints presentation anchors
         */
        public SpriteBinding {
            assetId = requireText(assetId, "assetId");
            Objects.requireNonNull(role, "role");
            texturePath = requireText(texturePath, "texturePath");
            Objects.requireNonNull(region, "region");
            if (!Float.isFinite(pivotX) || !Float.isFinite(pivotY)
                    || pivotX < 0f || pivotX > 1f || pivotY < 0f || pivotY > 1f) {
                throw new IllegalArgumentException("sprite pivot must be finite and normalized");
            }
            Objects.requireNonNull(sourceFacing, "sourceFacing");
            requirePositive(nominalLengthM, "nominalLengthM");
            requirePositive(nominalWidthM, "nominalWidthM");
            hardpoints = List.copyOf(Objects.requireNonNull(hardpoints, "hardpoints"));
        }
    }

    /**
     * One resolved sprite plus the scale authority used by a renderer.
     *
     * @param binding immutable authored asset binding
     * @param worldLengthM exact physical length or fallback display length
     * @param worldWidthM exact physical width or fallback display width
     * @param scaleAuthority whether the scale is physical or presentation-only
     * @param authorityId exact source/fallback contract identifier
     */
    public record ResolvedSprite(
            SpriteBinding binding,
            double worldLengthM,
            double worldWidthM,
            ScaleAuthority scaleAuthority,
            String authorityId) {
        /**
         * Validates resolved immutable presentation metadata.
         *
         * @param binding immutable authored asset binding
         * @param worldLengthM positive length
         * @param worldWidthM positive width
         * @param scaleAuthority scale provenance kind
         * @param authorityId scale provenance identifier
         */
        public ResolvedSprite {
            Objects.requireNonNull(binding, "binding");
            requirePositive(worldLengthM, "worldLengthM");
            requirePositive(worldWidthM, "worldWidthM");
            Objects.requireNonNull(scaleAuthority, "scaleAuthority");
            authorityId = requireText(authorityId, "authorityId");
        }
    }

    private static final Map<VisualRole, SpriteBinding> BINDINGS = createBindings();
    private static final Map<String, ShipRole> EXACT_HULL_ROLES = Map.of(
            "hull.escort_destroyer_v1", ShipRole.LIGHT_COMBAT_ESCORT,
            "hull.test_bulk_freighter_v1", ShipRole.CARGO_TRANSPORT);
    private static final Map<String, VisualRole> STATION_ROLES = Map.ofEntries(
            Map.entry("station.infrastructure.mining_outpost", VisualRole.INDUSTRIAL_STATION),
            Map.entry("station.infrastructure.volatile_depot", VisualRole.INDUSTRIAL_STATION),
            Map.entry("station.infrastructure.refinery_complex", VisualRole.INDUSTRIAL_STATION),
            Map.entry("station.infrastructure.industrial_station", VisualRole.INDUSTRIAL_STATION),
            Map.entry("station.infrastructure.high_tech_hub", VisualRole.INDUSTRIAL_STATION),
            Map.entry("station.infrastructure.trade_logistics_hub", VisualRole.TRADE_DOCK_STATION),
            Map.entry("station.infrastructure.naval_ordnance_depot", VisualRole.TRADE_DOCK_STATION),
            Map.entry("station.infrastructure.frontier_multipurpose", VisualRole.TRADE_DOCK_STATION));
    private static final Map<String, VisualRole> OCCURRENCE_ROLES = Map.ofEntries(
            Map.entry("occurrence.carbonaceous", VisualRole.RESOURCE_CARBONACEOUS),
            Map.entry("occurrence.water_ice", VisualRole.RESOURCE_WATER_ICE),
            Map.entry("occurrence.volatiles", VisualRole.RESOURCE_WATER_ICE),
            Map.entry("occurrence.metallic", VisualRole.RESOURCE_METALLIC),
            Map.entry("occurrence.light_metals", VisualRole.RESOURCE_METALLIC),
            Map.entry("occurrence.strategic_metals", VisualRole.RESOURCE_METALLIC),
            Map.entry("occurrence.conductors", VisualRole.RESOURCE_MINERAL),
            Map.entry("occurrence.silicates", VisualRole.RESOURCE_MINERAL),
            Map.entry("occurrence.fissiles", VisualRole.RESOURCE_MINERAL));

    private Stage20MinimumPlayableSpriteCatalog() {
        throw new AssertionError("No instances");
    }

    /**
     * Resolves an exact accepted hull mapping or the caller's explicit visual-role fallback.
     *
     * @param hullId stable ordinary hull identity
     * @param fallbackRole explicit visual role used only when the hull is outside the minimum set
     * @param engineering catalog containing the authoritative hull definition when exact
     * @return immutable presentation resolution
     */
    public static ResolvedSprite resolveShip(
            String hullId,
            ShipRole fallbackRole,
            ShipEngineeringCatalog engineering) {
        String id = requireText(hullId, "hullId");
        ShipRole exactRole = EXACT_HULL_ROLES.get(id);
        ShipRole role = exactRole == null
                ? Objects.requireNonNull(fallbackRole, "fallbackRole")
                : exactRole;
        SpriteBinding binding = binding(shipVisualRole(role));
        HullDefinition hull = Objects.requireNonNull(engineering, "engineering").findHull(id);
        if (exactRole == null || hull == null) {
            return fallback(binding);
        }
        validateHardpointAlignment(hull, binding);
        return new ResolvedSprite(
                binding,
                hull.boundingDimensionsM().lengthM(),
                hull.boundingDimensionsM().widthM(),
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                engineering.getFingerprint() + '#' + id);
    }

    /**
     * Resolves an accepted station archetype, optionally selecting the installed shipyard role.
     *
     * @param stationArchetypeId stable Stage-18 station identity
     * @param shipyardInstalled ordinary runtime yard-presence state
     * @param geometry accepted Stage-20A physical station geometry
     * @return exact-scale station sprite resolution
     */
    public static ResolvedSprite resolveStation(
            String stationArchetypeId,
            boolean shipyardInstalled,
            Stage20StationPhysicalGeometryProfile geometry) {
        String id = requireText(stationArchetypeId, "stationArchetypeId");
        VisualRole mapped = STATION_ROLES.get(id);
        if (mapped == null) {
            return fallback(binding(shipyardInstalled
                    ? VisualRole.SHIPYARD_STATION
                    : VisualRole.TRADE_DOCK_STATION));
        }
        var design = Objects.requireNonNull(geometry, "geometry").stationDesign(id);
        SpriteBinding binding = binding(shipyardInstalled ? VisualRole.SHIPYARD_STATION : mapped);
        return new ResolvedSprite(
                binding,
                design.footprintLengthM(),
                design.footprintWidthM(),
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                geometry.version() + '#' + id);
    }

    /**
     * Resolves an accepted occurrence class and caller-supplied accepted physical footprint.
     *
     * @param occurrenceTypeId stable Stage-18 occurrence type
     * @param physicalLengthM accepted resource-body length
     * @param physicalWidthM accepted resource-body width
     * @return exact-scale resource sprite resolution
     */
    public static ResolvedSprite resolveResource(
            String occurrenceTypeId,
            double physicalLengthM,
            double physicalWidthM) {
        String id = requireText(occurrenceTypeId, "occurrenceTypeId");
        VisualRole role = OCCURRENCE_ROLES.get(id);
        if (role == null) {
            return fallback(binding(VisualRole.RESOURCE_MINERAL));
        }
        requirePositive(physicalLengthM, "physicalLengthM");
        requirePositive(physicalWidthM, "physicalWidthM");
        return new ResolvedSprite(
                binding(role),
                physicalLengthM,
                physicalWidthM,
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                "stage20e.accepted-resource-host#" + id);
    }

    /**
     * Resolves the Stage-20H derelict path and rejects non-derelict kinds.
     *
     * @param kind accepted special-location kind
     * @return derelict sprite resolution
     */
    public static ResolvedSprite resolveSpecialLocation(LocationKind kind) {
        if (Objects.requireNonNull(kind, "kind") != LocationKind.DERELICT) {
            throw new IllegalArgumentException("minimum special-location sprite is derelict-only");
        }
        SpriteBinding binding = binding(VisualRole.DERELICT);
        return new ResolvedSprite(
                binding,
                220d,
                72d,
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                "hull.escort_destroyer_v1#finite-stage20h-salvage");
    }

    /**
     * Resolves the existing playable-world ship role without changing its ECS component.
     *
     * @param type ordinary functional ship type, or null for legacy utility fallback
     * @return versioned presentation-only resolution
     */
    public static ResolvedSprite resolvePlayable(ShipType type) {
        ShipRole role;
        if (type == null) {
            role = ShipRole.UTILITY;
        } else if (type.isCarrier()) {
            role = ShipRole.CARGO_TRANSPORT;
        } else if (type.isMining()) {
            role = ShipRole.MINING_INDUSTRIAL;
        } else {
            role = ShipRole.LIGHT_COMBAT_ESCORT;
        }
        return fallback(binding(shipVisualRole(role)));
    }

    /**
     * Resolves a tactical-viewer role to the same minimum pack without altering combat state.
     *
     * @param role presentation role already derived from ordinary combat authority
     * @return versioned presentation-only resolution
     */
    public static ResolvedSprite resolveCombatRole(ShipVisualRole role) {
        VisualRole visual = switch (Objects.requireNonNull(role, "role")) {
            case KINETIC, MISSILE -> VisualRole.LIGHT_COMBAT_ESCORT_SHIP;
            case BEAM, BALANCED -> VisualRole.MEDIUM_COMBAT_SHIP;
            case DEFENSIVE_EW, UNCLASSIFIED -> VisualRole.UTILITY_SHIP;
        };
        return fallback(binding(visual));
    }

    /**
     * Returns an immutable binding by exact visual role.
     *
     * @param role minimum-pack visual role
     * @return immutable authored binding
     */
    public static SpriteBinding binding(VisualRole role) {
        SpriteBinding result = BINDINGS.get(Objects.requireNonNull(role, "role"));
        if (result == null) {
            throw new IllegalArgumentException("minimum sprite role is not bound: " + role);
        }
        return result;
    }

    /** @return deterministic immutable list of every role-complete binding */
    public static List<SpriteBinding> allBindings() {
        return BINDINGS.values().stream()
                .sorted(Comparator.comparing(value -> value.role().name()))
                .toList();
    }

    /** @return deterministic distinct classpath texture paths used by the minimum pack */
    public static List<String> allTexturePaths() {
        return allBindings().stream().map(SpriteBinding::texturePath).distinct().sorted().toList();
    }

    private static ResolvedSprite fallback(SpriteBinding binding) {
        return new ResolvedSprite(
                binding,
                binding.nominalLengthM(),
                binding.nominalWidthM(),
                ScaleAuthority.PRESENTATION_FALLBACK,
                FALLBACK_VERSION + '#' + binding.role().name());
    }

    private static VisualRole shipVisualRole(ShipRole role) {
        return switch (role) {
            case UTILITY -> VisualRole.UTILITY_SHIP;
            case CARGO_TRANSPORT -> VisualRole.CARGO_TRANSPORT_SHIP;
            case MINING_INDUSTRIAL -> VisualRole.MINING_INDUSTRIAL_SHIP;
            case LIGHT_COMBAT_ESCORT -> VisualRole.LIGHT_COMBAT_ESCORT_SHIP;
            case MEDIUM_COMBAT -> VisualRole.MEDIUM_COMBAT_SHIP;
        };
    }

    private static void validateHardpointAlignment(HullDefinition hull, SpriteBinding binding) {
        if (hull.hardpoints().size() != binding.hardpoints().size()) {
            throw new IllegalArgumentException("authored sprite hardpoint count differs from physical hull: " + hull.id());
        }
        for (HardpointDefinition physical : hull.hardpoints()) {
            VisualHardpoint visual = binding.hardpoints().stream()
                    .filter(value -> value.id().equals(physical.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "sprite lacks physical hardpoint: " + physical.id()));
            double expectedX = 0.5d + physical.positionM().yM()
                    / hull.boundingDimensionsM().lengthM();
            double expectedY = 0.5d + physical.positionM().xM()
                    / hull.boundingDimensionsM().widthM();
            if (Math.abs(expectedX - visual.normalizedX()) > HARDPOINT_TOLERANCE
                    || Math.abs(expectedY - visual.normalizedY()) > HARDPOINT_TOLERANCE) {
                throw new IllegalArgumentException(
                        "sprite hardpoint differs from physical hull axes: " + physical.id());
            }
        }
    }

    private static Map<VisualRole, SpriteBinding> createBindings() {
        EnumMap<VisualRole, SpriteBinding> result = new EnumMap<>(VisualRole.class);
        add(result, ship(
                "imperial.utility.v1", VisualRole.UTILITY_SHIP,
                "ships/imperial_utility_v1.png", 110d, 34d, List.of()));
        add(result, ship(
                "imperial.bulk-freighter.v1", VisualRole.CARGO_TRANSPORT_SHIP,
                "ships/imperial_bulk_freighter_v1.png", 280d, 88d, List.of()));
        add(result, ship(
                "imperial.mining.v1", VisualRole.MINING_INDUSTRIAL_SHIP,
                "ships/imperial_mining_v1.png", 180d, 70d, List.of()));
        add(result, ship(
                "imperial.escort-destroyer.v1", VisualRole.LIGHT_COMBAT_ESCORT_SHIP,
                "ships/imperial_escort_destroyer_v1.png", 220d, 72d,
                List.of(new VisualHardpoint(
                        "weapon_spinal",
                        VisualHardpointType.WEAPON,
                        0.8272727f,
                        0.5f,
                        0f))));
        add(result, ship(
                "imperial.medium-cruiser.v1", VisualRole.MEDIUM_COMBAT_SHIP,
                "ships/imperial_medium_cruiser_v1.png", 310d, 96d, List.of()));
        add(result, full(
                "imperial.trade-hub.v1", VisualRole.TRADE_DOCK_STATION,
                "stations/imperial_trade_hub_v1.png", AtlasRegion.fullSquare(), 1_600d, 1_000d));
        add(result, full(
                "imperial.industrial-station.v1", VisualRole.INDUSTRIAL_STATION,
                "stations/imperial_industrial_station_v1.png", AtlasRegion.fullLandscape(), 1_200d, 780d));
        add(result, full(
                "imperial.shipyard.v1", VisualRole.SHIPYARD_STATION,
                "stations/imperial_shipyard_v1.png", AtlasRegion.fullLandscape(), 1_600d, 1_000d));
        add(result, atlas(
                "resource.carbonaceous.v1", VisualRole.RESOURCE_CARBONACEOUS,
                new AtlasRegion(0, 0, 320, 320)));
        add(result, atlas(
                "resource.water-ice.v1", VisualRole.RESOURCE_WATER_ICE,
                new AtlasRegion(320, 0, 320, 320)));
        add(result, atlas(
                "resource.metallic.v1", VisualRole.RESOURCE_METALLIC,
                new AtlasRegion(0, 320, 320, 320)));
        add(result, atlas(
                "resource.mineral.v1", VisualRole.RESOURCE_MINERAL,
                new AtlasRegion(320, 320, 320, 320)));
        add(result, full(
                "imperial.derelict.v1", VisualRole.DERELICT,
                "special/imperial_derelict_v1.png", AtlasRegion.fullLandscape(), 220d, 72d));
        if (result.size() != VisualRole.values().length) {
            throw new IllegalStateException("minimum sprite catalogue lacks role coverage");
        }
        return Map.copyOf(result);
    }

    private static SpriteBinding ship(
            String assetId,
            VisualRole role,
            String path,
            double nominalLengthM,
            double nominalWidthM,
            List<VisualHardpoint> hardpoints) {
        return full(assetId, role, path, AtlasRegion.fullLandscape(), nominalLengthM, nominalWidthM, hardpoints);
    }

    private static SpriteBinding atlas(String assetId, VisualRole role, AtlasRegion region) {
        return full(assetId, role, "resources/resource_body_atlas_v1.png", region, 80d, 80d);
    }

    private static SpriteBinding full(
            String assetId,
            VisualRole role,
            String path,
            AtlasRegion region,
            double nominalLengthM,
            double nominalWidthM) {
        return full(assetId, role, path, region, nominalLengthM, nominalWidthM, List.of());
    }

    private static SpriteBinding full(
            String assetId,
            VisualRole role,
            String path,
            AtlasRegion region,
            double nominalLengthM,
            double nominalWidthM,
            List<VisualHardpoint> hardpoints) {
        return new SpriteBinding(
                "sprite.stage20_5." + assetId,
                role,
                ROOT + path,
                region,
                0.5f,
                0.5f,
                SourceFacing.RIGHT,
                nominalLengthM,
                nominalWidthM,
                hardpoints);
    }

    private static void add(Map<VisualRole, SpriteBinding> target, SpriteBinding binding) {
        if (target.putIfAbsent(binding.role(), binding) != null) {
            throw new IllegalStateException("duplicate minimum sprite role: " + binding.role());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
