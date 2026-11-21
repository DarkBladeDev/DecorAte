package dev.decorate.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.spi.ProtectionHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * {@link ProtectionHook} integrating with WorldGuard 7.x regions and custom flags
 * ({@code decorate-place}, {@code decorate-interact}).
 */
public final class WorldGuardProtectionHook implements ProtectionHook {

    public static final String FLAG_PLACE_NAME = "decorate-place";
    public static final String FLAG_INTERACT_NAME = "decorate-interact";

    private static volatile @Nullable StateFlag placeFlag;
    private static volatile @Nullable StateFlag interactFlag;

    private final QueryAdapter queryAdapter;
    private final Function<String, @Nullable World> worldLookup;

    public WorldGuardProtectionHook() {
        this(new LiveWorldGuardAdapter(), name -> Bukkit.getServer() == null ? null : Bukkit.getWorld(name));
    }

    public WorldGuardProtectionHook(QueryAdapter queryAdapter, Function<String, @Nullable World> worldLookup) {
        this.queryAdapter = Objects.requireNonNull(queryAdapter, "queryAdapter must not be null");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup must not be null");
    }

    /**
     * Registers DecorAte's custom WorldGuard flags during {@code JavaPlugin#onLoad()}.
     * Safe to invoke even if WorldGuard is not installed or flags were already registered.
     *
     * @param logger plugin logger
     */
    public static void registerFlags(Logger logger) {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            placeFlag = registerStateFlag(registry, FLAG_PLACE_NAME, true, logger);
            interactFlag = registerStateFlag(registry, FLAG_INTERACT_NAME, true, logger);
        } catch (NoClassDefFoundError | IllegalStateException ex) {
            logger.fine("WorldGuard flag registration skipped: " + ex.getMessage());
        }
    }

    private static @Nullable StateFlag registerStateFlag(
            FlagRegistry registry,
            String name,
            boolean defaultAllow,
            Logger logger
    ) {
        try {
            StateFlag flag = new StateFlag(name, defaultAllow);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag existingState) {
                return existingState;
            }
            logger.warning("WorldGuard flag conflict for '" + name + "' with incompatible type; falling back to BUILD/INTERACT.");
            return null;
        }
    }

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean canPlace(Player player, Location location, Block supportBlock) {
        return queryAdapter.testPlace(player, location, placeFlag);
    }

    @Override
    public boolean canInteract(Player player, PlacedItem item, DecorInteractEvent.Action action) {
        World world = worldLookup.apply(item.world());
        if (world == null) {
            return true;
        }
        Location loc = new Location(
                world,
                item.pose().exactPosition().x(),
                item.pose().exactPosition().y(),
                item.pose().exactPosition().z()
        );
        return queryAdapter.testInteract(player, loc, interactFlag);
    }

    /**
     * Adapter around WorldGuard's static runtime state for unit testability.
     */
    public interface QueryAdapter {
        boolean testPlace(Player player, Location location, @Nullable StateFlag customPlaceFlag);

        boolean testInteract(Player player, Location location, @Nullable StateFlag customInteractFlag);
    }

    private static final class LiveWorldGuardAdapter implements QueryAdapter {

        @Override
        public boolean testPlace(Player player, Location location, @Nullable StateFlag customPlaceFlag) {
            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);
            if (location.getWorld() != null && WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(localPlayer, BukkitAdapter.adapt(location.getWorld()))) {
                return true;
            }
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(location);
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            if (customPlaceFlag != null) {
                return query.testState(wgLoc, localPlayer, Flags.BUILD, customPlaceFlag);
            }
            return query.testState(wgLoc, localPlayer, Flags.BUILD);
        }

        @Override
        public boolean testInteract(Player player, Location location, @Nullable StateFlag customInteractFlag) {
            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);
            if (location.getWorld() != null && WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(localPlayer, BukkitAdapter.adapt(location.getWorld()))) {
                return true;
            }
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(location);
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            if (customInteractFlag != null) {
                return query.testState(wgLoc, localPlayer, Flags.INTERACT, customInteractFlag);
            }
            return query.testState(wgLoc, localPlayer, Flags.INTERACT);
        }
    }
}
