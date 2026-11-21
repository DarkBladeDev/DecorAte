package dev.decorate.api.model;

import org.bukkit.block.BlockFace;

/**
 * Cardinal surface face on which an item can be placed.
 */
public enum SurfaceFace {
    UP(0, 1, 0, BlockFace.UP),
    DOWN(0, -1, 0, BlockFace.DOWN),
    NORTH(0, 0, -1, BlockFace.NORTH),
    SOUTH(0, 0, 1, BlockFace.SOUTH),
    EAST(1, 0, 0, BlockFace.EAST),
    WEST(-1, 0, 0, BlockFace.WEST);

    private final int normalX;
    private final int normalY;
    private final int normalZ;
    private final BlockFace bukkitFace;

    SurfaceFace(int normalX, int normalY, int normalZ, BlockFace bukkitFace) {
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.bukkitFace = bukkitFace;
    }

    public int normalX() {
        return normalX;
    }

    public int normalY() {
        return normalY;
    }

    public int normalZ() {
        return normalZ;
    }

    public BlockFace toBlockFace() {
        return bukkitFace;
    }

    public boolean isHorizontal() {
        return normalY == 0;
    }

    /**
     * Converts a cardinal Bukkit {@link BlockFace} into a {@link SurfaceFace}.
     *
     * @param face Bukkit block face
     * @return matching surface face
     * @throws IllegalArgumentException if the face is not one of the 6 cardinal block faces
     */
    public static SurfaceFace fromBlockFace(BlockFace face) {
        return switch (face) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Unsupported non-cardinal BlockFace: " + face);
        };
    }
}
