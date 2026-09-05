# M22.6 exact-content tactical controls

Status: diagnostic integration evidence; not balance sign-off.

Source: `42ebd4ccf2a7cffd204c4b0999759ab48e69d07f`, clean working tree. Local Java 17 execution using all compiled branch sources and the main desktop dependency JAR. Full repository CI is a separate gate.

Each variant ran 100 seeds, each in default and mirrored geometry, for 600 ticks / 30 seconds. The seed changes separation, lateral offset and initial velocity. This is a controlled geometry sensitivity sweep; it is not 100 independent campaign histories.

The ordinary Stage-19 policy is shared. Starting actor knowledge is empty. Faction identities, fitted modules, material definitions and physical ammunition remain attached to the faction when geometry and side are swapped.

| Variant | Empire shots (mean; range) | Union shots (mean; range) | Empire hull integrity (mean) | Union hull integrity (mean) | Unauthorized target ticks |
| --- | --- | --- | --- | --- | --- |
| EMPIRE_SENSOR_LOSS | 0.00; 0–0 | 8.00; 8–8 | 0.920729 | 1.000000 | 0 |
| LIMITED_MAGAZINES | 4.00; 4–4 | 4.00; 4–4 | 0.961148 | 0.950165 | 0 |
| PATROL | 7.09; 7–8 | 7.10; 7–8 | 0.929541 | 0.910236 | 0 |
| UNION_SENSOR_LOSS | 8.00; 8–8 | 0.00; 0–0 | 1.000000 | 0.898194 | 0 |

## Starting physical burden

| Faction | Dry mass, kg | Loaded mass, kg | Crew | Continuous power demand, W | Ammunition, kg | Propellant, kg | Acceleration, m/s² |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| faction.imperial_directorate | 32810000.0 | 33828000.0 | 190 | 2857000000.0 | 18000.0 | 1000000.0 | 0.8868393047179851 |
| faction.industrial_combine | 31350000.0 | 32366800.0 | 170 | 2560000000.0 | 16800.0 | 1000000.0 | 0.9577715436805616 |

Normal and sensor-loss variants begin with 120 rounds per destroyer. The magazine control begins with four rounds: 600 kg for Empire and 560 kg for Union. Both begin with 1,000,000 kg of reaction mass. These are declared scenario inputs; equal round count does not equate ammunition mass or industrial burden.

## Causal checks

- A destroyed fitted `utility_sensor` produces zero hostile-contact ticks and zero shots for that faction. The opponent continues through its own sensor authority. No target is selected outside the actor-visible domain.
- In the four-round control both sides expend exactly four physical rounds and subsequently have no authorized fire. No stock refill occurs.
- The source commit corrects repeated armor/shield settlement while a penetrating body remains in the same hull. Native and external residual tests check one hit per crossing and a new hit after exit/re-entry.
- Initial engineering saves are tested separately for sampled continuation in CI. This archive was executed from fresh initial states; it is not mid-flight battle-save evidence.

## Representative adverse traces

- Lowest Empire hull integrity: seed `22600067`, permutation `MIRRORED`, separation `953.345 m`. See raw PATROL phases at 0, 7.5, 15, 22.5 and 30 seconds for shield expenditure, local integrity, fire authorization and withdrawal decisions.
- Lowest Union hull integrity: seed `22600018`, permutation `DEFAULT`, separation `1092.333 m`. See raw PATROL phases at 0, 7.5, 15, 22.5 and 30 seconds for shield expenditure, local integrity, fire authorization and withdrawal decisions.

## Limits and remaining gates

Equal-burden fleet normalization, strategic faction-policy competence, support convoys, prepared defense, offensive projection, rolling attrition, replacement/recovery curves and human B18–B20 are still required. These controls cannot establish a scenario winner, an economy-wide advantage, a completed B07/B11/B12 campaign, or M22.6 completion. No balance values were tuned from these runs.

Raw JSON is retained with deterministic gzip compression. `manifest.json` records both compressed and uncompressed SHA-256 digests; the JSON includes the exact source SHA, clean-tree flag and content fingerprint.
