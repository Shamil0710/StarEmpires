# Star Empires — Ship Mathematics v0.7: Protection Domain, Debris & Compartment Exposure

> Статус: **executable engineering / design seed v0.7**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_mathematics_v0_1.md`–`v0_6.md`  
> Код: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV07ProtectionHarness.java`  
> Snapshot: `docs/benchmarks/protection_debris_reference_v0_7.json`

---

## 1. Задача v0.7

Предыдущие версии уже доказали, что `intercepted` не должно означать «объект исчез». v0.7 переводит это в явную protection architecture.

Нужно ответить на четыре вопроса:

1. какие реальные hypervelocity shield equations можно использовать непосредственно, а где экстраполяция становится физически нечестной;
2. что происходит с массой, энергией и импульсом 12-тонной ракеты после удалённого разрушения;
3. как stand-off distance и степень fragmentation меняют нагрузку на корабль;
4. в каком формате последствия должны поступать в будущие armor / compartment / subsystem solvers.

Ключевой результат:

> **MMOD/Whipple ballistic-limit equations нельзя переносить на intact ship weapons.**

Они остаются полезной инженерной опорой для архитектуры spaced protection и для будущей калибровки мелких fragments, но прямое попадание полноценной противокорабельной ракеты или корабельного kinetic projectile требует отдельной material/geometry-calibrated response model.

---

## 2. Внешняя инженерная опора и пределы применимости

Использованы первичные NASA/NTRS материалы.

### 2.1. Whipple/MMOD equations эмпиричны и domain-specific

NASA/TP-2003-210788 (`20030068423`) описывает Whipple, Stuffed Whipple и Multi-Shock shields и соответствующие ballistic-limit equations, полученные из анализов и hypervelocity testing.

NASA TM `105527` (`19920010785`) прямо трактует такие equations как shield sizing / response equations, которые обновляются по мере получения новых HVI test data.

Современная работа `20240000104` также подчёркивает, что MMOD ballistic-limit curve является semi-empirical и зависит как минимум от impact speed, projectile density, angle и других свойств impactor.

Следовательно, BLE — это **калиброванная инженерная модель**, а не универсальный закон `armorThickness → stoppedEnergy`.

### 2.2. Impact regimes полезны качественно

NASA work по Whipple shields (`20090024821`) описывает три характерных области для tested shield problem:

```text
< ~3 km/s   ballistic / intact-like
~3–7 km/s   shatter / fragmentation
> ~7 km/s    melt/vaporization-dominated regime
```

Эти границы полезны как качественное описание phase behavior, но не дают права считать многотонную ракету эквивалентной миллиметровому MMOD projectile.

### 2.3. Масштаб опубликованных shield tests

Примеры:

- `19920056054`: ~0.8 g aluminum debris at 7 and 10 km/s;
- `19970012905`: ~1 g aluminum projectiles above 11 km/s;
- `20090024821`: 1.6–2.6 mm aluminum spheres around 9 km/s;
- `20140006462`: unusually large ~598 g projectile at 6.905 km/s, approximately 15 MJ, against a multi-shock shield.

Последний пример используется v0.7 только как **order-of-magnitude guardrail**, а не как предел конкретной NASA equation.

---

## 3. Наши intact weapon impacts

### M anti-ship missile

```text
mass     = 12 000 kg
velocity = 18 000 m/s

kinetic energy = 0.5 m v²
               = 1.944e12 J
               = 1.944 TJ

momentum = m v
         = 216e6 N·s
```

Относительно 15-MJ NASA large shield-test reference:

```text
energy ratio = 129 600 ×
mass ratio   ≈ 20 067 ×
```

### XL capital kinetic

```text
mass     = 1 000 kg
velocity = 30 000 m/s
energy   = 450 GJ
```

Energy ratio:

```text
30 000 × 15 MJ reference
```

### M coilgun projectile

```text
mass     = 25 kg
velocity = 15 000 m/s
energy   = 2.8125 GJ
```

Даже он:

```text
187.5 × 15 MJ reference
```

