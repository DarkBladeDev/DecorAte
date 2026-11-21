package dev.decorate.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacketEventsDisplayRendererTest {

    @BeforeAll
    static void initPacketEventsStub() {
        if (PacketEvents.getAPI() == null) {
            PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
            PacketEventsSettings settings = new PacketEventsSettings();
            ServerManager serverManager = mock(ServerManager.class);
            when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_21_8);
            when(api.getSettings()).thenReturn(settings);
            when(api.getServerManager()).thenReturn(serverManager);
            PacketEvents.setAPI(api);
        }
    }

    @Test
    void buildsExpectedMetadataIndicesForDisplayAndInteraction() {
        PacketMetadataBuilder builder = new PacketMetadataBuilder(bytes -> null);

        PlacedItem item = sampleItem(SurfaceFace.NORTH, PoseMode.FLAT);
        List<EntityData<?>> displayData = builder.buildItemDisplayMetadata(item, 0);
        assertEquals(10, displayData.size());
        assertEquals(new Vector3f(0.75f, 0.75f, 0.75f), displayData.stream()
                .filter(d -> d.getIndex() == PacketMetadataBuilder.DISPLAY_SCALE_INDEX)
                .findFirst().orElseThrow().getValue());
        Quaternion4f q = (Quaternion4f) displayData.stream()
                .filter(d -> d.getIndex() == PacketMetadataBuilder.DISPLAY_LEFT_ROTATION_INDEX)
                .findFirst().orElseThrow().getValue();
        double norm = q.getX() * q.getX() + q.getY() * q.getY() + q.getZ() * q.getZ() + q.getW() * q.getW();
        assertEquals(1.0, norm, 1e-4);
        assertEquals(DisplayTransformType.FIXED.protocolId(), displayData.stream()
                .filter(d -> d.getIndex() == PacketMetadataBuilder.ITEM_DISPLAY_TRANSFORM_INDEX)
                .findFirst().orElseThrow().getValue());

        List<EntityData<?>> interactionData = builder.buildInteractionMetadata(item.pose());
        assertEquals(3, interactionData.size());
        assertEquals(0.4f, interactionData.get(0).getValue());
        assertEquals(0.5f, interactionData.get(1).getValue());
        assertEquals(true, interactionData.get(2).getValue());
    }

    @Test
    void spawnsTracksResolvesAndDestroysVirtualEntities() {
        List<PacketWrapper<?>> sent = new ArrayList<>();
        AtomicInteger nextId = new AtomicInteger(1_000_000);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        PacketMetadataBuilder builder = new PacketMetadataBuilder(bytes -> null);
        PacketEventsDisplayRenderer renderer = new PacketEventsDisplayRenderer(
                (p, packet) -> sent.add(packet),
                builder,
                nextId::getAndIncrement,
                () -> List.of(player),
                (p, item) -> true
        );

        PlacedItem item = sampleItem(SurfaceFace.UP, PoseMode.FLAT);
        renderer.broadcastSpawn(item);

        assertEquals(4, sent.size());
        WrapperPlayServerSpawnEntity displaySpawn = assertInstanceOf(WrapperPlayServerSpawnEntity.class, sent.get(0));
        assertInstanceOf(WrapperPlayServerEntityMetadata.class, sent.get(1));
        WrapperPlayServerSpawnEntity interactionSpawn = assertInstanceOf(WrapperPlayServerSpawnEntity.class, sent.get(2));
        assertInstanceOf(WrapperPlayServerEntityMetadata.class, sent.get(3));

        assertEquals(EntityTypes.ITEM_DISPLAY, displaySpawn.getEntityType());
        assertEquals(EntityTypes.INTERACTION, interactionSpawn.getEntityType());
        assertEquals(item.id(), renderer.resolveItemByVirtualEntityId(interactionSpawn.getEntityId()).orElseThrow());

        renderer.broadcastDestroy(item.id());
        assertTrue(renderer.resolveItemByVirtualEntityId(interactionSpawn.getEntityId()).isEmpty());
        WrapperPlayServerDestroyEntities destroy = assertInstanceOf(WrapperPlayServerDestroyEntities.class, sent.get(4));
        assertEquals(2, destroy.getEntityIds().length);
    }

    private static PlacedItem sampleItem(SurfaceFace face, PoseMode poseMode) {
        return new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 0, 64, 0),
                new SurfacePose(
                        new Vec3d(0.5, 64.5, -0.02),
                        face,
                        45.0f,
                        poseMode,
                        new Vec3f(0.75f, 0.75f, 0.75f),
                        DisplayTransformType.FIXED,
                        0.4f,
                        0.5f
                ),
                UUID.randomUUID(),
                "Steve",
                false,
                new byte[] {1, 2, 3},
                "default",
                "decor:mug",
                1000L
        );
    }
}
