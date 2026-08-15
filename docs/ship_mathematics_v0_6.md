# Star Empires — Ship Mathematics v0.6: Thermal / Power Endurance

> Статус: **executable engineering / design seed v0.6**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_mathematics_v0_1.md`–`v0_5.md`  
> Код: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV06ThermalHarness.java`  
> Snapshot: `docs/benchmarks/thermal_power_reference_v0_6.json`

---

## 1. Задача v0.6

v0.5 обнаружил потенциальный optimistic seam: после исчерпания interceptor magazines связка `Battleship + Escort Destroyer` всё ещё могла закрывать каноническую 48-ракетную волну только point-defense lasers.

Первоначальная гипотеза была:

> после добавления heat carryover PD должен заметно деградировать.

v0.6 проверяет эту гипотезу физически, а не подгоняет thermal coefficients под желаемый combat outcome.

Результат важный:

> **общекорабельное тепло не является естественным bottleneck для healthy reference PD destroyer / battleship.**

Уже принятый v0.2 design имеет большой ship-level heat margin. Реалистичное ограничение возникает раньше на уровне **локального laser emitter / coolant interface**, а затем — только при повреждении cooling, уменьшении radiator availability или у кораблей, которые уже находятся близко к своему sustained thermal envelope.

Поэтому v0.6 вводит многоуровневую thermal architecture вместо одного абстрактного `heat bar`.

---

## 2. Внешняя инженерная опора

Используются только как физические ориентиры, а не как доказательство современной реализуемости боевых систем Star Empires:

1. NASA spacecraft thermal-control guidance: в вакууме конечный сброс тепла выполняется radiative surfaces; deployable radiators увеличивают доступную излучающую площадь.
2. NASA NTRS `20080048181`, *Lightweight Carbon-Carbon High-Temperature Space Radiator*: carbon-carbon / heat-pipe radiator исследовался для порядка `500–1000 K`, с целью areal density около `2 kg/m²`.
3. NASA NTRS `20130001608`, *Lightweight, High-Temperature Radiator for Space Propulsion*: повышение rejection temperature — основной путь увеличения heat rejection per area; исследовались carbon-based radiators около `1000 K` и выше.
4. NASA NTRS `20150003481`, *Phase Change Material Heat Sink for an ISS Flight Experiment*: PCM heat sink рассматривается как реальный способ временного хранения thermal energy и уменьшения требований к instantaneous rejection.
5. NASA optical-terminal work отдельно показывает, что high-precision optical hardware имеет собственные thermal/thermo-mechanical constraints; ship-wide heat capacity не означает, что optics можно нагревать без локального ограничения.

Наши конкретные `1100 K`, multi-MW combat lasers и их cooling interfaces остаются technology assumptions зрелой fusion-era цивилизации.

---

## 3. Каноническая thermal architecture v0.6

Вместо одного heat pool:

```text
weapon / sensor / reactor subsystem
        ↓ generated waste heat
local thermal mass / PCM / structure
        ↓ finite coolant-transfer interface
ship thermal bus / heat exchangers
        ↓ finite transport capacity
radiator loops / emergency thermal stores
        ↓
radiation to space
```

Для production data это означает минимум три разные величины:

```text
moduleLocalThermalCapacityJ
moduleCoolantTransferW
shipHeatBus / radiatorRejectionW
```

Одна величина не заменяет другую.

### Почему это важно

5 MW PD laser может иметь:

- достаточно общекорабельной electrical power;
- огромный battleship thermal reserve;
- но перегреть чувствительный emitter/optics, если локальный coolant interface не успевает переносить несколько MW waste heat.

И наоборот: локально хорошо охлаждаемый emitter не может бесконечно работать на корабле с разрушенной общей cooling loop.

---

## 4. Radiator equation

Сохраняется SI-модель:

```text
P = ε × σ × A × (T⁴ - T_space⁴)
```

В benchmark `T_space` пренебрежимо мало относительно hot radiator.

Reference:

```text
ε = 0.90
T = 1100 K
σ = 5.670374419e-8 W/(m² K⁴)
```

Получаем:

```text
heat flux = 74 717.96 W/m²
```

Это немного точнее прежнего округлённого `74.7 kW/m²` и не меняет v0.1/v0.2 balance conclusions.

`1100 K` — setting-era extrapolation чуть выше диапазона многих опубликованных NASA high-temperature radiator concepts; до v1.0 конкретные radiator materials должны получить отдельную material definition / validation.

---

## 5. Ship-level thermal margins из уже принятого v0.2