### Design rule

Executable harness возвращает для всех трёх:

```text
EXTRAPOLATION_FORBIDDEN
```

Это **не означает**, что armor бессмысленна. Это означает, что нельзя брать ISS/MMOD BLE, подставлять туда 12 000 kg и выдавать результат за физическую модель.

---

## 4. Что armor всё ещё должна делать

Protection architecture сохраняет огромную ценность против:

- dispersed fragments;
- secondary ejecta;
- near-miss debris;
- laser-ablation / partial structural damage;
- spall;
- grazing hits;
- локальных penetrations;
- damage propagation между compartments;
- internal fragment cones после пробития внешнего слоя.

NASA HVI work показывает сам принцип spaced protection: bumper разрушает incoming object и перераспределяет его momentum по большей площади вместо простого «поглощения энергии».

Поэтому будущая Star Empires protection stack остаётся многослойной:

```text
outer sacrificial / bumper layer
→ stand-off / void
→ catcher / intermediate layer
→ structural wall
→ armored citadel where justified
→ internal bulkheads / splinter protection
→ subsystem redundancy
```

Но конкретный material response каждого слоя должен иметь свою calibration domain.

---

## 5. Fragmentation не уничтожает кинетическую энергию

Если missile body разрушен без огромного тормозящего внешнего импульса, центр масс debris cloud продолжает двигаться примерно с прежней forward velocity.

Перехват выигрывает в первую очередь потому, что:

- intact penetrator превращается в множество элементов;
- появляется lateral velocity distribution;
- cloud расширяется с distance;
- только часть массы/импульса пересекает projected area корабля;
- оставшаяся масса проходит мимо;
- следующий protection layer работает уже против распределённой нагрузки, а не одного intact body.

NASA debris-cloud experiments показывают, что momentum distribution зависит от impact conditions; для ряда тестов измерялся Gaussian-like profile. Large projectiles также могут давать discrete coarse fragments, поэтому одна фиксированная dispersion velocity была бы ложной точностью.

---

## 6. v0.7 debris-cloud geometry

Для первого deterministic engineering model принимается **не physical fragment generator**, а статистическое momentum/mass field.

Если axial velocity остаётся примерно `v_x`, а lateral distribution имеет Gaussian sigma `σ_v`, то после stand-off distance `D`:

```text
flight time ≈ D / v_x

cloud spatial sigma
σ_space = D × σ_v / v_x
```

Для centered cloud и прямоугольной projected target area mass/energy fraction вычисляется интегрированием 2D Gaussian по target rectangle.

Это не говорит, сколько существует отдельных fragments. Оно отвечает на более ранний вопрос:

> какая доля исходной массы/энергии/импульса геометрически всё ещё направлена в корабль?

---

## 7. Reference battleship projected area

Из v0.1:

```text
beam   = 110 m
height = 85 m
```

Для nose-on engineering benchmark:

```text
projected rectangle = 110 × 85 m
                    = 9 350 m²
```

Это намеренно простой reference orientation. Production model позже должен использовать реальную sprite/hull collision geometry и текущий relative aspect.

---

## 8. Central dispersion seed: σ_v = 200 m/s

`200 m/s` **не объявляется реальной универсальной fragment velocity**. Это central sensitivity seed между deliberately narrow `50 m/s` и wide `500 m/s` cases.

Для исходной M missile (`12 t @ 18 km/s`):

| Stand-off | Cloud sigma | Source fraction intersecting 110×85 m | Intersecting mass | Intersecting kinetic energy |
|---:|---:|---:|---:|---:|
| 10 km | 111.1 m | **11.303%** | 1 356 kg | **219.7 GJ** |
| 20 km | 222.2 m | **2.965%** | 356 kg | **57.64 GJ** |
| 50 km | 555.6 m | **0.4809%** | 57.7 kg | **9.35 GJ** |
| 100 km | 1 111.1 m | **0.1205%** | 14.5 kg | **2.34 GJ** |

### Главный вывод

Даже удалённый hard kill не делает угрозу автоматически безопасной.

