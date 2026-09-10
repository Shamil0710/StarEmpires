package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic materialized runtime roster for one exact local Stage-19I tactical battle.
 *
 * <p>Acceptance scenarios may still author pristine state from a doctrine fixture. Production
 * strategic handoff may instead use {@link #importExact(List)} to install detached copies of exact
 * fitted engineering/damage/stores state. M22.6 may use the catalog-aware overload to import an exact
 * Stage-22 fit through the same Stage-19 runtime without assigning a Stage-17.5 doctrine as physical
 * authority. In all paths derived capability remains recomputed from ordinary physical state and the
 * contact registry remains actor-bounded.</p>
 */
public final class LiveTacticalBattleRuntimeState {
    private final LiveTacticalBattleScenario scenario;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipProtectionCatalog protectionCatalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final DerivedShipCalculator calculator;
    private final ShipShieldEngineeringAdapter shieldAdapter;
    private final ShieldFieldRuntime shieldRuntime;
    private final boolean legacyDoctrineBindingRequired;
    private final TreeMap<Long, CombatantRuntime> combatantsById = new TreeMap<>();
    private final TreeMap<Long, List<ObservedContact>> visibleContactsByObserverId = new TreeMap<>();

    /**
     * Materializes deterministic authored production physical state and empty contact domains.
     *
     * @param scenario authored exact-local battle roster
     */
    public LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario scenario) {
        this(
                Objects.requireNonNull(scenario, "scenario"),
                Map.of(),
                Stage175ICombatTestContentPack.loadDoctrines(),
                Stage175ICombatTestProtectionPack.load(),
                true);
    }

    private LiveTacticalBattleRuntimeState(
            LiveTacticalBattleScenario scenario,
            Map<Long, ImportedCombatantState> importedByEntityId,
            ShipEngineeringCatalog engineeringCatalog,
            ShipProtectionCatalog protectionCatalog,
            boolean legacyDoctrineBindingRequired) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(importedByEntityId, "importedByEntityId");
        this.engineeringCatalog = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        this.protectionCatalog = Objects.requireNonNull(protectionCatalog, "protectionCatalog");
        this.legacyDoctrineBindingRequired = legacyDoctrineBindingRequired;
        engineeringRuntime = new ShipEngineeringRuntime(this.engineeringCatalog);
        calculator = new DerivedShipCalculator(this.engineeringCatalog);
        shieldAdapter = new ShipShieldEngineeringAdapter();
        shieldRuntime = new ShieldFieldRuntime();

        for (CombatantSpec spec : scenario.combatants()) {
            ImportedCombatantState imported = importedByEntityId.get(spec.entityId());
            CombatantRuntime runtime = imported == null
                    ? materialize(spec)
                    : materializeImported(spec, imported);
            combatantsById.put(spec.entityId(), runtime);
            visibleContactsByObserverId.put(spec.entityId(), List.of());
        }
        if (combatantsById.size() != importedByEntityId.size() && !importedByEntityId.isEmpty()) {
            throw new IllegalArgumentException("imported combatant set differs from exact tactical scenario");
        }
    }

    /**
     * Creates a detached Stage-19 tactical roster from exact current physical engineering snapshots.
     *
     * <p>Each imported fit must exactly equal either one currently supported provisional Stage-17.5/19
     * demonstrator fit or its single registered Stage-21 strategic-mobility variant. The matching
     * doctrine supplies metadata/content identity only; imported damage, shields, cooldowns,
     * ammunition, propellant, energy, heat and maintenance are preserved unchanged. Arbitrary
     * same-hull or modified fits fail closed rather than being substituted.</p>
     *
     * @param combatants exact physical combatants with stable tactical identities and kinematics
     * @return independent Stage-19 battle state containing detached exact physical snapshots
     */
    public static LiveTacticalBattleRuntimeState importExact(List<ImportedCombatantState> combatants) {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        return importExactInternal(
                combatants,
                catalog,
                Stage175ICombatTestProtectionPack.load(),
                true);
    }

    /**
     * Imports exact fitted state from another accepted content package into the ordinary Stage-19 runtime.
     *
     * <p>This overload is deliberately content-agnostic. The supplied engineering catalog must contain
     * every exact imported fit as a demonstrator and the supplied protection catalog must contain the
     * corresponding hull layouts. The legacy {@link CombatantSpec#doctrineId()} field is populated with
     * a compatibility placeholder because the Stage-19 scenario record predates package-neutral exact
     * imports; it is not consulted for fit, stores, weapon loadout, damage or any numeric capability.
     * Those remain entirely in the imported {@link EngineeringComponent} and supplied common catalogs.</p>
     *
     * @param combatants exact physical combatants with stable tactical identities and kinematics
     * @param engineeringCatalog accepted engineering authority containing every exact imported fit
     * @param protectionCatalog common Stage-17.5F protection authority projected for those hulls
     * @return independent Stage-19 battle state containing detached exact physical snapshots
     */
    public static LiveTacticalBattleRuntimeState importExact(
            List<ImportedCombatantState> combatants,
            ShipEngineeringCatalog engineeringCatalog,
            ShipProtectionCatalog protectionCatalog) {
        return importExactInternal(combatants, engineeringCatalog, protectionCatalog, false);
    }

    private static LiveTacticalBattleRuntimeState importExactInternal(
            List<ImportedCombatantState> combatants,
            ShipEngineeringCatalog engineeringCatalog,
            ShipProtectionCatalog protectionCatalog,
            boolean legacyDoctrineBindingRequired) {
        Objects.requireNonNull(combatants, "combatants");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        ShipProtectionCatalog checkedProtection = Objects.requireNonNull(protectionCatalog, "protectionCatalog");
        if (combatants.size() < 2) {
            throw new IllegalArgumentException("exact tactical import requires at least two combatants");
        }
        TreeMap<Long, ImportedCombatantState> byId = new TreeMap<>();
        ArrayList<CombatantSpec> specs = new ArrayList<>(combatants.size());
        boolean alpha = false;
        boolean beta = false;
        for (ImportedCombatantState row : combatants) {
            ImportedCombatantState checked = Objects.requireNonNull(row, "combatant");
            if (byId.putIfAbsent(checked.entityId(), checked) != null) {
                throw new IllegalArgumentException("duplicate exact tactical entity id: " + checked.entityId());
            }
            Doctrine doctrine = legacyDoctrineBindingRequired
                    ? requireDoctrineForFit(checkedEngineering, checked.engineering().fit)
                    : Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
            if (!legacyDoctrineBindingRequired) {
                requireExactCatalogFit(checkedEngineering, checked.engineering().fit);
            }
            specs.add(new CombatantSpec(
                    checked.entityId(), checked.side(), doctrine.id(), checked.xM(), checked.yM()));
            alpha |= checked.side() == Side.ALPHA;
            beta |= checked.side() == Side.BETA;
        }
        if (!alpha || !beta) {
            throw new IllegalArgumentException("exact tactical import requires combatants on both sides");
        }
        return new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(specs),
                byId,
                checkedEngineering,
                checkedProtection,
                legacyDoctrineBindingRequired);
    }

    /** @return immutable authored scenario/identity roster driving this battle */
    public LiveTacticalBattleScenario scenario() {
        return scenario;
    }

    /**
     * Returns the immutable engineering catalog that materialized this battle state.
     *
     * <p>Authored Stage-19 scenarios retain the stable baseline catalog, while exact imports retain
     * the caller-supplied accepted content universe. Downstream tactical runtimes must reuse this
     * catalog rather than silently loading a different engineering authority.</p>
     *
     * @return battle-local immutable engineering content authority
     */
    public ShipEngineeringCatalog engineeringCatalog() {
        return engineeringCatalog;
    }

    /**
     * Returns the protection catalog paired with this battle's engineering content.
     *
     * @return battle-local immutable protection authority
     */
    public ShipProtectionCatalog protectionCatalog() {
        return protectionCatalog;
    }

    /** @return immutable combatants in canonical stable-identity order */
    public List<CombatantRuntime> combatants() {
        return List.copyOf(combatantsById.values());
    }

    /**
     * Resolves one materialized combatant by stable tactical identity.
     *
     * @param entityId stable tactical combatant identity
     * @return materialized combatant runtime
     */
    public CombatantRuntime requireCombatant(long entityId) {
        CombatantRuntime runtime = combatantsById.get(entityId);
        if (runtime == null) {
            throw new IllegalArgumentException("Unknown live tactical combatant entity id: " + entityId);
        }
        return runtime;
    }

    /**
     * Returns the immutable actor-visible contact list for one observer.
     *
     * @param observerEntityId stable observing combatant identity
     * @return actor-bounded contacts ordered by target identity
     */
    public List<ObservedContact> visibleContacts(long observerEntityId) {
        requireCombatant(observerEntityId);
        return visibleContactsByObserverId.get(observerEntityId);
    }

    /**
     * Atomically replaces one combatant's actor-visible contact domain.
     *
     * @param observerEntityId stable observing combatant identity
     * @param contacts actor-visible production contacts only
     */
    public void replaceVisibleContacts(long observerEntityId, Collection<ObservedContact> contacts) {
        requireCombatant(observerEntityId);
        Objects.requireNonNull(contacts, "contacts");
        TreeMap<Long, ObservedContact> canonical = new TreeMap<>();
        for (ObservedContact contact : contacts) {
            ObservedContact checked = Objects.requireNonNull(contact, "contact");
            long targetId = checked.track().targetId();
            if (targetId == observerEntityId) {
                throw new IllegalArgumentException("Combatant cannot observe itself as a tactical contact");
            }
            if (canonical.putIfAbsent(targetId, checked) != null) {
                throw new IllegalArgumentException("Duplicate actor-visible target identity: " + targetId);
            }
        }
        visibleContactsByObserverId.put(observerEntityId, List.copyOf(canonical.values()));
    }

    private CombatantRuntime materialize(CombatantSpec spec) {
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.get(spec.doctrineId());
        InstalledFit fit = InstalledFit.fromDemonstrator(engineeringCatalog.findDemonstratorFit(doctrine.fitId()));
        HullDefinition hull = engineeringCatalog.findHull(fit.hullId());
        ShipProtectionCatalog.HullDamageLayout damageLayout = protectionCatalog.findHullDamageLayout(hull.id());
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, damageLayout);
        RuntimeState operatingState = engineeringRuntime.initialize(fit, doctrine.initialConsumables(), damage.moduleDamage());
        DerivedShipState derived = calculator.derive(hull, fit, operatingState.consumables(), damage.moduleDamage());
        TreeMap<String, ShieldFieldRuntime.State> shieldStates = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield shield : shieldAdapter.derive(derived)) {
            shieldStates.put(shield.mountId(), shield.chargedState(shieldRuntime));
        }
        ShipInstanceRuntimeState instanceState = new ShipInstanceRuntimeState(
                damage,
                shieldStates,
                new ShipyardEngineeringService.MaintenanceState(Map.of()),
                doctrine.weaponLoadout(),
                WeaponMountRuntime.RuntimeState.empty());
        return new CombatantRuntime(
                spec, doctrine, hull, damageLayout,
                new EngineeringComponent(fit, operatingState, instanceState), 0d, 0d);
    }

    private CombatantRuntime materializeImported(CombatantSpec spec, ImportedCombatantState imported) {
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.get(spec.doctrineId());
        EngineeringComponent source = imported.engineering();
        if (legacyDoctrineBindingRequired && !matchesDoctrineFit(engineeringCatalog, doctrine, source.fit)) {
            throw new IllegalArgumentException("imported fit differs from resolved Stage-19 content identity");
        }
        if (!legacyDoctrineBindingRequired) {
            requireExactCatalogFit(engineeringCatalog, source.fit);
        }
        HullDefinition hull = engineeringCatalog.findHull(source.fit.hullId());
        if (hull == null) {
            throw new IllegalArgumentException("Stage-19 exact import does not support hull: " + source.fit.hullId());
        }
        ShipProtectionCatalog.HullDamageLayout layout = protectionCatalog.findHullDamageLayout(hull.id());
        if (layout == null) {
            throw new IllegalArgumentException("Stage-19 exact import lacks protection layout: " + hull.id());
        }
        Set<String> expectedCompartments = new HashSet<>();
        hull.compartments().forEach(value -> expectedCompartments.add(value.id()));
        if (!source.instanceState.damage().compartmentIntegrityById().keySet().equals(expectedCompartments)) {
            throw new IllegalArgumentException("imported damage snapshot differs from fitted hull compartments");
        }
        calculator.derive(
                hull,
                source.fit,
                source.runtimeState.consumables(),
                source.instanceState.damage().moduleDamage());
        EngineeringComponent detached = new EngineeringComponent(
                source.fit, source.runtimeState, source.instanceState);
        return new CombatantRuntime(
                spec, doctrine, hull, layout, detached,
                imported.velocityXMps(), imported.velocityYMps());
    }

    private static Doctrine requireDoctrineForFit(ShipEngineeringCatalog catalog, InstalledFit fit) {
        InstalledFit checked = Objects.requireNonNull(fit, "fit");
        return Stage175IFleetDoctrineCatalog.all().stream()
                .filter(doctrine -> matchesDoctrineFit(catalog, doctrine, checked))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stage-19 exact import does not support fitted state: " + checked.hullId()));
    }

    private static void requireExactCatalogFit(ShipEngineeringCatalog catalog, InstalledFit fit) {
        InstalledFit checked = Objects.requireNonNull(fit, "fit");
        boolean present = catalog.getDemonstratorFits().stream()
                .map(InstalledFit::fromDemonstrator)
                .anyMatch(checked::equals);
        if (!present) {
            throw new IllegalArgumentException(
                    "Exact tactical import fit is not present in supplied engineering catalog: " + checked.hullId());
        }
    }

    private static boolean matchesDoctrineFit(
            ShipEngineeringCatalog catalog,
            Doctrine doctrine,
            InstalledFit fit) {
        InstalledFit base = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit(doctrine.fitId()));
        if (base.equals(fit)) {
            return true;
        }
        String strategicId = Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId());
        ShipEngineeringCatalog.DemonstratorFitDefinition strategic = catalog.findDemonstratorFit(strategicId);
        return strategic != null && InstalledFit.fromDemonstrator(strategic).equals(fit);
    }

    /**
     * Detached exact physical input for one Stage-19 combatant.
     *
     * @param entityId positive stable tactical identity supplied by the caller
     * @param side local battle allegiance only
     * @param engineering exact current fitted engineering state to copy into the tactical runtime
     * @param xM exact local x coordinate in meters
     * @param yM exact local y coordinate in meters
     * @param velocityXMps exact local x velocity in meters per second
     * @param velocityYMps exact local y velocity in meters per second
     */
    public record ImportedCombatantState(
            long entityId,
            Side side,
            EngineeringComponent engineering,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps) {
        /**
         * Validates one exact import row.
         *
         * @param entityId positive stable tactical identity supplied by the caller
         * @param side local battle allegiance only
         * @param engineering exact current fitted engineering state
         * @param xM exact local x coordinate in meters
         * @param yM exact local y coordinate in meters
         * @param velocityXMps exact local x velocity in meters per second
         * @param velocityYMps exact local y velocity in meters per second
         */
        public ImportedCombatantState {
            if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(engineering.fit, "engineering.fit");
            Objects.requireNonNull(engineering.runtimeState, "engineering.runtimeState");
            Objects.requireNonNull(engineering.instanceState, "engineering.instanceState");
            if (!Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)) {
                throw new IllegalArgumentException("exact tactical kinematics must be finite");
            }
        }
    }

    /** Mutable production-local runtime state for one materialized combatant. */
    public static final class CombatantRuntime {
        private final CombatantSpec spec;
        private final Doctrine doctrine;
        private final HullDefinition hull;
        private final ShipProtectionCatalog.HullDamageLayout damageLayout;
        private final TransformComponent transform;
        private final EngineeringComponent engineering;

        private CombatantRuntime(
                CombatantSpec spec,
                Doctrine doctrine,
                HullDefinition hull,
                ShipProtectionCatalog.HullDamageLayout damageLayout,
                EngineeringComponent engineering,
                double velocityXMps,
                double velocityYMps) {
            this.spec = Objects.requireNonNull(spec, "spec");
            this.doctrine = Objects.requireNonNull(doctrine, "doctrine");
            this.hull = Objects.requireNonNull(hull, "hull");
            this.damageLayout = Objects.requireNonNull(damageLayout, "damageLayout");
            this.engineering = Objects.requireNonNull(engineering, "engineering");
            this.transform = new TransformComponent();
            this.transform.position.set((float) spec.xM(), (float) spec.yM());
            this.transform.velocity.set((float) velocityXMps, (float) velocityYMps);
        }

        /** @return immutable authored identity/side/content metadata */
        public CombatantSpec spec() { return spec; }
        /**
         * Returns legacy doctrine metadata.
         *
         * <p>For package-neutral exact imports this is compatibility metadata only; physical capability
         * remains the imported engineering state and supplied battle-local catalogs.</p>
         *
         * @return legacy Stage-19 scenario doctrine metadata
         */
        public Doctrine doctrine() { return doctrine; }
        /** @return production hull definition referenced by the installed fit */
        public HullDefinition hull() { return hull; }
        /** @return production local-damage routing layout for this hull */
        public ShipProtectionCatalog.HullDamageLayout damageLayout() { return damageLayout; }
        /** @return authoritative mutable local tactical transform */
        public TransformComponent transform() { return transform; }
        /** @return authoritative detached fitted physical state for this tactical runtime */
        public EngineeringComponent engineering() { return engineering; }

        /** @return true only after all fitted structure and installed local subsystems are physically destroyed */
        public boolean fullyDestroyed() {
            return ShipDamageRuntime.isFullyDestroyed(
                    hull, engineering.fit, damageLayout, engineering.instanceState.damage());
        }
    }
}
