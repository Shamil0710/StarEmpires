# Star Empires — Stage 17.5I Combat Test Content / Tactical Prototype Visual Acceptance

> Статус: **PLANNED — mandatory Stage 17.5 exit gate**  
> Scope: Stage **17.5I deterministic aggregate acceptance**  
> Назначение: доказать production-ready combat/fitting foundation на нескольких физически различных кораблях и флотах, используя временный tactical visual layer, не превращая тестовый контент в финальный канон Stage 22.

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

---

# 8. Tactical Prototype Visual Set

Stage 17.5 requires temporary readable combat visuals sufficient for interactive inspection and debugging.

They are not final faction art.

## Ship sprites

Prototype top-down sprites must make readable:

- hull size;
- facing/orientation;
- broad role silhouette where useful;
- engine position;
- major visible weapon hardpoints;
- missile-launch regions where visible;
- major sensor assemblies where visible;
- shield/damage state through overlays/effects rather than hidden state.

A simple grayscale/limited-palette engineering style is acceptable.

## Projectile / weapon visuals

Required prototype representations:

### Kinetic

- projectile/tracer/debug representation;
- impact;
- deflection/ricochet where solver exposes it;
- fragments/spall/debris where authoritative state exists.

### Guided

- missile body;
- propulsion plume;
- interceptor;
- decoy;
- damaged/guidance-lost state where useful;
- residual ballistic body after guidance/seeker kill.

### Beam

- beam path;
- beam spot/impact;
- shield interaction;
- local heating/ablation cue where represented.

### Ship/VFX state

- main drive plume;
- maneuver-thruster cue;
- shield hit/collapse/restart;
- armor impact/penetration;
- compartment/subsystem damage cue;
- disabled capability cue where readable;
- wreck/debris state.

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
→ penetration / fragments / spall / debris
→ compartment hit
→ subsystem degradation/failure
→ changed thrust / power / thermal / sensors / weapons
→ ammunition/reaction-mass depletion
→ disablement / destruction
→ persistent post-combat state
```

Where a subsystem is legitimately absent from a specific fit, another scenario in the acceptance suite must cover it.

---

# 12. Deterministic acceptance outputs

Headless runs should emit machine-comparable summaries for regression analysis, including where available:

- scenario/content fingerprint;
- initial fleet composition and fitted mass;
- seed/configuration;
- detection/track acquisition times;
- shots/launches/beam dwell;
- ammunition expenditure;
- interception outcomes derived from physical state;
- shield energy/recharge/thermal history summary;
- armor/compartment/subsystem damage summary;
- destroyed/disabled/surviving assets by stable ID;
- remaining reaction mass/ammunition;
- battle duration;
- deterministic state/result fingerprint.

Repeated run with the same authoritative inputs must produce the same authoritative result within the project’s deterministic contract.

---

# 13. Stage 17.5 exit gate additions

Stage 17.5 **cannot be marked COMPLETE** until all of the following are true:

1. representative production-valid hull/module/ammunition/fits exist;
2. at least four materially different fleet doctrines plus balanced control fleet can be assembled from them;
3. deterministic fleet-vs-fleet scenario matrix runs on the production combat resolver;
4. at least one interactive tactical battle is readable through prototype visuals;
5. kinetic, beam, guided, interception, shield, armor, compartment and subsystem-damage paths are visibly and/or diagnostically inspectable;
6. consumables, power, heat and damage remain authoritative across the battle;
7. save/load or materialize/dematerialize boundaries required by 17.5 do not reset combat-relevant state;
8. test visuals are presentation-only and replaceable;
9. Stage-17.5 test content is explicitly marked provisional and does not silently become Stage-22 canon;
10. full repository CI plus deterministic acceptance suite are green on the exact merge head.

After this gate, Stage 18 may begin with confidence that it is industrializing a combat/fitting model already proven across multiple real configurations rather than a single demonstrator ship.
