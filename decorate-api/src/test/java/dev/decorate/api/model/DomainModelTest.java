package dev.decorate.api.model;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {

    @Test
    void chunkAndBlockCoordinatesHandleNegativeFloorDiv() {
        BlockCoordinate block = new BlockCoordinate("world", -1, 64, -17);
        assertEquals(new ChunkCoordinate("world", -1, -2), block.chunk());
        assertEquals(new ChunkCoordinate("world", -1, -2), ChunkCoordinate.fromPosition("world", -0.25, -16.1));
    }

    @Test
    void surfacePoseNormalizesYawAndTogglesPose() {
        SurfacePose pose = new SurfacePose(
                new Vec3d(10.5, 65.0, -3.2),
                SurfaceFace.UP,
                -45.0f,
                PoseMode.FLAT,
                Vec3f.ONE,
                DisplayTransformType.FIXED,
                0.4f,
                0.4f
        );

        assertEquals(315.0f, pose.yawDegrees(), 0.001f);
        assertEquals(0.0f, pose.withRotatedYaw(45.0f).yawDegrees(), 0.001f);
        assertEquals(PoseMode.UPRIGHT, pose.withToggledPose().poseMode());
    }

    @Test
    void placedItemDefensivelyCopiesSerializedBytes() {
        byte[] raw = new byte[] {1, 2, 3, 4};
        PlacedItem item = new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 0, 64, 0),
                new SurfacePose(
                        new Vec3d(0.5, 65.02, 0.5),
                        SurfaceFace.UP,
                        90.0f,
                        PoseMode.FLAT,
                        Vec3f.ONE,
                        DisplayTransformType.FIXED,
                        0.35f,
                        0.35f
                ),
                UUID.randomUUID(),
                "Steve",
                false,
                raw,
                "default",
                "minecraft:paper",
                1234L
        );

        raw[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3, 4}, item.serializedItem());
        assertNotSame(item.serializedItem(), item.serializedItem());
        assertEquals(new ChunkCoordinate("world", 0, 0), item.chunk());
    }

    @Test
    void surfaceFaceMapsCardinalBlockFacesOnly() {
        assertEquals(SurfaceFace.NORTH, SurfaceFace.fromBlockFace(BlockFace.NORTH));
        assertTrue(SurfaceFace.EAST.isHorizontal());
        assertThrows(IllegalArgumentException.class, () -> SurfaceFace.fromBlockFace(BlockFace.NORTH_EAST));
    }

    @Test
    void itemInspectionDescriptiveLabelPrefersItemModelThenCustomModelData() {
        ItemInspection withModel = new ItemInspection(
                Material.PAPER,
                "decor:mug",
                new CustomModelDataSpec(List.of(10f), List.of(), List.of(), List.of())
        );
        assertEquals("decor:mug", withModel.descriptiveLabel());

        ItemInspection withCmd = new ItemInspection(
                Material.PAPER,
                null,
                new CustomModelDataSpec(List.of(1001f), List.of(), List.of(), List.of())
        );
        assertTrue(withCmd.descriptiveLabel().contains("1001.0"));
    }
}
