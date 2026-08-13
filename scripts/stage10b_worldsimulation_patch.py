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
    "    private final FleetWorldService fleetWorldService;\n"
    "    private final StarSystemId activeSystemId;",
    "    private final FleetWorldService fleetWorldService;\n"
    "    private final FleetJumpService fleetJumpService;\n"
    "    private StarSystemId activeSystemId;",
    "fields",
)

replace_once(
    "            long nextFleetIdValue,\n"
    "            List<FleetPlacementState> fleetPlacements,\n"
    "            StarSystemId activeSystemId,",
    "            long nextFleetIdValue,\n"
    "            List<FleetPlacementState> fleetPlacements,\n"
    "            List<FleetJumpState> fleetJumpStates,\n"
    "            StarSystemId activeSystemId,",
    "constructor params",
)

replace_once(
    "        this.fleetWorldService = new FleetWorldService(\n"
    "                this.sessionsById, nextFleetIdValue, fleetPlacements);\n"
    "        this.activeSystemId = activeSystemId;",
    "        this.fleetWorldService = new FleetWorldService(\n"
    "                this.sessionsById, nextFleetIdValue, fleetPlacements);\n"
    "        this.fleetJumpService = new FleetJumpService(\n"
    "                topology, this.sessionsById, this.fleetWorldService,\n"
    "                JumpTransitTiming.DEFAULT, fleetJumpStates);\n"
    "        this.activeSystemId = activeSystemId;",
    "constructor service",
)

replace_once(
    "                checked.nextFleetIdValue(),\n"
    "                checked.fleets(),\n"
    "                activeId,",
    "                checked.nextFleetIdValue(),\n"
    "                checked.fleets(),\n"
    "                checked.fleetJumps(),\n"
    "                activeId,",
    "restore args",
)

replace_once(
    "        constructionProjectService.advance();\n"
    "        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));",
    "        constructionProjectService.advance();\n"
    "        fleetJumpService.advance(activeTick);\n"
    "        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));",
    "advance jump service",
)

replace_once(
    "                fleetWorldService.nextIdValue(),\n"
    "                fleetWorldService.snapshots());",
    "                fleetWorldService.nextIdValue(),\n"
    "                fleetWorldService.snapshots(),\n"
    "                fleetJumpService.snapshots());",
    "snapshot jumps",
)

insert_marker = """    /**
     * Передаёт fleet из local SimulationSession во world-owned transit state.
"""
insert_api = """    /** @return immutable active jump states sorted by stable FleetId */
    public List<FleetJumpState> getFleetJumpStates() {
        return fleetJumpService.snapshots();
    }

    /**
     * Ищет active jump operation fleet.
     *
     * @param fleetId stable FleetId or {@code null}
     * @return current jump phase or empty
     */
    public Optional<FleetJumpState> findFleetJump(FleetId fleetId) {
        return fleetJumpService.find(fleetId);
    }

    /**
     * Запрашивает authoritative direct jump по topology edge.
     *
     * <p>Если fleet находится в remote system, её local session сначала догоняется ровно до
     * текущего world tick через тот же coarse simulation core. После этого jump FSM использует
     * абсолютные tick boundaries и не зависит от render-frame partitioning.</p>
     *
     * @param fleetId stable fleet identity
     * @param destinationSystemId directly connected destination
     * @param arrivalX finite destination-local arrival X
     * @param arrivalY finite destination-local arrival Y
     * @return persistent MOVING_TO_JUMP state
     */
    public FleetJumpState requestFleetJump(
            FleetId fleetId,
            StarSystemId destinationSystemId,
            float arrivalX,
            float arrivalY) {
        FleetPlacementState placement = fleetWorldService.find(
                Objects.requireNonNull(fleetId, "FleetId jump request не задан")).orElseThrow(
                () -> new IllegalArgumentException("Unknown FleetId: " + fleetId));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Jump request требует fleet в local StarSystem: " + fleetId);
        }
        long worldTick = sessionsById.get(activeSystemId).getClock().getTick();
        synchronizeSystemToTick(placement.systemId(), worldTick);
        return fleetJumpService.requestJump(
                fleetId, destinationSystemId, worldTick, arrivalX, arrivalY);
    }

"""
replace_once(insert_marker, insert_api + insert_marker, "jump API insertion")

