# Star Empires — Stage 17.5I Combat Test Content / Tactical Prototype Visual Acceptance

> Статус: **ACCEPTANCE COMPLETE / GREEN — exact-head implementation gate passed; merge/post-merge gate pending**  
> Scope: Stage **17.5I deterministic aggregate acceptance**  
> Назначение: доказать production-ready combat/fitting foundation на нескольких физически различных кораблях и флотах, используя временный tactical visual layer, не превращая тестовый контент в финальный канон Stage 22.  
> Pre-closeout evidence: `750604aa0a6216a739a584544dc5e1a439ffb378` / CI **#2789 SUCCESS** / **868 tests, 0 failures**.

---

# 1. Главный принцип

Stage 17.5 не считается завершённой только потому, что отдельные subsystem tests зелёные.

Перед переходом к Stage 18 должен существовать **Combat Test Content Pack** и **Tactical Prototype Visual Set**, достаточные для повторяемых end-to-end столкновений нескольких по-разному сконфигурированных флотов.

```text
production hull/module/fitting contracts
+ propulsion/power/thermal/FTL
+ sensors/tracks/EW/datalink
+ weapons/ammunition/guidance/layered defense
+ shields/armor/compartments/subsystem damage
+ repair/refit/maintenance seam
+ persistence/capability APIs
+ representative combat content
+ tactical prototype visuals
→ deterministic fleet engagements
→ Stage 17.5 acceptance
```

Тестовые hull/module/ammunition/fit definitions обязаны использовать обычные production schemas и runtime. Отдельные combat-only stats, fake fleets и special-case physics запрещены.

---

# 2. Content status: production-valid, content-provisional

Stage 17.5 test content имеет двойной статус:

- **production-valid** — корпуса, модули, боеприпасы, consumables и fits проходят обычные loaders/validators, используют общие budgets, persistence и combat runtime;
- **content-provisional** — названия, визуальная форма, faction identity, технологическая принадлежность, баланс и конкретные loadouts не получают автоматический финальный канонический статус.

Hard rule:

> **Ни один hull, module, weapon, ammunition type, fit или visual asset из Stage 17.5 Combat Test Content Pack не считается автоматически финальным игровым контентом.**

Stage 22 обязан либо re-author/rebalance такой контент по общей technology/faction/content парадигме, либо явно принять конкретный asset как production content после content review.

Stage 23 может заменить prototype visuals финальными art/VFX assets без изменения authoritative simulation semantics.

---

# 3. Минимальный representative hull set

Нужен не полный Stage-22 catalog, а компактный набор, который создаёт materially different fitting and combat envelopes.

Минимум:

- light combat hull / corvette-scale;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

Желательно дополнительно, если не раздувает scope:

- dedicated recon/EW hull;
- missile-specialized hull;
- carrier/drone-support demonstrator.

Каждый корпус должен иметь реальные:

- dimensions/collision geometry;
- bare/structural mass;
- compartment topology;
- slot/hardpoint topology;
- propulsion compatibility;
- armor/protection geometry;
- signature geometry;
- operational mass envelope.

Representative hulls существуют для mechanics coverage, а не для окончательной faction roster.

**Acceptance result:** mandatory six-hull set is implemented through ordinary production content schema and remains explicitly `content_provisional`.

---

# 4. Минимальный equipment/ammunition set

Combat Test Content Pack должен содержать достаточно оборудования, чтобы создавать не cosmetic, а физически разные fits.

Обязательные группы:

## Power / energy

- минимум два materially different reactor profiles;
- energy storage where required;
- power distribution support where runtime distinguishes it.

## Propulsion / maneuver / FTL

- endurance/efficiency-oriented drive profile;
- high-thrust combat drive profile;
- maneuver thruster variants where required;
- reaction-mass consumables;
- FTL module profile for persistence/capability coverage.

## Thermal

- at least two thermal/radiator approaches with different mass/heat/vulnerability tradeoffs;
- thermal stores/sinks where supported.

## Sensors / communications / EW

- passive sensor capability;
- active ranging/fire-control sensor capability;
- datalink/command module;
- ECM;
- ECCM;
- decoy support.

## Protection

