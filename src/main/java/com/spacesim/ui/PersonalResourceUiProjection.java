package com.spacesim.ui;

import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.ProductionUiSnapshot.Row;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Personal resource inspection accepts observation state, never physical reserve truth. */
public final class PersonalResourceUiProjection {
    private PersonalResourceUiProjection() { throw new AssertionError("No instances"); }

    /**
     * Lists only resource observations actually retained by the player, using the same values
     * as the local inspector. No physical world state is consulted to fill missing knowledge.
     * @param knowledge actual personal knowledge owner
     * @param nowSeconds actual simulation time
     * @param worldTick current presentation tick
     * @return immutable intelligence rows for observed resource objects
     */
    public static List<Row> rows(Stage20DiscoveryKnowledgeState knowledge, double nowSeconds, long worldTick) {
        requirePersonalKnowledge(knowledge, nowSeconds);
        if (worldTick < 0) throw new IllegalArgumentException("Negative world tick");
        return knowledge.entries().stream().filter(entry -> entry.object().resourceObject()).map(entry -> {
            var object = entry.object();
            boolean measured = entry.resourceKnowledge().estimate().isPresent();
            boolean sample = entry.evidence().stream()
                    .anyMatch(e -> e.provenanceId().startsWith("personal-extraction-sample:"));
            String name = measured ? "Личная оценка ресурса" : sample
                    ? "Исследованный пробой участок добычи" : "Личное наблюдение ресурса";
            String summary = measured ? "Сохранён диапазон измерения" : sample
                    ? "Получен физический образец" : "Количественная оценка отсутствует";
            return new Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    "personal-resource:" + object.systemId().value() + ':' + object.kind() + ':' + object.objectId()),
                    name, "Личные открытия", summary,
                    List.of(observedSection(Optional.of(entry), nowSeconds), InfoSection.of("Наблюдаемый объект",
                            "Первое наблюдение", String.format(Locale.ROOT, "%.1f с кампании", entry.firstObservedSeconds()),
                            "Положение", entry.knownLocation().isPresent()
                                    ? "Сохранено наблюдавшееся статическое положение" : "Точное положение не установлено",
                            "Границы сведений", "Только собственные наблюдения; запасы других владельцев не раскрываются")),
                    "Собственный реестр наблюдений; диапазоны и время сохранённых измерений",
                    entry.knownLocation().isPresent() ? object.systemId() : null, 0, worldTick);
        }).toList();
    }

    /**
     * Projects bounded personal measurements with their original time and freshness.
     * @param knowledge actual personal knowledge owner
     * @param object inspected resource occurrence
     * @param nowSeconds actual simulation time
     * @return estimates or explicit lack of measurements
     */
    public static InfoSection section(Stage20DiscoveryKnowledgeState knowledge, StaticObjectRef object, double nowSeconds) {
        requirePersonalKnowledge(knowledge, nowSeconds);
        Objects.requireNonNull(object);
        if (!object.resourceObject()) throw new IllegalArgumentException("Invalid resource object");
        return observedSection(knowledge.knowledge(object), nowSeconds);
    }

    private static InfoSection observedSection(Optional<StaticKnowledge> observed, double nowSeconds) {
        var estimate = observed.flatMap(k -> k.resourceKnowledge().estimate());
        if (estimate.isPresent()) {
            var row = observed.orElseThrow();
            var e = estimate.orElseThrow();
            String freshness = nowSeconds < row.lastUpdatedSeconds() ? "Время наблюдения позже текущего времени"
                    : switch (row.freshnessAt(nowSeconds)) {
                        case CURRENT -> "В пределах срока наблюдения";
                        case STALE -> "Срок наблюдения истёк; оценка может устареть";
                        case PERMANENT -> "Сохранённое измерение; изменения после наблюдения не учтены";
                    };
            return InfoSection.of("Личная разведка ресурса", "Содержание по оценке", String.format(Locale.ROOT,
                            "%.1f%% — %.1f%%", e.minimumGradeFraction() * 100, e.maximumGradeFraction() * 100),
                    "Извлекаемая масса по оценке", String.format(Locale.ROOT, "%.1f кг — %.1f кг", e.minimumRecoverableMassKg(), e.maximumRecoverableMassKg()),
                    "Уверенность наблюдения", String.format(Locale.ROOT, "%.1f%%", e.confidence() * 100),
                    "Время сведений", String.format(Locale.ROOT, "%.1f с кампании", row.lastUpdatedSeconds()),
                    "Актуальность", freshness, "Точность", "Диапазон измерения; точный остаток источника неизвестен");
        }
        return InfoSection.of("Личная разведка ресурса", "Свидетельство", observed.isPresent()
                        ? "Сохранено личное наблюдение; количественные измерения отсутствуют" : "Личных измерений нет",
                "Содержание", "Оценка отсутствует", "Извлекаемая масса", "Оценка отсутствует",
                "Условия", "Физический образец сам по себе не определяет содержание и оставшийся запас");
    }

    private static void requirePersonalKnowledge(Stage20DiscoveryKnowledgeState knowledge, double nowSeconds) {
        Objects.requireNonNull(knowledge);
        if (!knowledge.ownerId().equals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                || !Double.isFinite(nowSeconds) || nowSeconds < 0) {
            throw new IllegalArgumentException("Invalid personal resource projection");
        }
    }
}
