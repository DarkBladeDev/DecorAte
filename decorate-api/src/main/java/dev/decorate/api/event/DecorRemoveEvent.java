package dev.decorate.api.event;

import dev.decorate.api.model.PlacedItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired before a placed item is removed from the world.
 */
public class DecorRemoveEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedItem placedItem;
    private final Reason reason;
    private final @Nullable Player actor;
    private boolean dropItem;
    private boolean cancelled;

    /**
     * Creates a new removal event.
     *
     * @param placedItem item being removed
     * @param reason removal cause
     * @param actor optional player initiating removal
     * @param dropItem whether the underlying item should be returned/dropped
     */
    public DecorRemoveEvent(
            PlacedItem placedItem,
            Reason reason,
            @Nullable Player actor,
            boolean dropItem
    ) {
        this.placedItem = Objects.requireNonNull(placedItem, "placedItem must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.actor = actor;
        this.dropItem = dropItem;
    }

    public PlacedItem getPlacedItem() {
        return placedItem;
    }

    public Reason getReason() {
        return reason;
    }

    public @Nullable Player getActor() {
        return actor;
    }

    public boolean isDropItem() {
        return dropItem;
    }

    public void setDropItem(boolean dropItem) {
        this.dropItem = dropItem;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Reason why a placed item is being removed.
     */
    public enum Reason {
        PLAYER_PICKUP,
        SUPPORT_BLOCK_BROKEN,
        COMMAND_CLEAR,
        API
    }
}
