from pathlib import Path

path = Path("src/main/java/com/spacesim/world/WorldSimulation.java")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "    private final FactionEconomicPressureTracker economicPressureTracker;\n"
    "    private final StarSystemId activeSystemId;",
    "    private final FactionEconomicPressureTracker economicPressureTracker;\n"
    "    private final FleetWorldService fleetWorldService;\n"
    "    private final StarSystemId activeSystemId;",
    "fleet field",
)

replace_once(
    "            List<ConstructionProjectState> constructionProjects,\n"
    "            List<FactionEconomicPressureState> factionEconomicPressures,\n"
    "            StarSystemId activeSystemId,",
    "            List<ConstructionProjectState> constructionProjects,\n"
    "            List<FactionEconomicPressureState> factionEconomicPressures,\n"
    "            long nextFleetIdValue,\n"
    "            List<FleetPlacementState> fleetPlacements,\n"
    "            StarSystemId activeSystemId,",
    "constructor fleet params",
)

replace_once(
    "        this.economicPressureTracker = new FactionEconomicPressureTracker(factionEconomicPressures);\n"
    "        this.activeSystemId = activeSystemId;",
    "        this.economicPressureTracker = new FactionEconomicPressureTracker(factionEconomicPressures);\n"
    "        this.fleetWorldService = new FleetWorldService(\n"
    "                this.sessionsById, nextFleetIdValue, fleetPlacements);\n"
    "        this.activeSystemId = activeSystemId;",
    "constructor fleet service",
)

replace_once(
    "                checked.nextConstructionProjectIdValue(),\n"
    "                checked.constructionProjects(),\n"
    "                checked.factionEconomicPressures(),\n"
    "                activeId,",
    "                checked.nextConstructionProjectIdValue(),\n"
    "                checked.constructionProjects(),\n"
    "                checked.factionEconomicPressures(),\n"
    "                checked.nextFleetIdValue(),\n"
    "                checked.fleets(),\n"
    "                activeId,",
    "restore fleet args",
)

replace_once(
    "                constructionProjectService.nextIdValue(),\n"
    "                constructionProjectService.snapshots(),\n"
    "                economicPressureTracker.snapshots());",
    "                constructionProjectService.nextIdValue(),\n"
    "                constructionProjectService.snapshots(),\n"
    "                economicPressureTracker.snapshots(),\n"
    "                fleetWorldService.nextIdValue(),\n"
    "                fleetWorldService.snapshots());",
    "snapshot fleet state",
)

replace_once(
    "    public DestructionResult destroyEntity(\n"
    "            StarSystemId systemId, EntityId entityId, DestructionPolicy policy) {\n"
    "        return destructionService.destroy(systemId, entityId, policy);\n"
    "    }",
    "    public DestructionResult destroyEntity(\n"
    "            StarSystemId systemId, EntityId entityId, DestructionPolicy policy) {\n"
    "        Optional<FleetId> fleetId = fleetWorldService.findByLocal(systemId, entityId);\n"
    "        DestructionResult result = destructionService.destroy(systemId, entityId, policy);\n"
    "        if (fleetId.isPresent() && !fleetWorldService.unregisterLocal(systemId, entityId)) {\n"
    "            throw new IllegalStateException(\n"
    "                    \"Destroyed fleet lost world mapping before unregister: \" + fleetId.orElseThrow());\n"
    "        }\n"
    "        return result;\n"
    "    }",
    "destruction fleet unregister",
)

