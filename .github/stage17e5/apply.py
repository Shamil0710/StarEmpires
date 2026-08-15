from pathlib import Path


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected once, found {count}')
    return text.replace(old, new, 1)


runtime_path = Path('src/main/java/com/spacesim/world/FactionDiplomacyRuntime.java')
runtime = runtime_path.read_text()
runtime = once(
    runtime,
    '    private static final int BREACH_CREDIBILITY_PENALTY = 25;\n',
    '    private static final int BREACH_CREDIBILITY_PENALTY = 25;\n'
    '    private static final int EMBARGO_GRIEVANCE_SEVERITY = 40;\n',
    'embargo grievance constant')

anchor = '''    DiplomaticTreatyState findTreaty(String treatyId) {
        TreatyLocation location = locateTreaty(treatyId);
        return location == null ? null : location.treaty();
    }

'''
addition = anchor + '''    /**
     * Applies one common unilateral embargo command at the authoritative world tick.
     *
     * @param command player/AI embargo command
     * @param worldTick authoritative world tick
     * @return immutable successful embargo transition result
     */
    DiplomaticEmbargoCommandResult apply(DiplomaticEmbargoCommand command, long worldTick) {
        DiplomaticEmbargoCommand checked = Objects.requireNonNull(command, "Diplomatic embargo command not set");
        requireWorldTick(worldTick);
        requireFaction(checked.actorFactionContentId());
        if (checked instanceof DiplomaticEmbargoCommand.Impose impose) {
            return impose(impose, worldTick);
        }
        if (checked instanceof DiplomaticEmbargoCommand.Revoke revoke) {
            return revoke(revoke, worldTick);
        }
        throw new IllegalArgumentException("Unsupported diplomatic embargo command: " + checked.getClass().getName());
    }

'''
runtime = once(runtime, anchor, addition, 'runtime embargo apply API')

private_anchor = '''    private DiplomaticTreatyCommandResult offer(
            DiplomaticTreatyCommand.Offer offer,
'''
embargo_methods = '''    private DiplomaticEmbargoCommandResult impose(
            DiplomaticEmbargoCommand.Impose command,
            long worldTick) {
        String actorId = requireFaction(command.actorFactionContentId());
        String targetId = requireFaction(command.targetFactionContentId());
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("Faction cannot embargo itself");
        }
        requireFutureExpiry(command.expiresTick(), worldTick);
        FactionDiplomacyState actor = byId.get(actorId);
        DiplomaticEmbargoState existing = marketEmbargoToward(actor, targetId);
        if (existing != null && existing.activeAt(worldTick)) {
            throw new IllegalStateException("Active market embargo already exists: " + actorId + " -> " + targetId);
        }

        DiplomaticEmbargoState embargo = new DiplomaticEmbargoState(
                targetId,
                DiplomaticEmbargoState.Scope.MARKET_ACCESS,
                worldTick,
                command.expiresTick(),
                command.reasonKey());
        FactionDiplomacyState target = byId.get(targetId);
        String grievanceId = allocateEmbargoGrievanceId(target, actorId, worldTick);
        List<DiplomaticGrievanceState> grievances = new ArrayList<>(target.grievances());
        String subject = command.reasonKey().isEmpty()
                ? "embargo:" + actorId + "->" + targetId
                : "embargo:" + actorId + "->" + targetId + ":" + command.reasonKey();
        grievances.add(new DiplomaticGrievanceState(
                grievanceId,
                actorId,
                DiplomaticGrievanceState.Kind.EMBARGO,
                EMBARGO_GRIEVANCE_SEVERITY,
                worldTick,
                -1L,
                subject));

        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        replacements.put(actorId, withMarketEmbargo(actor, embargo));
        replacements.put(targetId, copyState(
                target, target.standings(), grievances, target.treaties(), target.embargoes()));
        install(replaceStates(replacements));
        return new DiplomaticEmbargoCommandResult(
                DiplomaticEmbargoCommandResult.Operation.IMPOSED,
                actorId,
                targetId,
                embargo,
                grievanceId);
    }

    private DiplomaticEmbargoCommandResult revoke(
            DiplomaticEmbargoCommand.Revoke command,
            long worldTick) {
        String actorId = requireFaction(command.actorFactionContentId());
        String targetId = requireFaction(command.targetFactionContentId());
        FactionDiplomacyState actor = byId.get(actorId);
        DiplomaticEmbargoState existing = marketEmbargoToward(actor, targetId);
        if (existing == null || !existing.activeAt(worldTick)) {
            throw new IllegalStateException("No active market embargo to revoke: " + actorId + " -> " + targetId);
        }
        List<DiplomaticEmbargoState> embargoes = new ArrayList<>(actor.embargoes());
        boolean removed = embargoes.removeIf(embargo ->
                embargo.scope() == DiplomaticEmbargoState.Scope.MARKET_ACCESS
                        && embargo.targetFactionContentId().equals(targetId));
        if (!removed) {
            throw new IllegalStateException("Active embargo disappeared during revocation");
        }
        install(replaceStates(Map.of(actorId, copyState(
                actor, actor.standings(), actor.grievances(), actor.treaties(), embargoes))));
        return new DiplomaticEmbargoCommandResult(
                DiplomaticEmbargoCommandResult.Operation.REVOKED,
                actorId,
                targetId,
                existing,
                "");
    }

    private static DiplomaticEmbargoState marketEmbargoToward(
            FactionDiplomacyState state,
            String targetFactionContentId) {
        for (DiplomaticEmbargoState embargo : state.embargoes()) {
            if (embargo.scope() == DiplomaticEmbargoState.Scope.MARKET_ACCESS
                    && embargo.targetFactionContentId().equals(targetFactionContentId)) {
                return embargo;
            }
        }
        return null;
    }

    private static FactionDiplomacyState withMarketEmbargo(
            FactionDiplomacyState state,
            DiplomaticEmbargoState replacement) {
        List<DiplomaticEmbargoState> embargoes = new ArrayList<>(state.embargoes());
        boolean replaced = false;
        for (int index = 0; index < embargoes.size(); index++) {
            DiplomaticEmbargoState current = embargoes.get(index);
            if (current.scope() == replacement.scope()
                    && current.targetFactionContentId().equals(replacement.targetFactionContentId())) {
                embargoes.set(index, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            embargoes.add(replacement);
        }
        return copyState(state, state.standings(), state.grievances(), state.treaties(), embargoes);
    }

'''
runtime = once(runtime, private_anchor, embargo_methods + private_anchor, 'runtime embargo methods')