Никакие ship heat numbers здесь не подменяются новыми. v0.6 пересчитывает уже существующие v0.2 значения.

| Reference design | Installed waste heat | Heat rejection | Continuous margin | Minimum deployed radiator fraction | Full hot-radiator area | Legacy zero-radiator emergency time* |
|---|---:|---:|---:|---:|---:|---:|
| Torpedo Corvette | 11 MW | 30 MW | 19 MW | 36.67% | 402 m² | 90.9 min |
| Recon/EW Frigate | 76.5 MW | 80 MW | **3.5 MW** | **95.63%** | 1 071 m² | 43.6 min |
| Escort Destroyer | 60.8 MW | 200 MW | **139.2 MW** | 30.40% | 2 677 m² | 164.5 min |
| General Cruiser | 402.3 MW | 600 MW | 197.7 MW | 67.05% | 8 030 m² | 82.9 min |
| Battlecruiser | 840.8 MW | 1.5 GW | 659.2 MW | 56.05% | 20 075 m² | 118.9 min |
| Battleship | 1.3873 GW | 4.0 GW | **2.6127 GW** | 34.68% | 53 535 m² | 240.3 min |
| Fleet Carrier | 1.0203 GW | 3.0 GW | 1.9797 GW | 34.01% | 40 151 m² | 245.0 min |

`*` Использует legacy v0.1 class thermal-buffer seed и предполагает полное отсутствие radiator rejection. Это **не production-ready number**: до v1.0 thermal capacity должна быть физически связана с mass/material/temperature range, а не существовать как бесплатные TJ.

### Главный результат

Reference destroyer и battleship специально имеют большой sustained heat margin.

Следовательно:

```text
healthy battleship/destroyer PD
≠ ship-level thermal bottleneck
```

Попытка снизить их PD endurance искусственным общим `heat cap` противоречила бы уже принятому physical fit.

Recon frigate — противоположный случай: он требует почти 96% своей reference radiator capacity для одновременной rated работы. Здесь heat действительно является сильным role trade-off.

---

## 6. Electrical power также не ограничивает reference PD

Из v0.2:

| Design | Rated electrical | Installed continuous | Margin |
|---|---:|---:|---:|
| Corvette | 150 MW | 34 MW | 116 MW |
| Recon Frigate | 400 MW | 194 MW | 206 MW |
| Escort Destroyer | 1.0 GW | 191 MW | 809 MW |
| Cruiser | 3.0 GW | 1.328 GW | 1.672 GW |
| Battlecruiser | 8.0 GW | 3.435 GW | 4.565 GW |
| Battleship | 20 GW | 5.909 GW | **14.091 GW** |
| Fleet Carrier | 15 GW | 3.171 GW | 11.829 GW |

Reference S PD laser:

```text
electrical input = 8 MW
beam output      = 5 MW
waste heat       = 3 MW
wall-plug seed   = 62.5%
```

Даже десять PD emitters требуют всего `80 MW` electrical input.

Поэтому для текущего escort group проблема также не в общей reactor/bus capacity.

---

## 7. Local PD thermal seed v0.6

Для healthy S PD emitter принимается provisional engineering package:

```text
beamPowerW                 = 5 000 000
electricalInputW           = 8 000 000
wasteHeatW                 = 3 000 000
localCoolantTransferW      = 1 200 000
localThermalCapacityJ      = 36 000 000
thermalRestartJ            = 18 000 000
```

Это **не material constant**. Это integration seed для 12-t / 20-m³ S weapon package.

### 7.1. Cold continuous burst

Во время firing:

```text
net local heating
= 3.0 MW - 1.2 MW
= 1.8 MW
```

Следовательно:

```text
36 MJ / 1.8 MW = 20 s
```

Healthy cold emitter может непрерывно работать примерно 20 s прежде, чем локальный thermal store заполнится.

### 7.2. Sustainable duty cycle

На длинном горизонте:

```text
sustainableDuty ≈ coolantTransfer / wasteHeat
                = 1.2 / 3.0
                = 40%
```

Это не значит, что лазер стреляет фиксированными 40%. Реальный duty получается из target availability, fire-control и локального heat state.

---

## 8. Связь с v0.5 terminal geometry

v0.5 threat seed:

```text
PD range       = 300 km
incoming speed = 18 km/s
```

Максимальное время одной ракеты внутри PD envelope:

```text
300 km / 18 km/s = 16.667 s
```

Весь 800-km approach занимает:

```text
800 km / 18 km/s = 44.444 s
```

Если волны не перекрываются и следующая начинается сразу после завершения предыдущего approach, абсолютный максимальный requested duty одного emitter:

