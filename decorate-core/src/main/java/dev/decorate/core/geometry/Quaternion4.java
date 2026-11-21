package dev.decorate.core.geometry;

/**
 * Immutable quaternion representation (x, y, z, w) used for display rotation math independent of packet libraries.
 *
 * @param x X imaginary component
 * @param y Y imaginary component
 * @param z Z imaginary component
 * @param w W scalar component
 */
public record Quaternion4(float x, float y, float z, float w) {

    /** Identity quaternion (no rotation). */
    public static final Quaternion4 IDENTITY = new Quaternion4(0.0f, 0.0f, 0.0f, 1.0f);

    /**
     * Constructs a quaternion from an axis-angle rotation in degrees.
     *
     * @param axisX unit axis X
     * @param axisY unit axis Y
     * @param axisZ unit axis Z
     * @param degrees rotation angle in degrees
     * @return resulting unit quaternion
     */
    public static Quaternion4 fromAxisAngleDegrees(double axisX, double axisY, double axisZ, double degrees) {
        double halfRad = Math.toRadians(degrees) * 0.5;
        double sin = Math.sin(halfRad);
        double cos = Math.cos(halfRad);
        return new Quaternion4(
                (float) (axisX * sin),
                (float) (axisY * sin),
                (float) (axisZ * sin),
                (float) cos
        ).normalized();
    }

    /**
     * Multiplies {@code this * other} (applying {@code other} first, then {@code this} in world space).
     *
     * @param other right-hand quaternion
     * @return product quaternion
     */
    public Quaternion4 multiply(Quaternion4 other) {
        float nx = w * other.x + x * other.w + y * other.z - z * other.y;
        float ny = w * other.y - x * other.z + y * other.w + z * other.x;
        float nz = w * other.z + x * other.y - y * other.x + z * other.w;
        float nw = w * other.w - x * other.x - y * other.y - z * other.z;
        return new Quaternion4(nx, ny, nz, nw).normalized();
    }

    /**
     * Returns the normalized unit quaternion.
     *
     * @return unit quaternion
     */
    public Quaternion4 normalized() {
        double len = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
        if (len < 1e-8) {
            return IDENTITY;
        }
        float inv = (float) (1.0 / len);
        return new Quaternion4(x * inv, y * inv, z * inv, w * inv);
    }
}
