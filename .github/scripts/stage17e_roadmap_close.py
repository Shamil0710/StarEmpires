from pathlib import Path

path = Path('docs/development_roadmap.md')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    text = text.replace(old, new, 1)


replace_once(
    '''## 17E — diplomacy / market access / tariffs

**ACTIVE.** 17E.1 persistent institutional diplomacy и 17E.3 market-access precedence реализуются первым production slice: explicit trust/credibility, grievances, treaty directory, embargo state и единый `embargo → treaty right → relation threshold` resolver поверх authored + world-defined faction identities.
''',
    '''## 17E — diplomacy / market access / tariffs

**COMPLETE — PR #101/#102/#103/#104/#105/#106/#107, финальный aggregate gate PR #108.**

Stage 17E теперь использует единый persistent `FactionDiplomacyState`/`FactionDiplomacyRuntime` для authored и world-defined factions. Дипломатия включает directed trust/credibility, grievances, treaty lifecycle, embargoes, explainable proposal evaluation, explicit market-access precedence, реальные transaction/customs tariffs и read-only structural economic-dependence diagnostics. Политические решения изменяют ordinary market/trade economics, но не создают деньги, товары или abstract economic damage.
''',
    '17E header')

replace_once(
    '''### 17E.1 — diplomatic state hardening

К существующим directed `relations` добавляются bounded persistent structures для:
''',
    '''### 17E.1 — diplomatic state hardening

**COMPLETE — PR #101.** Persistent diplomacy хранится отдельно от territorial strategy: directed standings, grievances, globally unique treaty directory и unilateral embargo state входят в versioned persistence; legacy saves мигрируют без выдуманных political instruments.

К существующим directed `relations` добавляются bounded persistent structures для:
''',
    '17E.1 status')

replace_once(
    '''### 17E.2 — proposal / response engine

**ACTIVE.** Common player/AI treaty lifecycle реализуется через один authoritative command boundary поверх `FactionDiplomacyRuntime`; lifecycle не создаёт параллельный diplomacy store и после legal transition сразу обновляет ordinary market-access projection.
''',
    '''### 17E.2 — proposal / response engine

**COMPLETE — PR #102/#103.** Common player/AI treaty lifecycle реализован через один authoritative command boundary поверх `FactionDiplomacyRuntime`; offer/counteroffer/accept/reject/notice termination/breach/renewal/expiry используют один persistent store. Explainable evaluator принимает explicit observed inputs с confidence/freshness и возвращает deterministic `ACCEPT / COUNTEROFFER / REJECT` с разложением utility для AI/UI.
''',
    '17E.2 status')

replace_once(
    '''### 17E.3 — market-access precedence

Effective legal access определяется в явном порядке:
''',
    '''### 17E.3 — market-access precedence

**COMPLETE — PR #101.** Persistent diplomacy materialize-ится в transient ECS market access через единый refresh boundary; treaty/embargo transitions и expiry немедленно меняют ordinary trade authorization без save/load.

Effective legal access определяется в явном порядке:
''',
    '17E.3 status')

replace_once(
    '''### 17E.4 — tariffs и fiscal separation

Существующий `foreignTerritoryTariffBasisPoints` фиксируется как **territorial fiscal levy** с реальным station→treasury transfer.
''',
    '''### 17E.4 — tariffs и fiscal separation

**COMPLETE — PR #105.** Transaction/customs tariff отделён от Stage-8 territorial fiscal levy. Market-owner customs rate persist-ится отдельно; domestic trade и active treaty exemption дают zero duty. Planner видит тот же effective tariff до выбора маршрута, который `TradeController` затем физически settlement-ит как conserved wallet transfer в faction treasury с отдельной ledger entry.

Существующий `foreignTerritoryTariffBasisPoints` фиксируется как **territorial fiscal levy** с реальным station→treasury transfer.
''',
    '17E.4 status')

