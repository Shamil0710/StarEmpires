package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldSimulation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Stage9EAccounting {
    private Stage9EAccounting() {
        throw new AssertionError("Utility class");
    }

    static Map<StarSystemId, Integer> ledgerStarts(WorldSimulation world) {
        Map<StarSystemId, Integer> result = new HashMap<>();
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            result.put(system.id(), session.getLedger().size());
        }
        return Map.copyOf(result);
    }

    static long totalMoney(WorldSimulation world, ContentCatalog content) {
        long total = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    total = Math.addExact(total, wallet.getBalanceMilliCredits());
                }
            }
        }
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            var state = world.findFactionEconomicState(faction.id()).orElse(null);
            if (state != null) {
                total = Math.addExact(total, state.treasuryMilliCredits());
            }
        }
        return total;
    }

    static long[] physicalResourceTotals(WorldSimulation world) {
        long[] totals = new long[Constants.MAX_ITEMS];
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (inventory != null) {
                    for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                        totals[itemId] = Math.addExact(totals[itemId], inventory.stock[itemId]);
                    }
                }
                AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
                if (asteroid != null) {
                    totals[asteroid.resourceItem] = Math.addExact(
                            totals[asteroid.resourceItem], asteroid.remainingResource);
                }
            }
        }
        return totals;
    }

    static LedgerDelta summarize(
            WorldSimulation world,
            ContentCatalog content,
            Map<StarSystemId, Integer> starts) {
        LedgerDelta delta = new LedgerDelta();
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            List<EconomicTransaction> entries = session.getLedger().getEntries();
            int start = Objects.requireNonNull(starts.get(system.id()), "Ledger start отсутствует");
            for (int index = start; index < entries.size(); index++) {
                EconomicTransaction entry = entries.get(index);
                switch (entry.type()) {
                    case MONEY_SOURCE -> delta.moneySourceMilliCredits = Math.addExact(
                            delta.moneySourceMilliCredits, entry.moneyMilliCredits());
                    case MONEY_SINK -> delta.moneySinkMilliCredits = Math.addExact(
                            delta.moneySinkMilliCredits, entry.moneyMilliCredits());
                    case RESOURCE_SOURCE -> add(delta.resourceSourceByItem, entry.itemId(), entry.itemAmount());
                    case RESOURCE_SINK -> add(delta.resourceSinkByItem, entry.itemId(), entry.itemAmount());
                    case RESOURCE_TRANSFORM -> accountTransform(delta, entry, content);
                    case TRADE, MONEY_TRANSFER, RESOURCE_TRANSFER -> {
                    }
                }
            }
        }
        return delta;
    }

    static boolean resourcesConserved(
            long[] initial,
            long[] actualFinal,
            LedgerDelta ledger,
            ContentCatalog content) {
        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            int itemId = item.runtimeId();
            long expected = initial[itemId];
            expected = Math.addExact(expected, ledger.resourceSourceByItem[itemId]);
            expected = Math.subtractExact(expected, ledger.resourceSinkByItem[itemId]);
            expected = Math.addExact(expected, ledger.resourceTransformByItem[itemId]);
            if (actualFinal[itemId] != expected) {
                return false;
            }
        }
        return true;
    }

    private static void accountTransform(
            LedgerDelta delta, EconomicTransaction entry, ContentCatalog content) {
        String reason = entry.reason();
        int separator = reason.lastIndexOf(" x");
        if (separator <= 0 || separator + 2 >= reason.length()) {
            throw new IllegalStateException("Unknown transform reason: " + reason);
        }
        String displayName = reason.substring(0, separator);
        long cycles = Long.parseLong(reason.substring(separator + 2));
        ContentCatalog.RecipeDefinition recipe = null;
        for (ContentCatalog.RecipeDefinition candidate : content.getRecipes()) {
            if (candidate.displayName().equals(displayName)) {
                if (recipe != null) {
                    throw new IllegalStateException("Duplicate recipe display name: " + displayName);
                }
                recipe = candidate;
            }
        }
        if (recipe == null || cycles <= 0L) {
            throw new IllegalStateException("Unknown transform recipe/cycles: " + reason);
        }
        for (var input : recipe.inputs().entrySet()) {
            int itemId = content.findItem(input.getKey()).runtimeId();
            delta.resourceTransformByItem[itemId] = Math.subtractExact(
                    delta.resourceTransformByItem[itemId],
                    Math.multiplyExact((long) input.getValue(), cycles));
        }
        for (var output : recipe.outputs().entrySet()) {
            int itemId = content.findItem(output.getKey()).runtimeId();
            delta.resourceTransformByItem[itemId] = Math.addExact(
                    delta.resourceTransformByItem[itemId],
                    Math.multiplyExact((long) output.getValue(), cycles));
        }
    }

    private static void add(long[] values, int itemId, long amount) {
        if (itemId < 0 || itemId >= values.length || amount < 0L) {
            throw new IllegalStateException("Invalid resource accounting entry");
        }
        values[itemId] = Math.addExact(values[itemId], amount);
    }

    static final class LedgerDelta {
        long moneySourceMilliCredits;
        long moneySinkMilliCredits;
        final long[] resourceSourceByItem = new long[Constants.MAX_ITEMS];
        final long[] resourceSinkByItem = new long[Constants.MAX_ITEMS];
        final long[] resourceTransformByItem = new long[Constants.MAX_ITEMS];
    }
}
