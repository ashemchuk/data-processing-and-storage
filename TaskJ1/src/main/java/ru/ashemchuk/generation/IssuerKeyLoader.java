package ru.ashemchuk.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class IssuerKeyLoader {
    public PrivateKey load(Path path) throws IOException, GeneralSecurityException {
        String pem = Files.readString(path);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----") || !pem.contains("-----END PRIVATE KEY-----")) {
            throw new IllegalArgumentException("Expected a PKCS#8 PEM private key");
        }
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }
}
