package com.spacesim.world;

record EconomicBottleneck(
        StarSystemId systemId,
        String itemContentId,
        EconomicBottleneckType type,
        long demandDeficitUnits,
        long availableSurplusUnits,
        long unmetDemandUnits,
        int stockoutMarketCount,
        int producerCount,
        int readyProducerCount,
        int inputBlockedProducerCount,
        int storageBlockedProducerCount,
        int structuralPricePressureBasisPoints,
        long severityScore) {
}
