package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ArchetypeEntityFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime owner of persistent construction projects and their physical ECS construction sites.
 *
 * <p>Project demand is not a virtual strategic number. Every non-terminal project owns a normal
 * local market entity with an InventoryComponent, WalletComponent and MarketComponent. Existing
 * TradeAI therefore discovers the same targetStock shortage and can deliver materials through the
 * ordinary bilateral trade path. Manual material delivery uses the same physical inventory.</p>
 *
 * <p>New project duration is derived by {@link ConstructionDurationPolicy}: authored buildSeconds
 * is only the base setup/complexity allowance and the real material bill contributes additional
 * weighted assembly work. Once created, the resulting {@code buildDurationTicks} is persisted as
 * part of the project contract, so save/load and later balance changes cannot rewrite an ongoing
 * construction clock.</p>
 */
final class ConstructionProjectService {
    private final ContentCatalog catalog;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final Map<String, FactionEconomicAccount> factionAccountsById;
    private final ConstructionProjectIdAllocator idAllocator;
    private final Map<ConstructionProjectId, ConstructionProjectState> projects = new LinkedHashMap<>();

    ConstructionProjectService(
            ContentCatalog catalog,
            Map<StarSystemId, SimulationSession> sessionsById,
            Map<String, FactionEconomicAccount> factionAccountsById,
            long nextProjectIdValue,
            List<ConstructionProjectState> restoredProjects) {
        this.catalog = Objects.requireNonNull(catalog, "ContentCatalog construction не задан");
        this.sessionsById = Objects.requireNonNull(sessionsById, "Construction sessions не заданы");
        this.factionAccountsById = Objects.requireNonNull(
                factionAccountsById, "Construction faction accounts не заданы");
        idAllocator = new ConstructionProjectIdAllocator(nextProjectIdValue);
        for (ConstructionProjectState state : Objects.requireNonNull(
                restoredProjects, "Restored construction projects не заданы")) {
            if (projects.putIfAbsent(state.id(), validateRestored(state)) != null) {
                throw new IllegalArgumentException("Duplicate restored construction project: " + state.id());
            }
        }
    }

    ConstructionProjectId create(
            String ownerFactionContentId,
            String stationArchetypeContentId,
            StarSystemId systemId,
            float x,
            float y) {
        String ownerId = normalizedId(ownerFactionContentId, "Construction owner faction");
        FactionEconomicAccount owner = requireFactionAccount(ownerId);
        ContentCatalog.StationArchetypeDefinition target = requireConstructible(stationArchetypeContentId);
        SimulationSession session = requireSession(systemId);
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Construction coordinates должны быть конечными");
        }

        ConstructionProjectId projectId = idAllocator.allocate();
        Entity site = ConstructionSiteFactory.create(catalog, projectId, target, owner.factionContentId(), x, y);
        EntityId siteId = session.createEntity(site);
        long tick = session.getClock().getTick();
        long minimumFunding = Money.fromCredits(target.construction().fundingCredits());
        ConstructionDurationPolicy.Estimate duration = ConstructionDurationPolicy.estimate(catalog, target);
        long buildDurationTicks = buildDurationTicks(duration.totalSeconds(), session);
        List<ConstructionMaterialState> materials = new ArrayList<>();
        for (Map.Entry<String, Integer> requirement : target.construction().materials().entrySet()) {
            materials.add(new ConstructionMaterialState(requirement.getKey(), requirement.getValue(), 0));
        }
        materials.sort(ConstructionMaterialState::compareTo);

