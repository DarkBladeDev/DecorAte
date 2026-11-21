package dev.decorate.paper.listener;

import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.service.PlacementService;
import dev.decorate.core.storage.ChunkVisibilityManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Synchronizes chunk loading/unloading and player movement/session lifecycle
 * with {@link ChunkVisibilityManager} and cached owner counts.
 */
public final class VisibilityLifecycleListener implements Listener {

    private final ChunkVisibilityManager visibilityManager;
    private final StorageProvider storage;
    private final PlacementService placementService;
    private final Consumer<Runnable> mainThreadRunner;

    public VisibilityLifecycleListener(
            ChunkVisibilityManager visibilityManager,
            StorageProvider storage,
            PlacementService placementService,
            Consumer<Runnable> mainThreadRunner
    ) {
        this.visibilityManager = Objects.requireNonNull(visibilityManager, "visibilityManager must not be null");
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.placementService = Objects.requireNonNull(placementService, "placementService must not be null");
        this.mainThreadRunner = Objects.requireNonNull(mainThreadRunner, "mainThreadRunner must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        visibilityManager.ensureChunkLoaded(
                new ChunkCoordinate(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()),
                mainThreadRunner
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        visibilityManager.handleChunkUnload(
                new ChunkCoordinate(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        storage.countByOwner(player.getUniqueId()).whenComplete((count, err) -> {
            if (err == null && count != null) {
                mainThreadRunner.accept(() -> placementService.setCachedOwnerCount(player.getUniqueId(), count));
            }
        });
        updateVisibility(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        visibilityManager.clearPlayer(player);
        placementService.clearPlayerSession(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        visibilityManager.clearPlayer(player);
        updateVisibility(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        int fromChunkX = Math.floorDiv(event.getFrom().getBlockX(), 16);
        int fromChunkZ = Math.floorDiv(event.getFrom().getBlockZ(), 16);
        int toChunkX = Math.floorDiv(event.getTo().getBlockX(), 16);
        int toChunkZ = Math.floorDiv(event.getTo().getBlockZ(), 16);
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) {
            return;
        }
        updateVisibility(event.getPlayer());
    }

    private void updateVisibility(Player player) {
        var loc = player.getLocation();
        ChunkCoordinate chunk = ChunkCoordinate.fromPosition(player.getWorld().getName(), loc.getX(), loc.getZ());
        visibilityManager.updatePlayerVisibility(player, chunk, mainThreadRunner);
    }
}
