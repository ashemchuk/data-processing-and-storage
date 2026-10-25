package ru.ashemchuk.network;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class RequestDecoder {
    private final StringBuilder name = new StringBuilder();
    private boolean complete;

    public Optional<GenerationRequest> decode(ByteBuffer input) {
        if (complete) throw new IllegalStateException("Request is already complete");
        while (input.hasRemaining()) {
            int value = Byte.toUnsignedInt(input.get());
            if (value == 0) {
                complete = true;
                return Optional.of(new GenerationRequest(name.toString()));
            }
            if (value > 127) throw new IllegalArgumentException("Name must contain ASCII characters only");
            name.append((char) value);
        }
        return Optional.empty();
    }
}
