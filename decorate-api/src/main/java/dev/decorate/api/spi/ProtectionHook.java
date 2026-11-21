package dev.decorate.api.spi;

import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.model.PlacedItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Protection integration hook evaluated before placement or interaction mutates world state.
 */
public interface ProtectionHook {

    /**
     * Human-readable hook identifier (e.g., {@code WorldGuard}).
     *
     * @return hook name
     */
    String name();

    /**
     * Checks whether the player is allowed to place a decoration at the target location.
     *
     * @param player placing player
     * @param location exact placement location
     * @param supportBlock supporting block
     * @return true if placement is permitted
     */
    boolean canPlace(Player player, Location location, Block supportBlock);

    /**
     * Checks whether the player is allowed to perform the specified interaction on an existing item.
     *
     * @param player interacting player
     * @param item target placed item
     * @param action requested action
     * @return true if interaction is permitted
     */
    boolean canInteract(Player player, PlacedItem item, DecorInteractEvent.Action action);
}
