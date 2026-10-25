package ru.ashemchuk;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.ECPrivateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ashemchuk.generation.IssuerKeyLoader;
import static org.junit.jupiter.api.Assertions.*;

class IssuerKeyLoaderTest {
    @TempDir Path temporary;

    @Test
    void loadsIssuerKeyFromPem() throws Exception {
        ECPrivateKey key = assertInstanceOf(ECPrivateKey.class,
                new IssuerKeyLoader().load(Path.of("config/SUPER_SECRET_ISSUER.key")));
        assertEquals(BigInteger.valueOf(4), key.getS());
        assertEquals(384, key.getParams().getCurve().getField().getFieldSize());
    }

    @Test
    void rejectsInvalidPem() throws Exception {
        Path file = temporary.resolve("invalid.key");
        Files.writeString(file, "not a private key");
        assertThrows(IllegalArgumentException.class, () -> new IssuerKeyLoader().load(file));
    }
}
