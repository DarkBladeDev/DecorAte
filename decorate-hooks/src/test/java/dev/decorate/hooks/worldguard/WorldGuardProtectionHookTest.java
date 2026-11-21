package dev.decorate.hooks.worldguard;

import com.sk89q.worldguard.protection.flags.StateFlag;
import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldGuardProtectionHookTest {

    @Test
    void delegatesPlaceAndInteractChecksToQueryAdapter() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        WorldGuardProtectionHook allowPlaceDenyInteract = new WorldGuardProtectionHook(
                new WorldGuardProtectionHook.QueryAdapter() {
                    @Override
                    public boolean testPlace(Player player, Location location, StateFlag customPlaceFlag) {
                        return true;
                    }

                    @Override
                    public boolean testInteract(Player player, Location location, StateFlag customInteractFlag) {
                        return false;
                    }
                },
                name -> world
        );

        assertEquals("WorldGuard", allowPlaceDenyInteract.name());
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        Location loc = new Location(world, 1, 64, 1);

        assertTrue(allowPlaceDenyInteract.canPlace(player, loc, block));

        PlacedItem item = new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 1, 64, 1),
                new SurfacePose(new Vec3d(1.5, 65.02, 1.5), SurfaceFace.UP, 0f, PoseMode.FLAT, Vec3f.ONE, DisplayTransformType.FIXED, 0.35f, 0.35f),
                UUID.randomUUID(),
                "Steve",
                false,
                new byte[] {1},
                "default",
                "decor:mug",
                10L
        );
        assertFalse(allowPlaceDenyInteract.canInteract(player, item, DecorInteractEvent.Action.PICKUP));

        WorldGuardProtectionHook denyPlace = new WorldGuardProtectionHook(
                new WorldGuardProtectionHook.QueryAdapter() {
                    @Override
                    public boolean testPlace(Player p, Location l, StateFlag flag) {
                        return false;
                    }

                    @Override
                    public boolean testInteract(Player p, Location l, StateFlag flag) {
                        return false;
                    }
                },
                name -> world
        );
        assertFalse(denyPlace.canPlace(player, loc, block));
    }

    @Test
    void registerFlagsDoesNotThrowWithoutRunningWorldGuardRuntime() {
        assertDoesNotThrow(() -> WorldGuardProtectionHook.registerFlags(Logger.getLogger("Test")));
    }
}
