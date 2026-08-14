# Star Empires — политика CI и публикации артефактов

> Статус: **ACTIVE ENGINEERING POLICY**
>
> Введено во время реализации Stage 16 после повторяющегося сбоя GitHub `upload-artifact`, при котором обязательный Maven verify успешно завершался, а workflow становился красным только на публикации архивов.

## 1. Обязательный gate

Перед merge функционального core-кода обязательным остаётся шаг:

```text
./mvnw --batch-mode --no-transfer-progress clean verify
```

Он должен успешно завершить:

- компиляцию production/test кода;
- все JUnit unit/integration/acceptance tests;
- JaCoCo coverage gates;
- strict Javadoc;
- сборку shaded desktop JAR;
- остальные Maven verification rules проекта.

Ошибка любого из этих пунктов блокирует merge.

## 2. Публикация workflow artifacts

Шаги `actions/upload-artifact` публикуют уже созданные результаты:

- desktop JAR;
- JaCoCo HTML report;
- generated Javadocs;
- Surefire test reports.

Эта публикация полезна для диагностики и скачивания результатов CI, но **не является отдельной проверкой корректности кода**. Сбой внешнего GitHub artifact storage после успешного `mvn verify` не должен превращаться в ложную функциональную регрессию.

Поэтому upload-steps используют:

```yaml
continue-on-error: true
```

При этом сам build/test/package step остаётся обязательным и не имеет `continue-on-error`.

## 3. Правило интерпретации

Допустимо сливать PR при ошибке только artifact-publication, если одновременно доказано, что обязательный `clean verify` завершился успешно.

Недопустимо:

- игнорировать падение tests;
- игнорировать JaCoCo failure;
- игнорировать strict Javadoc failure;
- игнорировать compilation/package failure;
- заменять локально созданный package отсутствующим artifact upload;
- объявлять Stage/PR зелёным без проверки обязательного Maven gate.

## 4. Возврат к строгой публикации

Если GitHub artifact storage снова станет стабильно доступен и публикацию артефактов потребуется сделать обязательной по организационным причинам, `continue-on-error` можно убрать отдельным инфраструктурным PR. Это изменение не влияет на игровые инварианты или acceptance criteria Stage 16.
