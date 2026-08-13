from pathlib import Path
p=Path("src/main/java/com/spacesim/world/WorldSimulation.java")
t=p.read_text()
a="import com.spacesim.simulation.SimulationSession;\n"
b="import com.spacesim.simulation.SimulationSession;\nimport com.spacesim.trade.TradeRoutePlanner;\n"
if t.count(a)!=1: raise SystemExit("import marker")
t=t.replace(a,b,1)
a="    /** @return immutable Galaxy topology этого runtime world */\n"
b="""    /**
     * Creates the canonical Stage-10C galactic trade planner for this world.
     *
     * @param scoringMode route ranking policy
     * @return planner configured with world content and strategic cost policy
     */
    public TradeRoutePlanner createGalacticTradeRoutePlanner(TradeRoutePlanner.ScoringMode scoringMode) {
        return new TradeRoutePlanner(
                contentCatalog,
                Objects.requireNonNull(scoringMode, "Trade route scoring mode не задан"),
                new WorldTradeRouteCostModel(contentCatalog, factionStrategies));
    }

    /** @return path planner whose edge timing matches Stage-10B jump execution */
    public GalacticPathPlanner createGalacticPathPlanner() {
        float fixedStep = sessionsById.get(activeSystemId).getClock().getFixedStepSeconds();
        return new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, fixedStep);
    }

    /** @return immutable Galaxy topology этого runtime world */
"""
if t.count(a)!=1: raise SystemExit("method marker")
t=t.replace(a,b,1)
p.write_text(t)
