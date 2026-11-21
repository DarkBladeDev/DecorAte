package dev.decorate.paper.command;

import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.ItemTemplate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.core.service.PlacementService;
import dev.decorate.paper.config.MessageService;
import dev.decorate.paper.item.ItemTemplateFactory;
import dev.decorate.paper.lookup.TargetedItemLookup;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Cloud Annotations 2.1.0 command handler for {@code /decorate} ({@code /dec}).
 */
public final class DecorateCommands {

    private final PlacementService placementService;
    private final PlacementProfileResolver profileResolver;
    private final TargetedItemLookup targetLookup;
    private final MessageService messages;
    private final Runnable reloadAction;
    private final BiFunction<ItemTemplate, Integer, ItemStack> itemFactory;

    public DecorateCommands(
            PlacementService placementService,
            PlacementProfileResolver profileResolver,
            TargetedItemLookup targetLookup,
            MessageService messages,
            Runnable reloadAction
    ) {
        this(placementService, profileResolver, targetLookup, messages, reloadAction, ItemTemplateFactory::createItem);
    }

    public DecorateCommands(
            PlacementService placementService,
            PlacementProfileResolver profileResolver,
            TargetedItemLookup targetLookup,
            MessageService messages,
            Runnable reloadAction,
            BiFunction<ItemTemplate, Integer, ItemStack> itemFactory
    ) {
        this.placementService = Objects.requireNonNull(placementService);
        this.profileResolver = Objects.requireNonNull(profileResolver);
        this.targetLookup = Objects.requireNonNull(targetLookup);
        this.messages = Objects.requireNonNull(messages);
        this.reloadAction = Objects.requireNonNull(reloadAction);
        this.itemFactory = Objects.requireNonNull(itemFactory);
    }

    @Command("decorate|dec reload")
    @Permission("decorate.command.reload")
    @CommandDescription("Reloads DecorAte configuration, messages, and YAML custom model profiles")
    public void reload(CommandSender sender) {
        reloadAction.run();
        messages.send(sender, "command.reloaded",
                MessageService.tag("count", String.valueOf(profileResolver.allProfiles().size())));
    }

