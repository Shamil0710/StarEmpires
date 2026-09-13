# Stage 22 M22.6 — core pair balance / freeze

> Status: **FROZEN MACHINE CANDIDATE — HUMAN SIGN-OFF PENDING**  
> Pair: `core.empire` / `core.industrial_union`  
> Stable identities: `faction.imperial_directorate` / `faction.industrial_combine`  
> Freeze manifest: `stage22.core_pair_freeze_manifest.v3`  
> Frozen fingerprint: `6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`

M22.6 closes the first cross-package balance/freeze gate. It reuses the accepted Stage 17.5, 18,
19, 20 and 21 authorities plus the authored Stage-22 package/profile/visual contracts. It does not add
a faction-owned combat, economy, logistics, territory, recovery or strategic-decision authority.

This document records the current machine candidate. It is **not** a Stage-22 completion record: the
B18–B20 human gates and final exact-SHA RC/sign-off still have to pass. A separate post-freeze
integrated-campaign handoff is tracked outside M22.6 and must not be pulled into this balance PR.

---

## 1. Canonical scenario and paired protocol

`Stage22CorePairBalanceCatalog` owns the versioned B00–B20 contract
`stage22.core_pair_balance_suite.v1`. All 21 scenarios are required because the repository already
contains production-ready territory, treaty/access and finite retool authorities for B15–B17.

`Stage22CorePairExperimentProtocol` schedules exactly two runs for every seed:

1. DEFAULT assignment;
2. MIRRORED slot/topology/hazard assignment.

Faction identity and doctrine stay attached to the faction rather than the slot. Tuning uses 30 paired
seeds. Materially stochastic RC lanes use at least 100 paired seeds.

The seed pair is the independent statistical unit. DEFAULT and MIRRORED observations are averaged per
seed before aggregation. Hard causal/integrity rules — authority ownership, actor-bounded knowledge,
conservation, finite stores, physical arrival/admission and save/load continuation — remain strict on
every individual run. Stochastic Empire survivability/robustness is accepted only when the paired
mean difference retains a positive approximate 95% lower confidence bound. Individual inversions stay
in raw evidence and causal traces; they are never deleted or converted into fake hard-rule failures.

---

## 2. Machine acceptance state

The reconciled evidence ledger is `stage22_m22_6_acceptance_ledger.md`. At the current frozen machine
candidate, B00–B17 are implemented through existing production authorities:

- **B00/B01:** stable identities, content legality, profile/production persistence, authored-product
  generated-world composition, final operation continuation and deterministic Stage-20.5 scheduler
  persistence;
- **B02:** matched finite cold start, ordinary logistics, zero free finished stock, Union paid retool,
  midpoint industrial/production persistence and actor-bounded 30-pair knowledge evidence;
- **B03/B04:** real facility construction and finite critical-material shortage/recovery through
  Stage-18 material/work/storage/manufacturing authorities;
- **B05:** 100-pair finite freight loss, destroyed-lot provenance and surviving alternate delivery;
- **B06:** three physical multi-system raid lanes with ordinary FTL, Stage-21E supply decisions,
  exact Stage-19 consequences, withheld-lane non-mutation and save continuation;
- **B07:** one raw equal-burden authorization envelope, common tactical policy, paid replacement/retool
  counter-cost and paired robustness without a scalar power score;
- **B08:** the same persistent freight order is denied/admitted solely by survival of the physical
  interdictor FleetId after exact Stage-19 resolution, with downstream physical production evidence;
- **B09:** prepared-defense readiness, physical reserve arrival, finite support, three-contact
  endurance, exhausted-stock fail-closed behavior and save continuation;
- **B10:** exact strategic destroyer/tanker/support projection through ordinary FTL, physical reaction
  mass burn and finite tanker replenishment across a generated-world checkpoint;
- **B11:** actor-bounded command/datalink behavior reconstructed from persisted physical sensor/network
  state rather than saved tactical omniscience;
- **B12:** finite authored magazine exhaustion and ordinary Stage-19F replenishment with exact
  post-reload continuation;
- **B13/B14:** rolling physical attrition, actual generated-world FleetId loss, Stage-21E/21G loss and
  paid replacement handoff, finite backlog, T50/T80 and lost-capability recovery evidence;
