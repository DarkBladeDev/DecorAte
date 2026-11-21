package dev.decorate.api.model;

import java.util.Objects;

/**
 * Immutable block coordinate identifying a supporting block in a world.
 *
 * @param world world name
 * @param x block X coordinate
 * @param y block Y coordinate
 * @param z block Z coordinate
 */
public record BlockCoordinate(String world, int x, int y, int z) {

    public BlockCoordinate {
        Objects.requireNonNull(world, "world must not be null");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }

    /**
     * Returns the chunk containing this block.
     *
     * @return chunk coordinate
     */
    public ChunkCoordinate chunk() {
        return ChunkCoordinate.fromBlock(world, x, z);
    }
}
