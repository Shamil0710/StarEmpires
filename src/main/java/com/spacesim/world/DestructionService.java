package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.events.NewsArticle;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;

import java.util.Map;
import java.util.Objects;

/**
 * Authoritative Stage-9C boundary for economic destruction of local persistent entities.
 *
 * <p>The service resolves all authoritative money and resource value first. Only after the target
 * inventory/wallet become empty does it delegate structural removal to Stage 9A. This keeps
 * lifecycle cleanup and economic accounting separate while making every destructive operation
 * explicit and testable.</p>
 */
final class DestructionService {
    private final ContentCatalog catalog;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final Map<String, FactionEconomicAccount> factionAccountsById;
    private final ConstructionProjectService constructionProjects;

    DestructionService(
            ContentCatalog catalog,
            Map<StarSystemId, SimulationSession> sessionsById,
            Map<String, FactionEconomicAccount> factionAccountsById,
            ConstructionProjectService constructionProjects) {
        this.catalog = Objects.requireNonNull(catalog, "ContentCatalog destruction не задан");
        this.sessionsById = Objects.requireNonNull(sessionsById, "Destruction sessions не заданы");
        this.factionAccountsById = Objects.requireNonNull(
                factionAccountsById, "Destruction faction accounts не заданы");
        this.constructionProjects = Objects.requireNonNull(
                constructionProjects, "ConstructionProjectService destruction не задан");
    }