- **B15:** actual generated-world FleetId, real FTL, exact core package, reconstructed readiness,
  Stage-21D/E/F occupation and byte-identical midpoint/final continuation;
- **B16:** actual core identities and ordinary `TradeController` prove pending/active/breached/recovered
  physical trade-volume consequences across persistence;
- **B17:** actor-observed threat → common strategic planner decision → finite Union series retool with
  paid work/energy and explicit opportunity cost.

The AI competence gate is machine-proven rather than inferred. Equivalent actor-known evidence is
identical before doctrine; the same doctrine and evidence choose the same strategic goal regardless of
faction name; unobserved shortages/threats cannot manufacture candidates. Empire/Union divergence
appears only after declarative doctrine is applied through the common planner.

Equal-burden and cross-scenario Gate C evidence preserves raw dimensions. The current authored
contrast remains causal: Union receives lower resource/replacement burden and series throughput but
pays correlated commonality/retool exposure; Empire receives surviving protection/preparation value
but pays capital/support burden. No global Pareto winner or faction-wide hidden combat/economic scalar
is introduced.

---

## 3. Literal schema-3 freeze surface

`Stage22CorePairFreezeManifest.captureFrozen()` compares the live semantic surface with literal pins
and fails closed on any drift. The foundation acceptance executes this comparison in normal CI.

The frozen aggregate fingerprint is:

```text
6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4
```

Pinned package/content identities:

| Surface | Empire | Industrial Union |
| --- | --- | --- |
| package | `53e74820d135495aa3b9cc518c7a295c03d7112997a1903767248354e4da97b3` | `e42309a19e5f61675b96255556ec34f09c1f21c96d14c0dc2f19daa556efc5a7` |
| production | `e9266a8f998197d8e1b54a387023ff8593c222108d802ec7e0aa823a51e66f05` | `b61e939f5fe7963379d253992e1b4682f7b9509a88fe5e14ea89eed701b9004b` |
| engineering | `465c25304591faf850c48730b8c82f73f379d300367a5e41e3d535ede12f5c24` | `d03531431e0054afa4adb1e61fd4854d26d3f13bf098829d814244231e527506` |
| manufacturing | `5fb1a97eb30b044e1c2d7c1efaa3e787a944105831b510443b17579acf08d19a` | `8f9811783f1094d6a3e1546c69db9728e8309dc46c093f383c68b5cdcf03917f` |
| shipyard | `53a2525b9a76383c13f3b837330c6d3f29de13057afba92f7ceec7a30bf57448` | `9c0aa7b1c235bc4271a4e875f116ecef33310839c512332302423c03fd5bbe3a` |
| station infrastructure | `2168d68878246a5b73023a3ff28688739df95866c1b1ac50d33d337f44914743` | `2168d68878246a5b73023a3ff28688739df95866c1b1ac50d33d337f44914743` |
| character lineup | `c350beff8eda3e5f2b2b638db23aa898a80f56ead0a96896f086acd881535cea` | `fe66d6faaa00b6511fdca96dc3df641012d4a4cbe7f7dc1c900db0811c71d307` |

Profile/runtime pins:

```text
empire.profile = 7da3b54e02b7ee57cef1d403e863e466ff72fbdc63f4b25b0c1db7ab77a7d521
core.profile   = 4269ac307ee11f29ba4fc64ddc6c276e3d2f7cb319bae6660c8fb871b1b1580d
runtime.engineering = 3e568c106a9c4607587aa216333d6de366e06ac95b16629641933575e74056bf
runtime.ammunition  = f0aff3cfce04d5df87aab37f70acc847e35845c630338f93bb09e1ffd965f591
runtime.launchers   = b855113394d552ea887130de5bbca80da7485f04834c0f19e0ee6a9a289756f1
runtime schema:migration = 1:1 for engineering, ammunition and launchers
```

The manifest also literally pins every B00–B20 scenario version and all current schema-version fields.
A content/schema/scenario edit after this point is therefore an intentional freeze revision, not an
implicit redefinition of the accepted candidate.

---

## 4. Persistence and migration freeze

The Stage-20.5 generated-world continuation contract is part of the balance freeze because scheduler
configuration changes remote authoritative state.

Pinned persistence contract:

