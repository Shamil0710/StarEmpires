package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.AsteroidFieldId;
import com.spacesim.world.AsteroidFieldNode;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionRelationState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.PlanetId;
import com.spacesim.world.PlanetNode;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Large deterministic manual-test galaxy used before Stage 17.5.
 *
 * <p>This is deliberately not the future Stage-20 physical world generator. It is a curated,
 * deterministic scale fixture: 100 ordinary {@link SimulationSession}s, a connected strategic
 * topology, several materially different starting economies and multiple persistent factions.
 * Once bootstrapped, every system continues through the normal simulation, logistics, diplomacy,
 * territory and construction paths.</p>
 *
 * <p>The original three-system demo remains unchanged in {@link DemoGalaxyFactory} for narrow
 * automated tests. System IDs 1/2/3 and their exact local snapshots are preserved here so existing
 * playable route and save assumptions remain valid.</p>
 */
public final class LargeDemoGalaxyFactory {
    /** Exact live-demo system count. */
    public static final int SYSTEM_COUNT = 100;
    /** Number of additional regional sectors beyond the two legacy sectors. */
    public static final int REGIONAL_SECTOR_COUNT = 10;

    private static final String[] REGIONS = {
            "Aquila Reach", "Borealis March", "Cygnus Verge", "Draconis Belt", "Erebus Expanse",
            "Fornax Corridor", "Gemini Frontier", "Helios Spur", "Icarus Drift", "Janus Rim"
    };
    private static final String[] SYSTEM_NAMES = {
            "Aster", "Bastion", "Cinder", "Dawn", "Eidolon",
            "Farpoint", "Garnet", "Horizon", "Iskra", "Junction"
    };

    private static final List<DemoFaction> DEMO_FACTIONS = List.of(
            new DemoFaction("faction.imperial_directorate", 3, "Имперский директорат",
                    new FactionDoctrineState(45, 80, 65, 85, 80, 70, 75)),
            new DemoFaction("faction.frontier_union", 4, "Союз пограничных миров",
                    new FactionDoctrineState(70, 55, 80, 75, 50, 45, 65)),
            new DemoFaction("faction.industrial_combine", 5, "Промышленный комбинат",
                    new FactionDoctrineState(60, 55, 70, 50, 70, 35, 90)),
            new DemoFaction("faction.free_ports", 6, "Лига свободных портов",
                    new FactionDoctrineState(95, 35, 55, 30, 55, 30, 45)),
            new DemoFaction("faction.research_consortium", 7, "Исследовательский консорциум",
                    new FactionDoctrineState(65, 45, 45, 40, 85, 25, 80)));

    private LargeDemoGalaxyFactory() {
        throw new AssertionError("LargeDemoGalaxyFactory does not create instances");
    }

