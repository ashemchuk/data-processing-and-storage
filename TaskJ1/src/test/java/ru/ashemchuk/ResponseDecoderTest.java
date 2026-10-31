package ru.ashemchuk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.ashemchuk.network.ResponseDecoder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseDecoderTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsIncompleteResponse(boolean truncateCertificate) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            if (truncateCertificate) {
                output.writeInt(3);
                output.write(new byte[] {1, 2, 3});
            }
            output.writeInt(4);
            output.write(new byte[] {1, 2});
        }

        assertThrows(EOFException.class, () -> new ResponseDecoder().decode(
                new ByteArrayInputStream(bytes.toByteArray())));
    }
}