replace_once(
    "    public EntityId createEntity(StarSystemId systemId, Entity entity) {\n"
    "        return requireLifecycleSession(systemId).createEntity(\n"
    "                Objects.requireNonNull(entity, \"Создаваемая Entity не задана\"));\n"
    "    }",
    "    public EntityId createEntity(StarSystemId systemId, Entity entity) {\n"
    "        StarSystemId checkedSystemId = Objects.requireNonNull(systemId, \"StarSystemId lifecycle не задан\");\n"
    "        SimulationSession session = requireLifecycleSession(checkedSystemId);\n"
    "        Entity checkedEntity = Objects.requireNonNull(entity, \"Создаваемая Entity не задана\");\n"
    "        IdentityComponent identity = checkedEntity.getComponent(IdentityComponent.class);\n"
    "        boolean fleet = identity != null && identity.kind == IdentityComponent.Kind.FLEET;\n"
    "        EntityId id = session.createEntity(checkedEntity);\n"
    "        if (!fleet) {\n"
    "            return id;\n"
    "        }\n"
    "        try {\n"
    "            fleetWorldService.registerLocal(checkedSystemId, id);\n"
    "            return id;\n"
    "        } catch (RuntimeException exception) {\n"
    "            if (!session.removeEntity(id)) {\n"
    "                exception.addSuppressed(new IllegalStateException(\n"
    "                        \"Fleet registration rollback could not remove local entity: \" + id));\n"
    "            }\n"
    "            throw exception;\n"
    "        }\n"
    "    }",
    "create fleet registration",
)

replace_once(
    "    public boolean removeEntity(StarSystemId systemId, EntityId entityId) {\n"
    "        return requireLifecycleSession(systemId).removeEntity(entityId);\n"
    "    }",
    "    public boolean removeEntity(StarSystemId systemId, EntityId entityId) {\n"
    "        StarSystemId checkedSystemId = Objects.requireNonNull(systemId, \"StarSystemId lifecycle не задан\");\n"
    "        SimulationSession session = requireLifecycleSession(checkedSystemId);\n"
    "        Optional<FleetId> fleetId = fleetWorldService.findByLocal(checkedSystemId, entityId);\n"
    "        boolean removed = session.removeEntity(entityId);\n"
    "        if (removed && fleetId.isPresent()\n"
    "                && !fleetWorldService.unregisterLocal(checkedSystemId, entityId)) {\n"
    "            throw new IllegalStateException(\n"
    "                    \"Removed fleet lost world mapping before unregister: \" + fleetId.orElseThrow());\n"
    "        }\n"
    "        return removed;\n"
    "    }",
    "remove fleet unregister",
)

fleet_api_marker = "    /**\n     * Ищет local simulation session системы.\n"
fleet_api = """    /** @return immutable world-level fleet placements sorted by stable FleetId */
    public List<FleetPlacementState> getFleetPlacements() {
        return fleetWorldService.snapshots();
    }

    /**
     * Ищет world-level placement fleet.
     *
     * @param fleetId stable world FleetId or {@code null}
     * @return current placement or empty
     */
    public Optional<FleetPlacementState> findFleet(FleetId fleetId) {
        return fleetWorldService.find(fleetId);
    }

    /**
     * Разрешает system-local fleet entity в стабильный world FleetId.
     *
     * @param systemId local StarSystem or {@code null}
     * @param entityId system-local EntityId or {@code null}
     * @return stable FleetId or empty
     */
    public Optional<FleetId> findFleetByLocal(StarSystemId systemId, EntityId entityId) {
        return fleetWorldService.findByLocal(systemId, entityId);
    }

    /**
     * Передаёт fleet из local SimulationSession во world-owned transit state.
     *
     * <p>Stage 10A выполняет только identity/location handoff без travel clock. Stage 10B will
     * schedule this boundary through the authoritative jump-transit FSM and deterministic travel
     * duration.</p>
     *
     * @param fleetId stable fleet identity
     * @param destinationSystemId destination StarSystem
     * @return persistent transit placement containing the detached physical fleet snapshot
     */
    public FleetPlacementState beginFleetTransfer(FleetId fleetId, StarSystemId destinationSystemId) {
        return fleetWorldService.beginTransfer(fleetId, destinationSystemId);
    }

    /**
     * Материализует transit fleet в destination local SimulationSession.
     *
     * @param fleetId stable fleet identity
     * @param arrivalX finite destination X coordinate
     * @param arrivalY finite destination Y coordinate
     * @return local placement with a freshly allocated destination EntityId
     */
    public FleetPlacementState completeFleetTransfer(FleetId fleetId, float arrivalX, float arrivalY) {
        return fleetWorldService.completeTransfer(fleetId, arrivalX, arrivalY);
    }

"""
if fleet_api_marker not in text:
    raise SystemExit("fleet API insertion marker not found")
text = text.replace(fleet_api_marker, fleet_api + fleet_api_marker, 1)

path.write_text(text)
