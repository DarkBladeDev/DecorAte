package dev.decorate.core.storage;

import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.registry.SpatialRegistry;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Coordinates chunk loading/unloading between {@link StorageProvider}, {@link SpatialRegistry},
 * and per-player {@link DisplayRenderer} visibility within the configured chunk render radius.
 */
public final class ChunkVisibilityManager {

    private final SpatialRegistry registry;
    private final StorageProvider storage;
    private final DisplayRenderer renderer;
    private final Map<UUID, Set<ChunkCoordinate>> visibleChunksByPlayer = new ConcurrentHashMap<>();
    private final Set<ChunkCoordinate> loadingChunks = ConcurrentHashMap.newKeySet();
    private volatile int chunkRadius;

    public ChunkVisibilityManager(
            SpatialRegistry registry,
            StorageProvider storage,
            DisplayRenderer renderer,
            int chunkRadius
    ) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.chunkRadius = Math.max(1, chunkRadius);
    }

    public void setChunkRadius(int chunkRadius) {
        this.chunkRadius = Math.max(1, chunkRadius);
    }

    /**
     * Loads a chunk's items from storage into {@link SpatialRegistry} if not already loaded.
     *
     * @param chunk chunk coordinate
     * @param mainThreadRunner callback executing continuation on the server main thread
     */
    public void ensureChunkLoaded(ChunkCoordinate chunk, Consumer<Runnable> mainThreadRunner) {
        if (registry.isChunkLoaded(chunk) || !loadingChunks.add(chunk)) {
            return;
        }
        storage.loadChunk(chunk).whenComplete((items, err) -> {
            loadingChunks.remove(chunk);
            if (err != null || items == null) {
                return;
            }
            mainThreadRunner.accept(() -> {
                registry.populateChunk(chunk, items);
                for (PlacedItem item : items) {
                    renderer.broadcastSpawn(item);
                }
            });
        });
    }

    /**
     * Unloads a chunk from the in-memory registry when no players need it.
     *
     * @param chunk chunk coordinate
     */
    public void handleChunkUnload(ChunkCoordinate chunk) {
        List<PlacedItem> evicted = registry.unloadChunk(chunk);
        for (PlacedItem item : evicted) {
            renderer.broadcastDestroy(item.id());
        }
    }

    /**
     * Updates the set of chunks rendered for a player around their current chunk coordinate.
     *
     * @param player observing player
     * @param centerChunk player's current chunk
     * @param mainThreadRunner callback executing storage load callbacks on the main thread
     */
    public void updatePlayerVisibility(
            Player player,
            ChunkCoordinate centerChunk,
            Consumer<Runnable> mainThreadRunner
    ) {
        Set<ChunkCoordinate> desired = new HashSet<>();
        int r = chunkRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                desired.add(new ChunkCoordinate(centerChunk.world(), centerChunk.x() + dx, centerChunk.z() + dz));
            }
        }

        Set<ChunkCoordinate> current = visibleChunksByPlayer.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        for (ChunkCoordinate oldChunk : List.copyOf(current)) {
            if (!desired.contains(oldChunk)) {
                current.remove(oldChunk);
                for (PlacedItem item : registry.findByChunk(oldChunk)) {
                    renderer.destroyForPlayer(player, item.id());
                }
            }
        }

        for (ChunkCoordinate newChunk : desired) {
            if (current.add(newChunk)) {
                if (registry.isChunkLoaded(newChunk)) {
                    for (PlacedItem item : registry.findByChunk(newChunk)) {
                        renderer.spawnForPlayer(player, item);
                    }
                } else {
                    ensureChunkLoaded(newChunk, mainThreadRunner);
                }
            }
        }
    }

    /**
     * Clears all tracked visibility state for a player leaving a world or disconnecting.
     *
     * @param player player leaving
     */
    public void clearPlayer(Player player) {
        visibleChunksByPlayer.remove(player.getUniqueId());
        renderer.destroyAllForPlayer(player);
    }

    /**
     * Checks whether a player is currently tracking the chunk containing the given item.
     *
     * @param playerId player UUID
     * @param chunk item's chunk coordinate
     * @return true if within the player's tracked visible chunks
     */
    public boolean isChunkVisibleToPlayer(UUID playerId, ChunkCoordinate chunk) {
        Set<ChunkCoordinate> chunks = visibleChunksByPlayer.get(playerId);
        return chunks != null && chunks.contains(chunk);
    }
}
