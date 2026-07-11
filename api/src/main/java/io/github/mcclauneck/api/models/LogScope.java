package io.github.mcclauneck.api.models;

/**
 * Represents the scope of an economy transaction log.
 */
public enum LogScope {
    SEND,
    RECEIVE,
    SPEND,
    DONATE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
