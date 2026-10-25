package ru.ashemchuk.network;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import ru.ashemchuk.generation.CryptoIdentity;

public final class ResponseDecoder {
    public CryptoIdentity decode(InputStream input) throws IOException, GeneralSecurityException {
        DataInputStream data = new DataInputStream(input);
        byte[] keyBytes = readPart(data);
        byte[] certBytes = readPart(data);
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certBytes));
        return new CryptoIdentity(key, certificate);
    }

    private byte[] readPart(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > 16 * 1024 * 1024) throw new IOException("Invalid response length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Incomplete server response");
        return bytes;
    }
}
