package dev.decorate.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import dev.decorate.api.spi.StorageProvider;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Embedded SQLite {@link StorageProvider} backed by a {@link HikariDataSource} pool over
 * {@link SQLiteDataSource} in WAL mode and a dedicated single-thread executor for safe
 * asynchronous chunk-indexed persistence.
 */
public final class SqliteStorageProvider implements StorageProvider {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS placed_items (
                id TEXT PRIMARY KEY,
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                support_x INTEGER NOT NULL,
                support_y INTEGER NOT NULL,
                support_z INTEGER NOT NULL,
                pos_x REAL NOT NULL,
                pos_y REAL NOT NULL,
                pos_z REAL NOT NULL,
                face TEXT NOT NULL,
                yaw REAL NOT NULL,
                pose_mode TEXT NOT NULL,
                scale_x REAL NOT NULL,
                scale_y REAL NOT NULL,
                scale_z REAL NOT NULL,
                display_transform TEXT NOT NULL,
                hitbox_width REAL NOT NULL,
                hitbox_height REAL NOT NULL,
                owner_uuid TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                creative_placed INTEGER NOT NULL,
                item_bytes BLOB NOT NULL,
                profile_id TEXT NOT NULL,
                item_display_key TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """;

    private final Path databasePath;
    private final ExecutorService executor;
    private volatile HikariDataSource dataSource;

    public SqliteStorageProvider(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath must not be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "DecorAte-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            HikariDataSource ds = null;
            try {
                Path parent = databasePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
                sqliteDataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());
                sqliteDataSource.setJournalMode("WAL");
                sqliteDataSource.setSynchronous("NORMAL");
                sqliteDataSource.setBusyTimeout(5000);

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setPoolName("DecorAte-SQLite");
                hikariConfig.setDataSource(sqliteDataSource);
                hikariConfig.setMaximumPoolSize(1);
                hikariConfig.setConnectionTimeout(10_000L);

                ds = new HikariDataSource(hikariConfig);
                try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                    stmt.execute("PRAGMA busy_timeout=5000;");
                    stmt.execute(CREATE_TABLE_SQL);
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_placed_chunk ON placed_items (world, chunk_x, chunk_z);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_placed_owner ON placed_items (owner_uuid);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_placed_support ON placed_items (world, support_x, support_y, support_z);");
                }
                this.dataSource = ds;
            } catch (IOException | SQLException ex) {
                if (ds != null) {
                    ds.close();
                }
                throw new CompletionException("Failed to initialize SQLite storage", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            HikariDataSource ds = this.dataSource;
            this.dataSource = null;
            if (ds != null) {
                ds.close();
            }
        }, executor).whenComplete((ignored, err) -> {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public CompletableFuture<List<PlacedItem>> loadChunk(ChunkCoordinate chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM placed_items WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chunk.world());
                ps.setInt(2, chunk.x());
                ps.setInt(3, chunk.z());
                try (ResultSet rs = ps.executeQuery()) {
                    List<PlacedItem> items = new ArrayList<>();
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                    return List.copyOf(items);
                }
            } catch (SQLException ex) {
                throw new CompletionException("Failed to load chunk " + chunk, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(PlacedItem item) {
        Objects.requireNonNull(item, "item must not be null");
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO placed_items (
                        id, world, chunk_x, chunk_z,
                        support_x, support_y, support_z,
                        pos_x, pos_y, pos_z,
                        face, yaw, pose_mode,
                        scale_x, scale_y, scale_z,
                        display_transform, hitbox_width, hitbox_height,
                        owner_uuid, owner_name, creative_placed,
                        item_bytes, profile_id, item_display_key, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        pos_x = excluded.pos_x,
                        pos_y = excluded.pos_y,
                        pos_z = excluded.pos_z,
                        face = excluded.face,
                        yaw = excluded.yaw,
                        pose_mode = excluded.pose_mode,
                        scale_x = excluded.scale_x,
                        scale_y = excluded.scale_y,
                        scale_z = excluded.scale_z,
                        display_transform = excluded.display_transform,
                        hitbox_width = excluded.hitbox_width,
                        hitbox_height = excluded.hitbox_height,
                        item_bytes = excluded.item_bytes,
                        profile_id = excluded.profile_id,
                        item_display_key = excluded.item_display_key
                    """;
            ChunkCoordinate chunk = item.chunk();
            SurfacePose pose = item.pose();
            BlockCoordinate support = item.supportBlock();
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, item.id().toString());
                ps.setString(2, item.world());
                ps.setInt(3, chunk.x());
                ps.setInt(4, chunk.z());
                ps.setInt(5, support.x());
                ps.setInt(6, support.y());
                ps.setInt(7, support.z());
                ps.setDouble(8, pose.exactPosition().x());
                ps.setDouble(9, pose.exactPosition().y());
                ps.setDouble(10, pose.exactPosition().z());
                ps.setString(11, pose.face().name());
                ps.setFloat(12, pose.yawDegrees());
                ps.setString(13, pose.poseMode().name());
                ps.setFloat(14, pose.scale().x());
                ps.setFloat(15, pose.scale().y());
                ps.setFloat(16, pose.scale().z());
                ps.setString(17, pose.displayTransform().name());
                ps.setFloat(18, pose.hitboxWidth());
                ps.setFloat(19, pose.hitboxHeight());
                ps.setString(20, item.ownerUuid().toString());
                ps.setString(21, item.ownerName());
                ps.setInt(22, item.creativePlaced() ? 1 : 0);
                ps.setBytes(23, item.serializedItem());
                ps.setString(24, item.resolvedProfileId());
                ps.setString(25, item.itemDisplayKey());
                ps.setLong(26, item.createdAtMillis());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new CompletionException("Failed to save placed item " + item.id(), ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM placed_items WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new CompletionException("Failed to delete placed item " + id, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBySupportBlock(BlockCoordinate supportBlock) {
        Objects.requireNonNull(supportBlock, "supportBlock must not be null");
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM placed_items WHERE world = ? AND support_x = ? AND support_y = ? AND support_z = ?";
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, supportBlock.world());
                ps.setInt(2, supportBlock.x());
                ps.setInt(3, supportBlock.y());
                ps.setInt(4, supportBlock.z());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new CompletionException("Failed to delete placed items for block " + supportBlock, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteByChunk(ChunkCoordinate chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM placed_items WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, chunk.world());
                ps.setInt(2, chunk.x());
                ps.setInt(3, chunk.z());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new CompletionException("Failed to delete placed items for chunk " + chunk, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countByOwner(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM placed_items WHERE owner_uuid = ?")) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                throw new CompletionException("Failed to count items for owner " + ownerUuid, ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countByOwnerInWorld(UUID ownerUuid, String world) {
        Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
        Objects.requireNonNull(world, "world must not be null");
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM placed_items WHERE owner_uuid = ? AND world = ?";
            try (Connection conn = requireDataSource().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, world);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                throw new CompletionException("Failed to count items for owner " + ownerUuid + " in world " + world, ex);
            }
        }, executor);
    }

    private HikariDataSource requireDataSource() {
        HikariDataSource ds = this.dataSource;
        if (ds == null || ds.isClosed()) {
            throw new IllegalStateException("SqliteStorageProvider is not initialized or already closed");
        }
        return ds;
    }

    private static PlacedItem mapRow(ResultSet rs) throws SQLException {
        String world = rs.getString("world");
        BlockCoordinate support = new BlockCoordinate(
                world,
                rs.getInt("support_x"),
                rs.getInt("support_y"),
                rs.getInt("support_z")
        );
        SurfacePose pose = new SurfacePose(
                new Vec3d(rs.getDouble("pos_x"), rs.getDouble("pos_y"), rs.getDouble("pos_z")),
                SurfaceFace.valueOf(rs.getString("face")),
                rs.getFloat("yaw"),
                PoseMode.valueOf(rs.getString("pose_mode")),
                new Vec3f(rs.getFloat("scale_x"), rs.getFloat("scale_y"), rs.getFloat("scale_z")),
                DisplayTransformType.valueOf(rs.getString("display_transform")),
                rs.getFloat("hitbox_width"),
                rs.getFloat("hitbox_height")
        );
        return new PlacedItem(
                UUID.fromString(rs.getString("id")),
                world,
                support,
                pose,
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getInt("creative_placed") != 0,
                rs.getBytes("item_bytes"),
                rs.getString("profile_id"),
                rs.getString("item_display_key"),
                rs.getLong("created_at")
        );
    }
}
