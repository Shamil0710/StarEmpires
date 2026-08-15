# WorldSimulation: own diplomacy runtime and materialize access on authoritative tick.
path = Path('src/main/java/com/spacesim/world/WorldSimulation.java')
text = path.read_text()
text = replace_once(text,
    '    private final TerritorialControlRuntime territorialControlRuntime;\n    private final DestructionService destructionService;\n',
    '    private final TerritorialControlRuntime territorialControlRuntime;\n    private final FactionDiplomacyRuntime diplomacyRuntime;\n    private final DestructionService destructionService;\n',
    'WorldSimulation diplomacy field')
text = replace_once(text,
    '            Map<String, FactionEconomicAccount> factionAccountsById,\n            List<FactionStrategicState> factionStrategies,\n            long nextConstructionProjectIdValue,',
    '            Map<String, FactionEconomicAccount> factionAccountsById,\n            List<FactionStrategicState> factionStrategies,\n            List<FactionDiplomacyState> factionDiplomacyStates,\n            long nextConstructionProjectIdValue,',
    'WorldSimulation constructor diplomacy param')
text = replace_once(text,
    '        this.territorialControlRuntime = new TerritorialControlRuntime(\n                topology,\n                this.sessionsById,\n                this.factionIdentityResolver,\n                this.constructionProjectService,\n                factionStrategies);\n        this.destructionService = new DestructionService(',
    '        this.territorialControlRuntime = new TerritorialControlRuntime(\n                topology,\n                this.sessionsById,\n                this.factionIdentityResolver,\n                this.constructionProjectService,\n                factionStrategies);\n        this.diplomacyRuntime = new FactionDiplomacyRuntime(\n                this.factionIdentityResolver, factionDiplomacyStates);\n        this.destructionService = new DestructionService(',
    'WorldSimulation diplomacy runtime init')
text = replace_once(text,
    '        this.activeSystemId = activeSystemId;\n        this.strategicStepTicks = strategicStepTicks;\n        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;\n    }',
    '        this.activeSystemId = activeSystemId;\n        this.strategicStepTicks = strategicStepTicks;\n        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;\n        refreshFactionMarketAccess();\n    }',
    'WorldSimulation initial diplomacy refresh')
text = replace_once(text,
    '                factionAccounts,\n                checked.factionStrategies(),\n                checked.nextConstructionProjectIdValue(),',
    '                factionAccounts,\n                checked.factionStrategies(),\n                checked.factionDiplomacyStates(),\n                checked.nextConstructionProjectIdValue(),',
    'WorldSimulation restore diplomacy argument')
text = replace_once(text,
    '        constructionProjectService.advance();\n        territorialControlRuntime.advance(activeTick);\n        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));',
    '        constructionProjectService.advance();\n        territorialControlRuntime.advance(activeTick);\n        if (diplomacyRuntime.marketAccessExpiryCrossed(activeTick)) {\n            refreshFactionMarketAccess();\n        }\n        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));',
    'WorldSimulation diplomacy expiry refresh')
identity_marker = '''    /** @return canonical immutable world-defined faction identities */
    public List<WorldFactionIdentityState> getWorldFactionIdentities() {
        return factionIdentityResolver.dynamicIdentities();
    }

'''
identity_replacement = identity_marker + '''    /** @return canonical immutable Stage-17E faction diplomacy aggregates */
    public List<FactionDiplomacyState> getFactionDiplomacyStates() {
        return diplomacyRuntime.snapshots();
    }

    /**
     * Finds persistent institutional diplomacy for one authored or world-defined faction.
     *
     * @param factionContentId stable faction ID
     * @return diplomacy aggregate or empty
     */
    public Optional<FactionDiplomacyState> findFactionDiplomacyState(String factionContentId) {
        return Optional.ofNullable(diplomacyRuntime.find(factionContentId));
    }

    /**
     * Evaluates explainable effective legal market access at the authoritative world tick.
     *
     * @param marketOwnerFactionContentId faction owning the market
     * @param participantFactionContentId participant faction, or null for unfactioned
     * @return precedence decision: embargo, treaty right, or relation threshold
     */
    public DiplomaticMarketAccessResolver.Decision evaluateFactionMarketAccess(
            String marketOwnerFactionContentId,
            String participantFactionContentId) {
        return DiplomaticMarketAccessResolver.evaluate(
                territorialControlRuntime.snapshots(),
                diplomacyRuntime.snapshots(),
                marketOwnerFactionContentId,
                participantFactionContentId,
                getAuthoritativeWorldTick());
    }

'''
text = replace_once(text, identity_marker, identity_replacement, 'WorldSimulation diplomacy read API')
text = replace_once(text,
    '                fleetJumpService.snapshots(),\n                factionIdentityResolver.dynamicIdentities());',
    '                fleetJumpService.snapshots(),\n                factionIdentityResolver.dynamicIdentities(),\n                diplomacyRuntime.snapshots());',
    'WorldSimulation snapshot diplomacy')
text = replace_once(text,
    '                new WorldTradeRouteCostModel(contentCatalog, territorialControlRuntime.snapshots()));',
    '                new WorldTradeRouteCostModel(\n                        factionIdentityResolver, territorialControlRuntime.snapshots()));',
    'WorldSimulation unified route identity')
helper = '''
    private void refreshFactionMarketAccess() {
        long worldTick = getAuthoritativeWorldTick();
        for (SimulationSession session : sessionsById.values()) {
            FactionPolicyRuntime.install(
                    session,
                    factionIdentityResolver,
                    territorialControlRuntime.snapshots(),
                    diplomacyRuntime.snapshots(),
                    worldTick);
        }
        diplomacyRuntime.noteMarketAccessPolicyRefreshed(worldTick);
    }
'''
index = text.rfind('\n}')
if index < 0:
    raise SystemExit('WorldSimulation final brace not found')
text = text[:index] + helper + text[index:]
path.write_text(text)

path = Path('docs/development_roadmap.md')
text = path.read_text()
heading = '## 17E — diplomacy / market access / tariffs\n\n'
status = ('## 17E — diplomacy / market access / tariffs\n\n'
          '**ACTIVE.** 17E.1 persistent institutional diplomacy и 17E.3 market-access precedence реализуются первым production slice: explicit trust/credibility, grievances, treaty directory, embargo state и единый `embargo → treaty right → relation threshold` resolver поверх authored + world-defined faction identities.\n\n')
text = replace_once(text, heading, status, 'Roadmap Stage17E ACTIVE status')
path.write_text(text)
