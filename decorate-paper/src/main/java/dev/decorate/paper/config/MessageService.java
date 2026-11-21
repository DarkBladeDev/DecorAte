package dev.decorate.paper.config;

import dev.decorate.api.spi.PlaceholderService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Formats and sends MiniMessage texts resolved through {@link PlaceholderService}.
 */
public final class MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private volatile FileConfiguration messages;
    private volatile PlaceholderService placeholderService;

    public MessageService(FileConfiguration messages, PlaceholderService placeholderService) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.placeholderService = Objects.requireNonNull(placeholderService, "placeholderService must not be null");
    }

    public void reload(FileConfiguration updatedMessages) {
        this.messages = Objects.requireNonNull(updatedMessages, "updatedMessages must not be null");
    }

    public void setPlaceholderService(PlaceholderService placeholderService) {
        this.placeholderService = Objects.requireNonNull(placeholderService, "placeholderService must not be null");
    }

    public void send(CommandSender recipient, String key, TagResolver... extraTags) {
        String raw = messages.getString(key);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        recipient.sendMessage(render(recipient instanceof Player p ? p : null, raw, extraTags));
    }

    public List<String> getStringList(String key) {
        return messages.getStringList(key);
    }

    public Component render(Player player, String rawTemplate, TagResolver... extraTags) {
        String prefix = messages.getString("prefix", "");
        String withPrefix = rawTemplate.replace("<prefix>", prefix);
        String parsedPapi = placeholderService.parse(player, withPrefix);
        TagResolver combined = extraTags.length == 0
                ? TagResolver.empty()
                : TagResolver.resolver(extraTags);
        return MINI_MESSAGE.deserialize(parsedPapi, combined);
    }

    public static TagResolver tag(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }
}
