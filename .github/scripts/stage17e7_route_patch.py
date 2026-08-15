from pathlib import Path

path = Path("src/main/java/com/spacesim/world/FactionEconomicDependenceAnalyzer.java")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """        List<ObservedMarket> markets = observeMarkets(checkedWorld);
        Set<StarSystemId> sourceFootprint = sourceFootprint(
                checkedWorld, markets, sourceId, sourceStrategy);

        List<FactionItemDependenceDiagnostic> rows = new ArrayList<>();
""",
    """        List<ObservedMarket> markets = observeMarkets(checkedWorld);

        List<FactionItemDependenceDiagnostic> rows = new ArrayList<>();
""",
    "remove global source footprint",
)

replace_once(
    """            Set<StarSystemId> partnerSupplierSystems = new HashSet<>();
            Set<StarSystemId> alternativeSupplierSystems = new HashSet<>();

            for (ObservedMarket observed : markets) {
""",
    """            Set<StarSystemId> partnerSupplierSystems = new HashSet<>();
            Set<StarSystemId> alternativeSupplierSystems = new HashSet<>();
            Set<StarSystemId> sourceDemandSystems = new HashSet<>();
            Set<StarSystemId> sourceMarketSystems = new HashSet<>();

            for (ObservedMarket observed : markets) {
""",
    "add commodity-specific source roots",
)

replace_once(
    """                if (sourceOwned) {
                    sourceTarget = safeAdd(sourceTarget, target);
                    sourceOnHand = safeAdd(sourceOnHand, stock);
                    sourceExportable = safeAdd(sourceExportable, surplus);
                    continue;
                }
""",
    """                if (sourceOwned) {
                    sourceTarget = safeAdd(sourceTarget, target);
                    sourceOnHand = safeAdd(sourceOnHand, stock);
                    sourceExportable = safeAdd(sourceExportable, surplus);
                    sourceMarketSystems.add(observed.systemId());
                    if (deficit > 0L) {
                        sourceDemandSystems.add(observed.systemId());
                    }
                    continue;
                }
""",
    "record physical demand roots",
)

replace_once(
    """            long externalRequirement = Math.max(0L, requiredStock - sourceOnHand);
            long productionInputPerCycle = observeSourceProductionInputPerCycle(
                    checkedWorld, sourceRuntimeId, itemId);
""",
    """            long externalRequirement = Math.max(0L, requiredStock - sourceOnHand);
            if (externalRequirement > 0L && sourceDemandSystems.isEmpty()) {
                if (sourceStrategy != null && !sourceStrategy.controlledSystems().isEmpty()) {
                    sourceDemandSystems.addAll(sourceStrategy.controlledSystems());
                } else {
                    sourceDemandSystems.addAll(sourceMarketSystems);
                }
            }
            long productionInputPerCycle = observeSourceProductionInputPerCycle(
                    checkedWorld, sourceRuntimeId, itemId);
""",
    "fallback roots for strategic-only requirement",
)

replace_once(
    """            RouteMetric partnerRoute = routeMetric(
                    checkedWorld.getTopology(), sourceFootprint, partnerSupplierSystems);
            RouteMetric alternativeRoute = routeMetric(
                    checkedWorld.getTopology(), sourceFootprint, alternativeSupplierSystems);
""",
    """            RouteMetric partnerRoute = routeMetric(
                    checkedWorld.getTopology(), sourceDemandSystems, partnerSupplierSystems);
            RouteMetric alternativeRoute = routeMetric(
                    checkedWorld.getTopology(), sourceDemandSystems, alternativeSupplierSystems);
""",
    "use commodity-specific demand roots",
)

start = text.find("    private static Set<StarSystemId> sourceFootprint(\n")
end = text.find("    private static RouteMetric routeMetric(\n", start)
if start < 0 or end < 0:
    raise SystemExit("sourceFootprint helper anchors not found")
text = text[:start] + text[end:]

path.write_text(text, encoding="utf-8")