helper_anchor = '''    private static String allocateGrievanceId(
            FactionDiplomacyState state,
            String treatyId,
'''
embargo_helper = '''    private static String allocateEmbargoGrievanceId(
            FactionDiplomacyState state,
            String embargoingFactionId,
            long worldTick) {
        String prefix = "grievance:embargo:" + embargoingFactionId + ":" + worldTick + ":";
        int sequence = 1;
        while (containsGrievance(state, prefix + sequence)) {
            sequence++;
        }
        return prefix + sequence;
    }

'''
runtime = once(runtime, helper_anchor, embargo_helper + helper_anchor, 'runtime embargo grievance allocator')
runtime_path.write_text(runtime)

world_path = Path('src/main/java/com/spacesim/world/WorldSimulation.java')
world = world_path.read_text()
marker = '''    public DiplomaticTreatyCommandResult applyDiplomaticTreatyCommand(DiplomaticTreatyCommand command) {
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
addition = marker + '''    /**
     * Applies one common player/AI unilateral market-access embargo command.
     *
     * <p>The command mutates only persistent diplomacy and immediately re-materializes ordinary
     * market-access policy. It performs no wallet transfer, cargo mutation, price mutation or
     * abstract economic damage.</p>
     *
     * @param command embargo impose/revoke command
     * @return immutable successful legal transition result
     */
    public DiplomaticEmbargoCommandResult applyDiplomaticEmbargoCommand(DiplomaticEmbargoCommand command) {
        long worldTick = getAuthoritativeWorldTick();
        boolean lifecycleChanged = diplomacyRuntime.advanceTime(worldTick);
        try {
            DiplomaticEmbargoCommandResult result = diplomacyRuntime.apply(command, worldTick);
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
world = once(world, marker, addition, 'WorldSimulation embargo command API')
world_path.write_text(world)

roadmap_path = Path('docs/development_roadmap.md')
roadmap = roadmap_path.read_text()
needle = '''### 17E.5 — embargoes / sanctions

Embargo не применяет абстрактный debuff.'''
replacement = '''### 17E.5 — embargoes / sanctions

**ACTIVE.** Unilateral market embargo использует общий player/AI command boundary и persistent `FactionDiplomacyState`; impose/revoke немедленно rematerialize-ят ordinary market access, а затронутая faction получает explicit `EMBARGO` grievance. Сам embargo не создаёт экономический урон вне ordinary trade/logistics consequences.

Embargo не применяет абстрактный debuff.'''
roadmap = once(roadmap, needle, replacement, 'Roadmap Stage17E.5 ACTIVE')
roadmap_path.write_text(roadmap)
