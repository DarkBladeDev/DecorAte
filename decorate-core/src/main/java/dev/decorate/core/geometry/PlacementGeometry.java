package dev.decorate.core.geometry;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;

import java.util.Objects;

/**
 * Pure placement geometry calculations for surface coordinates, sub-grid snapping,
 * display orientation quaternions, and interaction hitbox alignment.
 */
public final class PlacementGeometry {

    private PlacementGeometry() {
    }

    /**
     * Computes the exact world placement coordinates on the supporting block face, applying optional sub-grid snapping.
     *
     * @param supportBlock supporting block coordinate
     * @param face targeted block face
     * @param hitPoint raw raycast hit point
     * @param profile resolved placement profile
     * @param playerMode player's active placement mode
     * @return final display center coordinates
     */
    public static Vec3d computePlacementPosition(
            BlockCoordinate supportBlock,
            SurfaceFace face,
            Vec3d hitPoint,
            PlacementProfile profile,
            PlacementMode playerMode
    ) {
        Objects.requireNonNull(supportBlock, "supportBlock must not be null");
        Objects.requireNonNull(face, "face must not be null");
        Objects.requireNonNull(hitPoint, "hitPoint must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(playerMode, "playerMode must not be null");

        boolean useSnap = playerMode.mode() == PlacementMode.Mode.SNAP;
        int divisions = profile.snapDivisionsOverride() != null
                ? profile.snapDivisionsOverride()
                : playerMode.gridDivisions();
        double offset = profile.normalOffset();

        double x = switch (face) {
            case EAST -> supportBlock.x() + 1.0 + offset;
            case WEST -> supportBlock.x() - offset;
            default -> projectTangent(hitPoint.x(), supportBlock.x(), useSnap, divisions);
        };

        double y = switch (face) {
            case UP -> supportBlock.y() + 1.0 + offset;
            case DOWN -> supportBlock.y() - offset;
            default -> projectTangent(hitPoint.y(), supportBlock.y(), useSnap, divisions);
        };

        double z = switch (face) {
            case SOUTH -> supportBlock.z() + 1.0 + offset;
            case NORTH -> supportBlock.z() - offset;
            default -> projectTangent(hitPoint.z(), supportBlock.z(), useSnap, divisions);
        };

        return new Vec3d(x, y, z);
    }

    /**
     * Computes the initial {@link SurfacePose} for a newly placed item.
     *
     * @param supportBlock supporting block coordinate
     * @param face targeted surface face
     * @param hitPoint raycast hit position
     * @param playerYaw player yaw in degrees
     * @param profile resolved placement profile
     * @param playerMode player placement mode
     * @return computed surface pose
     */
    public static SurfacePose computeInitialPose(
            BlockCoordinate supportBlock,
            SurfaceFace face,
            Vec3d hitPoint,
            float playerYaw,
            PlacementProfile profile,
            PlacementMode playerMode
    ) {
        Vec3d position = computePlacementPosition(supportBlock, face, hitPoint, profile, playerMode);
        float baseYaw = face.isHorizontal() ? 0.0f : quantizeCardinalOrKeep(playerYaw + 180.0f);
        return new SurfacePose(
                position,
                face,
                baseYaw + profile.rotationOffsetDegrees(),
                profile.defaultPose(),
                profile.scale(),
                profile.displayTransform(),
                profile.hitboxWidth(),
                profile.hitboxHeight()
        );
    }

    /**
     * Computes the left-rotation quaternion for an {@code ItemDisplay} given its surface face, yaw, and pose mode.
     *
     * @param pose surface pose
     * @return unit quaternion
     */
    public static Quaternion4 computeDisplayRotation(SurfacePose pose) {
        SurfaceFace face = pose.face();
        float yaw = pose.yawDegrees();
        PoseMode mode = pose.poseMode();

        return switch (face) {
            case UP -> {
                Quaternion4 yawRot = Quaternion4.fromAxisAngleDegrees(0, 1, 0, -yaw);
                if (mode == PoseMode.FLAT) {
                    yield yawRot.multiply(Quaternion4.fromAxisAngleDegrees(1, 0, 0, -90.0));
                }
                yield yawRot;
            }
            case DOWN -> {
                Quaternion4 yawRot = Quaternion4.fromAxisAngleDegrees(0, 1, 0, -yaw);
                if (mode == PoseMode.FLAT) {
                    yield yawRot.multiply(Quaternion4.fromAxisAngleDegrees(1, 0, 0, 90.0));
                }
                yield yawRot.multiply(Quaternion4.fromAxisAngleDegrees(0, 0, 1, 180.0));
            }
            case SOUTH -> wallQuaternion(0.0, yaw, mode);
            case NORTH -> wallQuaternion(180.0, yaw, mode);
            case EAST -> wallQuaternion(90.0, yaw, mode);
            case WEST -> wallQuaternion(-90.0, yaw, mode);
        };
    }

    /**
     * Computes the bottom-center spawn coordinate for a client-side {@code Interaction} entity
     * so its hitbox encloses the visual {@code ItemDisplay} across floors, walls, and ceilings.
     *
     * @param pose surface pose
     * @return bottom-center world position for the interaction entity
     */
    public static Vec3d computeInteractionAnchor(SurfacePose pose) {
        Vec3d center = pose.exactPosition();
        SurfaceFace face = pose.face();
        double width = pose.hitboxWidth();
        double height = pose.hitboxHeight();

        return switch (face) {
            case UP -> new Vec3d(center.x(), center.y() - Math.min(0.04, height * 0.2), center.z());
            case DOWN -> new Vec3d(center.x(), center.y() - height + Math.min(0.04, height * 0.2), center.z());
            case NORTH, SOUTH, EAST, WEST -> new Vec3d(
                    center.x() + face.normalX() * (width * 0.25),
                    center.y() - (height * 0.5),
                    center.z() + face.normalZ() * (width * 0.25)
            );
        };
    }

    private static Quaternion4 wallQuaternion(double facingYawDeg, float localRollDeg, PoseMode mode) {
        Quaternion4 facing = Quaternion4.fromAxisAngleDegrees(0, 1, 0, facingYawDeg);
        Quaternion4 roll = Quaternion4.fromAxisAngleDegrees(0, 0, 1, localRollDeg);
        if (mode == PoseMode.UPRIGHT) {
            return facing.multiply(Quaternion4.fromAxisAngleDegrees(1, 0, 0, -35.0)).multiply(roll);
        }
        return facing.multiply(roll);
    }

    private static double projectTangent(double worldCoord, int blockCoord, boolean snap, int divisions) {
        double clampedLocal = Math.max(0.0, Math.min(1.0, worldCoord - blockCoord));
        if (!snap) {
            return blockCoord + clampedLocal;
        }
        double snappedLocal = Math.round(clampedLocal * divisions) / (double) divisions;
        return blockCoord + snappedLocal;
    }

    private static float quantizeCardinalOrKeep(float angle) {
        float normalized = angle % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }
}
