package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;

import java.util.Objects;

/**
 * M22.8A identity-preserving Stage-17.5 engineering materialization seam for one small craft.
 *
 * <p>This bridge deliberately materializes only the already-authoritative engineering payload. It
 * owns no position, mission, hangar, tactical AI or combat timeline; those remain later M22.8
 * authorities. The returned {@link EngineeringComponent} is an independent mutable-by-replacement
 * ECS shell over the craft's immutable Stage-17.5 value state. Dematerialization preserves the
 * original craft identity/faction/design metadata and returns the exact fitted/runtime/instance
 * values carried by the component. Admission back into a live registry is still validated through
 * {@link SmallCraftRegistry#commitMaterializedEngineering(SmallCraftId, EngineeringComponent)}.</p>
 */
public final class SmallCraftEngineeringMaterializationBridge {
    private SmallCraftEngineeringMaterializationBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * Materializes one individual craft's Stage-17.5 engineering state without granting resources.
     *
     * @param state authoritative individual craft state
     * @return independent ECS engineering component carrying the exact fit/runtime/instance state
     */
    public static EngineeringComponent materialize(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        return new EngineeringComponent(
                checked.fit(),
                checked.runtimeState(),
                checked.instanceState());
    }

    /**
     * Reconstitutes persistent individual state from one materialized engineering component.
     *
     * <p>Identity, faction and authored design identity come only from the pre-existing craft. The
     * component may change physical engineering state during lawful simulation, but it cannot mint a
     * new identity through dematerialization.</p>
     *
     * @param identitySource pre-existing craft supplying stable identity metadata
     * @param engineering materialized authoritative Stage-17.5 component
     * @return individual state preserving identity and the component's exact physical state
     */
    public static SmallCraftState dematerialize(
            SmallCraftState identitySource,
            EngineeringComponent engineering) {
        SmallCraftState source = Objects.requireNonNull(identitySource, "identitySource");
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        return new SmallCraftState(
                source.id(),
                source.stableFactionId(),
                source.designId(),
                Objects.requireNonNull(component.fit, "engineering.fit"),
                Objects.requireNonNull(component.runtimeState, "engineering.runtimeState"),
                Objects.requireNonNull(component.instanceState, "engineering.instanceState"));
    }
}