replace_once(
    "        Optional<FleetId> fleetId = fleetWorldService.findByLocal(systemId, entityId);\n"
    "        DestructionResult result = destructionService.destroy(systemId, entityId, policy);\n"
    "        if (fleetId.isPresent() && !fleetWorldService.unregisterLocal(systemId, entityId)) {",
    "        Optional<FleetId> fleetId = fleetWorldService.findByLocal(systemId, entityId);\n"
    "        DestructionResult result = destructionService.destroy(systemId, entityId, policy);\n"
    "        fleetId.ifPresent(fleetJumpService::remove);\n"
    "        if (fleetId.isPresent() && !fleetWorldService.unregisterLocal(systemId, entityId)) {",
    "destruction cleanup",
)

replace_once(
    "        boolean removed = session.removeEntity(entityId);\n"
    "        if (removed && fleetId.isPresent()\n"
    "                && !fleetWorldService.unregisterLocal(checkedSystemId, entityId)) {\n"
    "            throw new IllegalStateException(\n"
    "                    \"Removed fleet lost world mapping before unregister: \" + fleetId.orElseThrow());\n"
    "        }\n"
    "        return removed;",
    "        boolean removed = session.removeEntity(entityId);\n"
    "        if (removed && fleetId.isPresent()) {\n"
    "            FleetId removedFleetId = fleetId.orElseThrow();\n"
    "            fleetJumpService.remove(removedFleetId);\n"
    "            if (!fleetWorldService.unregisterLocal(checkedSystemId, entityId)) {\n"
    "                throw new IllegalStateException(\n"
    "                        \"Removed fleet lost world mapping before unregister: \" + removedFleetId);\n"
    "            }\n"
    "        }\n"
    "        return removed;",
    "remove cleanup",
)

activate_marker = """    /** @return число local ticks в одном remote update */
    public int getStrategicStepTicks() {
"""
activate_api = """    /**
     * Переключает StarSystem полного local tick без изменения transit state других fleets.
     *
     * <p>Target session сначала детерминированно догоняется до текущего authoritative world tick;
     * после этого прежняя active system становится обычной remote session с тем же tick.</p>
     *
     * @param systemId target StarSystem
     */
    public void activateSystem(StarSystemId systemId) {
        StarSystemId target = Objects.requireNonNull(systemId, "Target active StarSystem не задан");
        if (!sessionsById.containsKey(target)) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + target);
        }
        if (target.equals(activeSystemId)) {
            return;
        }
        long worldTick = sessionsById.get(activeSystemId).getClock().getTick();
        synchronizeSystemToTick(target, worldTick);
        activeSystemId = target;
    }

"""
replace_once(activate_marker, activate_api + activate_marker, "activate system insertion")

helper_marker = """    private FactionEconomicAccount requireFactionAccount(String factionId) {
"""
helper = """    private void synchronizeSystemToTick(StarSystemId systemId, long worldTick) {
        SimulationSession session = requireLifecycleSession(systemId);
        long tick = session.getClock().getTick();
        if (tick > worldTick) {
            throw new IllegalStateException(
                    "StarSystem clock опережает authoritative world tick: " + systemId);
        }
        while (tick < worldTick) {
            long remaining = worldTick - tick;
            int step = (int) Math.min((long) strategicStepTicks, remaining);
            session.advanceStrategicSteps(step);
            totalStrategicUpdatesExecuted = safeAdd(totalStrategicUpdatesExecuted, 1L);
            tick = session.getClock().getTick();
        }
    }

"""
replace_once(helper_marker, helper + helper_marker, "sync helper insertion")

path.write_text(text)
