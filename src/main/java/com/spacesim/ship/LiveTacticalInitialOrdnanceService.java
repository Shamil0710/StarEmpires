package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Validated Stage-19I scenario-authoring seam for initial physical weapon-feed contents.
 *
 * <p>The service changes neither hull nor fitted module identity and grants no combat multiplier. It
 * exists so required scaled variants (interceptor screens, partial ammunition and depleted starts)
 * can author explicit physical initial stores without adding new doctrine IDs or mutating the five
 * canonical Stage-17.5I doctrine fixtures. Every requested ammunition identity must fit the already
 * installed production launcher family/envelope/interface, and itemized count, interface-native
 * amount and physical mass are replaced atomically in the central consumable state.</p>
 *
 * <p>This is an initial-condition authoring boundary, not an in-combat reload command. Runtime reload
 * and station logistics remain separate gameplay operations.</p>
 */
public final class LiveTacticalInitialOrdnanceService {
    private static final double EPSILON = 1e-9d;

    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;

    /** Creates the Stage-19I initial-ordnance authoring service over production test content. */
    public LiveTacticalInitialOrdnanceService() {
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
    }

    /**
     * One explicit initial feed content/count override.
     *
     * @param mountId already fitted physical weapon mount
     * @param ammunitionContentId authored ammunition content identity compatible with that launcher
     * @param roundCount non-negative initial itemized physical round count
     */
    public record FeedLoad(String mountId, String ammunitionContentId, long roundCount) {
        /**
         * Validates one initial feed request.
         *
         * @param mountId already fitted physical weapon mount
         * @param ammunitionContentId authored ammunition content identity
         * @param roundCount non-negative itemized physical round count
         */
        public FeedLoad {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(ammunitionContentId, "ammunitionContentId");
            if (roundCount < 0L) {
                throw new IllegalArgumentException("roundCount must be non-negative");
            }
        }
    }

    /**
     * Applies a complete set of initial weapon-feed contents to one already materialized combatant.
     *
     * <p>Duplicate mount requests are rejected. All requests are validated before the combatant is
     * mutated, so an invalid later request cannot partially author earlier feeds.</p>
     *
     * @param combatant production combatant whose fit remains unchanged
     * @param loads explicit initial feed contents/counts
     */
    public void apply(CombatantRuntime combatant, List<FeedLoad> loads) {
        CombatantRuntime checkedCombatant = Objects.requireNonNull(combatant, "combatant");
        Objects.requireNonNull(loads, "loads");
        EngineeringComponent engineering = checkedCombatant.engineering();

        TreeMap<String, ResolvedLoad> resolvedByMount = new TreeMap<>();
        for (FeedLoad load : loads) {
            FeedLoad checked = Objects.requireNonNull(load, "load");
            if (resolvedByMount.containsKey(checked.mountId())) {
                throw new IllegalArgumentException("duplicate initial ordnance mount: " + checked.mountId());
            }
            ResolvedLoad resolved = resolve(engineering, checked);
            resolvedByMount.put(checked.mountId(), resolved);
        }
        if (resolvedByMount.isEmpty()) {
            return;
        }

        ConsumableState current = engineering.runtimeState.consumables();
        ArrayList<ConsumableLoad> nextLoads = new ArrayList<>();
        for (ConsumableLoad existing : current.interfaceLoads()) {
            ResolvedLoad replacement = resolvedByMount.get(existing.mountId());
            if (replacement != null
                    && existing.kind() == InterfaceKind.AMMUNITION
                    && existing.interfaceId().equals(replacement.interfaceId())) {
                continue;
            }
            nextLoads.add(existing);
        }
        for (ResolvedLoad resolved : resolvedByMount.values()) {
            FeedLoad request = resolved.request();
            double amount = request.roundCount() * resolved.launcherProfile().ammunitionAmountPerShot();
            double massKg = request.roundCount() * resolved.roundMassKg();
            nextLoads.add(new ConsumableLoad(
                    request.mountId(),
                    resolved.interfaceId(),
                    InterfaceKind.AMMUNITION,
                    amount,
                    massKg,
                    request.roundCount()));
        }
        ConsumableState nextConsumables = new ConsumableState(
                current.cargoMassKg(),
                current.storesMassKg(),
                current.missionPayloadMassKg(),
                current.missionIntegrationVolumeM3(),
                nextLoads);

        ArrayList<FeedBinding> nextBindings = new ArrayList<>();
        for (FeedBinding binding : engineering.instanceState.weaponLoadout().feeds()) {
            ResolvedLoad replacement = resolvedByMount.get(binding.mountId());
            if (replacement != null && binding.interfaceId().equals(replacement.interfaceId())) {
                continue;
            }
            nextBindings.add(binding);
        }
        for (ResolvedLoad resolved : resolvedByMount.values()) {
            nextBindings.add(new FeedBinding(
                    resolved.request().mountId(),
                    resolved.interfaceId(),
                    resolved.request().ammunitionContentId()));
        }

        RuntimeState state = engineering.runtimeState;
        engineering.setRuntimeState(new RuntimeState(
                nextConsumables,
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
        ShipInstanceRuntimeState instance = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                instance.damage(),
                instance.shieldStatesByMount(),
                instance.maintenance(),
                new WeaponLoadoutState(nextBindings),
                instance.weaponMountRuntime()));
    }

