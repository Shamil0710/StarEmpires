# Star Empires — AI Behavior Acceptance Matrix

This checklist accompanies `docs/ai_behavior_roadmap.md` and provides concrete scenario-level acceptance targets for future implementation.

| Area | Scenario | Expected behavior | Earliest target stage |
| --- | --- | --- | --- |
| Civilian survival | Unescorted freighter attacked | Interrupt economic task and attempt physical escape | 15 |
| Civilian survival | Armed transport attacked by weak pursuer | May return fire while disengaging, according to profile | 15–18 |
| Risk routing | Safe long route vs dangerous profitable short route | Route depends on actor-specific expected utility, not one global choice | 15 |
| Escort awareness | Same dangerous route with strong escort | Convoy may rationally accept risk previously rejected alone | 15 |
| Threat memory | Combat ended recently | Risk decays with time/confidence; traffic does not return instantly | 15–18 |
| Information limits | Distant conflict unknown to actor | No immediate reroute until an allowed intelligence path supplies knowledge | 18 |
| Weapon doctrine | Long-range platform vs short-range brawler | Different preferred engagement distance using same combat executor | 18 |
| Ship condition | Damaged combat ship | Retreat threshold changes with actual hull/shield state | 18 |
| Fleet cohesion | Escort convoy attacked | Escorts protect convoy instead of chasing irrelevant target indefinitely | 18 |
| Fleet power | Outmatched group | Coordinated disengagement when doctrine threshold is exceeded | 18 |
| Faction doctrine | Mercantile vs militarist faction | Different risk/retreat/escort decisions from data-driven doctrine | 17–18 |
| Economic consequence | War zone raises route danger | Physical traffic reroutes and real throughput/shortage/price state changes | 18 |
| Recovery | War zone becomes safe | Traffic gradually returns as threat confidence/severity decays | 18 |
| Determinism | Equal state/knowledge/doctrine | Equal decision with stable tie-breaks | all |
| Observability | Any route/retreat decision | Structured diagnostic explains main utility/risk factors | 15+ |

No item in this matrix authorizes an AI-only shortcut around movement, jump, trade, mining, combat or destruction rules.
