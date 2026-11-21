package dev.decorate.api.model;

/**
 * Item display render context matching Minecraft's ItemDisplay transform enum ordinals.
 */
public enum DisplayTransformType {
    NONE((byte) 0),
    THIRDPERSON_LEFTHAND((byte) 1),
    THIRDPERSON_RIGHTHAND((byte) 2),
    FIRSTPERSON_LEFTHAND((byte) 3),
    FIRSTPERSON_RIGHTHAND((byte) 4),
    HEAD((byte) 5),
    GUI((byte) 6),
    GROUND((byte) 7),
    FIXED((byte) 8);

    private final byte protocolId;

    DisplayTransformType(byte protocolId) {
        this.protocolId = protocolId;
    }

    /**
     * Returns the Minecraft protocol byte ordinal for entity metadata.
     *
     * @return protocol id
     */
    public byte protocolId() {
        return protocolId;
    }
}