- shield emitter/field configuration;
- light and heavy armor/protection stacks;
- backing/spall protection where Stage 17.5F model exposes it.

## Weapons / ammunition

- kinetic weapon + physical ammunition;
- beam weapon;
- guided anti-ship missile + physical propellant/seeker/guidance state;
- interceptor/anti-missile weapon;
- point-defense weapon;
- at least one alternative ammunition/load profile where it creates meaningful behavior.

## Support

- magazines/stores;
- fuel/reaction-mass tanks;
- repair/maintenance support seam where applicable;
- crew/life-support/automation baseline required by production fitting.

No equipment exists solely to manufacture a desired class bonus.

**Acceptance result:** required combat-equipment vocabulary exists in normal production schemas. FTL persistence is covered through the existing production engineering/persistence field rather than inventing an incompatible combat-only FTL fixture.

---

# 5. Required fit diversity

Один hull должен допускать несколько materially different valid fits where topology allows it.

Representative examples:

```text
frigate hull
→ long-range recon / EW fit
→ escort / point-defense fit
→ missile-support fit
```

```text
destroyer hull
→ kinetic line-combat fit
→ missile strike fit
→ fleet-defense fit
```

```text
cruiser hull
→ armor-heavy gun fit
→ energy/beam fit
→ command/support fit
```

Fit differences должны возникать через реальные module/ammunition/armor/power/thermal/mass choices, а не через role-name multipliers.

**Acceptance result:** five doctrine fixtures derive differences from fitted physical content only. Doctrine IDs themselves do not grant performance modifiers.

---

# 6. Required fleet doctrines for acceptance

Stage 17.5I должен уметь собрать минимум **четыре принципиально разных боевых флота** плюс один balanced/control fleet.

## Fleet A — kinetic line fleet

Characteristics:

- heavier protection;
- kinetic primary battery;
- strong fire control;
- lower relative mobility/thermal flexibility where fitting produces this tradeoff.

## Fleet B — missile strike fleet

Characteristics:

- large guided-munition inventory;
- recon/datalink support;
- salvo/saturation behavior;
- explicit magazine and launcher-cycle endurance.

## Fleet C — high-mobility / beam fleet

Characteristics:

- high-thrust or high-specific-power fit;
- beam-heavy weapons;
- high reactor/thermal demand;
- lower protection or endurance where chosen by fit.

## Fleet D — defensive / EW fleet

Characteristics:

- strong sensors/EW/ECCM;
- layered point defense/interceptors;
- shield emphasis where appropriate;
- formation-dependent defensive geometry.

## Fleet E — balanced control fleet

Mixed capability without a single extreme specialization, used as regression/control opponent.

These are **test doctrines**, not mandatory final factions.

**Acceptance result:** A–E are implemented by `Stage175IFleetDoctrineCatalog` and load ordinary production-valid fits/consumables/loadouts.

---

# 7. Combat matrix

Acceptance must include deterministic matrices rather than one showcase battle.

At minimum:

```text
A vs A
A vs B
A vs C
A vs D
A vs E
B vs C
B vs D
B vs E
C vs D
C vs E
D vs E
```

And scenario variations:

- equal fleet count;
- approximately equal fitted mass;
- approximately equal provisional industrial/reference cost where available;
- small vs large formation;
- compact vs dispersed formation;
- full ammunition vs partially depleted magazines;
- fresh vs pre-damaged ships;
- hot/thermally stressed vs cold-start state;
- sensor-rich vs degraded-information state;
- escort-protected vulnerable/logistics asset scenario.

The goal is not final win-rate balance. The goal is to expose broken mechanics, dominant abstractions, nonphysical shortcuts, persistence defects and unusable capability APIs.

**Acceptance result:** all eleven required pairs execute deterministically. Equal-count, approximate equal-mass, compact/dispersed, partial-ammo, pre-damage, thermal-stress, degraded-information and protected-logistics variants are covered. Multi-ship saturation materializes individual guided bodies and finite defense resources.

Equal-cost remains explicitly:

```text
DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS
```

Stage 17.5 has no legitimate scalar exchange basis for heterogeneous industrial/resource/facility inputs, so inventing one would violate Stage-18 ownership rather than strengthen this acceptance gate.

