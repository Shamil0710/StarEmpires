from pathlib import Path

path = Path("src/main/java/com/spacesim/world/FleetJumpService.java")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import java.util.Optional;\n",
    "import java.util.Optional;\nimport java.util.function.BiConsumer;\n",
    "import",
)
replace_once(
    "    private final JumpTransitTiming timing;\n"
    "    private final Map<FleetId, FleetJumpState> jumpsByFleetId = new HashMap<>();",
    "    private final JumpTransitTiming timing;\n"
    "    private final BiConsumer<StarSystemId, Long> systemSynchronizer;\n"
    "    private final Map<FleetId, FleetJumpState> jumpsByFleetId = new HashMap<>();",
    "field",
)
old_ctor = """    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            List<FleetJumpState> initialStates) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        this.sessionsById = Map.copyOf(Objects.requireNonNull(sessionsById, "Simulation sessions не заданы"));
        this.fleetWorldService = Objects.requireNonNull(fleetWorldService, "FleetWorldService не задан");
        this.timing = Objects.requireNonNull(timing, "JumpTransitTiming не задан");
        for (FleetJumpState state : Objects.requireNonNull(initialStates, "Jump states не заданы")) {
"""
new_ctor = """    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            List<FleetJumpState> initialStates) {
        this(topology, sessionsById, fleetWorldService, timing, initialStates,
                defaultSynchronizer(sessionsById));
    }

    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            List<FleetJumpState> initialStates,
            BiConsumer<StarSystemId, Long> systemSynchronizer) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        this.sessionsById = Map.copyOf(Objects.requireNonNull(sessionsById, "Simulation sessions не заданы"));
        this.fleetWorldService = Objects.requireNonNull(fleetWorldService, "FleetWorldService не задан");
        this.timing = Objects.requireNonNull(timing, "JumpTransitTiming не задан");
        this.systemSynchronizer = Objects.requireNonNull(systemSynchronizer, "System synchronizer не задан");
        for (FleetJumpState state : Objects.requireNonNull(initialStates, "Jump states не заданы")) {
"""
replace_once(old_ctor, new_ctor, "constructor")
replace_once(
    "            case JUMP_PENDING -> {\n"
    "                FleetPlacementState transit = fleetWorldService.beginTransfer(",
    "            case JUMP_PENDING -> {\n"
    "                systemSynchronizer.accept(state.originSystemId(), boundary);\n"
    "                FleetPlacementState transit = fleetWorldService.beginTransfer(",
    "origin sync",
)
replace_once(
    "            case IN_TRANSIT -> {\n"
    "                FleetPlacementState arrived = fleetWorldService.completeTransfer(",
    "            case IN_TRANSIT -> {\n"
    "                systemSynchronizer.accept(state.destinationSystemId(), boundary);\n"
    "                FleetPlacementState arrived = fleetWorldService.completeTransfer(",
    "destination sync",
)
helper_marker = """    private static long addTicks(long start, long duration) {
"""
helper = """    private static BiConsumer<StarSystemId, Long> defaultSynchronizer(
            Map<StarSystemId, SimulationSession> sessionsById) {
        Map<StarSystemId, SimulationSession> sessions = Map.copyOf(
                Objects.requireNonNull(sessionsById, "Simulation sessions не заданы"));
        return (systemId, targetTick) -> {
            SimulationSession session = sessions.get(systemId);
            if (session == null) {
                throw new IllegalStateException(
                        "Missing SimulationSession for jump system: " + systemId);
            }
            long currentTick = session.getClock().getTick();
            if (currentTick > targetTick) {
                throw new IllegalStateException(
                        "Jump boundary is behind local system clock: " + systemId);
            }
            while (currentTick < targetTick) {
                long remaining = targetTick - currentTick;
                int step = (int) Math.min((long) Integer.MAX_VALUE, remaining);
                session.advanceStrategicSteps(step);
                currentTick = session.getClock().getTick();
            }
        };
    }

"""
replace_once(helper_marker, helper + helper_marker, "helper")
path.write_text(text)
