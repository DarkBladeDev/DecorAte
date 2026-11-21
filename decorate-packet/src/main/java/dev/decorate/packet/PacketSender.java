package dev.decorate.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.bukkit.entity.Player;

/**
 * Thin abstraction over sending PacketEvents packets to a Bukkit {@link Player},
 * enabling headless unit testing without active Netty channels.
 */
@FunctionalInterface
public interface PacketSender {

    /** Sends packets via PacketEvents {@code PlayerManager#sendPacket}. */
    PacketSender PACKET_EVENTS = (player, packet) ->
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);

    void send(Player player, PacketWrapper<?> packet);
}
