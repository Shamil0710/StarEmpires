package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;

import java.util.ArrayList;
import java.util.List;

/** Persists exact core ships between committed encounters, including damage and finite stores. */
final class Stage22CorePairEncounterContinuationProbe {
    private Stage22CorePairEncounterContinuationProbe() { }

    static List<Stage19ExactTacticalEncounterResolver.Result> run(
            Stage22CorePairExperimentProtocol.Permutation permutation, boolean reloadBetweenEncounters) {
        var fixture = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        var resolver = new Stage19ExactTacticalEncounterResolver(fixture.content().engineering(),
                fixture.protection(), fixture.content().ammunition(), fixture.content().launchers());
        List<ImportedCombatantState> inputs = fixture.weapons().battleState().combatants().stream()
                .map(actor -> new ImportedCombatantState(actor.spec().entityId(), actor.spec().side(), actor.engineering(),
                        actor.transform().position.x, actor.transform().position.y,
                        actor.transform().velocity.x, actor.transform().velocity.y)).toList();
        // B13 models three distinct operational contacts. Strategic transit between contacts is outside
        // this boundary probe, so every contact reuses the same mirrored encounter geometry while the
        // exact engineering state (damage, ammunition, reaction mass, shields and maintenance) carries
        // forward. Reusing retreat kinematics from the previous local battle can make later contacts
        // never re-enter sensing/weapon range and therefore would not exercise rolling attrition.
        List<ImportedCombatantState> engagementGeometry = inputs;
        boolean rejectedByLegacyContent = false;
        try {
            new Stage19ExactTacticalEncounterResolver().resolve(inputs, 1L);
        } catch (IllegalArgumentException expected) {
            rejectedByLegacyContent = true;
        }
        if (!rejectedByLegacyContent) throw new AssertionError("Legacy content silently accepted unknown core fits");
        var results = new ArrayList<Stage19ExactTacticalEncounterResolver.Result>();
        for (int encounter = 0; encounter < 3; encounter++) {
            var before = inputs.stream().map(actor -> state(actor.entityId(), actor.engineering())).toList();
            var result = resolver.resolve(inputs, 600L);
            if (!before.equals(inputs.stream().map(actor -> state(actor.entityId(), actor.engineering())).toList())) {
                throw new AssertionError("Resolver mutated world-owned input ships");
            }
            for (var actor : result.combatants()) {
                var previous = inputs.stream().filter(row -> row.entityId() == actor.entityId()).findFirst().orElseThrow();
                if (!actor.fit().equals(previous.engineering().fit)
                        || actor.runtimeState().consumables().ammunitionCount()
                            > previous.engineering().runtimeState.consumables().ammunitionCount()
                        || actor.runtimeState().consumables().reactionMassKg()
                            > previous.engineering().runtimeState.consumables().reactionMassKg()) {
                    throw new AssertionError("Encounter exit changed fit or refilled finite stores");
                }
            }
            results.add(result);
            inputs = result.combatants().stream().map(actor -> {
                var component = new EngineeringComponent(actor.fit(), actor.runtimeState(), actor.instanceState());
                if (reloadBetweenEncounters) {
                    var saved = state(actor.entityId(), component);
                    var restored = EntityStateMapper.restore(saved);
                    if (!saved.equals(EntityStateMapper.capture(restored))) throw new AssertionError("Encounter-exit save drift");
                    component = restored.getComponent(EngineeringComponent.class);
                }
                var geometry = engagementGeometry.stream()
                        .filter(row -> row.entityId() == actor.entityId())
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Missing encounter geometry for " + actor.entityId()));
                return new ImportedCombatantState(actor.entityId(), actor.side(), component,
                        geometry.xM(), geometry.yM(), geometry.velocityXMps(), geometry.velocityYMps());
            }).toList();
            if (result.termination() == Stage19ExactTacticalEncounterResolver.Termination.PHYSICAL_DESTRUCTION) break;
        }
        return List.copyOf(results);
    }

    private static com.spacesim.persistence.EntityState state(long id, EngineeringComponent component) {
        return EntityStateMapper.capture(new Entity().add(new EntityIdComponent(new EntityId(id))).add(component));
    }

    public static void main(String[] args) {
        for (var permutation : Stage22CorePairExperimentProtocol.Permutation.values()) {
            var direct = run(permutation, false);
            if (!direct.equals(run(permutation, true))) throw new AssertionError("Committed-encounter save continuation diverged");
            System.out.println(permutation + "|encounters=" + direct.size() + "|remainingRounds="
                    + direct.get(direct.size() - 1).combatants().stream()
                            .map(actor -> actor.runtimeState().consumables().ammunitionCount()).toList());
        }
    }
}
