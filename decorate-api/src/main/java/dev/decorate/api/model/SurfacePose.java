package dev.decorate.api.model;

import java.util.Objects;

/**
 * Complete surface placement pose for an item display.
 *
 * @param exactPosition world coordinates of the visual display center
 * @param face supporting surface face
 * @param yawDegrees rotation angle in degrees around the surface normal (or vertical axis)
 * @param poseMode flat vs upright orientation mode
 * @param scale 3D display scale
 * @param displayTransform item display transform context
 * @param hitboxWidth width of the client-side interaction hitbox
 * @param hitboxHeight height of the client-side interaction hitbox
 */
public record SurfacePose(
        Vec3d exactPosition,
        SurfaceFace face,
        float yawDegrees,
        PoseMode poseMode,
        Vec3f scale,
        DisplayTransformType displayTransform,
        float hitboxWidth,
        float hitboxHeight
) {

    public SurfacePose {
        Objects.requireNonNull(exactPosition, "exactPosition must not be null");
        Objects.requireNonNull(face, "face must not be null");
        Objects.requireNonNull(poseMode, "poseMode must not be null");
        Objects.requireNonNull(scale, "scale must not be null");
        Objects.requireNonNull(displayTransform, "displayTransform must not be null");
        if (hitboxWidth <= 0 || hitboxHeight <= 0) {
            throw new IllegalArgumentException("Hitbox dimensions must be positive");
        }
        yawDegrees = normalizeYaw(yawDegrees);
    }

    /**
     * Returns a copy rotated by the given step in degrees.
     *
     * @param deltaDegrees angle delta in degrees
     * @return rotated pose
     */
    public SurfacePose withRotatedYaw(float deltaDegrees) {
        return new SurfacePose(
                exactPosition,
                face,
                normalizeYaw(yawDegrees + deltaDegrees),
                poseMode,
                scale,
                displayTransform,
                hitboxWidth,
                hitboxHeight
        );
    }

    /**
     * Returns a copy with toggled {@link PoseMode}.
     *
     * @return pose with toggled mode
     */
    public SurfacePose withToggledPose() {
        return new SurfacePose(
                exactPosition,
                face,
                yawDegrees,
                poseMode.toggled(),
                scale,
                displayTransform,
                hitboxWidth,
                hitboxHeight
        );
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }
}
