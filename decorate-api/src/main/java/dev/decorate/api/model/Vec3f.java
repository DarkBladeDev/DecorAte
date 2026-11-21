package dev.decorate.api.model;

/**
 * Immutable 3D single-precision vector, typically used for item display scale or translation.
 *
 * @param x X component
 * @param y Y component
 * @param z Z component
 */
public record Vec3f(float x, float y, float z) {

    /** Unit scale (1, 1, 1). */
    public static final Vec3f ONE = new Vec3f(1.0f, 1.0f, 1.0f);

    public Vec3f {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Vec3f components must be finite");
        }
    }
}