---

# 8. Tactical Prototype Visual Set

Stage 17.5 requires temporary readable combat visuals sufficient for interactive inspection and debugging.

They are not final faction art.

## Ship sprites / silhouettes

Prototype top-down presentation must make readable:

- hull size;
- facing/orientation;
- broad role silhouette where useful;
- engine/thrust state;
- shield/damage/wreck state through overlays/effects rather than hidden simulation variables.

The current Generation-0 implementation uses deterministic libGDX shape-based top-down silhouettes instead of final sprites. This is intentional: its acceptance role is readability/debugging while Stage 23 remains free to replace all visual assets.

## Projectile / weapon visuals

Required prototype representations:

### Kinetic

- projectile/tracer/debug representation;
- impact;
- armor/penetration cues;
- deterministic cosmetic wreck debris where authoritative destruction exists.

### Guided

- missile body;
- propulsion/trail cue;
- interceptor;
- decoy/deception contact.

### Beam

- beam path;
- beam impact/penetration context;
- shield interaction.

### Ship/VFX state

- main drive plume;
- shield reserve/collapse cue;
- armor impact/penetration;
- compartment/subsystem damage cue;
- wreck/debris state.

**Acceptance result:** `TacticalPrototypeVisualSnapshot`, `Stage175ITacticalVisualProjection` and `TacticalPrototypeRenderer` cover the mandatory families from authoritative state. `Stage175ITacticalAcceptancePlayback` exposes engagement, penetration and wreck frames in a deterministic interactive viewer.

---

# 9. Rendering is never authoritative

Visual assets are projections of simulation state.

Example:

```text
GuidedWeaponState
→ position / velocity
→ dry mass / propellant
→ seeker / guidance / datalink state
→ damage state
→ presentation adapter
→ temporary missile sprite + plume/VFX
```

Deleting, hiding, replacing or changing a sprite cannot delete/repair/change the authoritative projectile/ship unless an ordinary simulation command/state transition already did so.

Projectile pooling/representation remains independent from rendering contracts established in Stage 17.5E.

**Acceptance result:** interactive controls only pause/step/reset immutable playback frames. The desktop viewer has no combat-service mutation path.

---

# 10. Prototype visual replacement contract

Three content generations are explicitly separated.

## Generation 0 — Stage 17.5 test assets

Priority:

```text
physics correctness
+ combat readability
+ debugging
```

No requirement for final faction identity or production polish.

## Generation 1 — Stage 22 Content / Balance Alpha

Stage 22 reworks or replaces test content using:

- accepted technology ladder;
- Stage-18 materials/components/industry;
- faction engineering doctrines;
- faction visual language;
- broad hull/module/ammunition catalog;
- real economic and combat balance.

Stage-17.5 identifiers may be retained only by explicit content decision; otherwise migration/test fixtures may map them to new production definitions.

## Generation 2 — Stage 23 / final art and polish

Final production sprites, module visuals, damage overlays, VFX and animations replace prototype assets while preserving authoritative simulation contracts.

Art replacement must not become a reason to rewrite physical mechanics.

---

# 11. Required end-to-end observable combat chain

Before Stage 17.5 can close, at least one interactive and one headless deterministic scenario must exercise the full relevant chain:

```text
detection
→ classification / tracking
→ datalink / EW / ECCM
→ fire-control solution
→ kinetic / beam / guided fire
→ launcher / magazine / power / thermal consumption
→ interception / point defense / decoys
→ shield interaction
→ armor/material response
→ penetration / local damage
→ compartment hit
→ subsystem degradation/failure
→ changed thrust / power / thermal / sensors / weapons
→ ammunition depletion
→ disablement / destruction
→ persistent post-combat state
```

Where a subsystem is legitimately absent from a specific fit, another scenario in the acceptance suite covers it.

**Acceptance result:** the aggregate suite closes this chain across shared interval-budget acceptance, deterministic matrix scenarios, finite-magazine destruction, immutable tactical playback, mid-combat persistence and post-destruction persistence.

The strongest single physical chain is:

