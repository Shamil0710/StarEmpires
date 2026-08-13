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
    "        this.fleetWorldService = new FleetWorldService(\n"
    "                this.sessionsById, nextFleetIdValue, fleetPlacements);\n"
    "        this.fleetJumpService = new FleetJumpService(\n"
    "                topology, this.sessionsById, this.fleetWorldService,\n"
    "                JumpTransitTiming.DEFAULT, fleetJumpStates);\n"
    "        this.activeSystemId = activeSystemId;\n"
    "        this.strategicStepTicks = strategicStepTicks;\n"
    "        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;",
    "        this.fleetWorldService = new FleetWorldService(\n"
    "                this.sessionsById, nextFleetIdValue, fleetPlacements);\n"
    "        this.activeSystemId = activeSystemId;\n"
    "        this.strategicStepTicks = strategicStepTicks;\n"
    "        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;\n"
    "        this.fleetJumpService = new FleetJumpService(\n"
    "                topology, this.sessionsById, this.fleetWorldService,\n"
    "                JumpTransitTiming.DEFAULT, fleetJumpStates, this::synchronizeSystemToTick);",
    "constructor ordering",
)

replace_once(
    "        for (StarSystemId systemId : order) {\n"
    "            SimulationSession session = sessions.get(systemId);\n"
    "            if (Float.floatToIntBits(session.getClock().getFixedStepSeconds()) != fixedStepBits) {\n"
    "                throw new IllegalArgumentException(\"StarSystem sessions используют разные fixed-step durations\");\n"
    "            }\n"
    "            if (!systemId.equals(activeId) && session.getClock().getTick() > activeTick) {\n"
    "                throw new IllegalArgumentException(\"Remote StarSystem не может опережать active system: \" + systemId);\n"
    "            }\n"
    "        }\n\n"
    "        return new WorldSimulation(",
    "        for (StarSystemId systemId : order) {\n"
    "            SimulationSession session = sessions.get(systemId);\n"
    "            if (Float.floatToIntBits(session.getClock().getFixedStepSeconds()) != fixedStepBits) {\n"
    "                throw new IllegalArgumentException(\"StarSystem sessions используют разные fixed-step durations\");\n"
    "            }\n"
    "            if (!systemId.equals(activeId) && session.getClock().getTick() > activeTick) {\n"
    "                throw new IllegalArgumentException(\"Remote StarSystem не может опережать active system: \" + systemId);\n"
    "            }\n"
    "        }\n"
    "        for (FleetJumpState jump : checked.fleetJumps()) {\n"
    "            if (jump.phaseStartedTick() > activeTick || jump.phaseEndsTick() <= activeTick) {\n"
    "                throw new IllegalArgumentException(\n"
    "                        \"Active jump phase не охватывает authoritative world tick: \" + jump.fleetId());\n"
    "            }\n"
    "        }\n\n"
    "        return new WorldSimulation(",
    "restore jump tick invariant",
)

replace_once(
    "        SimulationSession active = sessionsById.get(activeSystemId);\n"
    "        int localTicks = active.advanceFrame(realDeltaSeconds);\n"
    "        totalLocalFixedTicksExecuted = safeAdd(totalLocalFixedTicksExecuted, localTicks);\n\n"
    "        int strategicUpdates = 0;\n"
    "        long activeTick = active.getClock().getTick();",
    "        SimulationSession active = sessionsById.get(activeSystemId);\n"
    "        int localTicks = active.advanceFrame(realDeltaSeconds, fleetJumpService::advance);\n"
    "        totalLocalFixedTicksExecuted = safeAdd(totalLocalFixedTicksExecuted, localTicks);\n\n"
    "        int strategicUpdates = 0;\n"
    "        long activeTick = active.getClock().getTick();",
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

path.write_text(text)
