from pathlib import Path


def patch(path_name, replacements):
    path = Path(path_name)
    text = path.read_text()
    for label, old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{path_name}: {label}: expected 1 marker, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)


patch("src/main/java/com/spacesim/trade/MarketDirectory.java", [
    ("import", "import com.spacesim.components.FactionComponent;\n",
     "import com.spacesim.components.FactionComponent;\nimport com.spacesim.components.FactionMarketAccessComponent;\n"),
    ("mapper", "    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);\n",
     "    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);\n"
     "    private final ComponentMapper<FactionMarketAccessComponent> fam =\n"
     "            ComponentMapper.getFor(FactionMarketAccessComponent.class);\n"),
    ("live access", "        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;\n"
     "        return previous.matchesLiveState(transform, factionId, wallet, inventory, market);",
     "        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;\n"
     "        FactionMarketAccessComponent access = fam.has(entity) ? fam.get(entity) : null;\n"
     "        return previous.matchesLiveState(transform, factionId, wallet, inventory, market, access);"),
    ("snapshot access", "        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;\n"
     "        return new StationMarket(\n"
     "                id,\n"
     "                transform.position.x,\n"
     "                transform.position.y,\n"
     "                factionId,\n"
     "                wallet.getBalanceMilliCredits(),",
     "        int factionId = fm.has(entity) ? fm.get(entity).factionId : -1;\n"
     "        FactionMarketAccessComponent access = fam.has(entity) ? fam.get(entity) : null;\n"
     "        return new StationMarket(\n"
     "                id,\n"
     "                transform.position.x,\n"
     "                transform.position.y,\n"
     "                factionId,\n"
     "                access != null,\n"
     "                access == null || access.canTrade(-1),\n"
     "                access == null ? new boolean[0] : access.copyAllowedFactionIds(),\n"
     "                wallet.getBalanceMilliCredits(),"),
    ("fields", "        private final int factionId;\n        private final long walletBalanceMilliCredits;",
     "        private final int factionId;\n"
     "        private final boolean hasAccessPolicy;\n"
     "        private final boolean allowUnfactioned;\n"
     "        private final boolean[] allowedFactionIds;\n"
     "        private final long walletBalanceMilliCredits;"),
    ("ctor args", "                int factionId,\n                long walletBalanceMilliCredits,",
     "                int factionId,\n"
     "                boolean hasAccessPolicy,\n"
     "                boolean allowUnfactioned,\n"
     "                boolean[] allowedFactionIds,\n"
     "                long walletBalanceMilliCredits,"),
    ("ctor assigns", "            this.factionId = factionId;\n            this.walletBalanceMilliCredits = walletBalanceMilliCredits;",
     "            this.factionId = factionId;\n"
     "            this.hasAccessPolicy = hasAccessPolicy;\n"
     "            this.allowUnfactioned = allowUnfactioned;\n"
     "            this.allowedFactionIds = Arrays.copyOf(allowedFactionIds, allowedFactionIds.length);\n"
     "            this.walletBalanceMilliCredits = walletBalanceMilliCredits;"),
    ("canTrade method", "        /** @return station wallet balance */",
     "        /**\n"
     "         * Checks immutable market access for a planning participant.\n"
     "         *\n"
     "         * @param participantFactionId runtime faction ID or {@code -1}\n"
     "         * @return whether planning may consider this market\n"
     "         */\n"
     "        public boolean canTrade(int participantFactionId) {\n"
     "            if (!hasAccessPolicy) {\n"
     "                return true;\n"
     "            }\n"
     "            if (participantFactionId < 0) {\n"
     "                return allowUnfactioned;\n"
     "            }\n"
     "            return participantFactionId < allowedFactionIds.length\n"
     "                    && allowedFactionIds[participantFactionId];\n"
     "        }\n\n"
     "        /** @return station wallet balance */"),
    ("match signature", "                WalletComponent wallet,\n                InventoryComponent inventory,\n                MarketComponent market) {",
     "                WalletComponent wallet,\n"
     "                InventoryComponent inventory,\n"
     "                MarketComponent market,\n"
     "                FactionMarketAccessComponent access) {"),
    ("match body", "                    && factionId == liveFactionId\n                    && walletBalanceMilliCredits == wallet.getBalanceMilliCredits()",
     "                    && factionId == liveFactionId\n"
     "                    && hasAccessPolicy == (access != null)\n"
     "                    && (!hasAccessPolicy\n"
     "                    || (allowUnfactioned == access.canTrade(-1)\n"
     "                    && Arrays.equals(allowedFactionIds, access.copyAllowedFactionIds())))\n"
     "                    && walletBalanceMilliCredits == wallet.getBalanceMilliCredits()"),
])

patch("src/main/java/com/spacesim/trade/TradeRoutePlanner.java", [
    ("new cargo access", "                if (supplier == null || consumer == null) {\n                    continue;\n                }\n\n                float purchasePrice",
     "                if (supplier == null || consumer == null\n"
     "                        || !supplier.canTrade(fleet.factionId())\n"
     "                        || !consumer.canTrade(fleet.factionId())) {\n"
     "                    continue;\n"
     "                }\n\n"
     "                float purchasePrice"),
    ("existing cargo access", "            for (MarketDirectory.StationMarket consumer : directory.consumers(itemId)) {\n                float salePrice",
     "            for (MarketDirectory.StationMarket consumer : directory.consumers(itemId)) {\n"
     "                if (!consumer.canTrade(fleet.factionId())) {\n"
     "                    continue;\n"
     "                }\n"
     "                float salePrice"),
])
