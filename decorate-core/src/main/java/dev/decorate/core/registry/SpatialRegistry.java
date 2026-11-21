package dev.decorate.core.registry;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory spatial index of loaded {@link PlacedItem} instances,
 * indexed by item ID, chunk, supporting block, and owner UUID.
 */
public final class SpatialRegistry {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, PlacedItem> byId = new ConcurrentHashMap<>();
    private final Map<ChunkCoordinate, Set<UUID>> byChunk = new ConcurrentHashMap<>();
    private final Map<BlockCoordinate, Set<UUID>> bySupportBlock = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> byOwner = new ConcurrentHashMap<>();
    private final Set<ChunkCoordinate> loadedChunks = ConcurrentHashMap.newKeySet();

    /**
     * Registers or replaces a placed item in all spatial indexes.
     *
     * @param item placed item to index
     */
    public void put(PlacedItem item) {
        lock.writeLock().lock();
        try {
            PlacedItem previous = byId.put(item.id(), item);
            if (previous != null) {
                unindexSecondary(previous);
            }
            indexSecondary(item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a placed item by ID if present.
     *
     * @param id item UUID
     * @return removed item if it existed
     */
    public Optional<PlacedItem> remove(UUID id) {
        lock.writeLock().lock();
        try {
            PlacedItem removed = byId.remove(id);
            if (removed != null) {
                unindexSecondary(removed);
            }
            return Optional.ofNullable(removed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds a loaded placed item by ID.
     *
     * @param id item UUID
     * @return placed item if loaded
     */
    public Optional<PlacedItem> findById(UUID id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all loaded placed items in the specified chunk.
     *
     * @param chunk chunk coordinate
     * @return snapshot list of items in that chunk
     */
    public List<PlacedItem> findByChunk(ChunkCoordinate chunk) {
        lock.readLock().lock();
        try {
            return collectItems(byChunk.get(chunk));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all loaded placed items supported by the given block.
     *
     * @param supportBlock supporting block coordinate
     * @return snapshot list of supported items
     */
    public List<PlacedItem> findBySupportBlock(BlockCoordinate supportBlock) {
        lock.readLock().lock();
        try {
            return collectItems(bySupportBlock.get(supportBlock));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all loaded placed items within spherical radius of a world point.
     *
     * @param world world name
     * @param center center position
     * @param radius radius in blocks
     * @return matching items
     */
    public List<PlacedItem> findInRadius(String world, Vec3d center, double radius) {
        double radiusSq = radius * radius;
        int minChunkX = Math.floorDiv((int) Math.floor(center.x() - radius), 16);
        int maxChunkX = Math.floorDiv((int) Math.floor(center.x() + radius), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(center.z() - radius), 16);
        int maxChunkZ = Math.floorDiv((int) Math.floor(center.z() + radius), 16);

        lock.readLock().lock();
        try {
            List<PlacedItem> matches = new ArrayList<>();
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    Set<UUID> ids = byChunk.get(new ChunkCoordinate(world, cx, cz));
                    if (ids == null) {
                        continue;
                    }
                    for (UUID id : ids) {
                        PlacedItem item = byId.get(id);
                        if (item != null && item.pose().exactPosition().distanceSquared(center) <= radiusSq) {
                            matches.add(item);
                        }
                    }
                }
            }
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int countBySupportBlock(BlockCoordinate supportBlock) {
        lock.readLock().lock();
        try {
            Set<UUID> ids = bySupportBlock.get(supportBlock);
            return ids == null ? 0 : ids.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int countByChunk(ChunkCoordinate chunk) {
        lock.readLock().lock();
        try {
            Set<UUID> ids = byChunk.get(chunk);
            return ids == null ? 0 : ids.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int countLoadedByOwner(UUID ownerUuid) {
        lock.readLock().lock();
        try {
            Set<UUID> ids = byOwner.get(ownerUuid);
            return ids == null ? 0 : ids.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int countLoadedByOwnerInWorld(UUID ownerUuid, String world) {
        lock.readLock().lock();
        try {
            Set<UUID> ids = byOwner.get(ownerUuid);
            if (ids == null) {
                return 0;
            }
            int count = 0;
            for (UUID id : ids) {
                PlacedItem item = byId.get(id);
                if (item != null && item.world().equals(world)) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Marks a chunk as loaded and populates its items atomically.
     *
     * @param chunk chunk coordinate
     * @param items loaded items from storage
     */
    public void populateChunk(ChunkCoordinate chunk, Collection<PlacedItem> items) {
        lock.writeLock().lock();
        try {
            loadedChunks.add(chunk);
            for (PlacedItem item : items) {
                PlacedItem previous = byId.put(item.id(), item);
                if (previous != null) {
                    unindexSecondary(previous);
                }
                indexSecondary(item);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Unloads a chunk's items from the in-memory index and returns the removed items.
     *
     * @param chunk chunk coordinate
     * @return items evicted from memory
     */
    public List<PlacedItem> unloadChunk(ChunkCoordinate chunk) {
        lock.writeLock().lock();
        try {
            loadedChunks.remove(chunk);
            Set<UUID> ids = byChunk.remove(chunk);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<PlacedItem> evicted = new ArrayList<>(ids.size());
            for (UUID id : List.copyOf(ids)) {
                PlacedItem item = byId.remove(id);
                if (item != null) {
                    removeFromBucket(bySupportBlock, item.supportBlock(), item.id());
                    removeFromBucket(byOwner, item.ownerUuid(), item.id());
                    evicted.add(item);
                }
            }
            return evicted;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isChunkLoaded(ChunkCoordinate chunk) {
        return loadedChunks.contains(chunk);
    }

    public Set<ChunkCoordinate> loadedChunks() {
        return Collections.unmodifiableSet(loadedChunks);
    }

    public Collection<PlacedItem> allLoadedItems() {
        lock.readLock().lock();
        try {
            return List.copyOf(byId.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<PlacedItem> collectItems(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<PlacedItem> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            PlacedItem item = byId.get(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private void indexSecondary(PlacedItem item) {
        byChunk.computeIfAbsent(item.chunk(), k -> new HashSet<>()).add(item.id());
        bySupportBlock.computeIfAbsent(item.supportBlock(), k -> new HashSet<>()).add(item.id());
        byOwner.computeIfAbsent(item.ownerUuid(), k -> new HashSet<>()).add(item.id());
    }

    private void unindexSecondary(PlacedItem item) {
        removeFromBucket(byChunk, item.chunk(), item.id());
        removeFromBucket(bySupportBlock, item.supportBlock(), item.id());
        removeFromBucket(byOwner, item.ownerUuid(), item.id());
    }

    private static <K> void removeFromBucket(Map<K, Set<UUID>> map, K key, UUID id) {
        Set<UUID> bucket = map.get(key);
        if (bucket != null) {
            bucket.remove(id);
            if (bucket.isEmpty()) {
                map.remove(key);
            }
        }
    }
}
