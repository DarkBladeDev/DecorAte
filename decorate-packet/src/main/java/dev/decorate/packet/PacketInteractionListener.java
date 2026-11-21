package dev.decorate.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.spi.DisplayRenderer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * PacketEvents listener intercepting {@code INTERACT_ENTITY} packets targeting virtual
 * {@code EntityTypes.INTERACTION} hitboxes and dispatching domain actions on the server thread.
 */
public final class PacketInteractionListener extends PacketListenerAbstract {

    private static final long DEDUPE_WINDOW_NANOS = 40_000_000L; // 40ms guards against INTERACT + INTERACT_AT pairs

    private final DisplayRenderer renderer;
    private final Consumer<Runnable> mainThreadDispatcher;
    private final InteractionHandler interactionHandler;
    private final Map<UUID, Long> lastInteractNanos = new ConcurrentHashMap<>();

    public PacketInteractionListener(
            DisplayRenderer renderer,
            Consumer<Runnable> mainThreadDispatcher,
            InteractionHandler interactionHandler
    ) {
        super(PacketListenerPriority.NORMAL);
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.mainThreadDispatcher = Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher must not be null");
        this.interactionHandler = Objects.requireNonNull(interactionHandler, "interactionHandler must not be null");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        Optional<UUID> itemIdOpt = renderer.resolveItemByVirtualEntityId(wrapper.getEntityId());
        if (itemIdOpt.isEmpty()) {
            return;
        }

        // Prevent vanilla server from processing an interaction against a non-existent virtual entity ID
        event.setCancelled(true);

        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player player)) {
            return;
        }

        // Only process main hand for right-click interactions to prevent double-firing with offhand
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK
                && wrapper.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        long now = System.nanoTime();
        Long prev = lastInteractNanos.put(player.getUniqueId(), now);
        if (prev != null && (now - prev) < DEDUPE_WINDOW_NANOS) {
            return;
        }

        UUID itemId = itemIdOpt.get();
        boolean sneaking = wrapper.isSneaking().orElse(player.isSneaking());
        DecorInteractEvent.Action action = mapAction(wrapper.getAction(), sneaking);

        mainThreadDispatcher.accept(() -> {
            if (player.isOnline()) {
                interactionHandler.onInteract(player, itemId, action);
            }
        });
    }

    /**
     * Maps a PacketEvents interact action and sneak state to a DecorAte {@link DecorInteractEvent.Action}.
     *
     * @param action packet interact action
     * @param sneaking whether player is sneaking
     * @return mapped domain action
     */
    public static DecorInteractEvent.Action mapAction(
            WrapperPlayClientInteractEntity.InteractAction action,
            boolean sneaking
    ) {
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return DecorInteractEvent.Action.PICKUP;
        }
        return sneaking ? DecorInteractEvent.Action.TOGGLE_POSE : DecorInteractEvent.Action.ROTATE;
    }

    /**
     * Callback invoked on the server main thread when a player interacts with a virtual item.
     */
    @FunctionalInterface
    public interface InteractionHandler {
        void onInteract(Player player, UUID itemId, DecorInteractEvent.Action action);
    }
}