Но сравнение с intact impact:

```text
intact = 1.944 TJ concentrated in one body

20 km / 200 m/s cloud:
~57.6 GJ intersects ship geometry
~97% source mass/energy misses projected ship
```

То есть remote interception может уменьшить **концентрированную** угрозу более чем на порядок без нарушения conservation.

---

## 9. Dispersion sensitivity при одном stand-off

Для `D = 20 km`:

| Lateral σ_v | Ship hit fraction | Intersecting mass | Intersecting energy |
|---:|---:|---:|---:|
| 50 m/s | **37.669%** | 4 520 kg | 732 GJ |
| 200 m/s | **2.965%** | 356 kg | 57.6 GJ |
| 500 m/s | **0.4809%** | 57.7 kg | 9.35 GJ |

Разница почти два порядка по intersecting energy между narrow и wide cloud.

Поэтому fragmentation quality является настоящей характеристикой interceptor / warhead / impact geometry.

Нельзя свести все successful intercepts к одной outcome category.

Будущая система должна различать минимум:

```text
intact miss
intact terminal hit
mission/guidance kill with intact ballistic body
coarse fragmentation
fine fragmentation / vapor-rich cloud
complete geometric miss after dispersal
```

---

## 10. Projected compartment map

Чтобы не возвращаться к global hull HP, v0.7 делит reference nose-on rectangle на пять простых projected zones:

```text
PORT_COOLANT
STARBOARD_POWER
CENTRAL_CITADEL
DORSAL_WEAPONS
VENTRAL_SERVICE
```

Это **не финальная геометрия battleship**. Это минимальный deterministic proof, что один debris field может распределять raw damage packets по разным системам.

Для `20 km / 200 m/s`:

| Zone | Fraction of source | Raw intersecting kinetic energy |
|---|---:|---:|
| Port coolant region | 0.9386% | **18.25 GJ** |
| Starboard power region | 0.9386% | **18.25 GJ** |
| Central citadel | 0.4501% | **8.75 GJ** |
| Dorsal weapons | 0.3188% | **6.20 GJ** |
| Ventral service | 0.3188% | **6.20 GJ** |
| **Total** | **2.9648%** | **57.64 GJ** |

Сумма zone packets точно равна общей geometric exposure.

Следующий material layer уже решает, какая часть каждого packet:

- отражается/рассеивается;
- уходит в outer-layer vapor/ejecta;
- создаёт spall;
- проходит дальше;
- повреждает subsystem.

---

## 11. Почему v0.7 пока не задаёт `penetration = energy / armor`

NASA data показывают, что shield result чувствителен к:

- projectile density;
- shape;
- velocity;
- angle;
- bumper thickness;
- wall thickness;
- spacing;
- material;
- fragmentation / phase state.

Даже относительно близкие empirical equations могут расходиться, если применяются вне своей calibration base.

Для наших weapon projectiles пока не определены полностью:

- penetrator geometry / length / diameter;
- penetrator material/density;
- missile terminal orientation and structure state;
- armor layer materials;
- exact incidence angle;
- whether hit is intact, shattered, molten or fragment cloud.

Поэтому формула вида:

```text
penetration = kineticEnergy / armorValue
```

была бы хуже честного отсутствия финальной penetration model.

### v0.7 design decision

До material/geometry pass authoritative impact packet хранит **raw physics**, а protection solver обязан выбирать response model по явной calibration domain.

Если подходящей model нет:

```text
UNCALIBRATED_HEAVY_IMPACT
```

является корректным engineering state; production v1.0 не сможет выйти с таким состоянием для основных weapon families, но research layer имеет право честно его сохранить.

---

## 12. DamagePacket contract к будущему v1.0

Минимум:

```text
sourceWeaponId
impactPosition / projectedZone
impactDirection
incidenceAngle
incomingMassKg
incomingVelocityVectorMps
kineticEnergyJ
momentumVectorNs
fragmentationState
fragmentMassDistribution
fragmentVelocityDistribution
thermalFractionJ
material / density / geometry metadata
```

