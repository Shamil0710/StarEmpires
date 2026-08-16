package com.spacesim.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Stage-17.5E identity binding between physical ammunition feeds and ammunition content.
 *
 * <p>This state intentionally stores no quantity or mass. Those remain authoritative in the common
 * {@link ShipEngineeringState.ConsumableState}; the loadout only answers which ammunition definition
 * occupies a given mount/interface so the weapon solver can resolve material, shape and guidance data.</p>
 *
 * @param feeds deterministic feed bindings
 */
public record WeaponLoadoutState(List<FeedBinding> feeds) {
    /**
     * Validates, sorts and freezes feed identity bindings.
     *
     * @param feeds feed bindings to freeze
     */
    public WeaponLoadoutState {
        Objects.requireNonNull(feeds, "feeds");
        List<FeedBinding> copy = new ArrayList<>(feeds);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("feeds must not contain null");
        }
        copy.sort(Comparator.comparing(FeedBinding::mountId)
                .thenComparing(FeedBinding::interfaceId)
                .thenComparing(FeedBinding::ammunitionContentId));
        for (int index = 1; index < copy.size(); index++) {
            FeedBinding previous = copy.get(index - 1);
            FeedBinding current = copy.get(index);
            if (previous.mountId().equals(current.mountId())
                    && previous.interfaceId().equals(current.interfaceId())) {
                throw new IllegalArgumentException(
                        "only one ammunition content identity may occupy a physical feed");
            }
        }
        feeds = List.copyOf(copy);
    }

    /** @return empty loadout with no feed identities */
    public static WeaponLoadoutState empty() {
        return new WeaponLoadoutState(List.of());
    }

    /**
     * Resolves the ammunition content identity loaded in one physical feed.
     *
     * @param mountId fitted module mount ID
     * @param interfaceId module-local ammunition interface ID
     * @return ammunition content ID when the feed is bound
     */
    public Optional<String> ammunitionContentId(String mountId, String interfaceId) {
        requireNonBlank(mountId, "mountId");
        requireNonBlank(interfaceId, "interfaceId");
        return feeds.stream()
                .filter(feed -> feed.mountId().equals(mountId) && feed.interfaceId().equals(interfaceId))
                .map(FeedBinding::ammunitionContentId)
                .findFirst();
    }

    /**
     * One identity binding for a concrete physical ammunition feed.
     *
     * @param mountId fitted module mount ID
     * @param interfaceId module-local ammunition interface ID
     * @param ammunitionContentId stable ammunition definition content ID
     */
    public record FeedBinding(String mountId, String interfaceId, String ammunitionContentId) {
        /**
         * Validates one physical-feed identity binding.
         *
         * @param mountId fitted module mount ID
         * @param interfaceId module-local ammunition interface ID
         * @param ammunitionContentId stable ammunition definition content ID
         */
        public FeedBinding {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(interfaceId, "interfaceId");
            requireNonBlank(ammunitionContentId, "ammunitionContentId");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
