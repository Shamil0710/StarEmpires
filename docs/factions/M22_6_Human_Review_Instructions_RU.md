# StarEmpires — M22.6 Human Review Runner v2

Runner предназначен для оставшихся human-only гейтов M22.6 и работает поверх **точного frozen release candidate**, не изменяя его.

## Frozen identity

- build SHA: `5fb4c523cf3d169677c602638b4f62726b915272`
- freeze manifest: `stage22.core_pair_freeze_manifest.v3`
- freeze fingerprint: `6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`
- scenario suite: `stage22.core_pair_balance_suite.v1`
- review packet: `m22.6-human-review-packet.v2`
- frozen source branch: `stage22-6-core-pair-balance-freeze`
- helper branch: `stage22-6-human-review-tools`

Runner создаёт отдельный detached worktree примерно по пути:

`..\StarEmpires-M22.6-review-5fb4c523`

Evidence хранится отдельно:

`%USERPROFILE%\Documents\StarEmpires-M22.6-HumanReview-5fb4c523`

Frozen worktree не должен изменяться.

---

## Как обновить tools-ветку из GitHub

Если `StarEmpires-M22.6-tools` уже создан как отдельный worktree:

```bat
git -C ..\StarEmpires-M22.6-tools fetch origin stage22-6-human-review-tools
git -C ..\StarEmpires-M22.6-tools checkout --detach origin/stage22-6-human-review-tools
```

Если tools-worktree ещё не создавался:

```bat
git fetch origin stage22-6-human-review-tools
git worktree add ..\StarEmpires-M22.6-tools origin/stage22-6-human-review-tools
```

После обновления запустите:

`..\StarEmpires-M22.6-tools\M22_6_Human_Review_Runner.bat`

---

## Что изменено в v2

Старая версия создавала CSV-шаблоны, но не создавала полноценный blinded review packet. Поэтому пустые `b18_responses.csv`, `b19_responses.csv`, `b20_responses.csv` не являются human evidence.

Версия v2 работает fail-closed:

- **B19 READY** — строится корректный blinded grayscale packet из exact production RC sprites;
- **B18 BLOCKED** — произвольный запуск мира больше не выдаётся за formal B18;
- **B20 BLOCKED** — prompt/placeholder/proxy не выдаётся за actual reviewed RC character sample.

Runner никогда не генерирует человеческие ответы.

---

# Меню

## [1] Prepare / verify exact frozen RC worktree

Проверяет exact SHA, чистоту detached worktree и обязательные файлы.

## [2] Run quick M22.6 machine preflight

Запускает Stage-22 acceptance/fingerprint/visual tests. Это только machine preflight и **не** human evidence.

## [3] Run full clean verify

Выполняет локальный:

```bat
mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Это также не заменяет B18–B20.

## [4] Build / refresh blinded human-review packet

Builder берёт только exact-RC production visuals и формирует пакет.

Для B19 используются 18 фактических production sprites:

- 2 core factions;
- 9 role families у каждой:
  - `battleship`
  - `carrier`
  - `corvette`
  - `cruiser`
  - `destroyer`
  - `fleet_support`
  - `freight`
  - `frigate`
  - `tanker`

Каждый source PNG:

1. берётся из exact frozen RC;
2. получает SHA-256 digest в private answer key;
3. переводится в grayscale без перерисовки силуэта;
4. получает детерминированный `sampleId`;
5. помещается в случайно не чередующий faction-order за счёт сортировки opaque sample IDs.

Правильные faction/role значения сохраняются отдельно:

`packet\facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW\b19_answer_key.csv`

**Не открывайте эту папку до завершения B19.**

## [5] Start NEW B19 human review session and begin review

Введите псевдоним, например:

`reviewer-01`

Runner автоматически запишет:

- `scenarioSuiteVersion`;
- `reviewPacketVersion`;
- `reviewerAnonymousId`;
- `reviewStartedAtUtc`.

После этого сразу начнётся интерактивный B19.

Если в `b19_responses.csv` уже есть записанные ответы, runner откажется их перезаписывать. Для продолжения используйте пункт 6.

## [6] Run / resume interactive B19 review

Runner открывает **по одному** grayscale PNG и спрашивает:

### Faction

- `1` — `core.empire` / Империя
- `2` — `core.industrial_union` / Индустриальный Союз
- `X` — исключить sample с обязательной причиной

### Role

- `1` battleship
- `2` carrier
- `3` corvette
- `4` cruiser
- `5` destroyer
- `6` fleet_support
- `7` freight
- `8` frigate
- `9` tanker

Confidence `1–5` необязателен.

После каждого ответа runner автоматически сохраняет строку и `answeredAtUtc`. Правильность ответа **не показывается**.

Можно закрыть runner и позже продолжить пунктом 6: уже записанные samples будут пропущены.

## [7] Finish B19 session and calculate result

Использовать только после всех 18 samples. Для защиты от случайного раннего раскрытия нужно ввести:

`FINALIZE`

Runner сначала проверяет полноту response set, фиксирует `reviewCompletedAtUtc`, и только затем открывает private answer key для scoring.

Пороги B19:

- faction accuracy `>= 90%`;
- role accuracy `>= 80%`.

Создаются:

- `validation_status.txt`;
- `b19_role_confusion.csv`.

Даже при B19 PASS общий M22.6 остаётся OPEN до B18 и B20.

## [8] Open B18/B20 blockers and canonical runbook

Открывает явные blocker-файлы и:

`docs/factions/stage22_m22_6_human_review_runbook.md`

### Почему B18 сейчас BLOCKED

Формальный B18 обязан иметь **до показа ревьюеру** скрытый answer-key row:

```text
taskId
scenarioId
seed
permutation
startCheckpointRef
endCheckpointRef
primaryDependencyId
acceptedEquivalentAnswers
visibleEvidenceRefs
```

В frozen RC есть протокол, но текущий review package не содержит такой заранее зафиксированный blinded task set. Свободный запуск `run-generated-world.bat` с произвольным seed не удовлетворяет B18 и больше не предлагается runner как formal review.

### Почему B20 сейчас BLOCKED

B20 требует actual reviewed character renders, предназначенных для RC visual manifest. Frozen RC фиксирует Character Master Prompt и character-lineup fingerprints, но текущий repository asset surface не предоставляет canonical reviewed-render manifest/sample set для человеческого blind review.

Нельзя заменить это:

- одним prompt;
- assistant self-review;
- machine image classification;
- заново созданными proxy-картинками, не являющимися RC samples.

## [9] Open evidence folder and exact identity

Открывает evidence folder, `review_identity.txt`, `packet_status.txt` и, после финализации, `validation_status.txt`.

---

# Что делать сейчас

Рекомендуемый порядок:

1. обновить `stage22-6-human-review-tools`;
2. запустить BAT;
3. `[1]` — проверить exact RC;
4. `[4]` — построить blinded packet;
5. `[5]` — указать reviewer ID и начать B19;
6. пройти все 18 изображений;
7. `[7]` — ввести `FINALIZE` и получить B19 result;
8. `[9]` — открыть evidence folder;
9. передать `review_identity.txt`, `b19_responses.csv`, `validation_status.txt`, `b19_role_confusion.csv` и `packet_manifest.json` для проверки.

B18/B20 пока не пытайтесь заполнять вручную: без обязательных frozen inputs это создало бы формально недействительные данные.

До настоящего PASS B18, B19 и B20 PR M22.6 не должен переходить в ready/merge.