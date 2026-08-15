# WorldStateCodec: file-format v4 adds diplomacy trailer; v1-v3 migrate to neutral state.
path = Path('src/main/java/com/spacesim/persistence/WorldStateCodec.java')
text = path.read_text()
text = replace_once(text,
    'import com.spacesim.world.FactionEconomicState;\n',
    'import com.spacesim.world.FactionEconomicState;\nimport com.spacesim.world.FactionDiplomacyState;\n',
    'WorldStateCodec diplomacy import')
text = replace_once(text,
    '    private static final int GROWTH_FILE_FORMAT_VERSION = 2;\n    private static final int FILE_FORMAT_VERSION = 3;\n',
    '    private static final int GROWTH_FILE_FORMAT_VERSION = 2;\n    private static final int TERRITORY_FILE_FORMAT_VERSION = 3;\n    private static final int FILE_FORMAT_VERSION = 4;\n',
    'WorldStateCodec format constants')
text = replace_once(text,
    '                WorldTerritoryBinary.write(output, checked.factionStrategies());\n',
    '                WorldTerritoryBinary.write(output, checked.factionStrategies());\n                WorldDiplomacyBinary.write(output, checked.factionDiplomacyStates());\n',
    'WorldStateCodec diplomacy write')
text = replace_once(text,
    '            if (fileVersion != FILE_FORMAT_VERSION\n                    && fileVersion != GROWTH_FILE_FORMAT_VERSION\n                    && fileVersion != LEGACY_FILE_FORMAT_VERSION) {',
    '            if (fileVersion != FILE_FORMAT_VERSION\n                    && fileVersion != TERRITORY_FILE_FORMAT_VERSION\n                    && fileVersion != GROWTH_FILE_FORMAT_VERSION\n                    && fileVersion != LEGACY_FILE_FORMAT_VERSION) {',
    'WorldStateCodec version acceptance')
text = replace_once(text,
    '            if (fileVersion >= FILE_FORMAT_VERSION) {\n                List<FactionStrategicState> strategies =\n                        WorldTerritoryBinary.readAndAttach(input, state.factionStrategies());\n                state = withStrategies(state, strategies);\n            }\n\n            if (input.read() != -1) {',
    '            if (fileVersion >= TERRITORY_FILE_FORMAT_VERSION) {\n                List<FactionStrategicState> strategies =\n                        WorldTerritoryBinary.readAndAttach(input, state.factionStrategies());\n                state = withStrategies(state, strategies);\n            }\n            if (fileVersion >= FILE_FORMAT_VERSION) {\n                state = withDiplomacy(state, WorldDiplomacyBinary.read(input));\n            }\n\n            if (input.read() != -1) {',
    'WorldStateCodec decode trailers')
text = replace_once(text,
    '                state.fleetJumps(),\n                state.factionIdentities());\n    }\n}',
    '                state.fleetJumps(),\n                state.factionIdentities(),\n                state.factionDiplomacyStates());\n    }\n\n    private static WorldState withDiplomacy(\n            WorldState state,\n            List<FactionDiplomacyState> diplomacyStates) {\n        return new WorldState(\n                state.schemaVersion(),\n                state.topology(),\n                state.systems(),\n                state.factions(),\n                state.factionStrategies(),\n                state.nextConstructionProjectIdValue(),\n                state.constructionProjects(),\n                state.factionEconomicPressures(),\n                state.nextFleetIdValue(),\n                state.fleets(),\n                state.fleetJumps(),\n                state.factionIdentities(),\n                diplomacyStates);\n    }\n}',
    'WorldStateCodec preserve diplomacy helper')
path.write_text(text)