    @Command("decorate|dec mode <mode>")
    @Permission("decorate.command.mode")
    @CommandDescription("Switches placement mode between FREE and SNAP")
    public void setMode(CommandSender sender, @Argument("mode") PlacementMode.Mode mode) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        PlacementMode current = placementService.getPlayerMode(player.getUniqueId());
        PlacementMode updated = new PlacementMode(mode, current.gridDivisions());
        placementService.setPlayerMode(player.getUniqueId(), updated);
        messages.send(player, "command.mode-set",
                MessageService.tag("mode", updated.mode().name()),
                MessageService.tag("divisions", String.valueOf(updated.gridDivisions())));
    }

    @Command("decorate|dec snap <divisions>")
    @Permission("decorate.command.mode")
    @CommandDescription("Enables sub-grid snap mode with the specified divisions per block (1-64)")
    public void setSnap(CommandSender sender, @Argument("divisions") int divisions) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        int clamped = Math.clamp(divisions, 1, 64);
        PlacementMode updated = new PlacementMode(PlacementMode.Mode.SNAP, clamped);
        placementService.setPlayerMode(player.getUniqueId(), updated);
        messages.send(player, "command.mode-set",
                MessageService.tag("mode", updated.mode().name()),
                MessageService.tag("divisions", String.valueOf(updated.gridDivisions())));
    }

    @Command("decorate|dec info")
    @Permission("decorate.command.info")
    @CommandDescription("Inspects the placed item you are currently looking at")
    public void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        Optional<PlacedItem> targetOpt = targetLookup.findTargetedItem(player);
        if (targetOpt.isEmpty()) {
            messages.send(player, "command.inspect-none");
            return;
        }
        PlacedItem item = targetOpt.get();
        messages.send(player, "command.inspect-header");
        for (String line : messages.getStringList("command.inspect-lines")) {
            player.sendMessage(messages.render(
                    player,
                    line,
                    MessageService.tag("profile", item.resolvedProfileId()),
                    MessageService.tag("model", item.itemDisplayKey()),
                    MessageService.tag("owner", item.ownerName()),
                    MessageService.tag("pose", item.pose().poseMode().name()),
                    MessageService.tag("yaw", String.format(Locale.ROOT, "%.1f", item.pose().yawDegrees())),
                    MessageService.tag("face", item.pose().face().name()),
                    MessageService.tag("x", String.format(Locale.ROOT, "%.2f", item.pose().exactPosition().x())),
                    MessageService.tag("y", String.format(Locale.ROOT, "%.2f", item.pose().exactPosition().y())),
                    MessageService.tag("z", String.format(Locale.ROOT, "%.2f", item.pose().exactPosition().z()))
            ));
        }
    }

    @Command("decorate|dec clear radius <radius>")
    @Permission("decorate.command.clear")
    @CommandDescription("Clears all placed items within the given block radius")
    public void clearRadius(CommandSender sender, @Argument("radius") double radius) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        double clamped = Math.max(0.5, Math.min(radius, 128.0));
        var loc = player.getLocation();
        int removed = placementService.clearInRadius(
                player.getWorld().getName(),
                new Vec3d(loc.getX(), loc.getY(), loc.getZ()),
                clamped,
                player
        );
        messages.send(player, "command.clear-done", MessageService.tag("count", String.valueOf(removed)));
    }

    @Command("decorate|dec clear chunk")
    @Permission("decorate.command.clear")
    @CommandDescription("Clears all placed items inside the current chunk")
    public void clearChunk(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        var loc = player.getLocation();
        ChunkCoordinate chunk = ChunkCoordinate.fromPosition(player.getWorld().getName(), loc.getX(), loc.getZ());
        int removed = placementService.clearChunk(chunk, player);
        messages.send(player, "command.clear-done", MessageService.tag("count", String.valueOf(removed)));
    }

    @Command("decorate|dec give <profile> [target] [amount]")
    @Permission("decorate.command.give")
    @CommandDescription("Gives a custom decorative item defined in a YAML profile")
    public void give(
            CommandSender sender,
            @Argument(value = "profile", suggestions = "giveable-profiles") String profileId,
            @Argument("target") @Nullable Player target,
            @Argument("amount") @Default("1") @Nullable Integer amount
    ) {
        Player recipient = target != null ? target : (sender instanceof Player p ? p : null);
        if (recipient == null) {
            messages.send(sender, "command.player-only");
            return;
        }

        Optional<PlacementProfile> profileOpt = profileResolver.findById(profileId);
        Optional<ItemTemplate> templateOpt = profileOpt.flatMap(PlacementProfile::optionalItemTemplate);
        if (templateOpt.isEmpty()) {
            messages.send(sender, "command.give-invalid", MessageService.tag("profile", profileId));
            return;
        }

        int count = amount == null ? 1 : Math.max(1, amount);
        ItemStack stack = itemFactory.apply(templateOpt.get(), count);
        Map<Integer, ItemStack> overflow = recipient.getInventory().addItem(stack);
        for (ItemStack extra : overflow.values()) {
            recipient.getWorld().dropItemNaturally(recipient.getLocation(), extra);
        }

        messages.send(sender, "command.give-done",
                MessageService.tag("amount", String.valueOf(count)),
                MessageService.tag("profile", profileId),
                MessageService.tag("target", recipient.getName()));
    }

    @Suggestions("giveable-profiles")
    public List<String> suggestGiveableProfiles(CommandContext<CommandSender> context, CommandInput input) {
        String prefix = input.lastRemainingToken().toLowerCase(Locale.ROOT);
        return profileResolver.giveableProfiles().stream()
                .map(PlacementProfile::id)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
