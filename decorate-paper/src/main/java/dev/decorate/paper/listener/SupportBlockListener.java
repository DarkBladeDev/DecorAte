package dev.decorate.paper.listener;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.core.service.PlacementService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;
import java.util.Objects;

/**
 * Enforces supporting-block integrity by cleaning up and optionally dropping
 * placed items when their supporting block breaks, explodes, or is moved by a piston.
 */
public final class SupportBlockListener implements Listener {

    private final PlacementService placementService;

    public SupportBlockListener(PlacementService placementService) {
        this.placementService = Objects.requireNonNull(placementService, "placementService must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        cleanupBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            cleanupBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            cleanupBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            cleanupBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            cleanupBlock(block);
        }
    }

    private void cleanupBlock(Block block) {
        BlockCoordinate coord = new BlockCoordinate(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        List<PlacementService.RemovedDrop> drops = placementService.handleSupportBlockRemoved(coord);
        if (drops.isEmpty()) {
            return;
        }
        for (PlacementService.RemovedDrop removed : drops) {
            if (removed.dropStack() != null) {
                var pos = removed.item().pose().exactPosition();
                Location dropLoc = new Location(block.getWorld(), pos.x(), pos.y(), pos.z());
                block.getWorld().dropItemNaturally(dropLoc, removed.dropStack());
            }
        }
    }
}
