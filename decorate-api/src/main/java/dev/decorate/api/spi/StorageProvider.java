package dev.decorate.api.spi;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.PlacedItem;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous persistence provider contract for placed items.
 * <p>
 * Implementations must be thread-safe and execute blocking I/O off the main server thread.
 * Custom storage backends (such as MySQL or YAML) can implement this interface and register via API.
 */
public interface StorageProvider {

    /**
     * Initializes the storage backend (e.g., creating schema or connection pools).
     *
     * @return completion future
     */
    CompletableFuture<Void> init();

    /**
     * Flushes pending writes and releases backend resources cleanly.
     *
     * @return completion future
     */
    CompletableFuture<Void> close();

    /**
     * Loads all placed items belonging to the specified chunk.
     *
     * @param chunk chunk coordinate
     * @return future resolving to items in that chunk
     */
    CompletableFuture<List<PlacedItem>> loadChunk(ChunkCoordinate chunk);

    /**
     * Inserts or updates a placed item record.
     *
     * @param item placed item to persist
     * @return completion future
     */
    CompletableFuture<Void> save(PlacedItem item);

    /**
     * Deletes a placed item by unique identifier.
     *
     * @param id placed item UUID
     * @return completion future
     */
    CompletableFuture<Void> delete(UUID id);

    /**
     * Deletes all placed items attached to the specified supporting block.
     *
     * @param supportBlock supporting block coordinate
     * @return completion future
     */
    CompletableFuture<Void> deleteBySupportBlock(BlockCoordinate supportBlock);

    /**
     * Deletes all placed items within the given chunk.
     *
     * @param chunk chunk coordinate
     * @return completion future
     */
    CompletableFuture<Void> deleteByChunk(ChunkCoordinate chunk);

    /**
     * Counts all persisted items owned by the given player across all worlds.
     *
     * @param ownerUuid player UUID
     * @return total persisted count
     */
    CompletableFuture<Integer> countByOwner(UUID ownerUuid);

    /**
     * Counts all persisted items owned by the given player in a specific world.
     *
     * @param ownerUuid player UUID
     * @param world world name
     * @return world persisted count
     */
    CompletableFuture<Integer> countByOwnerInWorld(UUID ownerUuid, String world);
}
