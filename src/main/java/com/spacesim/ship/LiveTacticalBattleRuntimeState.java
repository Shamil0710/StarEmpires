package com.spacesim.ship;

import com.spacesim.components.TransformComponent;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic materialized runtime roster for one exact local Stage-19I tactical battle.
 *
 * <p>This class materializes per-combatant production {@link TransformComponent} state from an
 * authored {@link LiveTacticalBattleScenario} and keeps a separate actor-visible contact view for
 * every combatant. It intentionally does not own engineering, ammunition, reaction mass, damage,
 * fire control or combat resolution yet; those remain the next Stage-19I-B integration step.</p>
 *
 * <p>The contact registry is actor-bounded. Replacing one observer's visible contacts cannot change
 * another observer's information domain, which prevents the future multi-ship live session from
 * accidentally falling back to one omniscient shared target list.</p>
 */
public final class LiveTacticalBattleRuntimeState {
    private final LiveTacticalBattleScenario scenario;
    private final TreeMap<Long, CombatantRuntime> combatantsById = new TreeMap<>();
    private final TreeMap<Long, List<ObservedContact>> visibleContactsByObserverId = new TreeMap<>();

    /**
     * Materializes deterministic runtime transforms and empty actor-visible contact domains.
     *
     * @param scenario authored exact-local battle roster
     */
    public LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        for (CombatantSpec spec : scenario.combatants()) {
            CombatantRuntime runtime = new CombatantRuntime(spec);
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

    /**
     * Mutable production-local runtime state for one materialized combatant.
     *
     * <p>The transform is intentionally mutable because flight integration is authoritative runtime
     * state. The authored {@link CombatantSpec} remains immutable.</p>
     */
    public static final class CombatantRuntime {
        private final CombatantSpec spec;
        private final TransformComponent transform;

        private CombatantRuntime(CombatantSpec spec) {
            this.spec = Objects.requireNonNull(spec, "spec");
            this.transform = new TransformComponent();
            this.transform.position.set((float) spec.xM(), (float) spec.yM());
        }

        /**
         * Returns immutable authored identity/side/doctrine/spawn metadata.
         *
         * @return combatant specification
         */
        public CombatantSpec spec() {
            return spec;
        }

        /**
         * Returns the authoritative mutable physical transform for this exact local battle.
         *
         * @return production transform owned by the live tactical runtime
         */
        public TransformComponent transform() {
            return transform;
        }
    }
}
