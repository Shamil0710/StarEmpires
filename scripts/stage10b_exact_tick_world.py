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
    "        SimulationSession active = sessionsById.get(activeSystemId);\n"
    "        int localTicks = active.advanceFrame(realDeltaSeconds);\n"
    "        totalLocalFixedTicksExecuted = safeAdd(totalLocalFixedTicksExecuted, localTicks);",
    "        SimulationSession active = sessionsById.get(activeSystemId);\n"
    "        int localTicks = active.advanceFrame(realDeltaSeconds, this::advanceJumpTransitionsAtTick);\n"
    "        totalLocalFixedTicksExecuted = safeAdd(totalLocalFixedTicksExecuted, localTicks);",
    "fixed-tick callback",
)
replace_once(
    "        constructionProjectService.advance();\n"
    "        fleetJumpService.advance(activeTick);\n"
    "        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));",
    "        constructionProjectService.advance();\n"
    "        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));",
    "remove frame-end jump advance",
)
replace_once(
    "        return new WorldSimulation(\n"
    "                checked.topology(),",
    "        for (FleetJumpState jump : checked.fleetJumps()) {\n"
    "            if (jump.phaseStartedTick() > activeTick || jump.phaseEndsTick() <= activeTick) {\n"
    "                throw new IllegalArgumentException(\n"
    "                        \"Active jump phase does not cover authoritative world tick: \" + jump.fleetId());\n"
    "            }\n"
    "        }\n\n"
    "        return new WorldSimulation(\n"
    "                checked.topology(),",
    "restore jump tick invariant",
)
marker = "    private SimulationSession requireLifecycleSession(StarSystemId systemId) {"
helper = """    private void advanceJumpTransitionsAtTick(long worldTick) {
        for (FleetJumpState jump : fleetJumpService.snapshots()) {
            if (jump.phaseEndsTick() > worldTick) {
                continue;
            }
            switch (jump.phase()) {
                case JUMP_PENDING ->
                        synchronizeSystemToTick(jump.originSystemId(), jump.phaseEndsTick());
                case IN_TRANSIT ->
                        synchronizeSystemToTick(jump.destinationSystemId(), jump.phaseEndsTick());
                case MOVING_TO_JUMP, ARRIVING -> {
                    // No cross-session physical handoff occurs at these boundaries.
                }
            }
        }
        fleetJumpService.advance(worldTick);
    }

"""
replace_once(marker, helper + marker, "jump boundary helper")

path.write_text(text)
