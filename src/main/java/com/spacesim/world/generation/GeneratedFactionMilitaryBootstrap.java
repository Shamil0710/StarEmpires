package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * New-campaign materializer for the first persistent generated-faction military forces.
 *
 * <p>Every ship is an ordinary world fleet entity and therefore consumes the shared sequential
 * {@link FleetId} allocator, persists through {@code WorldState}, jumps through the ordinary fleet
 * FSM and disappears permanently when destroyed. The current A-E engineering baseline and explicit
 * Stage-21 strategic-mobility variants are provisional Stage-17.5/19 content; this bootstrap does
 * not promote them into Stage-22 hull or faction-doctrine canon.</p>
 */
@SuppressWarnings("doclint:missing")
public final class GeneratedFactionMilitaryBootstrap {
    /** Stable contract for the provisional pre-Stage-22 military bootstrap. */
    public static final String CURRENT_VERSION = "generated-faction-military.provisional.v1";
    /** Initial physical patrol strength per generated faction. */
    public static final int SHIPS_PER_FACTION = 3;

    // Stage-13 weapon projection only; EngineeringComponent remains physical ship authority.
    private static final String LEGACY_COMBAT_ARCHETYPE_ID = "ship.guard_frigate";
    private static final ShipEngineeringCatalog ENGINEERING =
            Stage21GeneratedMilitaryEngineeringCatalog.load();
    private static final ShipProtectionCatalog PROTECTION =
            Stage175ICombatTestProtectionPack.load();

    private GeneratedFactionMilitaryBootstrap() {
        throw new AssertionError("No instances");
    }

    /**
     * Materializes one finite starting patrol around each accepted faction start station.
     *
     * @param runtime already materialized generated-world runtime
     * @param assignments accepted faction start assignments
     * @return immutable evidence ordered by assigned FleetId
     */
    public static BootstrapReport materialize(
            LiveRuntime runtime,
            List<Assignment> assignments) {
        LiveRuntime live = Objects.requireNonNull(runtime, "runtime");
        List<Assignment> starts = Objects.requireNonNull(assignments, "assignments").stream()
                .sorted(Comparator.comparing(Assignment::stableFactionId))
                .toList();
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("military bootstrap requires faction starts");
        }

        TreeMap<StarSystemId, List<RuntimeEndpoint>> endpointsBySystem = new TreeMap<>();
        for (RuntimeEndpoint endpoint : live.infrastructure().endpoints()) {
            endpointsBySystem.computeIfAbsent(endpoint.systemId(), ignored -> new ArrayList<>())
                    .add(endpoint);
        }
        endpointsBySystem.values().forEach(value -> value.sort(
                Comparator.comparing(RuntimeEndpoint::stationId)));