    DestructionResult destroy(
            StarSystemId systemId,
            EntityId targetId,
            DestructionPolicy policy) {
        SimulationSession session = requireSession(systemId);
        EntityId checkedTargetId = Objects.requireNonNull(targetId, "Destroyed EntityId не задан");
        DestructionPolicy checkedPolicy = Objects.requireNonNull(policy, "DestructionPolicy не задан");
        Entity target = session.getEntityRegistry().find(checkedTargetId);
        if (target == null) {
            throw new IllegalArgumentException("Destroyed Entity отсутствует: " + checkedTargetId);
        }

        InventoryComponent inventory = target.getComponent(InventoryComponent.class);
        WalletComponent wallet = target.getComponent(WalletComponent.class);
        long resourceUnits = totalStock(inventory);
        long money = wallet == null ? 0L : wallet.getBalanceMilliCredits();
        String targetLabel = label(target, checkedTargetId);
        boolean removedMarket = target.getComponent(MarketComponent.class) != null;
        boolean removedProduction = target.getComponent(ProductionComponent.class) != null;

        Entity recipient = null;
        InventoryComponent recipientInventory = null;
        String recipientLabel = null;
        if (checkedPolicy.resourceFate() == ResourceDestructionFate.TRANSFER_TO_ENTITY && resourceUnits > 0L) {
            EntityId recipientId = checkedPolicy.resourceRecipientEntityId();
            if (checkedTargetId.equals(recipientId)) {
                throw new IllegalArgumentException("Destroyed Entity не может быть своим resource recipient");
            }
            recipient = session.getEntityRegistry().find(recipientId);
            if (recipient == null) {
                throw new IllegalArgumentException("Resource recipient отсутствует в target system: " + recipientId);
            }
            recipientInventory = recipient.getComponent(InventoryComponent.class);
            if (recipientInventory == null) {
                throw new IllegalArgumentException("Resource recipient не имеет InventoryComponent");
            }
            requireCapacity(recipientInventory, resourceUnits, "Resource recipient");
            recipientLabel = label(recipient, recipientId);
        }

        TransformComponent salvageTransform = null;
        if (checkedPolicy.resourceFate() == ResourceDestructionFate.SALVAGE && resourceUnits > 0L) {
            if (resourceUnits > Integer.MAX_VALUE) {
                throw new IllegalStateException("Salvage inventory capacity превышает Integer.MAX_VALUE");
            }
            TransformComponent transform = target.getComponent(TransformComponent.class);
            if (transform == null) {
                throw new IllegalArgumentException("SALVAGE требует TransformComponent у destroyed Entity");
            }
            salvageTransform = new TransformComponent();
            salvageTransform.position.set(transform.position);
        }

        FactionEconomicAccount moneyRecipient = null;
        String moneyRecipientLabel = null;
        if (checkedPolicy.moneyFate() == MoneyDestructionFate.TRANSFER_TO_FACTION_TREASURY && money > 0L) {
            FactionComponent faction = target.getComponent(FactionComponent.class);
            if (faction == null) {
                throw new IllegalArgumentException(
                        "TRANSFER_TO_FACTION_TREASURY требует FactionComponent у destroyed Entity");
            }
            ContentCatalog.FactionDefinition definition = catalog.findFaction(faction.factionId);
            if (definition == null) {
                throw new IllegalStateException("Destroyed Entity ссылается на неизвестную runtime faction");
            }
            moneyRecipient = factionAccountsById.get(definition.id());
            if (moneyRecipient == null) {
                throw new IllegalStateException("Destroyed faction не имеет persistent treasury: " + definition.id());
            }
            if (!moneyRecipient.treasury().canCredit(money)) {
                throw new IllegalStateException("Faction treasury не может принять destroyed wallet без overflow");
            }
            moneyRecipientLabel = "faction:" + definition.id() + ":treasury";
        }

        ConstructionProjectState siteHistory = constructionProjects.findBySite(systemId, checkedTargetId);

        EntityId salvageId = null;
        Entity salvage = null;
        InventoryComponent salvageInventory = null;
        String salvageLabel = null;
        if (checkedPolicy.resourceFate() == ResourceDestructionFate.SALVAGE && resourceUnits > 0L) {
            salvageInventory = new InventoryComponent();
            salvageInventory.capacity = (int) resourceUnits;
            salvage = new Entity()
                    .add(new IdentityComponent("Salvage " + checkedTargetId.value(), IdentityComponent.Kind.SALVAGE))
                    .add(salvageTransform)
                    .add(salvageInventory);
            salvageId = session.createEntity(salvage);
            salvageLabel = label(salvage, salvageId);
        }

        long destroyedResources = 0L;
        long transferredResources = 0L;
        if (inventory != null) {
            for (int itemId = 0; itemId < inventory.stock.length; itemId++) {
                int amount = inventory.stock[itemId];
                if (amount <= 0) {
                    continue;
                }
                switch (checkedPolicy.resourceFate()) {
                    case DESTROY -> {
                        inventory.stock[itemId] = 0;
                        session.getLedger().recordResourceSink(
                                targetLabel, itemId, amount, "entity-destruction");
                        destroyedResources = Math.addExact(destroyedResources, amount);
                    }
                    case SALVAGE -> {
                        inventory.stock[itemId] = 0;
                        salvageInventory.stock[itemId] = Math.addExact(salvageInventory.stock[itemId], amount);
                        session.getLedger().recordResourceTransfer(
                                targetLabel, salvageLabel, itemId, amount, "entity-destruction-salvage");
                        transferredResources = Math.addExact(transferredResources, amount);
                    }
                    case TRANSFER_TO_ENTITY -> {
                        inventory.stock[itemId] = 0;
                        recipientInventory.stock[itemId] = Math.addExact(recipientInventory.stock[itemId], amount);
                        MarketComponent recipientMarket = recipient.getComponent(MarketComponent.class);
                        if (recipientMarket != null) {
                            recipientMarket.isDirty = true;
                        }
                        session.getLedger().recordResourceTransfer(
                                targetLabel, recipientLabel, itemId, amount, "entity-destruction-transfer");
                        transferredResources = Math.addExact(transferredResources, amount);
                    }
                }
            }
            MarketComponent targetMarket = target.getComponent(MarketComponent.class);
            if (targetMarket != null) {
                targetMarket.isDirty = true;
            }
        }

        long sunkMoney = 0L;
        long transferredMoney = 0L;
        if (wallet != null && money > 0L) {
            switch (checkedPolicy.moneyFate()) {
                case SINK -> {
                    if (!wallet.debitToSink(money)) {
                        throw new IllegalStateException("Destroyed wallet sink неожиданно не выполнен");
                    }
                    session.getLedger().recordMoneySink(targetLabel, money, "entity-destruction");
                    sunkMoney = money;
                }
                case TRANSFER_TO_FACTION_TREASURY -> {
                    if (!wallet.transferTo(moneyRecipient.treasury(), money)) {
                        throw new IllegalStateException("Destroyed wallet transfer неожиданно не выполнен");
                    }
                    session.getLedger().recordMoneyTransfer(
                            targetLabel, moneyRecipientLabel, money, "entity-destruction-owner-transfer");
                    transferredMoney = money;
                }
            }
        }

        ConstructionProjectId failedProject = null;
        if (siteHistory != null) {
            failedProject = constructionProjects.failDestroyedSite(siteHistory, session.getClock().getTick());
        }
        if (!session.removeEntity(checkedTargetId)) {
            throw new IllegalStateException("Stage-9A structural removal не удалил destroyed Entity");
        }

        long timestampMillis = Math.max(
                0L,
                Math.round(session.getEventManager().getSimulationTimeSeconds() * 1_000d));
        String body = "Уничтожен объект «" + targetLabel + "»."
                + " Ресурсы уничтожено: " + destroyedResources
                + ", передано/сохранено: " + transferredResources
                + ", деньги уничтожено: " + sunkMoney
                + ", передано: " + transferredMoney + ".";
        session.getEventManager().publishNews(new NewsArticle(
                "Уничтожен космический объект",
                body,
                null,
                timestampMillis));

        return new DestructionResult(
                checkedTargetId,
                salvageId,
                destroyedResources,
                transferredResources,
                sunkMoney,
                transferredMoney,
                removedMarket,
                removedProduction,
                failedProject);
    }

    private SimulationSession requireSession(StarSystemId systemId) {
        SimulationSession session = sessionsById.get(Objects.requireNonNull(systemId, "Destruction system не задан"));
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная destruction StarSystem: " + systemId);
        }
        return session;
    }

    private static long totalStock(InventoryComponent inventory) {
        if (inventory == null) {
            return 0L;
        }
        long total = 0L;
        for (int amount : inventory.stock) {
            if (amount < 0) {
                throw new IllegalStateException("Inventory содержит отрицательный stock");
            }
            total = Math.addExact(total, amount);
        }
        return total;
    }

    private static void requireCapacity(InventoryComponent inventory, long incoming, String label) {
        if (incoming > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " incoming stock превышает Integer.MAX_VALUE");
        }
        long free = (long) inventory.capacity - inventory.getTotalStock();
        if (free < incoming) {
            throw new IllegalArgumentException(label + " не имеет достаточной inventory capacity");
        }
    }

    private static String label(Entity entity, EntityId id) {
        IdentityComponent identity = entity.getComponent(IdentityComponent.class);
        return identity == null ? "entity:" + id.value() : identity.name + "[" + id.value() + "]";
    }
}