    private ResolvedLoad resolve(EngineeringComponent engineering, FeedLoad request) {
        InstalledModuleDefinition installed = engineering.fit.installedModules().stream()
                .filter(value -> value.mountId().equals(request.mountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "initial ordnance mount is not fitted: " + request.mountId()));
        LauncherProfile profile = launcherCatalog.findByModuleId(installed.moduleId());
        if (profile == null) {
            throw new IllegalArgumentException(
                    "initial ordnance mount has no production launcher profile: " + request.mountId());
        }
        var module = engineeringCatalog.findModule(installed.moduleId());
        if (module == null) {
            throw new IllegalArgumentException("unknown installed weapon module: " + installed.moduleId());
        }
        var physicalInterface = module.interfaces().stream()
                .filter(value -> value.id().equals(profile.ammunitionInterfaceId()))
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "launcher ammunition interface is missing: " + request.mountId()));

        double roundMassKg;
        double lengthM;
        double diameterM;
        if (profile.family() == Family.KINETIC) {
            var ammunition = ammunitionCatalog.findKinetic(request.ammunitionContentId());
            if (ammunition == null) {
                throw new IllegalArgumentException(
                        "kinetic launcher cannot load non-kinetic ammunition: " + request.ammunitionContentId());
            }
            roundMassKg = ammunition.massKg();
            lengthM = ammunition.lengthM();
            diameterM = ammunition.diameterM();
        } else if (profile.family() == Family.GUIDED) {
            var ammunition = ammunitionCatalog.findGuided(request.ammunitionContentId());
            if (ammunition == null) {
                throw new IllegalArgumentException(
                        "guided launcher cannot load non-guided ammunition: " + request.ammunitionContentId());
            }
            roundMassKg = ammunition.wetMassKg();
            lengthM = ammunition.lengthM();
            diameterM = ammunition.diameterM();
        } else {
            throw new IllegalArgumentException(
                    "initial ordnance authoring supports physical kinetic/guided launchers only: " + request.mountId());
        }
        if (roundMassKg > profile.maxProjectileMassKg() + EPSILON
                || lengthM > profile.maxProjectileLengthM() + EPSILON
                || diameterM > profile.maxProjectileDiameterM() + EPSILON) {
            throw new IllegalArgumentException(
                    "ammunition exceeds fitted launcher physical envelope: " + request.ammunitionContentId());
        }
        double requestedAmount = request.roundCount() * profile.ammunitionAmountPerShot();
        if (!Double.isFinite(requestedAmount)
                || requestedAmount > physicalInterface.capacity() + EPSILON) {
            throw new IllegalArgumentException(
                    "initial ammunition count exceeds fitted interface capacity on " + request.mountId());
        }
        return new ResolvedLoad(
                request,
                profile,
                profile.ammunitionInterfaceId(),
                roundMassKg);
    }

    private record ResolvedLoad(
            FeedLoad request,
            LauncherProfile launcherProfile,
            String interfaceId,
            double roundMassKg) {
        private ResolvedLoad {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(launcherProfile, "launcherProfile");
            requireNonBlank(interfaceId, "interfaceId");
            if (!Double.isFinite(roundMassKg) || roundMassKg <= 0d) {
                throw new IllegalArgumentException("roundMassKg must be finite and positive");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
