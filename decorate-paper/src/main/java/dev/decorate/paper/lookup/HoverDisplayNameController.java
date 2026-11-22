package dev.decorate.paper.lookup;

import dev.decorate.api.event.DecorRemoveEvent;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.geometry.PlacementGeometry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Periodically resolves the placed item each player is looking at and drives per-player
 * virtual {@code TextDisplay} hover labels for items with an explicit {@code CUSTOM_NAME}.
 */
public final class HoverDisplayNameController implements Listener {

    private final TargetedItemLookup targetLookup;
    private final DisplayRenderer renderer;
    private final Function<byte[], Optional<Component>> customNameExtractor;
    private final Map<UUID, Optional<Component>> customNameCache = new ConcurrentHashMap<>();
    private volatile DecorateSettings settings;

    public HoverDisplayNameController(
            TargetedItemLookup targetLookup,
            DisplayRenderer renderer,
            DecorateSettings settings
    ) {
        this(targetLookup, renderer, settings, HoverDisplayNameController::extractCustomName);
    }

    public HoverDisplayNameController(
            TargetedItemLookup targetLookup,
            DisplayRenderer renderer,
            DecorateSettings settings,
            Function<byte[], Optional<Component>> customNameExtractor
    ) {
        this.targetLookup = Objects.requireNonNull(targetLookup, "targetLookup must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.customNameExtractor = Objects.requireNonNull(customNameExtractor, "customNameExtractor must not be null");
    }

    /**
     * Updates runtime settings and clears active hover labels if the feature is disabled.
     *
     * @param updatedSettings new settings snapshot
     * @param onlinePlayers currently online players to clear when disabled
     */
    public void updateSettings(DecorateSettings updatedSettings, Collection<? extends Player> onlinePlayers) {
        this.settings = Objects.requireNonNull(updatedSettings, "updatedSettings must not be null");
        if (!updatedSettings.hoverDisplayNameEnabled() && onlinePlayers != null) {
            for (Player player : onlinePlayers) {
                renderer.clearHoverText(player);
            }
        }
    }

    /**
     * Evaluates hover display names for all supplied online players.
     *
     * @param onlinePlayers players to update
     */
    public void tick(Collection<? extends Player> onlinePlayers) {
        DecorateSettings current = this.settings;
        if (!current.hoverDisplayNameEnabled()) {
            return;
        }
        for (Player player : onlinePlayers) {
            updatePlayer(player, current);
        }
    }

    /**
     * Updates the hover label state for a single player.
     *
     * @param player observing player
     */
    public void updatePlayer(Player player) {
        updatePlayer(player, this.settings);
    }

    private void updatePlayer(Player player, DecorateSettings current) {
        if (player == null || !current.hoverDisplayNameEnabled() || !renderer.hasVisibleItems(player)) {
            if (player != null) {
                renderer.clearHoverText(player);
            }
            return;
        }
        Optional<PlacedItem> targetedOpt = targetLookup.findTargetedItem(player);
        if (targetedOpt.isEmpty()) {
            renderer.clearHoverText(player);
            return;
        }
        PlacedItem item = targetedOpt.get();
        Optional<Component> customName = resolveCustomName(item);
        if (customName.isEmpty()) {
            renderer.clearHoverText(player);
            return;
        }
        Vec3d labelPosition = PlacementGeometry.computeHoverLabelPosition(item.pose(), current.hoverVerticalOffset());
        renderer.showHoverText(player, item.id(), customName.get(), labelPosition, current.hoverDisplayScale());
    }

    /**
     * Returns the cached custom display name for {@code item}, extracting it once from serialized bytes.
     *
     * @param item placed item record
     * @return optional custom name component
     */
    public Optional<Component> resolveCustomName(PlacedItem item) {
        Objects.requireNonNull(item, "item must not be null");
        return customNameCache.computeIfAbsent(item.id(), id -> customNameExtractor.apply(item.serializedItem()));
    }

    /**
     * Evicts any cached custom name for the given placed item UUID.
     *
     * @param itemId placed item UUID
     */
    public void evict(UUID itemId) {
        if (itemId != null) {
            customNameCache.remove(itemId);
        }
    }

    /**
     * Clears all cached custom display names.
     */
    public void clearCache() {
        customNameCache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecorRemove(DecorRemoveEvent event) {
        evict(event.getPlacedItem().id());
    }

    /**
     * Extracts {@link DataComponentTypes#CUSTOM_NAME} from serialized item bytes on a running Paper server.
     *
     * @param serializedItem serialized Paper {@link ItemStack} bytes
     * @return custom display name component if explicitly set
     */
    public static Optional<Component> extractCustomName(byte[] serializedItem) {
        if (serializedItem == null || serializedItem.length == 0 || Bukkit.getServer() == null) {
            return Optional.empty();
        }
        try {
            ItemStack stack = ItemStack.deserializeBytes(serializedItem);
            return extractCustomName(stack);
        } catch (UnsupportedOperationException | NullPointerException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Extracts {@link DataComponentTypes#CUSTOM_NAME} from a live {@link ItemStack},
     * intentionally ignoring {@link DataComponentTypes#ITEM_NAME} and default material names.
     *
     * @param stack item stack to inspect
     * @return custom display name component if explicitly set
     */
    public static Optional<Component> extractCustomName(ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        if (Bukkit.getServer() != null) {
            try {
                if (!stack.hasData(DataComponentTypes.CUSTOM_NAME)) {
                    return Optional.empty();
                }
                return Optional.ofNullable(stack.getData(DataComponentTypes.CUSTOM_NAME));
            } catch (UnsupportedOperationException | NoClassDefFoundError | ExceptionInInitializerError | NullPointerException ignored) {
                // Fallback for headless test environments without a running Paper server registry
            }
        }
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return Optional.ofNullable(meta.displayName());
            }
        }
        return Optional.empty();
    }
}
