package ru.ashemchuk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ru.ashemchuk.network.GenerationRequest;
import ru.ashemchuk.network.RequestDecoder;
import static org.junit.jupiter.api.Assertions.*;

class RequestDecoderTest {
    private ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void waitsForCompleteName() {
        RequestDecoder decoder = new RequestDecoder();
        assertTrue(decoder.decode(bytes("An")).isEmpty());
        assertTrue(decoder.decode(bytes("na")).isEmpty());
        assertEquals(new GenerationRequest("Anna"), decoder.decode(bytes("\0")).orElseThrow());
    }

    @Test
    void keepsClientNamesSeparate() {
        RequestDecoder first = new RequestDecoder();
        RequestDecoder second = new RequestDecoder();
        assertTrue(first.decode(bytes("An")).isEmpty());
        assertEquals("Bob", second.decode(bytes("Bob\0")).orElseThrow().name());
        assertEquals("Anna", first.decode(bytes("na\0")).orElseThrow().name());
    }

    @Test
    void rejectsNonAsciiBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new RequestDecoder().decode(ByteBuffer.wrap(new byte[] {(byte) 200})));
    }

    @Test
    void acceptsEmptyName() {
        assertEquals("", new RequestDecoder().decode(bytes("\0")).orElseThrow().name());
    }
}
