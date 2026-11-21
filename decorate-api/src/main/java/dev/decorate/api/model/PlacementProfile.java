package dev.decorate.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolved placement rules and display defaults for an item or custom model.
 *
 * @param id unique profile identifier
 * @param priority matching priority (higher wins)
 * @param placeable whether items matching this profile may be placed
 * @param allowedFaces set of allowed surface faces
 * @param displayTransform default item display transform
 * @param scale default display scale
 * @param normalOffset offset along the surface normal from the block face
 * @param rotationOffsetDegrees additional initial yaw offset in degrees
 * @param defaultPose default orientation pose
 * @param hitboxWidth interaction hitbox width
 * @param hitboxHeight interaction hitbox height
 * @param snapDivisionsOverride optional profile-level snap grid override
 * @param itemTemplate optional template enabling {@code /decorate give} synthesis
 */
public record PlacementProfile(
        String id,
        int priority,
        boolean placeable,
        Set<SurfaceFace> allowedFaces,
        DisplayTransformType displayTransform,
        Vec3f scale,
        double normalOffset,
        float rotationOffsetDegrees,
        PoseMode defaultPose,
        float hitboxWidth,
        float hitboxHeight,
        Integer snapDivisionsOverride,
        ItemTemplate itemTemplate
) {

    /** Global fallback profile when no specific profile matches. */
    public static final PlacementProfile DEFAULT = new PlacementProfile(
            "default",
            Integer.MIN_VALUE,
            true,
            EnumSet.allOf(SurfaceFace.class),
            DisplayTransformType.FIXED,
            new Vec3f(0.5f, 0.5f, 0.5f),
            0.02,
            0.0f,
            PoseMode.FLAT,
            0.35f,
            0.35f,
            null,
            null
    );

    public PlacementProfile {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        allowedFaces = allowedFaces == null || allowedFaces.isEmpty()
                ? Set.of()
                : Set.copyOf(allowedFaces);
        Objects.requireNonNull(displayTransform, "displayTransform must not be null");
        Objects.requireNonNull(scale, "scale must not be null");
        Objects.requireNonNull(defaultPose, "defaultPose must not be null");
        if (hitboxWidth <= 0 || hitboxHeight <= 0) {
            throw new IllegalArgumentException("Hitbox dimensions must be positive");
        }
        if (snapDivisionsOverride != null && (snapDivisionsOverride < 1 || snapDivisionsOverride > 64)) {
            throw new IllegalArgumentException("snapDivisionsOverride must be between 1 and 64");
        }
    }

    /**
     * Returns the optional item template when this profile can be spawned via {@code /decorate give}.
     *
     * @return optional item template
     */
    public Optional<ItemTemplate> optionalItemTemplate() {
        return Optional.ofNullable(itemTemplate);
    }

    /**
     * Checks whether the given surface face is permitted by this profile.
     *
     * @param face candidate face
     * @return true if allowed
     */
    public boolean allowsFace(SurfaceFace face) {
        return allowedFaces.contains(face);
    }
}
