from pathlib import Path


def replace_once(path, old, new):
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one anchor in {path}, found {count}")
    target.write_text(text.replace(old, new, 1))


catalog = "src/main/java/com/spacesim/content/ContentCatalog.java"
replace_once(
    catalog,
    '''            canonical.append("faction|").append(faction.runtimeId()).append('|')
                    .append(faction.id()).append('|').append(faction.displayName()).append('\\n');''',
    '''            FactionDoctrineDefinition doctrine = faction.doctrine();
            canonical.append("faction|").append(faction.runtimeId()).append('|')
                    .append(faction.id()).append('|').append(faction.displayName()).append('|')
                    .append(doctrine.tradeOpenness()).append('|')
                    .append(doctrine.securityPosture()).append('|')
                    .append(doctrine.expansionPreference()).append('|')
                    .append(doctrine.sovereigntySensitivity()).append('|')
                    .append(doctrine.treatyLegalism()).append('|')
                    .append(doctrine.interventionism()).append('|')
                    .append(doctrine.economicResiliencePriority()).append('\\n');''',
)
old = '''    /**
     * @param id стабильный persistent faction ID
     * @param runtimeId плотный ID массива репутации
     * @param displayName отображаемое имя
     */
    public record FactionDefinition(String id, int runtimeId, String displayName) {
        /**
         * @param id стабильный persistent faction ID
         * @param runtimeId плотный ID массива репутации
         * @param displayName отображаемое имя
         */
        public FactionDefinition {
            Objects.requireNonNull(id, "Faction ID не задан");
            Objects.requireNonNull(displayName, "Faction displayName не задан");
        }
    }
'''
new = '''    /**
     * Data-driven institutional doctrine defaults for one authored faction.
     *
     * @param tradeOpenness willingness to prefer external trade and market integration [0,100]
     * @param securityPosture priority given to security exposure and strategic risk [0,100]
     * @param expansionPreference willingness to pursue territorial/infrastructure expansion [0,100]
     * @param sovereigntySensitivity aversion to foreign jurisdiction and concessions [0,100]
     * @param treatyLegalism importance assigned to trust and contractual continuity [0,100]
     * @param interventionism willingness to bear costs for external commitments [0,100]
     * @param economicResiliencePriority willingness to pay for diversification [0,100]
     */
    public record FactionDoctrineDefinition(
            int tradeOpenness,
            int securityPosture,
            int expansionPreference,
            int sovereigntySensitivity,
            int treatyLegalism,
            int interventionism,
            int economicResiliencePriority) {
        /**
         * Validates bounded content-authored institutional preferences.
         *
         * @param tradeOpenness trade openness [0,100]
         * @param securityPosture security posture [0,100]
         * @param expansionPreference expansion preference [0,100]
         * @param sovereigntySensitivity sovereignty sensitivity [0,100]
         * @param treatyLegalism treaty legalism [0,100]
         * @param interventionism interventionism [0,100]
         * @param economicResiliencePriority resilience priority [0,100]
         */
        public FactionDoctrineDefinition {
            requireDoctrineAxis(tradeOpenness, "Trade openness");
            requireDoctrineAxis(securityPosture, "Security posture");
            requireDoctrineAxis(expansionPreference, "Expansion preference");
            requireDoctrineAxis(sovereigntySensitivity, "Sovereignty sensitivity");
            requireDoctrineAxis(treatyLegalism, "Treaty legalism");
            requireDoctrineAxis(interventionism, "Interventionism");
            requireDoctrineAxis(economicResiliencePriority, "Economic resilience priority");
        }

        /** @return neutral midpoint doctrine for source-compatible catalogs without explicit doctrine */
        public static FactionDoctrineDefinition neutral() {
            return new FactionDoctrineDefinition(50, 50, 50, 50, 50, 50, 50);
        }

        private static void requireDoctrineAxis(int value, String label) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException(label + " must be in [0,100]");
            }
        }
    }

    /**
     * @param id стабильный persistent faction ID
     * @param runtimeId плотный ID массива репутации
     * @param displayName отображаемое имя
     * @param doctrine data-driven institutional decision defaults
     */
    public record FactionDefinition(
            String id,
            int runtimeId,
            String displayName,
            FactionDoctrineDefinition doctrine) {
        /**
         * Source-compatible definition for narrow catalogs predating Stage 17F.
         *
         * @param id persistent faction ID
         * @param runtimeId dense runtime ID
         * @param displayName display name
         */
        public FactionDefinition(String id, int runtimeId, String displayName) {
            this(id, runtimeId, displayName, FactionDoctrineDefinition.neutral());
        }

        /**
         * @param id стабильный persistent faction ID
         * @param runtimeId плотный ID массива репутации
         * @param displayName отображаемое имя
         * @param doctrine data-driven institutional decision defaults
         */
        public FactionDefinition {
            Objects.requireNonNull(id, "Faction ID не задан");
            Objects.requireNonNull(displayName, "Faction displayName не задан");
            Objects.requireNonNull(doctrine, "Faction doctrine не задана");
        }
    }
'''
replace_once(catalog, old, new)

