package dev.decorate.api.event;

import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.SurfacePose;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before an item from a player's hand is placed onto a surface.
 */
public class DecorPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Block supportBlock;
    private final BlockFace face;
    private final ItemStack itemStack;
    private final PlacementProfile profile;
    private SurfacePose pose;
    private boolean cancelled;

    /**
     * Creates a new placement event.
     *
     * @param player placing player
     * @param supportBlock target supporting block
     * @param face target block face
     * @param itemStack item being placed
     * @param profile resolved placement profile
     * @param pose computed initial surface pose
     */
    public DecorPlaceEvent(
            Player player,
            Block supportBlock,
            BlockFace face,
            ItemStack itemStack,
            PlacementProfile profile,
            SurfacePose pose
    ) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.supportBlock = Objects.requireNonNull(supportBlock, "supportBlock must not be null");
        this.face = Objects.requireNonNull(face, "face must not be null");
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.pose = Objects.requireNonNull(pose, "pose must not be null");
    }

    public Player getPlayer() {
        return player;
    }

    public Block getSupportBlock() {
        return supportBlock;
    }

    public BlockFace getFace() {
        return face;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public PlacementProfile getProfile() {
        return profile;
    }

    public SurfacePose getPose() {
        return pose;
    }

    public void setPose(SurfacePose pose) {
        this.pose = Objects.requireNonNull(pose, "pose must not be null");
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
}
