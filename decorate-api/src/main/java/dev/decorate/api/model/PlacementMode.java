package dev.decorate.api.model;

/**
 * Per-player placement positioning mode and optional sub-grid snap divisions.
 *
 * @param mode free continuous vs quantized sub-grid snap
 * @param gridDivisions number of divisions per block when {@link Mode#SNAP} is active (e.g. 8 or 16)
 */
public record PlacementMode(Mode mode, int gridDivisions) {

    /** Default continuous placement mode with 1/16th fallback snap resolution. */
    public static final PlacementMode DEFAULT_FREE = new PlacementMode(Mode.FREE, 16);

    public PlacementMode {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (gridDivisions < 1 || gridDivisions > 64) {
            throw new IllegalArgumentException("gridDivisions must be between 1 and 64");
        }
    }

    /**
     * Placement positioning strategy.
     */
    public enum Mode {
        FREE,
        SNAP
    }
}
