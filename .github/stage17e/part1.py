from pathlib import Path

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected once, found {count}')
    return text.replace(old, new, 1)

path = Path('src/main/java/com/spacesim/world/WorldState.java')
text = path.read_text()
text = replace_once(text,
    '        List<FleetJumpState> fleetJumps,\n        List<WorldFactionIdentityState> factionIdentities) {',
    '        List<FleetJumpState> fleetJumps,\n        List<WorldFactionIdentityState> factionIdentities,\n        List<FactionDiplomacyState> factionDiplomacyStates) {',
    'WorldState record tail')
marker = '    /** Текущая Stage-17 версия world-level persistent schema. */\n'
compatibility = '''    /**
     * Source-compatible pre-Stage-17E constructor with neutral explicit diplomacy.
     *
     * @param schemaVersion world schema version
     * @param topology galaxy topology
     * @param systems local simulation snapshots
     * @param factions faction economic states
     * @param factionStrategies faction strategic states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     * @param factionEconomicPressures persistent economic-pressure states
     * @param nextFleetIdValue fleet allocator watermark
     * @param fleets fleet placements
     * @param fleetJumps active jump states
     * @param factionIdentities world-defined faction identities
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets,
            List<FleetJumpState> fleetJumps,
            List<WorldFactionIdentityState> factionIdentities) {
        this(
                schemaVersion,
                topology,
                systems,
                factions,
                factionStrategies,
                nextConstructionProjectIdValue,
                constructionProjects,
                factionEconomicPressures,
                nextFleetIdValue,
                fleets,
                fleetJumps,
                factionIdentities,
                neutralDiplomacy(factionStrategies));
    }

'''
text = replace_once(text, marker, compatibility + marker, 'WorldState compatibility constructor')
text = replace_once(text,
    '        Objects.requireNonNull(factionIdentities, "World faction identities WorldState не заданы");\n',
    '        Objects.requireNonNull(factionIdentities, "World faction identities WorldState не заданы");\n        Objects.requireNonNull(factionDiplomacyStates, "Faction diplomacy states WorldState not set");\n',
    'WorldState diplomacy null check')
strategy_anchor = '        factionStrategies = List.copyOf(sortedStrategies);\n\n'
diplomacy_validation = '''        factionStrategies = List.copyOf(sortedStrategies);

        List<FactionDiplomacyState> sortedDiplomacy = new ArrayList<>(factionDiplomacyStates.size());
        Set<String> diplomacyFactionIds = new HashSet<>();
        Set<String> treatyIds = new HashSet<>();
        for (FactionDiplomacyState diplomacy : factionDiplomacyStates) {
            FactionDiplomacyState value = Objects.requireNonNull(diplomacy, "FactionDiplomacyState not set");
            if (!strategicFactionIds.contains(value.factionContentId())) {
                throw new IllegalArgumentException("Diplomacy state references unknown strategic faction: "
                        + value.factionContentId());
            }
            if (!diplomacyFactionIds.add(value.factionContentId())) {
                throw new IllegalArgumentException("Duplicate faction diplomacy state: " + value.factionContentId());
            }
            for (DiplomaticStandingState standing : value.standings()) {
                requireDiplomaticTarget(strategicFactionIds, standing.targetFactionContentId());
            }
            for (DiplomaticGrievanceState grievance : value.grievances()) {
                requireDiplomaticTarget(strategicFactionIds, grievance.targetFactionContentId());
            }
            for (DiplomaticTreatyState treaty : value.treaties()) {
                requireDiplomaticTarget(strategicFactionIds, treaty.counterpartyFactionContentId());
                if (!treatyIds.add(treaty.treatyId())) {
                    throw new IllegalArgumentException("Duplicate world treaty ID: " + treaty.treatyId());
                }
                for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
                    if (clause.systemId() != null && topology.findSystem(clause.systemId()).isEmpty()) {
                        throw new IllegalArgumentException("Treaty clause references unknown StarSystem: "
                                + clause.systemId());
                    }
                }
            }
            for (DiplomaticEmbargoState embargo : value.embargoes()) {
                requireDiplomaticTarget(strategicFactionIds, embargo.targetFactionContentId());
            }
            sortedDiplomacy.add(value);
        }
        if (!diplomacyFactionIds.equals(strategicFactionIds)) {
            throw new IllegalArgumentException("Faction diplomacy states must exactly cover strategic factions");
        }
        sortedDiplomacy.sort(Comparator.naturalOrder());
        factionDiplomacyStates = List.copyOf(sortedDiplomacy);

'''
text = replace_once(text, strategy_anchor, diplomacy_validation, 'WorldState diplomacy validation')
helper = '''
    private static List<FactionDiplomacyState> neutralDiplomacy(List<FactionStrategicState> strategies) {
        Objects.requireNonNull(strategies, "Faction strategic states not set");
        List<FactionDiplomacyState> result = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            result.add(FactionDiplomacyState.neutral(
                    Objects.requireNonNull(strategy, "FactionStrategicState not set").factionContentId()));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static void requireDiplomaticTarget(Set<String> knownFactionIds, String targetFactionContentId) {
        if (!knownFactionIds.contains(targetFactionContentId)) {
            throw new IllegalArgumentException("Diplomacy state references unknown target faction: "
                    + targetFactionContentId);
        }
    }
'''
index = text.rfind('\n}')
if index < 0:
    raise SystemExit('WorldState final brace not found')
text = text[:index] + helper + text[index:]
path.write_text(text)
