# Star Empires — AI Behavior Acceptance Matrix

This checklist accompanies `docs/ai_behavior_roadmap.md` and provides concrete scenario-level acceptance targets under the current roadmap.

| Area | Scenario | Expected behavior | Earliest target stage |
| --- | --- | --- | --- |
| Civilian survival | Unescorted freighter attacked | Interrupt economic task and attempt physical escape | 15 |
| Civilian survival | Armed transport attacked by weak pursuer | May return fire while disengaging, according to profile | 15–19 |
| Risk routing | Safe long route vs dangerous profitable short route | Route depends on actor-specific expected utility, not one global choice | 15 |
| Escort awareness | Same dangerous route with strong escort | Convoy may rationally accept risk previously rejected alone | 15 |
| Threat memory | Combat ended recently | Risk decays with time/confidence; traffic does not return instantly | 15–19 |
| Faction doctrine | Mercantile vs militarist faction | Different risk/retreat/escort decisions from data-driven doctrine | 17–19 |
| Industrial awareness | Critical Stage-18 facility or route becomes unavailable | Planning reacts to real material/component/logistics consequence, not a hidden production modifier | 18–19 |
| Information limits | Distant conflict unknown to actor | No immediate reroute until an allowed intelligence path supplies knowledge | 19 |
| Weapon doctrine | Long-range platform vs short-range brawler | Different preferred engagement distance through Stage-17.5 capabilities | 19 |
| Ship condition | Damaged combat ship | Retreat threshold changes with actual fitted damage/capability state | 19 |
| Fleet cohesion | Escort convoy attacked | Escorts protect convoy instead of chasing irrelevant target indefinitely | 19 |
| Fleet power | Outmatched group | Coordinated disengagement when doctrine threshold is exceeded | 19 |
| Economic consequence | War zone raises route danger | Physical traffic reroutes and real Stage-18 throughput/shortage/price state changes | 19 |
| Recovery | War zone becomes safe | Traffic gradually returns as threat confidence/severity decays | 19 |
| Physical geography | Generated route has greater travel/exposure cost | Stage-20 distance/topology changes route utility without AI-only map shortcuts | 20 |
| NPC personality | Two commanders share faction but differ in traits | Stage-21 preferences alter choices without granting knowledge or physical bonuses | 21 |
| Determinism | Equal state/knowledge/doctrine | Equal decision with stable tie-breaks | all |
| Observability | Any route/retreat decision | Structured diagnostic explains main utility/risk/industrial factors | 15+ |

No item in this matrix authorizes an AI-only shortcut around movement, jump, trade, mining, extraction, production, combat, diplomacy or destruction rules.