```text
real doctrine-A magazine
→ AmmunitionRuntime.consumeOne
→ physical 150 kg ProjectileBody
→ fitted charged ShieldFieldRuntime
→ bounded HeavyImpactResolver material response
→ ShipDamageRuntime compartment + mount damage
→ shield emitter follows module integrity
→ all local compartments + mounts destroyed
→ damage-aware DerivedShipCalculator
→ acceleration = 0 / sensor capability lost
→ wreck/debris visual projection
→ production save/load
→ restored entity remains the same wreck
```

No hidden damage multiplier or infinite acceptance ammunition source is used.

---

# 12. Deterministic acceptance outputs

Headless runs expose machine-comparable summaries/fingerprints including where applicable:

- scenario/content fingerprint;
- initial fleet composition and fitted mass;
- seed/configuration;
- detection/track/fire-control state;
- shots/launches/beam use;
- ammunition expenditure;
- finite interceptor assignments;
- shield interaction;
- armor/compartment/subsystem damage;
- remaining resources;
- deterministic result fingerprint.

Repeated runs with the same authoritative inputs produce the same authoritative result under the project deterministic contract. Playback construction is also equality-tested across repeated creation.

---

# 13. Persistence acceptance

Stage 17.5I uses the existing production persistence boundary only.

`Stage175ICombatPersistenceAcceptanceTest` round-trips a combined mid-combat state through `ContentBoundSaveCodec` envelope v2, preserving:

- partial magazine;
- stored energy;
- heat/coolant/thrust state;
- FTL cooldown field;
- compartment/module damage;
- shield reserve/heat/collapse;
- maintenance;
- weapon feed identity/cooldown;
- sensor knowledge and pending datalink measurements.

`Stage175IPostCombatPersistenceAcceptanceTest` then round-trips the final fully destroyed state and restores the ECS entity. It remains destroyed and still projects as a wreck. Save/load therefore cannot function as free repair, recharge, respawn or asset replacement.

Core `GameStateCodec` remains schema v4; Stage-H/I continuity remains in production `ContentBoundSaveCodec` envelope v2.

---

# 14. Interactive inspection

The dedicated Stage-17.5I desktop validation mode is:

```text
java -jar target/star-empires-1.0-SNAPSHOT-all.jar --tactical-acceptance
```

Controls:

```text
SPACE       play / pause
LEFT / P    previous frame
RIGHT / N   next frame
R           reset
ESC         exit
```

The three immutable frames are:

1. engagement — kinetic + missile + interceptor + beam + EW/deception + shield/thrust;
2. penetration — shield + armor + local damage;
3. wreck — subsystem loss + deterministic debris.

This is an acceptance/debug presentation mode, not a separate tactical simulation authority.

---

# 15. Stage 17.5 exit gate result

The original exit conditions are now satisfied by the exact-head implementation:

1. **PASS** — representative production-valid hull/module/ammunition/fits exist;
2. **PASS** — four materially different doctrines plus balanced control are assembled from ordinary content;
3. **PASS** — deterministic fleet-vs-fleet pair matrix and physical saturation run on production subsystem APIs;
4. **PASS** — interactive tactical battle/playback is readable through prototype visuals;
5. **PASS** — kinetic, beam, guided, interception, shield, armor, compartment and subsystem-damage paths are inspectable;
6. **PASS** — consumables, power, heat and damage remain authoritative across acceptance paths;
7. **PASS** — mid-combat and post-destruction save/load preserve combat state instead of resetting it;
8. **PASS** — test visuals are presentation-only and replaceable;
9. **PASS** — test content remains explicitly provisional and is not Stage-22 canon;
10. **PASS at implementation checkpoint** — repository CI plus deterministic acceptance suite are green on exact SHA `750604aa0a6216a739a584544dc5e1a439ffb378`, CI #2789, 868 tests.

One deliberate non-blocking deferral remains:

- equal-cost fleet comparison waits for Stage 18 to provide a comparable industrial/resource/facility cost basis.

This is not a missing combat mechanic and must not be filled by an invented Stage-17.5 scalar price.

After the final canonical-document exact-head CI, exact PR-head CI, exact-SHA merge and post-merge main CI succeed, **Stage 17.5 is COMPLETE and Stage 18 becomes NEXT**.
