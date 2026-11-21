package dev.decorate.core.geometry;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementGeometryTest {

    @Test
    void continuousFreeModePreservesExactTangentHitCoords() {
        BlockCoordinate block = new BlockCoordinate("world", 10, 64, 20);
        Vec3d hit = new Vec3d(10.37, 65.0, 20.81);

        Vec3d placed = PlacementGeometry.computePlacementPosition(
                block,
                SurfaceFace.UP,
                hit,
                PlacementProfile.DEFAULT,
                PlacementMode.DEFAULT_FREE
        );

        assertEquals(10.37, placed.x(), 1e-6);
        assertEquals(65.0 + PlacementProfile.DEFAULT.normalOffset(), placed.y(), 1e-6);
        assertEquals(20.81, placed.z(), 1e-6);
    }

    @Test
    void subGridSnapModeQuantizesTangentCoordsForAllFaces() {
        BlockCoordinate block = new BlockCoordinate("world", 0, 64, 0);
        PlacementMode snap8 = new PlacementMode(PlacementMode.Mode.SNAP, 8);

        Vec3d up = PlacementGeometry.computePlacementPosition(
                block, SurfaceFace.UP, new Vec3d(0.31, 65.0, 0.74), PlacementProfile.DEFAULT, snap8
        );
        assertEquals(0.25, up.x(), 1e-6);
        assertEquals(0.75, up.z(), 1e-6);

        Vec3d north = PlacementGeometry.computePlacementPosition(
                block, SurfaceFace.NORTH, new Vec3d(0.63, 64.49, 0.0), PlacementProfile.DEFAULT, snap8
        );
        assertEquals(0.625, north.x(), 1e-6);
        assertEquals(64.5, north.y(), 1e-6);
        assertEquals(-PlacementProfile.DEFAULT.normalOffset(), north.z(), 1e-6);

        Vec3d east = PlacementGeometry.computePlacementPosition(
                block, SurfaceFace.EAST, new Vec3d(1.0, 64.13, 0.88), PlacementProfile.DEFAULT, snap8
        );
        assertEquals(1.0 + PlacementProfile.DEFAULT.normalOffset(), east.x(), 1e-6);
        assertEquals(64.125, east.y(), 1e-6);
        assertEquals(0.875, east.z(), 1e-6);
    }

    @Test
    void displayRotationAlwaysProducesUnitQuaternion() {
        for (SurfaceFace face : SurfaceFace.values()) {
            for (PoseMode mode : PoseMode.values()) {
                SurfacePose pose = new SurfacePose(
                        new Vec3d(0.5, 65.0, 0.5),
                        face,
                        45.0f,
                        mode,
                        Vec3f.ONE,
                        DisplayTransformType.FIXED,
                        0.4f,
                        0.4f
                );
                Quaternion4 q = PlacementGeometry.computeDisplayRotation(pose);
                double norm = q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w();
                assertEquals(1.0, norm, 1e-4, "Quaternion must be unit length for " + face + "/" + mode);
            }
        }
    }

    @Test
    void interactionAnchorOffsetsWallsAndCeilingProperly() {
        SurfacePose ceiling = new SurfacePose(
                new Vec3d(0.5, 64.0 - 0.02, 0.5),
                SurfaceFace.DOWN,
                0.0f,
                PoseMode.FLAT,
                Vec3f.ONE,
                DisplayTransformType.FIXED,
                0.4f,
                0.4f
        );
        Vec3d ceilingAnchor = PlacementGeometry.computeInteractionAnchor(ceiling);
        assertTrue(ceilingAnchor.y() < ceiling.exactPosition().y(), "Ceiling anchor must drop below display center");

        SurfacePose wall = new SurfacePose(
                new Vec3d(1.02, 64.5, 0.5),
                SurfaceFace.EAST,
                0.0f,
                PoseMode.FLAT,
                Vec3f.ONE,
                DisplayTransformType.FIXED,
                0.4f,
                0.4f
        );
        Vec3d wallAnchor = PlacementGeometry.computeInteractionAnchor(wall);
        assertTrue(wallAnchor.x() > wall.exactPosition().x(), "East wall anchor must shift outward along +X");
        assertEquals(64.3, wallAnchor.y(), 1e-6);
    }
}