loader = "src/main/java/com/spacesim/content/ContentCatalogLoader.java"
replace_once(
    loader,
    '''    private static ContentCatalog.FactionDefinition parseFaction(JsonValue node) {
        return new ContentCatalog.FactionDefinition(
                requireString(node, "id"),
                requireInt(node, "runtimeId"),
                requireNonBlank(node, "displayName"));
    }
''',
    '''    private static ContentCatalog.FactionDefinition parseFaction(JsonValue node) {
        JsonValue doctrineNode = node.get("doctrine");
        ContentCatalog.FactionDoctrineDefinition doctrine = doctrineNode == null
                ? ContentCatalog.FactionDoctrineDefinition.neutral()
                : parseFactionDoctrine(requireObject(doctrineNode, "faction doctrine"));
        return new ContentCatalog.FactionDefinition(
                requireString(node, "id"),
                requireInt(node, "runtimeId"),
                requireNonBlank(node, "displayName"),
                doctrine);
    }

    private static ContentCatalog.FactionDoctrineDefinition parseFactionDoctrine(JsonValue node) {
        return new ContentCatalog.FactionDoctrineDefinition(
                requireInt(node, "tradeOpenness"),
                requireInt(node, "securityPosture"),
                requireInt(node, "expansionPreference"),
                requireInt(node, "sovereigntySensitivity"),
                requireInt(node, "treatyLegalism"),
                requireInt(node, "interventionism"),
                requireInt(node, "economicResiliencePriority"));
    }
''',
)

strategic = "src/main/java/com/spacesim/world/FactionStrategicState.java"
anchor = '''    /**
     * Stage-8 совместимый constructor с пустыми policy lists.
'''
addition = '''    /**
     * Compact bootstrap constructor with an explicit Stage-17F doctrine profile.
     *
     * @param factionContentId stable faction ID
     * @param minimumMarketAccessRelation relation threshold for ordinary market access
     * @param relations directed strategic relations
     * @param controlledSystems initially controlled systems
     * @param doctrine institutional decision profile
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems,
            FactionDoctrineState doctrine) {
        this(
                factionContentId,
                minimumMarketAccessRelation,
                relations,
                controlledSystems,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                legacyControlStates(controlledSystems),
                List.of(),
                List.of(),
                doctrine);
    }

''' + anchor
replace_once(strategic, anchor, addition)

demo = "src/main/java/com/spacesim/DemoGalaxyFactory.java"
replace_once(
    demo,
    'import com.spacesim.world.FactionEconomicState;\n',
    'import com.spacesim.world.FactionDoctrineState;\nimport com.spacesim.world.FactionEconomicState;\n',
)
replace_once(
    demo,
    '''                                List.of(FRONTIER_SYSTEM_ID)),''',
    '''                                List.of(FRONTIER_SYSTEM_ID),
                                doctrineState(content, "faction.neutral")),''',
)
replace_once(
    demo,
    '''                                List.of(ACTIVE_SYSTEM_ID)),''',
    '''                                List.of(ACTIVE_SYSTEM_ID),
                                doctrineState(content, "faction.trade_league")),''',
)
replace_once(
    demo,
    '''                                List.of(INNER_SYSTEM_ID))));''',
    '''                                List.of(INNER_SYSTEM_ID),
                                doctrineState(content, "faction.miners"))));''',
)
anchor = '''    private static FactionEconomicState factionState(String contentId, double treasuryCredits) {
'''
helper = '''    private static FactionDoctrineState doctrineState(ContentCatalog content, String factionContentId) {
        ContentCatalog.FactionDefinition faction = Objects.requireNonNull(
                content.findFaction(factionContentId),
                "Authored faction missing from content catalog: " + factionContentId);
        ContentCatalog.FactionDoctrineDefinition doctrine = faction.doctrine();
        return new FactionDoctrineState(
                doctrine.tradeOpenness(),
                doctrine.securityPosture(),
                doctrine.expansionPreference(),
                doctrine.sovereigntySensitivity(),
                doctrine.treatyLegalism(),
                doctrine.interventionism(),
                doctrine.economicResiliencePriority());
    }

''' + anchor
replace_once(demo, anchor, helper)

json_path = "src/main/resources/data/content/catalog-v1.json"
replace_once(
    json_path,
    '''      "displayName": "Нейтралы"
    },''',
    '''      "displayName": "Нейтралы",
      "doctrine": {
        "tradeOpenness": 45,
        "securityPosture": 50,
        "expansionPreference": 35,
        "sovereigntySensitivity": 65,
        "treatyLegalism": 55,
        "interventionism": 25,
        "economicResiliencePriority": 60
      }
    },''',
)
replace_once(
    json_path,
    '''      "displayName": "Торговая лига"
    },''',
    '''      "displayName": "Торговая лига",
      "doctrine": {
        "tradeOpenness": 90,
        "securityPosture": 40,
        "expansionPreference": 65,
        "sovereigntySensitivity": 35,
        "treatyLegalism": 75,
        "interventionism": 55,
        "economicResiliencePriority": 40
      }
    },''',
)
replace_once(
    json_path,
    '''      "displayName": "Шахтёры"
    }''',
    '''      "displayName": "Шахтёры",
      "doctrine": {
        "tradeOpenness": 55,
        "securityPosture": 60,
        "expansionPreference": 55,
        "sovereigntySensitivity": 70,
        "treatyLegalism": 60,
        "interventionism": 30,
        "economicResiliencePriority": 75
      }
    }''',
)
