package dev.decorate.core.storage;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteStorageProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndQueriesByChunkSupportBlockAndOwnerAcrossReopen() {
        Path dbFile = tempDir.resolve("decorate.db");
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        PlacedItem item1 = sampleItem(ownerA, "world", 5, 64, 5, 5.2, 65.02, 5.4);
        PlacedItem item2 = sampleItem(ownerA, "world", 5, 64, 5, 5.8, 65.02, 5.7);
        PlacedItem item3 = sampleItem(ownerB, "world_nether", 20, 70, 20, 20.5, 71.02, 20.5);

        SqliteStorageProvider storage = new SqliteStorageProvider(dbFile);
        CompletionException uninitEx = assertThrows(
                CompletionException.class,
                () -> storage.loadChunk(new ChunkCoordinate("world", 0, 0)).join()
        );
        assertInstanceOf(IllegalStateException.class, uninitEx.getCause());

        storage.init().join();
        storage.save(item1).join();
        storage.save(item2).join();
        storage.save(item3).join();

        assertEquals(2, storage.countByOwner(ownerA).join());
        assertEquals(2, storage.countByOwnerInWorld(ownerA, "world").join());
        assertEquals(0, storage.countByOwnerInWorld(ownerA, "world_nether").join());
        storage.close().join();

        // Reopen database to verify restart durability
        SqliteStorageProvider reopened = new SqliteStorageProvider(dbFile);
        reopened.init().join();
        List<PlacedItem> chunkItems = reopened.loadChunk(new ChunkCoordinate("world", 0, 0)).join();
        assertEquals(2, chunkItems.size());
        assertArrayEquals(new byte[] {10, 20, 30}, chunkItems.getFirst().serializedItem());

        reopened.delete(item1.id()).join();
        assertEquals(1, reopened.loadChunk(new ChunkCoordinate("world", 0, 0)).join().size());

        reopened.deleteBySupportBlock(new BlockCoordinate("world", 5, 64, 5)).join();
        assertEquals(0, reopened.loadChunk(new ChunkCoordinate("world", 0, 0)).join().size());

        reopened.deleteByChunk(new ChunkCoordinate("world_nether", 1, 1)).join();
        assertEquals(0, reopened.countByOwner(ownerB).join());
        reopened.close().join();

        assertThrows(
                RejectedExecutionException.class,
                () -> reopened.loadChunk(new ChunkCoordinate("world", 0, 0))
        );
    }

    private static PlacedItem sampleItem(UUID owner, String world, int bx, int by, int bz, double x, double y, double z) {
        return new PlacedItem(
                UUID.randomUUID(),
                world,
                new BlockCoordinate(world, bx, by, bz),
                new SurfacePose(
                        new Vec3d(x, y, z),
                        SurfaceFace.UP,
                        45.0f,
                        PoseMode.FLAT,
                        Vec3f.ONE,
                        DisplayTransformType.FIXED,
                        0.35f,
                        0.35f
                ),
                owner,
                "Player",
                false,
                new byte[] {10, 20, 30},
                "default",
                "decor:mug",
                System.currentTimeMillis()
        );
    }
}
