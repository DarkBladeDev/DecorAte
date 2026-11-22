package dev.decorate.api.spi;

import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Client-side packet display renderer managing virtual {@code ItemDisplay} and {@code Interaction} entities.
 */
public interface DisplayRenderer {

    /**
     * Spawns the display and interaction entities for a single observing player.
     *
     * @param player observing player
     * @param item placed item to render
     */
    void spawnForPlayer(Player player, PlacedItem item);

    /**
     * Updates the metadata/pose of an already spawned item for a single observing player.
     *
     * @param player observing player
     * @param item updated placed item
     */
    void updateForPlayer(Player player, PlacedItem item);

    /**
     * Destroys the virtual entities for a placed item on the player's client.
     *
     * @param player observing player
     * @param itemId placed item UUID
     */
    void destroyForPlayer(Player player, UUID itemId);

    /**
     * Destroys all virtual entities currently tracked for the player (e.g., on disconnect or world change).
     *
     * @param player observing player
     */
    void destroyAllForPlayer(Player player);

    /**
     * Spawns the placed item for all eligible observing players in range.
     *
     * @param item placed item
     */
    void broadcastSpawn(PlacedItem item);

    /**
     * Broadcasts updated metadata/pose for all players currently viewing the item.
     *
     * @param item updated placed item
     */
    void broadcastUpdate(PlacedItem item);

    /**
     * Broadcasts entity destruction to all players currently viewing the item.
     *
     * @param itemId placed item UUID
     */
    void broadcastDestroy(UUID itemId);

    /**
     * Resolves a virtual {@code Interaction} entity ID back to its owning {@link PlacedItem} UUID.
     *
     * @param entityId virtual entity ID received from a client interact packet
     * @return matching placed item UUID if tracked
     */
    Optional<UUID> resolveItemByVirtualEntityId(int entityId);

    /**
     * Displays or replaces the per-player floating hover label for a targeted placed item.
     *
     * @param player observing player
     * @param itemId targeted placed item UUID
     * @param text custom display name component
     * @param position world coordinates for the virtual text display
     * @param scale uniform display scale
     */
    default void showHoverText(Player player, UUID itemId, Component text, Vec3d position, float scale) {
    }

    /**
     * Destroys any active floating hover label currently displayed for the player.
     *
     * @param player observing player
     */
    default void clearHoverText(Player player) {
    }

    /**
     * Returns whether at least one placed item is currently tracked as visible to the player.
     *
     * @param player observing player
     * @return {@code true} if the player currently has visible placed items
     */
    default boolean hasVisibleItems(Player player) {
        return true;
    }
}
