package com.spacesim.economy;

import com.spacesim.ship.ShipyardRefitContinuity.Completion;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Individual removed modules, distinct from interchangeable pristine product counts. */
public record ShipyardModuleCustodyState(List<StoredModule> modules) {
    /** Physical inventory limit; occupied rows are never silently discarded. */
    public static final int CAPACITY = 4096;

    /**
     * Validates and orders physical custody by stable identity.
     * @param modules exact physical modules
     */
    public ShipyardModuleCustodyState {
        Objects.requireNonNull(modules, "modules");
        if (modules.size() > CAPACITY) throw new IllegalArgumentException("Module custody limit exceeded");
        var sorted = new TreeMap<String, StoredModule>();
        for (var module : modules) {
            Objects.requireNonNull(module, "module");
            if (sorted.putIfAbsent(module.custodyId(), module) != null)
                throw new IllegalArgumentException("Duplicate physical module identity");
        }
        modules = List.copyOf(sorted.values());
    }

    /** @return empty inventory; historical adoption grants no modules */
    public static ShipyardModuleCustodyState empty() { return new ShipyardModuleCustodyState(List.of()); }

    /**
     * Removes exactly identified installed inputs; other physical modules retain their identity and condition.
     * @param custodyIds unique actual input identities
     * @return proposed custody after installation, without changing station stock
     */
    public ShipyardModuleCustodyState withdraw(java.util.Set<String> custodyIds) {
        Objects.requireNonNull(custodyIds);
        var remaining = modules.stream().filter(m -> !custodyIds.contains(m.custodyId())).toList();
        if (modules.size() - remaining.size() != custodyIds.size()) throw new IllegalArgumentException("Unknown used refit input");
        return new ShipyardModuleCustodyState(remaining);
    }

    /**
     * Stages a change of physical storage owner without changing identity or removal evidence.
     * Authorization, travel and finite handling must be checked by the caller before publication.
     * @param custodyId exact individual module
     * @param sourceStorageId original physical owner
     * @param destinationStorageId new physical owner
     * @return proposed inventory retaining condition and historical references
     */
    public ShipyardModuleCustodyState relocate(String custodyId, String sourceStorageId, String destinationStorageId) {
        text(custodyId, "custodyId"); text(sourceStorageId, "sourceStorageId"); text(destinationStorageId, "destinationStorageId");
        if (sourceStorageId.equals(destinationStorageId)) throw new IllegalArgumentException("Equipment already belongs to destination");
        var actual = modules.stream().filter(row -> row.custodyId().equals(custodyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown individual module"));
        if (!actual.stationId().equals(sourceStorageId)) throw new IllegalArgumentException("Wrong equipment source");
        var next = new ArrayList<>(modules); next.remove(actual);
        next.add(new StoredModule(actual.custodyId(), destinationStorageId, actual.sourceAssetId(), actual.removedAtTick(), actual.condition(), actual.ownerActorId()));
        return new ShipyardModuleCustodyState(next);
    }
    /**
     * Records a caller-authorized historical owner's right without transferring another actor's property.
     * @param custodyId actual equipment
     * @param ownerActorId owner verified by the caller's live authority
     * @return same physical custody with the explicit right
     */
    public ShipyardModuleCustodyState declareOwner(String custodyId, String ownerActorId) {
        text(ownerActorId, "ownerActorId");
        var actual = modules.stream().filter(m -> m.custodyId().equals(custodyId)).findFirst().orElseThrow();
        if (actual.ownerActorId() != null && !actual.ownerActorId().equals(ownerActorId))
            throw new IllegalArgumentException("Cannot replace another equipment owner's right");
        var next = new ArrayList<>(modules); next.remove(actual);
        next.add(new StoredModule(actual.custodyId(), actual.stationId(), actual.sourceAssetId(), actual.removedAtTick(), actual.condition(), ownerActorId));
        return new ShipyardModuleCustodyState(next);
    }

    /**
     * Stages one completion's removed modules without modifying stock or the ship.
     * @param operationId unique physical refit identity
     * @param stationId actual receiving station
     * @param completion condition-preserving refit handoff
     * @param tick actual removal tick
     * @return proposed exact custody, rejecting duplicate identities and exhausted capacity
     */
    public ShipyardModuleCustodyState deposit(String operationId, String stationId, Completion completion, long tick) {
        return deposit(operationId, stationId, completion, tick, null);
    }

    /**
     * Deposits removed equipment retaining its actual actor owner independently of storage location.
     * The caller must authorize the completed ship and actor; this method never infers ownership.
     * @param operationId exact completed operation
     * @param stationId physical receiving store
     * @param completion actual condition-preserving removal
     * @param tick actual removal tick
     * @param ownerActorId actual authorized actor, or null for historical storage ownership
     * @return proposed equipment custody
     */
    public ShipyardModuleCustodyState deposit(String operationId, String stationId, Completion completion, long tick, String ownerActorId) {
        operationId = text(operationId, "operationId");
        text(stationId, "stationId");
        if (tick < 0) throw new IllegalArgumentException("Negative removal tick");
        Objects.requireNonNull(completion, "completion");
        if ((long) modules.size() + completion.removedModules().size() > CAPACITY)
            throw new IllegalArgumentException("Module custody limit exceeded");
        var next = new ArrayList<>(modules);
        for (var removed : completion.removedModules()) next.add(new StoredModule(
                operationId + '/' + removed.assignment().mountId(), stationId,
                completion.assetId().value(), tick, removed, ownerActorId));
        return new ShipyardModuleCustodyState(next);
    }

    /**
     * One physical module and its historical removal evidence.
     * @param custodyId stable individual inventory identity
     * @param stationId canonical storage owner
     * @param sourceAssetId original physical ship ID, retained even after its loss
     * @param removedAtTick actual removal tick
     * @param condition original mount, module definition, damage and service age
     * @param ownerActorId actual actor owner independent of storage, or null for legacy custody
     */
    public record StoredModule(String custodyId, String stationId, long sourceAssetId,
            long removedAtTick, RemovedModuleState condition, String ownerActorId) {
        /**
         * Retains historical custody without synthesizing an actor ownership grant.
         * @param custodyId exact equipment identity
         * @param stationId physical storage
         * @param sourceAssetId original ship
         * @param removedAtTick removal tick
         * @param condition original condition
         */
        public StoredModule(String custodyId, String stationId, long sourceAssetId,
                long removedAtTick, RemovedModuleState condition) {
            this(custodyId, stationId, sourceAssetId, removedAtTick, condition, null);
        }
        /**
         * Validates a physical custody row.
         * @param custodyId stable inventory identity
         * @param stationId canonical storage owner
         * @param sourceAssetId original ship ID
         * @param removedAtTick removal tick
         * @param condition preserved module condition
         * @param ownerActorId actual actor owner, or null for historical custody
         */
        public StoredModule {
            custodyId = text(custodyId, "custodyId");
            stationId = text(stationId, "stationId");
            if (sourceAssetId <= 0 || removedAtTick < 0) throw new IllegalArgumentException("Invalid removal evidence");
            Objects.requireNonNull(condition, "condition");
            text(condition.assignment().mountId(), "mountId");
            text(condition.assignment().moduleId(), "moduleId");
            if (ownerActorId != null) ownerActorId = text(ownerActorId, "ownerActorId");
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