```text
16.667 / 44.444 = 37.5%
```

Сравнение:

```text
healthy local sustainable duty = 40.0%
worst current non-overlap demand = 37.5%
```

Это ключевой quantitative result v0.6.

---

## 9. Conservative 13-wave emitter acceptance

Новый executable harness намеренно **строже**, чем фактический v0.5 fire-control schedule:

- один emitter каждый раз обязан стрелять весь `300 km` envelope;
- `16.667 s` full-power request на каждую волну;
- 13 волн;
- следующая 800-km волна начинается без дополнительного recovery pause;
- target availability никогда не даёт бесплатного idle time внутри terminal window.

### Healthy cooling: 1.2 MW

Результат каждой из 13 волн:

```text
delivered beam ≈ 16.66 s
thermal throttling = 0 s
peak local heat ≈ 29.988 MJ
capacity = 36 MJ
```

**PASS.**

Следовательно, при текущей non-overlapping v0.5 wave geometry healthy thermal model не изменяет ни одну v0.5 result point: фактическая target schedule не может требовать больше emitter time, чем полный continuous terminal envelope.

Это означает, что прежний v0.5 warning «PD почти наверняка ослабнет от heat carryover» был слишком сильным. Исследование его уточняет:

> heat carryover становится problem только если cooling degraded, волны начинают физически перекрываться, radiator/coolant topology повреждена или fit изначально ближе к thermal limit.

---

## 10. Cooling damage sensitivity

Чтобы thermal model имел физический failure mode, harness повторяет тот же строгий train при ухудшенном local coolant throughput.

### Degraded cooling: 1.0 MW

```text
sustainable duty = 33.3%
requested duty   = 37.5%
```

Wave 1 ещё проходит полностью за счёт local store:

```text
delivered ≈ 16.66 s
throttled = 0
```

Но уже wave 2:

```text
delivered ≈ 15.24 s
throttled ≈ 1.42 s
peak = 36 MJ
```

К wave 13 система приходит примерно к:

```text
delivered ≈ 14.82 s
throttled ≈ 1.84 s
peak = 36 MJ
```

То есть повреждение coolant path / pump / heat exchanger даёт реальный и накопительный combat effect без `-20% laser damage` modifier.

### Почему это полезно для damage model

Будущий subsystem damage может менять:

```text
moduleCoolantTransferW
```

и автоматически получать:

- меньший sustained duty;
- thermal inhibit;
- меньше доступного dwell;
- больше terminal leakers.

Никакой отдельный scripted `PD damaged state` не нужен.

---

## 11. Что делать с legacy class thermal buffers

v0.1 задавал:

```text
Corvette      60 GJ
Frigate      200 GJ
Destroyer    600 GJ
Cruiser      2 TJ
Battlecruiser 6 TJ
Battleship   20 TJ
Carrier      15 TJ
```

v0.6 **не объявляет эти числа окончательными**.

Проблема: `thermalCapacityJ` не может существовать без физического носителя.

К v1.0 нужно перейти к одному из вариантов:

```text
thermalStoreMassKg
specificHeatJPerKgK
usableTemperatureRangeK
phaseChangeLatentHeatJPerKg
```

или к валидированному эквивалентному material model, из которого capacity выводится.

Conceptually:

```text
sensibleHeatCapacityJ
= mass × specificHeat × usableDeltaT

phaseChangeCapacityJ
= mass × latentHeat
```

Допустимо использовать структуру/броню как часть emergency thermal inertia только если это явно учтено в mass topology и допустимых температурах соответствующих compartments.

### Design decision v0.6

До material pass legacy TJ values трактуются как:

> **upper-bound emergency whole-ship thermal-capacity seeds**, а не бесплатный тактический heat pool.

Их нельзя использовать для production fitting без mass accounting.

---

## 12. Radiator deployment становится реальной характеристикой корпуса

Из ratio:

```text
minimumRadiatorFraction
= installedContinuousWasteHeat / sustainedHeatRejection
```

получается естественная доктринальная разница.

### Recon frigate

```text
minimum ≈ 95.6%
```

При rated recon/EW load он почти не может уменьшить radiator exposure без накопления тепла.

Это соответствует его роли:

- сильные sensors;
- сильная EW;
- высокая thermal signature;
- ограниченная способность долго скрывать radiators.

### Escort destroyer

```text
minimum ≈ 30.4%
```

Он имеет большой cooling reserve и способен переживать потерю части radiator capability значительно лучше.

### Battleship

```text
minimum ≈ 34.7%
```

