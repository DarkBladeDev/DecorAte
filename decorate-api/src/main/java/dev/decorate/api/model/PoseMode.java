package dev.decorate.api.model;

/**
 * Orientation pose of a placed item relative to its supporting face.
 */
public enum PoseMode {
    /** Item lies flat against the supporting surface. */
    FLAT,
    /** Item stands upright facing outward or toward the player's yaw. */
    UPRIGHT;

    /**
     * Returns the opposite pose mode.
     *
     * @return toggled pose mode
     */
    public PoseMode toggled() {
        return this == FLAT ? UPRIGHT : FLAT;
    }
}
