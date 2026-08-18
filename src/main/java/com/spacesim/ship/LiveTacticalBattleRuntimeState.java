package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic materialized runtime roster for one exact local Stage-19I tactical battle.
 *
 * <p>This class materializes each authored {@link CombatantSpec} through the same production-valid
 * Stage-17.5 engineering/protection content and runtime boundaries used by the live duel. Every
 * combatant owns an independent {@link TransformComponent}, {@link EngineeringComponent}, local
 * damage snapshot, charged fitted shield state, physical doctrine consumables and weapon-feed
 * identity. Derived combat capability is never stored here as a second source of truth.</p>
 *
 * <p>The contact registry remains actor-bounded. Replacing one observer's visible contacts cannot
 * change another observer's information domain, which prevents the multi-ship live session from
 * falling back to one omniscient shared target list.</p>
 */
public final class LiveTacticalBattleRuntimeState {
    private final LiveTacticalBattleScenario scenario;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipProtectionCatalog protectionCatalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final DerivedShipCalculator calculator;
    private final ShipShieldEngineeringAdapter shieldAdapter;
    private final ShieldFieldRuntime shieldRuntime;
    private final TreeMap<Long, CombatantRuntime> combatantsById = new TreeMap<>();
    private final TreeMap<Long, List<ObservedContact>> visibleContactsByObserverId = new TreeMap<>();

    /**
     * Materializes deterministic production physical state and empty actor-visible contact domains.
     *
     * @param scenario authored exact-local battle roster
     */
    public LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        protectionCatalog = Stage175ICombatTestProtectionPack.load();
        engineeringRuntime = new ShipEngineeringRuntime(engineeringCatalog);
        calculator = new DerivedShipCalculator(engineeringCatalog);
        shieldAdapter = new ShipShieldEngineeringAdapter();
        shieldRuntime = new ShieldFieldRuntime();

        for (CombatantSpec spec : scenario.combatants()) {
            CombatantRuntime runtime = materialize(spec);
            combatantsById.put(spec.entityId(), runtime);
            visibleContactsByObserverId.put(spec.entityId(), List.of());
        }
    }

    /**
     * Returns the immutable authored scenario used to materialize this runtime roster.
     *
     * @return battle scenario
     */
    public LiveTacticalBattleScenario scenario() {
        return scenario;
    }

    /**
     * Returns combatant runtime entries in canonical stable-entity order.
     *
     * @return immutable ordered runtime collection
     */
    public List<CombatantRuntime> combatants() {
        return List.copyOf(combatantsById.values());
    }

    /**
     * Resolves one materialized combatant by stable entity identity.
     *
     * @param entityId stable combatant entity identity
     * @return materialized combatant runtime
     * @throws IllegalArgumentException when the entity does not belong to this battle
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
     * <p>The supplied contacts must already have been produced by the production observation/track
     * pipeline. This method does not create, improve or infer tracks. Duplicate target identities and
     * self-target contacts are rejected so future tactical planning consumes an unambiguous
     * actor-local information set.</p>
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
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineeringCatalog.findDemonstratorFit(doctrine.fitId()));
        HullDefinition hull = engineeringCatalog.findHull(fit.hullId());
        ShipProtectionCatalog.HullDamageLayout damageLayout = protectionCatalog.findHullDamageLayout(hull.id());
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, damageLayout);
        RuntimeState operatingState = engineeringRuntime.initialize(
                fit,
                doctrine.initialConsumables(),
                damage.moduleDamage());
        DerivedShipState derived = calculator.derive(
                hull,
                fit,
                operatingState.consumables(),
                damage.moduleDamage());

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
        EngineeringComponent engineering = new EngineeringComponent(fit, operatingState, instanceState);
        return new CombatantRuntime(spec, doctrine, hull, damageLayout, engineering);
    }

    /**
     * Mutable production-local runtime state for one materialized combatant.
     *
     * <p>The transform and engineering component are intentionally mutable-by-runtime because flight,
     * propulsion, consumables, damage, shields and launcher continuity evolve during the battle. The
     * authored {@link CombatantSpec}, doctrine fixture, hull definition and damage layout remain
     * immutable references to validated content.</p>
     */
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
                EngineeringComponent engineering) {
            this.spec = Objects.requireNonNull(spec, "spec");
            this.doctrine = Objects.requireNonNull(doctrine, "doctrine");
            this.hull = Objects.requireNonNull(hull, "hull");
            this.damageLayout = Objects.requireNonNull(damageLayout, "damageLayout");
            this.engineering = Objects.requireNonNull(engineering, "engineering");
            this.transform = new TransformComponent();
            this.transform.position.set((float) spec.xM(), (float) spec.yM());
        }

        /** @return immutable authored identity/side/doctrine/spawn metadata */
        public CombatantSpec spec() {
            return spec;
        }

        /** @return acceptance doctrine fixture selecting physical fit/stores only */
        public Doctrine doctrine() {
            return doctrine;
        }

        /** @return production hull definition referenced by the installed fit */
        public HullDefinition hull() {
            return hull;
        }

        /** @return production local-damage routing layout for this hull */
        public ShipProtectionCatalog.HullDamageLayout damageLayout() {
            return damageLayout;
        }

        /** @return authoritative mutable physical transform for this exact local battle */
        public TransformComponent transform() {
            return transform;
        }

        /**
         * Returns the authoritative fitted production state for this combatant.
         *
         * <p>Callers may replace mutable runtime/instance snapshots through the component's ordinary
         * production methods, but must not replace the fit as a hidden tactical stat edit.</p>
         *
         * @return independent production engineering component
         */
        public EngineeringComponent engineering() {
            return engineering;
        }
    }
}
