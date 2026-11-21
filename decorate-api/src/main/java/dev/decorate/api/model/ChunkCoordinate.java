package dev.decorate.api.model;

import java.util.Objects;

/**
 * Immutable world-scoped chunk coordinates.
 *
 * @param world world name
 * @param x chunk X coordinate
 * @param z chunk Z coordinate
 */
public record ChunkCoordinate(String world, int x, int z) {

    public ChunkCoordinate {
        Objects.requireNonNull(world, "world must not be null");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }

    /**
     * Derives the chunk coordinate containing the given block coordinates.
     *
     * @param world world name
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return containing chunk coordinate
     */
    public static ChunkCoordinate fromBlock(String world, int blockX, int blockZ) {
        return new ChunkCoordinate(world, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    /**
     * Derives the chunk coordinate containing the given world position.
     *
     * @param world world name
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return containing chunk coordinate
     */
    public static ChunkCoordinate fromPosition(String world, double x, double z) {
        return fromBlock(world, (int) Math.floor(x), (int) Math.floor(z));
    }
}