        ConstructionProjectState state = new ConstructionProjectState(
                projectId,
                ownerId,
                target.id(),
                systemId,
                x,
                y,
                siteId,
                materials,
                minimumFunding,
                0L,
                buildDurationTicks,
                ConstructionProjectStatus.PLANNED,
                tick,
                tick,
                -1L,
                -1L,
                null);
        projects.put(projectId, state);
        return projectId;
    }

    long fund(ConstructionProjectId projectId, long amountMilliCredits) {
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Construction funding amount должен быть положительным");
        }
        ConstructionProjectState state = requireNonTerminal(projectId);
        if (state.status() == ConstructionProjectStatus.BUILDING) {
            throw new IllegalStateException("Нельзя финансировать уже строящийся project");
        }
        Entity site = requireSite(state);
        WalletComponent projectWallet = requireComponent(site, WalletComponent.class, "construction site wallet");
        FactionEconomicAccount owner = requireFactionAccount(state.ownerFactionContentId());
        if (!owner.treasury().transferTo(projectWallet, amountMilliCredits)) {
            return 0L;
        }
        requireSession(state.systemId()).getLedger().recordMoneyTransfer(
                treasuryName(state.ownerFactionContentId()),
                siteName(state),
                amountMilliCredits,
                "construction-project-funding");
        ConstructionProjectState refreshed = refresh(state);
        long tick = requireSession(state.systemId()).getClock().getTick();
        if (refreshed.status() == ConstructionProjectStatus.PLANNED
                && refreshed.projectWalletMilliCredits() >= refreshed.minimumFundingMilliCredits()) {
            refreshed = copy(
                    refreshed,
                    ConstructionProjectStatus.FUNDED,
                    tick,
                    -1L,
                    -1L,
                    refreshed.constructionSiteEntityId(),
                    null,
                    refreshed.materials(),
                    refreshed.projectWalletMilliCredits());
        }
        projects.put(projectId, refreshed);
        return amountMilliCredits;
    }

    int deliver(
            ConstructionProjectId projectId,
            EntityId sourceEntityId,
            String itemContentId,
            int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Construction delivery amount должен быть положительным");
        }
        ConstructionProjectState state = requireNonTerminal(projectId);
        if (state.status() == ConstructionProjectStatus.BUILDING) {
            throw new IllegalStateException("BUILDING project больше не принимает materials");
        }
        ContentCatalog.ItemDefinition item = catalog.findItem(normalizedId(itemContentId, "Construction item"));
        if (item == null) {
            throw new IllegalArgumentException("Неизвестный construction item: " + itemContentId);
        }
        ConstructionMaterialState requirement = findMaterial(state, item.id());
        if (requirement == null) {
            throw new IllegalArgumentException("Project не требует item: " + item.id());
        }

        SimulationSession session = requireSession(state.systemId());
        Entity source = session.getEntityRegistry().find(
                Objects.requireNonNull(sourceEntityId, "Source EntityId не задан"));
        if (source == null) {
            throw new IllegalArgumentException("Source Entity отсутствует в target system: " + sourceEntityId);
        }
        Entity site = requireSite(state);
        if (source == site) {
            throw new IllegalArgumentException("Construction site не может доставлять материал самому себе");
        }
        InventoryComponent sourceInventory = requireComponent(source, InventoryComponent.class, "source inventory");
        InventoryComponent siteInventory = requireComponent(site, InventoryComponent.class, "construction inventory");
        int accepted = Math.min(amount, requirement.remainingAmount());
        if (accepted <= 0) {
            return 0;
        }
        if (sourceInventory.stock[item.runtimeId()] < accepted) {
            throw new IllegalArgumentException("Source inventory не содержит requested construction amount");
        }
        if (siteInventory.getTotalStock() > siteInventory.capacity - accepted) {
            throw new IllegalStateException("Construction site inventory capacity exceeded");
        }
        sourceInventory.stock[item.runtimeId()] -= accepted;
        siteInventory.stock[item.runtimeId()] += accepted;
        MarketComponent market = site.getComponent(MarketComponent.class);
        if (market != null) {
            market.isDirty = true;
        }
        projects.put(projectId, refresh(state));
        return accepted;
    }

    boolean cancel(ConstructionProjectId projectId) {
        ConstructionProjectState state = requireNonTerminal(projectId);
        ConstructionProjectState refreshed = refresh(state);
        if (refreshed.totalDeliveredUnits() != 0L) {
            throw new IllegalStateException(
                    "Project с уже доставленными materials нельзя отменить без explicit material-fate policy");
        }
        if (refreshed.status() == ConstructionProjectStatus.BUILDING) {
            throw new IllegalStateException("BUILDING project нельзя отменить");
        }
        SimulationSession session = requireSession(refreshed.systemId());
        Entity site = requireSite(refreshed);
        WalletComponent projectWallet = requireComponent(site, WalletComponent.class, "construction site wallet");
        long refund = projectWallet.getBalanceMilliCredits();
        if (refund > 0L) {
            FactionEconomicAccount owner = requireFactionAccount(refreshed.ownerFactionContentId());
            if (!projectWallet.transferTo(owner.treasury(), refund)) {
                throw new IllegalStateException("Не удалось вернуть construction project wallet в treasury");
            }
            session.getLedger().recordMoneyTransfer(
                    siteName(refreshed), treasuryName(refreshed.ownerFactionContentId()), refund,
                    "construction-project-cancel-refund");
        }
        if (!session.removeEntity(refreshed.constructionSiteEntityId())) {
            throw new IllegalStateException("Construction site исчез до cancellation: " + projectId);
        }
        long tick = session.getClock().getTick();
        projects.put(projectId, copy(
                refreshed,
                ConstructionProjectStatus.CANCELLED,
                tick,
                -1L,
                tick,
                null,
                null,
                refreshed.materials(),
                0L));
        return true;
    }

    void advance() {
        List<ConstructionProjectId> ids = new ArrayList<>(projects.keySet());
        ids.sort(ConstructionProjectId::compareTo);
        for (ConstructionProjectId id : ids) {
            ConstructionProjectState state = projects.get(id);
            if (isTerminal(state.status())) {
                continue;
            }
            state = refresh(state);
            SimulationSession session = requireSession(state.systemId());
            long tick = session.getClock().getTick();
            if (state.status() == ConstructionProjectStatus.PLANNED
                    && state.projectWalletMilliCredits() >= state.minimumFundingMilliCredits()) {
                state = copy(state, ConstructionProjectStatus.FUNDED, tick, -1L, -1L,
                        state.constructionSiteEntityId(), null, state.materials(), state.projectWalletMilliCredits());
            } else if (state.status() == ConstructionProjectStatus.FUNDED
                    && tick > state.stateChangedTick()) {
                state = copy(state, ConstructionProjectStatus.AWAITING_MATERIALS, tick, -1L, -1L,
                        state.constructionSiteEntityId(), null, state.materials(), state.projectWalletMilliCredits());
            } else if (state.status() == ConstructionProjectStatus.AWAITING_MATERIALS
                    && state.materialsFulfilled()) {
                state = copy(state, ConstructionProjectStatus.BUILDING, tick, tick, -1L,
                        state.constructionSiteEntityId(), null, state.materials(), state.projectWalletMilliCredits());
            } else if (state.status() == ConstructionProjectStatus.BUILDING
                    && tick - state.buildStartedTick() >= state.buildDurationTicks()) {
                state = complete(state, tick);
            }
            projects.put(id, state);
        }
    }

    Optional<ConstructionProjectState> find(ConstructionProjectId id) {
        ConstructionProjectState state = id == null ? null : projects.get(id);
        return state == null ? Optional.empty() : Optional.of(snapshotState(state));
    }

    List<ConstructionProjectState> snapshots() {
        List<ConstructionProjectState> result = new ArrayList<>(projects.size());
        for (ConstructionProjectState state : projects.values()) {
            result.add(snapshotState(state));
        }
        result.sort(ConstructionProjectState::compareTo);
        return List.copyOf(result);
    }

    long nextIdValue() {
        return idAllocator.peekNextValue();
    }

    ConstructionProjectState findBySite(StarSystemId systemId, EntityId entityId) {
        if (systemId == null || entityId == null) {
            return null;
        }
        for (ConstructionProjectState state : projects.values()) {
            if (!isTerminal(state.status())
                    && state.systemId().equals(systemId)
                    && entityId.equals(state.constructionSiteEntityId())) {
                return snapshotState(state);
            }
        }
        return null;
    }

    ConstructionProjectId failDestroyedSite(ConstructionProjectState beforeDestruction, long tick) {
        if (beforeDestruction == null) {
            return null;
        }
        ConstructionProjectState current = projects.get(beforeDestruction.id());
        if (current == null || isTerminal(current.status())
                || !current.systemId().equals(beforeDestruction.systemId())
                || !Objects.equals(current.constructionSiteEntityId(), beforeDestruction.constructionSiteEntityId())) {
            throw new IllegalStateException("Construction project изменился во время destruction: "
                    + beforeDestruction.id());
        }
        ConstructionProjectState failed = copy(
                beforeDestruction,
                ConstructionProjectStatus.FAILED,
                tick,
                beforeDestruction.buildStartedTick(),
                tick,
                null,
                null,
                beforeDestruction.materials(),
                0L);
        projects.put(beforeDestruction.id(), failed);
        return beforeDestruction.id();
    }

    boolean isConstructionSite(StarSystemId systemId, EntityId entityId) {
        if (systemId == null || entityId == null) {
            return false;
        }
        for (ConstructionProjectState state : projects.values()) {
            if (!isTerminal(state.status())
                    && state.systemId().equals(systemId)
                    && entityId.equals(state.constructionSiteEntityId())) {
                return true;
            }
        }
        return false;
    }

    private ConstructionProjectState complete(ConstructionProjectState state, long tick) {
        ConstructionProjectState refreshed = refresh(state);
        if (!refreshed.materialsFulfilled()) {
            throw new IllegalStateException("BUILDING project потерял delivered materials: " + state.id());
        }
        SimulationSession session = requireSession(refreshed.systemId());
        Entity site = requireSite(refreshed);
        InventoryComponent inventory = requireComponent(site, InventoryComponent.class, "construction inventory");
        MarketComponent market = site.getComponent(MarketComponent.class);
        for (ConstructionMaterialState material : refreshed.materials()) {
            ContentCatalog.ItemDefinition item = catalog.findItem(material.itemContentId());
            int required = material.requiredAmount();
            if (item == null || inventory.stock[item.runtimeId()] < required) {
                throw new IllegalStateException("Construction inventory расходится с project state");
            }
            inventory.stock[item.runtimeId()] -= required;
            if (market != null) {
                market.isDirty = true;
            }
            session.getLedger().recordResourceSink(
                    siteName(refreshed), item.runtimeId(), required,
                    "station-construction:" + refreshed.stationArchetypeContentId());
        }

        WalletComponent projectWallet = requireComponent(site, WalletComponent.class, "construction wallet");
        long refund = projectWallet.getBalanceMilliCredits();
        if (refund > 0L) {
            FactionEconomicAccount owner = requireFactionAccount(refreshed.ownerFactionContentId());
            if (!projectWallet.transferTo(owner.treasury(), refund)) {
                throw new IllegalStateException("Не удалось вернуть construction wallet после completion");
            }
            session.getLedger().recordMoneyTransfer(
                    siteName(refreshed), treasuryName(refreshed.ownerFactionContentId()), refund,
                    "construction-project-completion-refund");
        }
        if (!session.removeEntity(refreshed.constructionSiteEntityId())) {
            throw new IllegalStateException("Construction site исчез до completion: " + refreshed.id());
        }

        ContentCatalog.StationArchetypeDefinition target = requireConstructible(
                refreshed.stationArchetypeContentId());
        Entity station = ArchetypeEntityFactory.createConstructedStation(
                catalog,
                target.id(),
                target.displayName() + " #" + refreshed.id().value(),
                refreshed.x(),
                refreshed.y(),
                refreshed.ownerFactionContentId());
        EntityId stationId = session.createEntity(station);
        return copy(
                refreshed,
                ConstructionProjectStatus.COMPLETED,
                tick,
                refreshed.buildStartedTick(),
                tick,
                null,
                stationId,
                refreshed.materials(),
                0L);
    }

    private ConstructionProjectState snapshotState(ConstructionProjectState state) {
        return isTerminal(state.status()) ? state : refresh(state);
    }

    private ConstructionProjectState refresh(ConstructionProjectState state) {
        if (isTerminal(state.status())) {
            return state;
        }
        Entity site = requireSite(state);
        InventoryComponent inventory = requireComponent(site, InventoryComponent.class, "construction inventory");
        WalletComponent wallet = requireComponent(site, WalletComponent.class, "construction wallet");
        List<ConstructionMaterialState> materials = new ArrayList<>(state.materials().size());
        for (ConstructionMaterialState requirement : state.materials()) {
            ContentCatalog.ItemDefinition item = catalog.findItem(requirement.itemContentId());
            if (item == null) {
                throw new IllegalStateException("Construction material исчез из content catalog");
            }
            int delivered = Math.min(requirement.requiredAmount(), inventory.stock[item.runtimeId()]);
            materials.add(new ConstructionMaterialState(
                    requirement.itemContentId(), requirement.requiredAmount(), delivered));
        }
        return copy(
                state, state.status(), state.stateChangedTick(), state.buildStartedTick(), state.completedTick(),
                state.constructionSiteEntityId(), state.completedStationEntityId(), materials,
                wallet.getBalanceMilliCredits());
    }

    private ConstructionProjectState validateRestored(ConstructionProjectState state) {
        ContentCatalog.StationArchetypeDefinition target = requireConstructible(state.stationArchetypeContentId());
        requireFactionAccount(state.ownerFactionContentId());
        requireSession(state.systemId());
        if (isTerminal(state.status())) {
            return state;
        }
        Entity site = requireSite(state);
        ConstructionSiteFactory.restoreDerivedPolicy(catalog, target, site);
        ConstructionProjectState refreshed = refresh(state);
        if (refreshed.projectWalletMilliCredits() != state.projectWalletMilliCredits()
                || !refreshed.materials().equals(state.materials())) {
            throw new IllegalArgumentException("Restored construction project расходится с physical site state");
        }
        if (Money.fromCredits(target.construction().fundingCredits()) != state.minimumFundingMilliCredits()) {
            throw new IllegalArgumentException("Construction funding contract расходится с content catalog");
        }
        return state;
    }

    private ContentCatalog.StationArchetypeDefinition requireConstructible(String archetypeId) {
        String id = normalizedId(archetypeId, "Station archetype");
        ContentCatalog.StationArchetypeDefinition target = catalog.findStationArchetype(id);
        if (target == null || target.construction() == null) {
            throw new IllegalArgumentException("Station archetype не является constructible: " + id);
        }
        return target;
    }

    private SimulationSession requireSession(StarSystemId systemId) {
        SimulationSession session = sessionsById.get(
                Objects.requireNonNull(systemId, "Construction system не задан"));
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная construction StarSystem: " + systemId);
        }
        return session;
    }

    private FactionEconomicAccount requireFactionAccount(String factionId) {
        FactionEconomicAccount account = factionAccountsById.get(factionId);
        if (account == null) {
            throw new IllegalArgumentException("Construction owner не имеет faction treasury: " + factionId);
        }
        return account;
    }

    private ConstructionProjectState requireNonTerminal(ConstructionProjectId id) {
        ConstructionProjectState state = projects.get(
                Objects.requireNonNull(id, "ConstructionProjectId не задан"));
        if (state == null) {
            throw new IllegalArgumentException("Неизвестный construction project: " + id);
        }
        if (isTerminal(state.status())) {
            throw new IllegalStateException("Construction project уже terminal: " + id);
        }
        return state;
    }

    private Entity requireSite(ConstructionProjectState state) {
        Entity entity = requireSession(state.systemId()).getEntityRegistry().find(state.constructionSiteEntityId());
        if (entity == null) {
            throw new IllegalStateException("Construction project потерял physical site: " + state.id());
        }
        return entity;
    }

    private static ConstructionMaterialState findMaterial(ConstructionProjectState state, String itemId) {
        for (ConstructionMaterialState material : state.materials()) {
            if (material.itemContentId().equals(itemId)) {
                return material;
            }
        }
        return null;
    }

    private static <T extends com.badlogic.ashley.core.Component> T requireComponent(
            Entity entity, Class<T> type, String label) {
        T component = entity.getComponent(type);
        if (component == null) {
            throw new IllegalStateException("Entity не содержит " + label);
        }
        return component;
    }

    private static long buildDurationTicks(double buildSeconds, SimulationSession session) {
        double raw = buildSeconds / session.getClock().getFixedStepSeconds();
        if (!Double.isFinite(raw) || raw <= 0d || raw > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Construction build duration не представима в ticks");
        }
        return Math.max(1L, (long) Math.ceil(raw));
    }

    private static boolean isTerminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }

    private static String normalizedId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " не задан").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " не должен быть пустым");
        }
        return normalized;
    }

    private static String siteName(ConstructionProjectState state) {
        return "construction:" + state.id().value() + ":site";
    }

    private static String treasuryName(String factionId) {
        return "faction:" + factionId + ":treasury";
    }

    private static ConstructionProjectState copy(
            ConstructionProjectState source,
            ConstructionProjectStatus status,
            long stateChangedTick,
            long buildStartedTick,
            long completedTick,
            EntityId siteId,
            EntityId completedStationId,
            List<ConstructionMaterialState> materials,
            long wallet) {
        return new ConstructionProjectState(
                source.id(), source.ownerFactionContentId(), source.stationArchetypeContentId(),
                source.systemId(), source.x(), source.y(), siteId, materials,
                source.minimumFundingMilliCredits(), wallet, source.buildDurationTicks(), status,
                source.createdTick(), stateChangedTick, buildStartedTick, completedTick, completedStationId);
    }
}
