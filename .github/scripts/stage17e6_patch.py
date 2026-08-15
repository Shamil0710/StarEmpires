from pathlib import Path

path = Path('src/main/java/com/spacesim/world/FactionDiplomacyRuntime.java')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    text = text.replace(old, new, 1)


replace_once(
    '''    private static final int EMBARGO_GRIEVANCE_SEVERITY = 40;\n''',
    '''    private static final int EMBARGO_GRIEVANCE_SEVERITY = 40;\n    private static final int HONORED_TREATY_TRUST_GAIN = 4;\n    private static final int HONORED_TREATY_CREDIBILITY_GAIN = 5;\n''',
    'credibility constants')

old_method = '''    boolean advanceTime(long worldTick) {
        requireWorldTick(worldTick);
        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        for (FactionDiplomacyState state : states) {
            List<DiplomaticTreatyState> updatedTreaties = null;
            for (int index = 0; index < state.treaties().size(); index++) {
                DiplomaticTreatyState treaty = state.treaties().get(index);
                if ((treaty.status() == DiplomaticTreatyState.Status.ACTIVE
                        || treaty.status() == DiplomaticTreatyState.Status.TERMINATING)
                        && treaty.expiresTick() >= 0L
                        && treaty.expiresTick() <= worldTick) {
                    if (updatedTreaties == null) {
                        updatedTreaties = new ArrayList<>(state.treaties());
                    }
                    updatedTreaties.set(index, copyTreaty(
                            treaty,
                            DiplomaticTreatyState.Status.EXPIRED,
                            treaty.effectiveTick(),
                            treaty.expiresTick()));
                }
            }
            if (updatedTreaties != null) {
                replacements.put(state.factionContentId(), copyState(
                        state,
                        state.standings(),
                        state.grievances(),
                        updatedTreaties,
                        state.embargoes()));
            }
        }
        if (replacements.isEmpty()) {
            return false;
        }
        install(replaceStates(replacements));
        return true;
    }'''

new_method = '''    boolean advanceTime(long worldTick) {
        requireWorldTick(worldTick);
        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        List<HonoredTreatyCompletion> honored = new ArrayList<>();
        for (FactionDiplomacyState state : states) {
            List<DiplomaticTreatyState> updatedTreaties = null;
            for (int index = 0; index < state.treaties().size(); index++) {
                DiplomaticTreatyState treaty = state.treaties().get(index);
                if ((treaty.status() == DiplomaticTreatyState.Status.ACTIVE
                        || treaty.status() == DiplomaticTreatyState.Status.TERMINATING)
                        && treaty.expiresTick() >= 0L
                        && treaty.expiresTick() <= worldTick) {
                    if (treaty.status() == DiplomaticTreatyState.Status.ACTIVE
                            && observableComplianceTreaty(treaty)
                            && !hadEmbargoDuringTreaty(
                                    state.factionContentId(),
                                    treaty.counterpartyFactionContentId(),
                                    treaty.effectiveTick(),
                                    treaty.expiresTick())) {
                        honored.add(new HonoredTreatyCompletion(
                                state.factionContentId(),
                                treaty.counterpartyFactionContentId()));
                    }
                    if (updatedTreaties == null) {
                        updatedTreaties = new ArrayList<>(state.treaties());
                    }
                    updatedTreaties.set(index, copyTreaty(
                            treaty,
                            DiplomaticTreatyState.Status.EXPIRED,
                            treaty.effectiveTick(),
                            treaty.expiresTick()));
                }
            }
            if (updatedTreaties != null) {
                replacements.put(state.factionContentId(), copyState(
                        state,
                        state.standings(),
                        state.grievances(),
                        updatedTreaties,
                        state.embargoes()));
            }
        }
        for (HonoredTreatyCompletion completion : honored) {
            FactionDiplomacyState owner = replacements.getOrDefault(
                    completion.ownerFactionContentId(),
                    byId.get(completion.ownerFactionContentId()));
            FactionDiplomacyState counterparty = replacements.getOrDefault(
                    completion.counterpartyFactionContentId(),
                    byId.get(completion.counterpartyFactionContentId()));
            replacements.put(
                    completion.ownerFactionContentId(),
                    withHonoredTreatyStanding(
                            owner,
                            completion.counterpartyFactionContentId(),
                            worldTick));
            replacements.put(
                    completion.counterpartyFactionContentId(),
                    withHonoredTreatyStanding(
                            counterparty,
                            completion.ownerFactionContentId(),
                            worldTick));
        }
        if (replacements.isEmpty()) {
            return false;
        }
        install(replaceStates(replacements));
        return true;
    }'''
replace_once(old_method, new_method, 'advanceTime')

anchor = '''    private FactionDiplomacyState withBreachConsequences(
'''
helpers = '''    private FactionDiplomacyState withHonoredTreatyStanding(
            FactionDiplomacyState state,
            String counterpartyFactionContentId,
            long worldTick) {
        List<DiplomaticStandingState> standings = new ArrayList<>(state.standings());
        DiplomaticStandingState previous = state.standingToward(counterpartyFactionContentId);
        DiplomaticStandingState updated = new DiplomaticStandingState(
                counterpartyFactionContentId,
                clamp((previous == null ? 0 : previous.trust()) + HONORED_TREATY_TRUST_GAIN, -100, 100),
                clamp((previous == null
                        ? DiplomaticStandingState.NEUTRAL_CREDIBILITY
                        : previous.credibility()) + HONORED_TREATY_CREDIBILITY_GAIN, 0, 100),
                worldTick);
        boolean replaced = false;
        for (int index = 0; index < standings.size(); index++) {
            if (standings.get(index).targetFactionContentId().equals(counterpartyFactionContentId)) {
                standings.set(index, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            standings.add(updated);
        }
        return copyState(state, standings, state.grievances(), state.treaties(), state.embargoes());
    }

    private boolean hadEmbargoDuringTreaty(
            String firstFactionContentId,
            String secondFactionContentId,
            long effectiveTick,
            long expiresTick) {
        return hasEmbargoGrievanceDuring(
                byId.get(firstFactionContentId), secondFactionContentId, effectiveTick, expiresTick)
                || hasEmbargoGrievanceDuring(
                        byId.get(secondFactionContentId), firstFactionContentId, effectiveTick, expiresTick);
    }

    private static boolean hasEmbargoGrievanceDuring(
            FactionDiplomacyState state,
            String targetFactionContentId,
            long effectiveTick,
            long expiresTick) {
        if (state == null) {
            return false;
        }
        for (DiplomaticGrievanceState grievance : state.grievances()) {
            if (grievance.kind() == DiplomaticGrievanceState.Kind.EMBARGO
                    && grievance.targetFactionContentId().equals(targetFactionContentId)
                    && grievance.createdTick() >= effectiveTick
                    && grievance.createdTick() <= expiresTick) {
                return true;
            }
        }
        return false;
    }

    private static boolean observableComplianceTreaty(DiplomaticTreatyState treaty) {
        if (treaty.clauses().isEmpty()) {
            return false;
        }
        for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
            if (clause.kind() != DiplomaticTreatyClauseState.Kind.MARKET_ACCESS
                    && clause.kind() != DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION) {
                return false;
            }
        }
        return true;
    }

'''
replace_once(anchor, helpers + anchor, 'compliance helpers')

record_anchor = '''    private record TreatyLocation(
'''
record_text = '''    private record HonoredTreatyCompletion(
            String ownerFactionContentId,
            String counterpartyFactionContentId) {
    }

'''
replace_once(record_anchor, record_text + record_anchor, 'completion record')

path.write_text(text, encoding='utf-8')