Protection response должен возвращать не `remainingHPDamage`, а следующий физический packet/state:

```text
stopped / deflected mass
residual mass
residual velocity / momentum
spall / secondary fragment distribution
thermal deposition
breach geometry
affected compartment(s)
```

---

## 13. Compartment / subsystem architecture

После внешней защиты damage path должен выглядеть примерно так:

```text
ImpactPacket
→ hull surface / protection stack
→ residual packet(s)
→ compartment intersection
→ local equipment / pipes / cables / magazines / crew
→ secondary effects
→ changed ship capability
```

Примеры emergent effects:

```text
coolant trunk damage
→ moduleCoolantTransferW falls
→ v0.6 thermal throttling appears naturally

power-bus damage
→ available electrical power falls
→ sensors/weapons compete for remaining capacity

magazine penetration
→ ammunition loss / secondary explosion risk

sensor aperture damage
→ track quality worsens
→ v0.3 fire-control envelope shrinks
```

Таким образом v0.7 связывает будущую damage model с уже построенными v0.3 и v0.6 systems вместо создания отдельного `damaged = -20% stats` слоя.

---

## 14. Armor mass и geometry

v0.1 уже резервирует для battleship десятки тысяч тонн selectable protection. Но эта масса не должна превращаться в равномерную сферу.

Будущая `ProtectionLayout` должна хранить физическую топологию:

```text
surface section / arc
layer material
layer thicknessM или arealDensityKgM2
standOffM
coverageAreaM2
backing / catcher
protected compartments
```

Это позволяет одной и той же массе дать разные design choices:

- толстая citadel на малой площади;
- более тонкая all-around fragment protection;
- усиленный nose / broadside;
- heavy magazine box;
- защищённые coolant trunks;
- дополнительные internal splinter bulkheads.

Armor allocation начинает конкурировать за реальную массу и geometry, а не только увеличивать HP.

---

## 15. Что v0.7 доказывает executable tests

1. M missile `12 t @ 18 km/s` = `1.944 TJ` и `216 MN·s`.
2. XL kinetic = `450 GJ`; M coilgun projectile = `2.8125 GJ`.
3. Все ship-weapon intact impacts находятся далеко за выбранным NASA shield-test reference scale и получают explicit extrapolation guardrail.
4. При фиксированном fragmentation seed увеличение stand-off монотонно снижает geometric exposure.
5. При фиксированном stand-off рост lateral dispersion монотонно снижает geometric exposure.
6. Fragmentation не «удаляет» source energy: harness только вычисляет, какая доля поля пересекает ship geometry.
7. Projected compartment zones строго сохраняют mass/energy/momentum accounting.
8. Ни один test не использует global armor HP или class-name damage modifier.

---

## 16. Что остаётся открытым после v0.7

v0.7 намеренно **не притворяется финальным armor solver**.

До `Ship Mathematics v1.0 Design Baseline` необходимо закрыть:

- reference penetrator geometries/materials для kinetic families;
- reference missile body / warhead terminal states;
- armor material families и их temperature/density/strength data;
- калиброванный response для coarse/fine fragments;
- heavy-impact response model, пригодный для наших energy scales;
- incidence / ricochet / grazing behavior;
- internal spall / secondary fragment generation;
- subsystem damage thresholds tied to physical construction.

Ключевая архитектура, однако, уже определена: **разные impact domains требуют разных response models, а все они обмениваются SI DamagePacket data**.

---

## 17. Следующий research step

Следующий основной pass остаётся **v0.8 — sensors / signature / track / ECM/ECCM**, потому что эта область уже достаточно независима от выбора final armor material coefficients.

Параллельно в backlog к v1.0 фиксируется отдельный **material-response closure pass** после того, как будут заданы реальные projectile geometries и protection materials. Он обязан убрать `UNCALIBRATED_HEAVY_IMPACT` для всех основных production weapon families до acceptance v1.0.

Это лучше, чем сейчас придумывать коэффициент penetration без данных, а затем строить на нём весь fitting/runtime слой.
