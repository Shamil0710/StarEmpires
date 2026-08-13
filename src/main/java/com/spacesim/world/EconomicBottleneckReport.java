package com.spacesim.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

record EconomicBottleneckReport(List<EconomicBottleneck> bottlenecks) {
    public EconomicBottleneckReport {
        bottlenecks = List.copyOf(Objects.requireNonNull(bottlenecks, "Bottleneck list не задан"));
    }

    public Optional<EconomicBottleneck> find(StarSystemId systemId, String itemContentId) {
        if (systemId == null || itemContentId == null) {
            return Optional.empty();
        }
        return bottlenecks.stream()
                .filter(value -> value.systemId().equals(systemId)
                        && value.itemContentId().equals(itemContentId))
                .findFirst();
    }
}