```text
bridgeVersion = stage20_5.generated-world-runtime-bridge.v1
checkpointSchemaVersion = 3
fileFormatVersion = 4
migrationVersion = stage20_5.generated-world-runtime-migration.v1
supportedFileFormats = [1, 2, 3, 4]
```

Schema 3 stores strategic-step size and remote-update budget. Legacy formats 1–3 deliberately restore
their historical scheduler defaults. Invalid future/unknown state remains fail-closed. The B15
occupation continuation and the dedicated scheduler persistence regression protect this contract.

---

## 5. Release-candidate regression

The `Stage 22 paired RC evidence` workflow runs seven independent materially stochastic lanes on one
exact SHA:

- tactical sensitivity;
- distributed raids;
- equal-burden patrol;
- prepared defense;
- degraded command;
- rolling attrition;
- cross-scenario dominance.

Every lane executes 100 distinct seeds with DEFAULT + MIRRORED assignments and retains raw vectors,
causal samples, Surefire reports and statistical summaries. `tools/stage22/summarize_evidence.py`
rejects dirty/mixed source identity, incomplete/duplicate pairs, non-finite metrics and any hard-rule
breach. Historical green RC runs are diagnostic only after the source SHA changes; final sign-off must
reference the final frozen candidate SHA.

Normal Java-17 `clean verify` remains mandatory in parallel. The pre-enforcement literal-freeze head
`0ebfe6a6d655a28acbbb0823a555ac1d4cd5e3d3` passed CI run `34352758441`; the final reconciled
candidate must pass its own normal CI and RC before human sign-off.

---

## 6. Human B18–B20 gates

Machine evidence cannot close L7. `stage22_m22_6_human_review_runbook.md` is the authority for the
review packet and raw-response format.

Required thresholds remain unchanged:

- **B18 causal explanation:** at least 80% correct primary causal dependency;
- **B19 grayscale ships:** at least 90% correct faction distinction and at least 80% correct role read;
- **B20 character style:** at least 90% shared Character Master Prompt style-check pass rate.

No submitted PR review or archived human response currently satisfies these gates. Assistant/image
classification/prompt inspection is not a substitute. Review results must be tied to the final build
SHA and the frozen fingerprint above.

---

## 7. Known limitations and explicit non-goals

M22.6 is a balance/freeze milestone, not the final production-campaign integration milestone. It does
not claim a newly introduced unified campaign coordinator, first-hour production-client journey,
ordinary-client Stage-22 asset resolver or wall-clock-to-simulation-time logistics rewrite. Those
integration concerns are tracked separately after the freeze so they do not contaminate balance
acceptance or create duplicate authorities here.

The bounded B00–B17 scenarios nevertheless cross the real common authorities required to support each
balance conclusion. Where a scenario needs physical persistence, generated-world FleetIds, actual
Stage-19 consequences, finite stock/work, ordinary trade, territorial transition or actor-bounded
planning, the acceptance test uses that authority rather than a Stage-22 proxy score.

No accepted limitation may waive:

- a hard authority/persistence/conservation failure;
- exact core content identity;
- finite resource costs;
- actor-bounded knowledge;
- paired stochastic protocol;
- human B18–B20 thresholds.

---

## 8. Closure checklist

M22.6 may be signed off only when all of the following are true:

- [x] B00–B17 machine acceptance implemented on the common authorities;
- [x] AI competence and equal-burden/no-global-dominance machine gates implemented;
- [x] literal schema-3 freeze pins and Stage-20.5 migration contract established;
- [x] freeze drift is executable/fail-closed in normal CI;
- [ ] final exact-SHA Java-17 `clean verify` is green;
- [ ] final exact-SHA seven-lane 100-pair RC is green and raw evidence retained;
- [ ] B18 human causal-explanation gate passes;
- [ ] B19 human grayscale faction/role gate passes;
- [ ] B20 human shared-character-style gate passes;
- [ ] no unresolved review blockers remain;
- [ ] signed-off report references the exact accepted SHA/fingerprint;
- [ ] PR #355 is merged only after all gates above;
- [ ] post-merge `main` verification is green;
- [ ] roadmap/status closeout is performed after merged evidence, not before.

Until the unchecked items are satisfied, M22.6 and Stage 22 remain open and Stage 23 must not start.