Capital line combatant также имеет большой redundancy margin.

Это намного интереснее class bonus: thermal survivability возникает из installed systems и radiator architecture.

---

## 13. Radiator mass: правильный вывод из реальных исследований

NASA carbon-carbon high-temperature radiator work показывает, что собственно radiator panel может иметь areal density порядка нескольких kg/m²; более традиционные spacecraft radiators могут быть заметно тяжелее.

Даже если future panel technology близка к `2.2 kg/m²`, reference Battleship `~53 535 m²` соответствует только порядка `118 t` bare radiator-panel mass.

При более консервативных `10 kg/m²` это порядка `535 t`.

Обе величины малы относительно `~339 000 t` design dry mass battleship.

Следовательно, для огромного future warship radiator design pressure вероятно возникает не столько из bare panel mass, сколько из:

- огромной геометрической площади;
- deployment structure;
- heat pipes / coolant manifolds;
- pumps / redundancy;
- armor / shutters;
- view-factor restrictions;
- vulnerability to fragments and laser fire;
- thermal signature.

Для v1.0 нельзя использовать NASA `2.2 kg/m²` как универсальную production constant: это reference technology result, а не масса всей нашей combat radiator installation.

---

## 14. Data-model consequence

Будущая thermal-capability модель должна быть compositional.

### ModuleDefinition

Минимально:

```text
continuousElectricalPowerW
peakElectricalPowerW
wasteHeatW
localThermalCapacityJ
coolantTransferRequiredW / coolantTransferLimitW
preferredOperatingTemperatureK
shutdownTemperature / thermalState metadata
```

### Hull / thermal architecture

```text
heatBusCapacityW
hotRadiatorAreaM2
hotRadiatorTemperatureK
hotRadiatorEmissivity
coolantLoopCapacityW
emergencyThermalStore definition
radiator deployment / damage state
```

### Derived state

```text
radiatorRejectionW
continuousHeatMarginW
localModuleHeatJ
shipStoredHeatJ
availableCoolantTransferW
thermalThrottleFraction / inhibit state
```

Player и AI должны использовать один и тот же state.

---

## 15. Acceptance invariants v0.6

Executable tests фиксируют:

1. `1100 K / ε 0.9` radiator flux остаётся `~74.718 kW/m²`.
2. Current PD terminal window = `16.667 s`.
3. Conservative non-overlap requested PD duty = `37.5%`.
4. Healthy 1.2-MW local cooling gives `40%` long-run duty and `20 s` cold full-power burst.
5. Healthy emitter выдерживает 13 consecutive full-envelope requests без thermal inhibit.
6. Degraded 1.0-MW coolant path уже создаёт deterministic thermal throttling.
7. Recon Frigate remains >95% radiator-loaded, while Destroyer/Battleship have large heat margins.
8. All calculations remain SI and derive from physical power/energy relationships.

---

## 16. Что v0.6 меняет в наших выводах

### Было

> PD-only endurance выглядит слишком сильным; вероятно heat автоматически исправит его.

### Стало

> При уже принятой v0.2 энергетике **здоровая** battleship/destroyer PD архитектура физически способна выдержать текущий non-overlapping v0.5 terminal envelope. Искусственно занижать cooling ради желаемого результата не следует.

Следовательно, если PD всё ещё окажется чрезмерно эффективной, следующие подозреваемые — не reactor/radiator budget, а:

- missile surface / internal hard-kill fluence;
- fragments after laser/interceptor kill;
- residual hypervelocity debris;
- track uncertainty;
- ECM/decoys;
- emitter damage;
- overlapping/coordinated salvo timing;
- target allocation limitations.

Это полезная коррекция: исследование отбрасывает неверный балансный рычаг вместо того, чтобы подтверждать первоначальное ожидание.

---

## 17. Следующий шаг к v1.0

Следующий приоритет — **Ship Mathematics v0.7: protection / penetration / fragmentation / compartment damage**.

Нужно закрыть:

1. intact missile / kinetic impact vs armor stack;
2. fragment cloud после interceptor kill;
3. residual missile/debris после laser hard kill;
4. spaced / Whipple-like protection для fragments в пределах применимости;
5. heavy penetrator path без псевдореалистичного переноса MMOD equations за их calibration envelope;
6. local compartment traversal;
7. damage to coolant/power/data/weapon/sensor systems;
8. deterministic damage packets и survivability matrix.

После v0.7 можно будет снова вернуться к тому же missile-defense benchmark и проверить уже не просто `leaker / no leaker`, а последствия близких перехватов и debris.
