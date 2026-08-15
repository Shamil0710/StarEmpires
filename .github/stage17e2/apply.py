from pathlib import Path


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected once, found {count}')
    return text.replace(old, new, 1)


runtime_path = Path('src/main/java/com/spacesim/world/FactionDiplomacyRuntime.java')
runtime = runtime_path.read_text()
anchor = '''    /** @return true when the cached ECS access projection crossed an activation/expiry boundary */
    boolean marketAccessTransitionCrossed(long worldTick) {
        requireWorldTick(worldTick);
        return nextMarketAccessTransitionTick >= 0L && worldTick >= nextMarketAccessTransitionTick;
    }
'''
replacement = anchor + '''
    /** Source-compatible name retained for the Stage-17E.1 scheduler acceptance test. */
    boolean marketAccessExpiryCrossed(long worldTick) {
        return marketAccessTransitionCrossed(worldTick);
    }
'''
runtime = once(runtime, anchor, replacement, 'runtime compatibility scheduler alias')
runtime_path.write_text(runtime)

world_path = Path('src/main/java/com/spacesim/world/WorldSimulation.java')
world = world_path.read_text()
old_advance = '''        constructionProjectService.advance();
        territorialControlRuntime.advance(activeTick);
        if (diplomacyRuntime.marketAccessExpiryCrossed(activeTick)) {
            refreshFactionMarketAccess();
        }
        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));
'''
new_advance = '''        constructionProjectService.advance();
        territorialControlRuntime.advance(activeTick);
        boolean diplomacyLifecycleChanged = diplomacyRuntime.advanceTime(activeTick);
        if (diplomacyLifecycleChanged || diplomacyRuntime.marketAccessTransitionCrossed(activeTick)) {
            refreshFactionMarketAccess();
        }
        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));
'''
world = once(world, old_advance, new_advance, 'WorldSimulation diplomacy lifecycle advance')

marker = '''    /**
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
addition = marker + '''    /**
     * Finds one persistent treaty by globally unique treaty ID.
     *
     * @param treatyId stable treaty ID
     * @return current treaty snapshot or empty
     */
    public Optional<DiplomaticTreatyState> findDiplomaticTreaty(String treatyId) {
        return Optional.ofNullable(diplomacyRuntime.findTreaty(treatyId));
    }

    /**
     * Applies one common player/AI treaty lifecycle command atomically to persistent diplomacy.
     *
     * <p>Natural treaty expiry is materialized first at the same authoritative tick. A successful
     * command immediately rebuilds transient market-access policy so accepted/breached agreements
     * affect the ordinary trade boundary without save/load. If command validation fails after a
     * natural expiry was materialized, the expiry remains authoritative and the projection is still
     * refreshed before the exception is propagated.</p>
     *
     * @param command treaty lifecycle command from player/UI or AI
     * @return immutable successful transition result
     */
    public DiplomaticTreatyCommandResult applyDiplomaticTreatyCommand(DiplomaticTreatyCommand command) {
        long worldTick = getAuthoritativeWorldTick();
        boolean lifecycleChanged = diplomacyRuntime.advanceTime(worldTick);
        try {
            DiplomaticTreatyCommandResult result = diplomacyRuntime.apply(command, worldTick);
            refreshFactionMarketAccess();
            return result;
        } catch (RuntimeException exception) {
            if (lifecycleChanged) {
                refreshFactionMarketAccess();
            }
            throw exception;
        }
    }

'''
world = once(world, marker, addition, 'WorldSimulation treaty command APIs')
world_path.write_text(world)

roadmap_path = Path('docs/development_roadmap.md')
roadmap = roadmap_path.read_text()
needle = '''### 17E.2 — proposal / response engine

Общий command/evaluator обрабатывает:
'''
replacement = '''### 17E.2 — proposal / response engine

**ACTIVE.** Common player/AI treaty lifecycle реализуется через один authoritative command boundary поверх `FactionDiplomacyRuntime`; lifecycle не создаёт параллельный diplomacy store и после legal transition сразу обновляет ordinary market-access projection.

Общий command/evaluator обрабатывает:
'''
roadmap = once(roadmap, needle, replacement, 'Roadmap Stage17E.2 ACTIVE')
roadmap_path.write_text(roadmap)
