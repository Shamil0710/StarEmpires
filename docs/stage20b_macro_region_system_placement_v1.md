# Stage 20B — macro region / star-system placement v1

> Статус: **GAP-CLOSURE IMPLEMENTATION SLICE**  
> Основание: `docs/stage20_physical_world_generation_plan.md` и `docs/galaxy_topology_resource_geography_generation_contract.md`  
> Причина: Stage-20D topology generator ранее получал `SectorNode/StarSystemNode` geometry как готовый caller input, поэтому representative topology tests не являлись доказательством полной procedural macro generation.

## 1. Закрываемый gap

Canonical generation chain требует:

```text
20-world seed/version
→ macro regions / sector geometry
→ system coordinates
→ topology candidate graph
→ topology quality gate
```

До этого production Stage 20 имел:

```text
caller-authored SectorNode/StarSystemNode
→ Stage20JumpTopologyGenerator
```

То есть `macro regions / system coordinates` отсутствовали как production generator layer.

`Stage20MacroGalaxyGeometryGenerator` добавляет этот недостающий слой.

## 2. Что означает macro x/y

`StarSystemNode.x/y` в этом slice имеют explicit semantics:

```text
TOPOLOGY_SPATIAL_PRIOR_NOT_TRAVEL_DISTANCE
```

Они используются для:

- пространственного clustering систем в регионы;
- topology candidate ranking;
- spatial identity/macro map presentation;
- дальнейших generation diagnostics.

Они **не** являются:

- SI local-system distance;
- light-year travel distance contract;
- jump time;
- fuel/reaction-mass cost;
- разрешением direct jump;
- заменой explicit neighbor graph.

Ordinary inter-system movement остаётся:

```text
GalaxyTopology explicit edge
+ fitted FTL capability
+ spool / energy / transit / cooldown
```

## 3. Sector — spatial region, не list partition

Generator не создаёт глобальный список systems с последующим механическим разрезанием на sectors.

Для каждого sector seed определяет:

```text
spatial centroid
cluster radius
aspect ratio
cluster orientation
system count inside requested range
```

Systems генерируются непосредственно внутри своей spatial region.

## 4. Internal region structure

Каждая система получает generation-only placement class:

```text
CORE
OUTER
FRONTIER
```

Этот class:

- является evidence/provenance generation;
- влияет только на spatial placement distribution;
- не является runtime system type;
- не даёт production/resource/AI bonus;
- не превращается в scripted `FRONTIER = shortage` или `CORE = +production`.

После generation placement class может быть использован только как diagnostic provenance. Strategic importance должна выводиться из topology/resources/facilities/logistics.

## 5. Distribution v1

V1 использует deterministic spatial mixture:

```text
CORE      majority of systems
OUTER     secondary ring population
FRONTIER  bounded sparse tail
```

Sector centers размещаются через seeded low-discrepancy/golden-angle spatial prior с radial/angular jitter.

Внутри region system angles также используют low-discrepancy sequence + deterministic local jitter, а radial band зависит от placement class.

Это уменьшает:

- регулярные одинаковые rings;
- exact grid artifacts;
- accidental identical sector shape;
- dependence от caller list order.

## 6. World-size request

`GenerationRequest` содержит только:

```text
sectorCount
minSystemsPerSector
maxSystemsPerSector
```

Это explicit world/scenario size input, а не bonus.

Generator не имеет права менять request ради последующей topology acceptance.

Пример:

```text
2 sectors × 1 system
```

остаётся именно таким macro world candidate. Если Stage20D quality gate считает topology неприемлемой, seed/request rejected downstream; macro generator не добавляет скрытые systems.

## 7. Stable identity

V1 создаёт:

```text
SectorId = 1..N
StarSystemId = stable deterministic sequential IDs inside the exact generated result
```

При одинаковых:

```text
root seed
+ generator version
+ GenerationRequest
```

результат полностью deterministic.

Изменение generator version является generation migration boundary и не обязано сохранять old seed layout byte-for-byte.

## 8. Bounded placement

Local placement внутри region имеет explicit finite attempt budget.

Если generator не может соблюсти собственное minimum spatial separation, он fail-fast с generation error.

Он не:

- уменьшает separation бесконечно;
- переносит систему в другой sector;
- увеличивает world size;
- добавляет topology edge.

## 9. Evidence rows

Result сохраняет:

### SectorGeometryEvidence

- sector ID;
- generated center;
- characteristic cluster radius;
- aspect ratio;
- orientation;
- generated system count.

### SystemGeometryEvidence

- system ID;
- owning sector ID;
- placement class;
- normalized radius relative to region cluster.

Это позволяет будущему world-quality gate анализировать macro geometry независимо от renderer/UI.

## 10. Coupling с Stage 20D

Output является прямым input для:

```text
Stage20JumpTopologyGenerator.generate(..., macro.sectors(), seed, quality)
```

Никакой adapter, Euclidean travel model или fully-connected graph между слоями не вводится.

Stage20D по-прежнему имеет право:

- deterministic bounded edge repair;
- seed rejection.

Macro generator не знает outcome topology gate заранее.

## 11. Acceptance tests

Regression suite доказывает:

1. same seed + same request → exact same macro geometry;
2. different seed → different spatial geometry при том же fixed world-size contract;
3. sectors являются spatial clusters, а не partition одного line/list;
4. stable system IDs уникальны;
5. system count остаётся внутри explicit request;
6. generated macro geometry напрямую feeding real Stage20D generator;
7. bounded deterministic seed corpus содержит topology-accepted representative result;
8. intentionally tiny request не silently inflated;
9. invalid requests fail explicitly.

## 12. Что всё ещё не закрыто в Stage 20B

Этот slice закрывает только:

```text
macro regions / sector geometry
+ system coordinates
```

Canonical Stage20B всё ещё требует generated local physical content concepts:

- planets/moons or abstracted celestial anchors where present;
- asteroid/resource bodies and fields;
- meaningful physical host/world conditions;
- later patrol/security/derelict/anomaly concepts where applicable.

Текущие `PlanetNode` / `AsteroidFieldNode` являются legacy strategic structures и не должны автоматически считаться Stage-20 SI authority.

Следующий Stage20B gap-closure должен определить physical host/content anchor model в `LocalPhysicalPosition` и затем позволить Stage20E resource occurrence generation получать `ResourceHostProfile` из generated physical hosts, а не из hand-authored test fixtures.

## 13. Почему это выполняется до дальнейшего Stage 20E batch closeout

Production-style Stage20E seed probe не может считаться production evidence, если его самый ранний input всё ещё hand-authored:

```text
hand-authored sector circles
→ topology
→ resources
→ economy
→ acceptance
```

Правильный порядок теперь:

```text
root seed
→ Stage20MacroGalaxyGeometryGenerator
→ Stage20JumpTopologyGenerator
→ Stage20 local physical content/host geometry
→ resources / facilities / economics
→ faction starts
→ whole-seed acceptance
```

Поэтому этот gap-closure является roadmap-correct prerequisite, а не отклонением от Stage20E.
