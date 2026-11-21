package dev.decorate.api.model;

/**
 * Immutable 3D double-precision vector.
 *
 * @param x X component
 * @param y Y component
 * @param z Z component
 */
public record Vec3d(double x, double y, double z) {

    /**
     * Adds an offset along each axis.
     *
     * @param dx delta X
     * @param dy delta Y
     * @param dz delta Z
     * @return translated vector
     */
    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    /**
     * Computes the squared Euclidean distance to another vector.
     *
     * @param other target vector
     * @return squared distance
     */
    public double distanceSquared(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