replace_once(
    '''### 17E.5 — embargoes / sanctions

**ACTIVE.** Unilateral market embargo использует общий player/AI command boundary и persistent `FactionDiplomacyState`; impose/revoke немедленно rematerialize-ят ordinary market access, а затронутая faction получает explicit `EMBARGO` grievance. Сам embargo не создаёт экономический урон вне ordinary trade/logistics consequences.
''',
    '''### 17E.5 — embargoes / sanctions

**COMPLETE — PR #104.** Unilateral market embargo использует общий player/AI command boundary и persistent `FactionDiplomacyState`; impose/revoke/expiry немедленно rematerialize-ят ordinary market access, а затронутая faction получает explicit `EMBARGO` grievance. Сам embargo не создаёт экономический урон вне ordinary trade/logistics consequences.
''',
    '17E.5 status')

replace_once(
    '''### 17E.6 — treaties / credibility

Выполнение договора постепенно укрепляет trust; нарушение создаёт explicit breach/grievance и снижает credibility. Эффект не обязан быть симметричным.
''',
    '''### 17E.6 — treaties / credibility

**COMPLETE — PR #107.** Explicit breach создаёт grievance и directed trust/credibility penalty. Положительная credibility не начисляется просто за прошедшее время: естественно завершённый ACTIVE treaty получает bounded bilateral trust/credibility gain только для clauses, исполнение которых реально наблюдаемо текущими systems (`MARKET_ACCESS`, `CUSTOMS_TARIFF_EXEMPTION`). Notice termination, embargo во время договора и пока неизмеримые будущие obligations не получают бесплатной награды.

Выполнение проверяемого договора укрепляет trust; нарушение создаёт explicit breach/grievance и снижает credibility. Эффект не обязан быть симметричным.
''',
    '17E.6 status')

replace_once(
    '''### 17E.7 — economic-dependence diagnostics

Для каждой значимой пары factions доступны read-only diagnostics:
''',
    '''### 17E.7 — economic-dependence diagnostics

**COMPLETE — PR #106.** Diagnostics намеренно измеряют current structural exposure, а не выдумывают historical trade shares, которых persistence пока не хранит. Authoritative snapshot использует physical inventories, market targets/quotes, active production inputs, legal access, strategic stock floors и topology; output уже содержит observation tick/confidence для будущего Stage-19 intelligence boundary.

Для каждой значимой пары factions доступны read-only diagnostics:
''',
    '17E.7 status')

replace_once(
    '''### 17E acceptance

```text
A depends on B for critical input
→ A proposes trade-access treaty
→ B evaluates benefit, dependency risk, trust and doctrine
→ treaty accepted
→ access projected to real markets
→ physical trade grows
→ B imposes transaction tariff / A searches alternatives
→ breach or embargo removes legal access
→ routes physically change and shortage emerges
→ save/load preserves treaty, trust, access and economic consequences
```
''',
    '''### 17E acceptance

**COMPLETE — final aggregate gate PR #108.**

```text
physical stock gap + partner/alternative supply
→ structural dependence diagnostics
→ A proposes market-access/customs treaty
→ B evaluates observed economic value through common explainable utility model
→ treaty accepted through common lifecycle boundary
→ explicit treaty right becomes effective market-access reason
→ active customs exemption changes the next real transaction from tariffed to zero-duty
→ breach restores ordinary customs law and creates grievance
→ embargo overrides fallback access
→ partner physical stock remains present but legal accessible supply becomes zero
→ alternatives / replacement premium / uncovered requirements remain measurable
→ binary save/load preserves treaty, embargo, customs, wallets, inventories, ledger and diagnostics
```
''',
    '17E aggregate acceptance')

replace_once(
    '''## 17F — faction policies / strategic economy

Цель — дать player faction и AI factions общий набор государственных economic-policy решений.''',
    '''## 17F — faction policies / strategic economy

**NEXT — следующий Stage-17 implementation block после закрытия 17E.**

Цель — дать player faction и AI factions общий набор государственных economic-policy решений.''',
    '17F next')

path.write_text(text, encoding='utf-8')