    /** Creates the 100-system live-demo runtime on the default content catalog. */
    public static WorldSimulation create(long rootSeed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        return WorldSimulation.restore(
                createState(rootSeed, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    /**
     * Builds the deterministic large demo state.
     *
     * @param rootSeed root world seed
     * @param contentCatalog semantic content catalog
     * @return current persistent world with exactly {@value #SYSTEM_COUNT} systems
     */
    public static WorldState createState(long rootSeed, ContentCatalog contentCatalog) {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog not set");
        WorldState compact = DemoGalaxyFactory.createState(rootSeed, content);

        List<StarSystemSimulationState> systems = new ArrayList<>(SYSTEM_COUNT);
        systems.addAll(compact.systems());

        List<SectorNode> sectors = new ArrayList<>();
        sectors.addAll(compact.topology().sectors());
        List<JumpConnection> connections = new ArrayList<>(compact.topology().connections());

        List<WorldFactionIdentityState> dynamicIdentities = demoFactionIdentities();
        List<FactionDescriptor> factions = allFactions(content);
        Map<String, List<StarSystemId>> controlled = new HashMap<>();
        for (FactionDescriptor faction : factions) {
            controlled.put(faction.id(), new ArrayList<>());
        }
        controlled.get("faction.trade_league").add(DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        controlled.get("faction.miners").add(DemoGalaxyFactory.INNER_SYSTEM_ID);
        controlled.get("faction.neutral").add(DemoGalaxyFactory.FRONTIER_SYSTEM_ID);

        int nextSystem = 4;
        StarSystemId previousGateway = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;
        for (int region = 0; region < REGIONAL_SECTOR_COUNT; region++) {
            int regionSize = region < 7 ? 10 : 9;
            List<StarSystemNode> regionalSystems = new ArrayList<>(regionSize);
            StarSystemId firstInRegion = null;
            StarSystemId previousInRegion = null;
            for (int local = 0; local < regionSize; local++) {
                int systemNumber = nextSystem++;
                StarSystemId systemId = new StarSystemId(systemNumber);
                if (firstInRegion == null) {
                    firstInRegion = systemId;
                }

                SystemProfile profile = SystemProfile.values()[(systemNumber + region * 3) % SystemProfile.values().length];
                FactionDescriptor controller = controllerFor(factions, region, local);
                FactionDescriptor secondary = secondaryFor(factions, controller, region, local);
                StarSystemNode node = generatedSystem(rootSeed, systemNumber, region, local, profile);
                regionalSystems.add(node);
                controlled.get(controller.id()).add(systemId);

                SimulationSession session = SimulationSession.createDemo(derivedSeed(rootSeed, systemNumber), content);
                configureSession(session, content, profile, controller.runtimeId(), secondary.runtimeId());
                systems.add(new StarSystemSimulationState(systemId, session.snapshot()));

                if (previousInRegion != null) {
                    connections.add(new JumpConnection(previousInRegion, systemId));
                }
                if (local >= 2 && (local % 3) == 2) {
                    connections.add(new JumpConnection(new StarSystemId(systemNumber - 2L), systemId));
                }
                previousInRegion = systemId;
            }

            connections.add(new JumpConnection(previousGateway, firstInRegion));
            previousGateway = previousInRegion;
            sectors.add(new SectorNode(new SectorId(10L + region), REGIONS[region], regionalSystems));
        }

        if (systems.size() != SYSTEM_COUNT || nextSystem != SYSTEM_COUNT + 1) {
            throw new IllegalStateException("Large demo bootstrap produced wrong system count: " + systems.size());
        }

        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Star Empires — 100 System Live Demo",
                sectors,
                connections);

        List<FactionEconomicState> economies = new ArrayList<>(factions.size());
        List<FactionStrategicState> strategies = new ArrayList<>(factions.size());
        for (FactionDescriptor faction : factions) {
            double treasury = faction.id().equals("faction.neutral") ? 500_000d : 1_250_000d;
            economies.add(new FactionEconomicState(
                    faction.id(),
                    Money.fromCredits(treasury),
                    Money.fromCredits(300_000d),
                    Money.fromCredits(100_000d)));

            List<FactionRelationState> relations = new ArrayList<>();
            for (FactionDescriptor other : factions) {
                if (!other.id().equals(faction.id())) {
                    relations.add(new FactionRelationState(other.id(), initialRelation(faction, other)));
                }
            }
            strategies.add(new FactionStrategicState(
                    faction.id(),
                    -25,
                    relations,
                    controlled.get(faction.id()),
                    faction.doctrine()));
        }

        economies.sort(Comparator.naturalOrder());
        strategies.sort(Comparator.naturalOrder());
        systems.sort(Comparator.comparing(StarSystemSimulationState::systemId));

        WorldState bootstrapped = new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                systems,
                economies,
                strategies);
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                systems,
                economies,
                strategies,
                bootstrapped.nextConstructionProjectIdValue(),
                bootstrapped.constructionProjects(),
                bootstrapped.factionEconomicPressures(),
                bootstrapped.nextFleetIdValue(),
                bootstrapped.fleets(),
                bootstrapped.fleetJumps(),
                dynamicIdentities);
    }

    private static StarSystemNode generatedSystem(
            long rootSeed,
            int systemNumber,
            int region,
            int local,
            SystemProfile profile) {
        long noise = derivedSeed(rootSeed, systemNumber * 17L + 5L);
        double regionAngle = (Math.PI * 2d * region) / REGIONAL_SECTOR_COUNT;
        double radial = 78d + region * 15d + local * 2.8d;
        double jitterX = signedUnit(noise) * 8d;
        double jitterY = signedUnit(noise >>> 17) * 8d;
        double x = Math.cos(regionAngle) * radial + jitterX;
        double y = Math.sin(regionAngle) * radial + jitterY;
        String name = REGIONS[region].split(" ")[0] + " " + SYSTEM_NAMES[local];

        int planetCount = (int) Math.floorMod(noise, 5L);
        List<PlanetNode> planets = new ArrayList<>(planetCount);
        for (int index = 0; index < planetCount; index++) {
            planets.add(new PlanetNode(
                    new PlanetId(systemNumber * 100L + index + 1L),
                    name + " " + roman(index + 1),
                    0.55d + index * 0.75d + Math.abs(signedUnit(noise >>> (index + 3))) * 0.25d));
        }

        int fieldCount = switch (profile) {
            case MINING -> 3;
            case FRONTIER, INDUSTRIAL -> 2;
            case CAPITAL, TRADE_HUB -> 1;
            default -> (int) Math.floorMod(noise >>> 9, 3L);
        };
        List<AsteroidFieldNode> fields = new ArrayList<>(fieldCount);
        for (int index = 0; index < fieldCount; index++) {
            double angle = regionAngle + 0.8d * index + signedUnit(noise >>> (index + 11)) * 0.35d;
            fields.add(new AsteroidFieldNode(
                    new AsteroidFieldId(systemNumber * 100L + index + 1L),
                    name + " Field " + (index + 1),
                    Math.cos(angle) * (0.7d + 0.65d * index),
                    Math.sin(angle) * (0.7d + 0.65d * index),
                    0.55d + 0.35d * index + (profile == SystemProfile.MINING ? 0.8d : 0d)));
        }
        return new StarSystemNode(new StarSystemId(systemNumber), name, x, y, planets, fields);
    }

    private static void configureSession(
            SimulationSession session,
            ContentCatalog content,
            SystemProfile profile,
            int controllerRuntimeId,
            int secondaryRuntimeId) {
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null) {
                if (faction.factionId == 2) {
                    faction.factionId = secondaryRuntimeId;
                } else if (faction.factionId == 0 && profile == SystemProfile.FRONTIER) {
                    faction.factionId = 0;
                } else {
                    faction.factionId = controllerRuntimeId;
                }
            }

            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null) {
                continue;
            }
            for (ContentCatalog.ItemDefinition item : content.getItems()) {
                int itemId = item.runtimeId();
                if (!market.isTradable(itemId)) {
                    continue;
                }
                double stockMultiplier = profile.stockMultiplier(item.id());
                double targetMultiplier = profile.targetMultiplier(item.id());
                inventory.stock[itemId] = Math.max(0, (int) Math.round(inventory.stock[itemId] * stockMultiplier));
                int target = Math.max(1, (int) Math.round(market.configuredTargetStock[itemId] * targetMultiplier));
                market.configuredTargetStock[itemId] = target;
                market.targetStock[itemId] = target;
                market.isDirty = true;
            }
            inventory.capacity = Math.max(inventory.capacity, inventory.getTotalStock() + 250);
        }
    }

    private static FactionDescriptor controllerFor(
            List<FactionDescriptor> factions, int region, int local) {
        List<FactionDescriptor> political = factions.stream()
                .filter(value -> !value.id().equals("faction.neutral"))
                .toList();
        return political.get(Math.floorMod(region * 2 + local / 4, political.size()));
    }

    private static FactionDescriptor secondaryFor(
            List<FactionDescriptor> factions,
            FactionDescriptor controller,
            int region,
            int local) {
        int start = Math.floorMod(region + local + 1, factions.size());
        for (int offset = 0; offset < factions.size(); offset++) {
            FactionDescriptor candidate = factions.get((start + offset) % factions.size());
            if (!candidate.id().equals(controller.id())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Large demo requires more than one faction");
    }

    private static List<FactionDescriptor> allFactions(ContentCatalog content) {
        List<FactionDescriptor> result = new ArrayList<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            ContentCatalog.FactionDoctrineDefinition doctrine = faction.doctrine();
            result.add(new FactionDescriptor(
                    faction.id(),
                    faction.runtimeId(),
                    faction.displayName(),
                    new FactionDoctrineState(
                            doctrine.tradeOpenness(),
                            doctrine.securityPosture(),
                            doctrine.expansionPreference(),
                            doctrine.sovereigntySensitivity(),
                            doctrine.treatyLegalism(),
                            doctrine.interventionism(),
                            doctrine.economicResiliencePriority())));
        }
        for (DemoFaction faction : DEMO_FACTIONS) {
            result.add(new FactionDescriptor(
                    faction.id(), faction.runtimeId(), faction.displayName(), faction.doctrine()));
        }
        result.sort(Comparator.comparingInt(FactionDescriptor::runtimeId));
        return List.copyOf(result);
    }

    private static List<WorldFactionIdentityState> demoFactionIdentities() {
        List<WorldFactionIdentityState> identities = new ArrayList<>();
        for (DemoFaction faction : DEMO_FACTIONS) {
            identities.add(new WorldFactionIdentityState(
                    faction.id(),
                    faction.runtimeId(),
                    faction.displayName(),
                    WorldFactionIdentityState.Origin.WORLD_BOOTSTRAP));
        }
        return List.copyOf(identities);
    }

    private static int initialRelation(FactionDescriptor source, FactionDescriptor target) {
        if (source.id().equals("faction.neutral") || target.id().equals("faction.neutral")) {
            return 5;
        }
        int distance = Math.abs(source.runtimeId() - target.runtimeId());
        return distance <= 1 ? 15 : (distance <= 3 ? 5 : -5);
    }

    private static long derivedSeed(long rootSeed, long systemOrdinal) {
        long value = rootSeed + 0x9E3779B97F4A7C15L * systemOrdinal;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double signedUnit(long value) {
        long bits = (value >>> 11) & ((1L << 53) - 1L);
        return (bits / (double) ((1L << 53) - 1L)) * 2d - 1d;
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(value);
        };
    }

    /** Curated startup economy profiles; they modify only initial physical stocks/market demand. */
    public enum SystemProfile {
        CAPITAL,
        TRADE_HUB,
        MINING,
        ENERGY,
        AGRICULTURAL,
        INDUSTRIAL,
        ARSENAL,
        FRONTIER;

        double stockMultiplier(String itemId) {
            return switch (this) {
                case CAPITAL -> 1.35d;
                case TRADE_HUB -> 1.55d;
                case MINING -> itemId.equals("item.ore") ? 2.2d : 0.75d;
                case ENERGY -> itemId.equals("item.energy") ? 2.25d : 0.8d;
                case AGRICULTURAL -> itemId.equals("item.food") ? 2.1d : 0.85d;
                case INDUSTRIAL -> itemId.equals("item.steel") ? 2.0d : 0.75d;
                case ARSENAL -> itemId.equals("item.weapons") ? 2.0d : 0.8d;
                case FRONTIER -> 0.45d;
            };
        }

        double targetMultiplier(String itemId) {
            return switch (this) {
                case CAPITAL -> 1.45d;
                case TRADE_HUB -> 1.2d;
                case MINING -> itemId.equals("item.energy") ? 1.8d : 1.0d;
                case ENERGY -> itemId.equals("item.ore") ? 1.35d : 0.9d;
                case AGRICULTURAL -> itemId.equals("item.energy") ? 1.65d : 1.0d;
                case INDUSTRIAL -> itemId.equals("item.ore") || itemId.equals("item.energy") ? 1.8d : 1.0d;
                case ARSENAL -> itemId.equals("item.steel") || itemId.equals("item.energy") ? 1.8d : 1.0d;
                case FRONTIER -> 1.65d;
            };
        }
    }

    private record DemoFaction(
            String id,
            int runtimeId,
            String displayName,
            FactionDoctrineState doctrine) {
    }

    private record FactionDescriptor(
            String id,
            int runtimeId,
            String displayName,
            FactionDoctrineState doctrine) {
    }
}
