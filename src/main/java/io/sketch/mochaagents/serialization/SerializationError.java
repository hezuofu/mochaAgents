package io.sketch.mochaagents.serialization;

/**
 * Raised when a value cannot be transported with the safe JSON codec (parity with Python smolagents
 * serialization module).
 */
public final class SerializationError extends RuntimeException {

    public SerializationError(String message) {
        super(message);
    }

    public SerializationError(String message, Throwable cause) {
        super(message, cause);
    }
}
