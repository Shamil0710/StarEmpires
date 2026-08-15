from pathlib import Path

path = Path("scripts/stage17f_authored_doctrine.py")
text = path.read_text()
old = """anchor = '''    /**
     * Stage-8 совместимый constructor с пустыми policy lists.
'''
"""
new = """anchor = '''    /**
     * Source-compatible diplomacy/territory constructor с нулевой fiscal/economic policy.
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold
     * @param relations directed relations
     * @param controlledSystems controlled systems
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems) {
'''
"""
if text.count(old) != 1:
    raise SystemExit(f"Expected one broken strategic constructor anchor, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
