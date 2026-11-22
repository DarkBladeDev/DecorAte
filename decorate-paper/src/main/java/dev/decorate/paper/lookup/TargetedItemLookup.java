package dev.decorate.paper.lookup;

import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;
import dev.decorate.core.geometry.PlacementGeometry;
import dev.decorate.core.registry.SpatialRegistry;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the {@link PlacedItem} a player is currently looking at via ray-AABB intersection
 * against the aligned client-side {@code Interaction} hitbox bounds.
 */
public final class TargetedItemLookup {

    private final SpatialRegistry registry;
    private final double maxDistance;

    public TargetedItemLookup(SpatialRegistry registry, double maxDistance) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.maxDistance = maxDistance;
    }

    /**
     * Finds the closest placed item intersecting the player's eye ray within {@code maxDistance}.
     *
     * @param player observing player
     * @return targeted placed item if any
     */
    public Optional<PlacedItem> findTargetedItem(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vec3d origin = new Vec3d(eye.getX(), eye.getY(), eye.getZ());
        Vec3d rayDir = new Vec3d(dir.getX(), dir.getY(), dir.getZ());

        double effectiveMaxDistance = resolveEffectiveMaxDistance(player, eye, dir);
        List<PlacedItem> candidates = registry.findInRadius(
                player.getWorld().getName(),
                origin.add(
                        rayDir.x() * (effectiveMaxDistance * 0.5),
                        rayDir.y() * (effectiveMaxDistance * 0.5),
                        rayDir.z() * (effectiveMaxDistance * 0.5)
                ),
                effectiveMaxDistance + 1.5
        );

        PlacedItem closest = null;
        double closestT = effectiveMaxDistance;

        for (PlacedItem item : candidates) {
            Vec3d anchor = PlacementGeometry.computeInteractionAnchor(item.pose());
            double halfW = item.pose().hitboxWidth() * 0.5;
            double minX = anchor.x() - halfW;
            double maxX = anchor.x() + halfW;
            double minY = anchor.y();
            double maxY = anchor.y() + item.pose().hitboxHeight();
            double minZ = anchor.z() - halfW;
            double maxZ = anchor.z() + halfW;

            double hitT = intersectRayAabb(origin, rayDir, minX, minY, minZ, maxX, maxY, maxZ);
            if (hitT >= 0.0 && hitT <= closestT) {
                closestT = hitT;
                closest = item;
            }
        }
        return Optional.ofNullable(closest);
    }

    private double resolveEffectiveMaxDistance(Player player, Location eye, Vector dir) {
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                eye,
                dir,
                maxDistance,
                FluidCollisionMode.NEVER,
                true
        );
        if (blockHit == null) {
            return maxDistance;
        }
        double blockDistance = blockHit.getHitPosition().distance(eye.toVector());
        return Math.min(maxDistance, blockDistance + 1e-4);
    }

    static double intersectRayAabb(
            Vec3d origin,
            Vec3d dir,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        double tMin = 0.0;
        double tMax = Double.POSITIVE_INFINITY;

        double[] o = {origin.x(), origin.y(), origin.z()};
        double[] d = {dir.x(), dir.y(), dir.z()};
        double[] bMin = {minX, minY, minZ};
        double[] bMax = {maxX, maxY, maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (o[i] < bMin[i] || o[i] > bMax[i]) {
                    return -1.0;
                }
            } else {
                double invD = 1.0 / d[i];
                double t1 = (bMin[i] - o[i]) * invD;
                double t2 = (bMax[i] - o[i]) * invD;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return -1.0;
                }
            }
        }
        return tMin;
    }
}