        ArrayList<CommissionedShip> commissioned = new ArrayList<>();
        for (Assignment assignment : starts) {
            List<RuntimeEndpoint> endpoints = endpointsBySystem.get(assignment.systemId());
            if (endpoints == null || endpoints.isEmpty()) {
                throw new IllegalStateException(
                        "faction start has no generated military staging endpoint: "
                                + assignment.systemId());
            }
            int runtimeFactionId = live.world()
                    .findFactionRuntimeId(assignment.stableFactionId()).orElseThrow();
            String factionDisplayName = live.world().getWorldFactionIdentities().stream()
                    .filter(value -> value.stableFactionId().equals(assignment.stableFactionId()))
                    .map(value -> value.displayName())
                    .findFirst().orElse(assignment.stableFactionId());
            LocalPhysicalPosition anchor = endpoints.get(0).position();
            for (int ordinal = 0; ordinal < SHIPS_PER_FACTION; ordinal++) {
                Doctrine doctrine = doctrine(assignment.stableFactionId(), ordinal);
                EngineeringComponent engineering = engineering(doctrine);
                LocalPhysicalPosition position = patrolPosition(anchor, doctrine, ordinal);
                Entity entity = entity(
                        factionDisplayName, runtimeFactionId, ordinal, position, engineering);
                EntityId localId = live.world().createEntity(assignment.systemId(), entity);
                FleetId fleetId = live.world().findFleetByLocal(
                        assignment.systemId(), localId).orElseThrow();
                live.arrival().materialization(assignment.systemId()).registerPhysicalState(
                        localId, LocalPhysicalKinematics.stationary(position));
                commissioned.add(new CommissionedShip(
                        fleetId,
                        assignment.stableFactionId(),
                        assignment.systemId(),
                        localId,
                        doctrine.id(),
                        engineering.fit.hullId(),
                        Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
            }
        }
        commissioned.sort(Comparator.comparing(CommissionedShip::fleetId));
        return new BootstrapReport(CURRENT_VERSION, List.copyOf(commissioned));
    }

    private static Doctrine doctrine(String factionId, int ordinal) {
        return switch (ordinal) {
            case 0 -> Stage175IFleetDoctrineCatalog.get(DoctrineId.D_DEFENSIVE_EW);
            case 1 -> Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
            case 2 -> {
                DoctrineId[] strike = {
                        DoctrineId.A_KINETIC_LINE,
                        DoctrineId.B_MISSILE_STRIKE,
                        DoctrineId.C_HIGH_MOBILITY_BEAM
                };
                yield Stage175IFleetDoctrineCatalog.get(
                        strike[Math.floorMod(factionId.hashCode(), strike.length)]);
            }
            default -> throw new IllegalArgumentException("unsupported military ship ordinal");
        };
    }

    private static EngineeringComponent engineering(Doctrine doctrine) {
        InstalledFit fit = InstalledFit.fromDemonstrator(
                ENGINEERING.findDemonstratorFit(
                        Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
        HullDefinition hull = ENGINEERING.findHull(fit.hullId());
        ShipProtectionCatalog.HullDamageLayout layout =
                PROTECTION.findHullDamageLayout(hull.id());
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        ShipEngineeringRuntime.RuntimeState operating = new ShipEngineeringRuntime(ENGINEERING)
                .initialize(fit, doctrine.initialConsumables(), damage.moduleDamage());
        DerivedShipState derived = new DerivedShipCalculator(ENGINEERING)
                .derive(hull, fit, operating.consumables(), damage.moduleDamage());
        ShipShieldEngineeringAdapter adapter = new ShipShieldEngineeringAdapter();
        ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield shield : adapter.derive(derived)) {
            shields.put(shield.mountId(), shield.chargedState(shieldRuntime));
        }
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                shields,
                new ShipyardEngineeringService.MaintenanceState(Map.of()),
                doctrine.weaponLoadout(),
                WeaponMountRuntime.RuntimeState.empty());
        return new EngineeringComponent(fit, operating, instance);
    }

    private static Entity entity(
            String factionDisplayName,
            int runtimeFactionId,
            int ordinal,
            LocalPhysicalPosition position,
            EngineeringComponent engineering) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(
                exactFloat(position.offsetXM(), "military position X"),
                exactFloat(position.offsetYM(), "military position Y"));
        String role = switch (ordinal) {
            case 0 -> "Дозор";
            case 1 -> "Страж";
            case 2 -> "Ударный";
            default -> throw new IllegalArgumentException("unsupported military ship ordinal");
        };
        return new Entity()
                .add(new IdentityComponent(
                        role + " " + shortFactionName(factionDisplayName), IdentityComponent.Kind.FLEET))
                .add(new ArchetypeComponent(LEGACY_COMBAT_ARCHETYPE_ID))
                .add(transform)
                .add(new ShipComponent(ShipType.COMBAT_SHIP))
                .add(new FactionComponent(runtimeFactionId))
                .add(new CombatComponent())
                .add(engineering);
    }

    private static LocalPhysicalPosition patrolPosition(
            LocalPhysicalPosition anchor,
            Doctrine doctrine,
            int ordinal) {
        double angle = -Math.PI * 0.74d + ordinal * Math.PI * 0.29d;
        double radius = doctrine.defaultSpacingM() * (3.5d + ordinal * 0.55d);
        return anchor.translated(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    private static String shortFactionName(String factionDisplayName) {
        String value = Objects.requireNonNull(factionDisplayName, "factionDisplayName").strip();
        return value.isEmpty() ? "НЕИЗВЕСТНОЙ ФРАКЦИИ" : value;
    }

    private static float exactFloat(double value, String label) {
        float result = (float) value;
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException(label + " is outside legacy ECS projection range");
        }
        return result;
    }

    /** One commissioned ordinary physical military fleet. */
    public record CommissionedShip(
            FleetId fleetId,
            String stableFactionId,
            StarSystemId systemId,
            EntityId localEntityId,
            DoctrineId provisionalDoctrineId,
            String hullId,
            String fitId) {
        /**
         * Validates immutable commissioning evidence.
         *
         * @param fleetId ordinary persistent fleet identity
         * @param stableFactionId stable generated-faction identity
         * @param systemId commissioned system
         * @param localEntityId system-local entity identity
         * @param provisionalDoctrineId selected provisional doctrine
         * @param hullId fitted engineering hull identity
         * @param fitId fitted provisional demonstrator identity
         */
        public CommissionedShip {
            Objects.requireNonNull(fleetId, "fleetId");
            stableFactionId = Objects.requireNonNull(stableFactionId, "stableFactionId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(localEntityId, "localEntityId");
            Objects.requireNonNull(provisionalDoctrineId, "provisionalDoctrineId");
            hullId = Objects.requireNonNull(hullId, "hullId");
            fitId = Objects.requireNonNull(fitId, "fitId");
        }
    }

    /** Immutable new-campaign commissioning report. */
    public record BootstrapReport(String version, List<CommissionedShip> ships) {
        /**
         * Validates immutable bootstrap evidence.
         *
         * @param version bootstrap contract version
         * @param ships commissioned ships ordered by FleetId
         */
        public BootstrapReport {
            version = Objects.requireNonNull(version, "version");
            ships = List.copyOf(Objects.requireNonNull(ships, "ships"));
            if (ships.isEmpty()) {
                throw new IllegalArgumentException("military bootstrap must commission ships");
            }
        }
    }
}
