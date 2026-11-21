package dev.decorate.core.service;

import org.bukkit.inventory.ItemStack;

/**
 * Abstraction over {@link ItemStack#serializeAsBytes()} and {@link ItemStack#deserializeBytes(byte[])}
 * allowing headless unit testing without a running Bukkit server.
 */
public interface ItemStackCodec {

    /** Default Paper 1.21.8 byte serialization preserving all Item Data Components. */
    ItemStackCodec PAPER_BYTES = new ItemStackCodec() {
        @Override
        public byte[] serialize(ItemStack stack) {
            return stack.serializeAsBytes();
        }

        @Override
        public ItemStack deserialize(byte[] bytes) {
            return ItemStack.deserializeBytes(bytes);
        }
    };

    byte[] serialize(ItemStack stack);

    ItemStack deserialize(byte[] bytes);
}
