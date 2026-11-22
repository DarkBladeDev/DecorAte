package dev.decorate.paper.lookup;

import dev.decorate.api.event.DecorRemoveEvent;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.registry.SpatialRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoverDisplayNameControllerTest {

    @Test
    void extractsOnlyExplicitCustomNameAndIgnoresItemName() {
        ItemStack vanilla = mock(ItemStack.class);
        when(vanilla.hasItemMeta()).thenReturn(false);
        assertTrue(HoverDisplayNameController.extractCustomName(vanilla).isEmpty());

        ItemMeta itemNameMeta = mock(ItemMeta.class);
        when(itemNameMeta.hasDisplayName()).thenReturn(false);
        when(itemNameMeta.hasItemName()).thenReturn(true);
        when(itemNameMeta.itemName()).thenReturn(Component.text("Chair"));
        ItemStack itemNameOnly = mock(ItemStack.class);
        when(itemNameOnly.hasItemMeta()).thenReturn(true);
        when(itemNameOnly.getItemMeta()).thenReturn(itemNameMeta);
        assertTrue(HoverDisplayNameController.extractCustomName(itemNameOnly).isEmpty(),
                "Items with only ITEM_NAME must not produce a hover display name");

        ItemMeta customMeta = mock(ItemMeta.class);
        Component expected = Component.text("Golden Mug");
        when(customMeta.hasDisplayName()).thenReturn(true);
        when(customMeta.displayName()).thenReturn(expected);
        ItemStack customNamed = mock(ItemStack.class);
        when(customNamed.hasItemMeta()).thenReturn(true);
        when(customNamed.getItemMeta()).thenReturn(customMeta);
        assertEquals(Optional.of(expected), HoverDisplayNameController.extractCustomName(customNamed));
    }

    @Test
    void cachesCustomNameExtractionsAndEvictsOnRemoval() {
        SpatialRegistry registry = new SpatialRegistry();
        TargetedItemLookup lookup = new TargetedItemLookup(registry, 5.0);
        DisplayRenderer renderer = mock(DisplayRenderer.class);
        AtomicInteger extractCalls = new AtomicInteger();

        PlacedItem item = sampleItem(10.5, 65.02, 10.5);
        Component label = Component.text("VIP Trophy");

        HoverDisplayNameController controller = new HoverDisplayNameController(
                lookup,
                renderer,
                DecorateSettings.DEFAULTS,
                bytes -> {
                    extractCalls.incrementAndGet();
                    return Optional.of(label);
                }
        );

        assertEquals(Optional.of(label), controller.resolveCustomName(item));
        assertEquals(Optional.of(label), controller.resolveCustomName(item));
        assertEquals(1, extractCalls.get(), "Repeated lookups must use cached CUSTOM_NAME");

        controller.onDecorRemove(new DecorRemoveEvent(item, DecorRemoveEvent.Reason.PLAYER_PICKUP, null, false));
        assertEquals(Optional.of(label), controller.resolveCustomName(item));
        assertEquals(2, extractCalls.get(), "Eviction on DecorRemoveEvent must invalidate cached entry");
    }

    @Test
    void tickDisplaysHoverLabelOnlyWhenLookingAtCustomNamedVisibleItem() {
        SpatialRegistry registry = new SpatialRegistry();
        PlacedItem namedItem = sampleItem(10.5, 65.02, 10.5);
        registry.put(namedItem);

        TargetedItemLookup lookup = new TargetedItemLookup(registry, 5.0);
        DisplayRenderer renderer = mock(DisplayRenderer.class);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeLocation()).thenReturn(new Location(world, 10.5, 66.2, 8.5, 0.0f, 28.8f));
        when(renderer.hasVisibleItems(player)).thenReturn(true);

        Component label = Component.text("VIP Trophy");
        HoverDisplayNameController controller = new HoverDisplayNameController(
                lookup,
                renderer,
                DecorateSettings.DEFAULTS,
                bytes -> Optional.of(label)
        );

        controller.tick(List.of(player));
        verify(renderer, times(1)).showHoverText(
                eq(player),
                eq(namedItem.id()),
                eq(label),
                any(Vec3d.class),
                eq(DecorateSettings.DEFAULTS.hoverDisplayScale())
        );

        // Looking away clears the hover label
        when(player.getEyeLocation()).thenReturn(new Location(world, 10.5, 66.2, 8.5, 180.0f, 0.0f));
        controller.tick(List.of(player));
        verify(renderer, times(1)).clearHoverText(player);

        // Players with no visible items skip raycast and clear any label
        when(renderer.hasVisibleItems(player)).thenReturn(false);
        when(player.getEyeLocation()).thenReturn(new Location(world, 10.5, 66.2, 8.5, 0.0f, 28.8f));
        controller.tick(List.of(player));
        verify(renderer, times(2)).clearHoverText(player);
        verify(renderer, times(1)).showHoverText(any(), any(), any(), any(), anyFloat());
    }

    @Test
    void disablingHoverSettingClearsActiveHoverLabels() {
        SpatialRegistry registry = new SpatialRegistry();
        TargetedItemLookup lookup = new TargetedItemLookup(registry, 5.0);
        DisplayRenderer renderer = mock(DisplayRenderer.class);
        Player player = mock(Player.class);

        HoverDisplayNameController controller = new HoverDisplayNameController(
                lookup,
                renderer,
                DecorateSettings.DEFAULTS,
                bytes -> Optional.empty()
        );

        DecorateSettings disabled = new DecorateSettings(
                5.0, 32, 256, 200, 45.0f, true, true, false, true, 4,
                DecorateSettings.DEFAULTS.defaultPlacementMode(), true, true,
                false, 2, 0.75f, 0.28
        );
        controller.updateSettings(disabled, List.of(player));
        verify(renderer, times(1)).clearHoverText(player);

        controller.tick(List.of(player));
        verify(renderer, never()).showHoverText(any(), any(), any(), any(), anyFloat());
    }

    private static PlacedItem sampleItem(double x, double y, double z) {
        return new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 10, 64, 10),
                new SurfacePose(
                        new Vec3d(x, y, z),
                        SurfaceFace.UP,
                        0.0f,
                        PoseMode.FLAT,
                        new Vec3f(0.5f, 0.5f, 0.5f),
                        DisplayTransformType.FIXED,
                        0.4f,
                        0.4f
                ),
                UUID.randomUUID(),
                "Steve",
                false,
                new byte[] {1, 2, 3},
                "default",
                "decor:trophy",
                100L
        );
    }
}
