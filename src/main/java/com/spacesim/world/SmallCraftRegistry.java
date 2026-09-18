package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * M22.8 identity registry for individual physical small craft.
 *
 * <p>The registry owns identity continuity only. It neither manufactures craft nor replenishes any
 * physical resource. Every registered/restored/replaced craft state is validated through one bound
 * {@link SmallCraftFitAuthority}, so identity continuity cannot bypass the ordinary Stage-17.5
 * production-content and physical fitting budgets. Future Stage-18 integration must still authorize
 * physical production before reserving an ID and registering its supplied engineering state.</p>
 */
public final class SmallCraftRegistry {
    private final SmallCraftFitAuthority fitAuthority;
    private final SmallCraftIdAllocator allocator;
    private final TreeMap<SmallCraftId, SmallCraftState> craftById = new TreeMap<>();

    private SmallCraftRegistry(
            SmallCraftFitAuthority fitAuthority,
            SmallCraftIdAllocator allocator,
            Collection<SmallCraftState> states) {
        this.fitAuthority = Objects.requireNonNull(fitAuthority, "fitAuthority");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        Objects.requireNonNull(states, "states");
        for (SmallCraftState state : states) {
            SmallCraftState checked = Objects.requireNonNull(state, "small-craft state");
            this.fitAuthority.requireValid(checked);
            if (craftById.putIfAbsent(checked.id(), checked) != null) {
                throw new IllegalArgumentException("Duplicate small-craft ID: " + checked.id());
            }
        }
        long maxId = craftById.isEmpty() ? 0L : craftById.lastKey().value();
        if (allocator.nextValue() <= maxId) {
            throw new IllegalArgumentException("Small-craft allocator watermark must exceed every existing ID");
        }
    }

    /**
     * Creates a new empty registry with no free or seeded craft.
     *
     * @param fitAuthority production-content and Stage-17.5 fitting authority
     * @return empty identity registry bound to the supplied production catalog
     */
    public static SmallCraftRegistry empty(SmallCraftFitAuthority fitAuthority) {
        return new SmallCraftRegistry(
                Objects.requireNonNull(fitAuthority, "fitAuthority"),
                SmallCraftIdAllocator.empty(),
                List.of());
    }

    /**
     * Restores exact persistent registry contents and validates every craft against production content.
     *
     * @param nextId next unused allocator value
     * @param states individual physical craft states
     * @param fitAuthority production-content and Stage-17.5 fitting authority
     * @return independent restored registry
     */
    public static SmallCraftRegistry restore(
            long nextId,
            Collection<SmallCraftState> states,
            SmallCraftFitAuthority fitAuthority) {
        return new SmallCraftRegistry(
                Objects.requireNonNull(fitAuthority, "fitAuthority"),
                SmallCraftIdAllocator.restore(nextId),
                states);
    }

    /**
     * Reserves one stable identity after an external physical-production authority has completed a craft.
     *
     * <p>This method creates no craft state and grants no material. The returned ID must be paired with
     * the exact physically produced engineering state through {@link #registerProducedCraft(SmallCraftState)}.</p>
     *
     * @return newly reserved identity
     */
    public SmallCraftId reserveIdentityForCompletedProduction() {
        return allocator.allocate();
    }

    /**
     * Registers an externally authorized completed physical craft.
     *
     * <p>The physical-production authorization remains external (M22.8G), but the supplied craft must
     * already resolve to one authored production fit and pass the ordinary Stage-17.5 fitting validator.</p>
     *
     * @param state exact individual craft state using a previously reserved ID
     */
    public void registerProducedCraft(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        if (checked.id().value() >= allocator.nextValue()) {
            throw new IllegalArgumentException("Small-craft ID was not reserved by this registry");
        }
        if (craftById.containsKey(checked.id())) {
            throw new IllegalArgumentException("Small-craft ID already registered: " + checked.id());
        }
        fitAuthority.requireValid(checked);
        craftById.put(checked.id(), checked);
    }

    /**
     * Materializes the exact Stage-17.5 engineering payload of one existing craft into an ECS component.
     *
     * <p>This operation does not remove the persistent craft, change identity, grant consumables or assign
     * any tactical/hangar state. Later M22.8 tactical integration may attach the returned component to an
     * entity while this registry remains the identity owner.</p>
     *
     * @param id stable craft identity
     * @return independent engineering component carrying the craft's exact physical state
     */
    public EngineeringComponent materializeEngineering(SmallCraftId id) {
        SmallCraftState current = requireExisting(id);
        return SmallCraftEngineeringMaterializationBridge.materialize(current);
    }

    /**
     * Commits a materialized Stage-17.5 engineering component back into the same persistent craft.
     *
     * <p>The bridge preserves identity/faction/design metadata, and the resulting state is revalidated
     * against the bound production fit authority before it replaces the persistent physical state. No
     * ammunition, reaction mass, damage or maintenance value is reset during the transition.</p>
     *
     * @param id stable pre-existing craft identity
     * @param engineering exact materialized engineering component after lawful simulation
     */
    public void commitMaterializedEngineering(
            SmallCraftId id,
            EngineeringComponent engineering) {
        SmallCraftState current = requireExisting(id);
        replacePhysicalState(SmallCraftEngineeringMaterializationBridge.dematerialize(
                current,
                Objects.requireNonNull(engineering, "engineering")));
    }

    /**
     * Replaces physical state for an existing craft without changing its identity or ownership/design identity.
     *
     * <p>The replacement must still match the authored production fit and ordinary Stage-17.5 physical
     * fitting budgets, preventing save/load or commit-back code from silently installing an ad-hoc fit.</p>
     *
     * @param state updated physical state
     */
    public void replacePhysicalState(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        SmallCraftState current = craftById.get(checked.id());
        if (current == null) {
            throw new IllegalArgumentException("Unknown small-craft ID: " + checked.id());
        }
        if (!current.stableFactionId().equals(checked.stableFactionId())
                || !current.designId().equals(checked.designId())) {
            throw new IllegalArgumentException("Physical-state replacement cannot rewrite craft identity metadata");
        }
        fitAuthority.requireValid(checked);
        craftById.put(checked.id(), checked);
    }

    /**
     * Resolves current physical bay footprint for one registered craft through the same production
     * content/fitting authority used at admission.
     *
     * @param id stable craft identity
     * @return current loaded mass and authored hull envelope
     */
    public SmallCraftHangarCapacity.CraftFootprint physicalFootprint(SmallCraftId id) {
        return fitAuthority.physicalFootprint(requireExisting(id));
    }

    /**
     * @param id stable craft identity
     * @return current individual craft state when present
     */
    public Optional<SmallCraftState> find(SmallCraftId id) {
        return Optional.ofNullable(craftById.get(Objects.requireNonNull(id, "id")));
    }

    /** @return immutable deterministic ascending-ID snapshot */
    public List<SmallCraftState> snapshot() {
        return List.copyOf(new ArrayList<>(craftById.values()));
    }

    /** @return number of individually registered physical craft */
    public int size() {
        return craftById.size();
    }

    /** @return exact next unused identity watermark for persistence */
    public long nextIdValue() {
        return allocator.nextValue();
    }

    /** @return semantic fingerprint of the production engineering catalog bound to this registry */
    public String engineeringCatalogFingerprint() {
        return fitAuthority.catalogFingerprint();
    }

    private SmallCraftState requireExisting(SmallCraftId id) {
        SmallCraftId checkedId = Objects.requireNonNull(id, "id");
        SmallCraftState current = craftById.get(checkedId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown small-craft ID: " + checkedId);
        }
        return current;
    }
}
