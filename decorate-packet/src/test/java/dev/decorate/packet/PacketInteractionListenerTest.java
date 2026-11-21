package dev.decorate.packet;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction;
import dev.decorate.api.event.DecorInteractEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketInteractionListenerTest {

    @Test
    void mapsPacketActionsCorrectly() {
        assertEquals(DecorInteractEvent.Action.PICKUP,
                PacketInteractionListener.mapAction(InteractAction.ATTACK, false));
        assertEquals(DecorInteractEvent.Action.PICKUP,
                PacketInteractionListener.mapAction(InteractAction.ATTACK, true));
        assertEquals(DecorInteractEvent.Action.ROTATE,
                PacketInteractionListener.mapAction(InteractAction.INTERACT, false));
        assertEquals(DecorInteractEvent.Action.TOGGLE_POSE,
                PacketInteractionListener.mapAction(InteractAction.INTERACT, true));
        assertEquals(DecorInteractEvent.Action.TOGGLE_POSE,
                PacketInteractionListener.mapAction(InteractAction.INTERACT_AT, true));
    }
}
