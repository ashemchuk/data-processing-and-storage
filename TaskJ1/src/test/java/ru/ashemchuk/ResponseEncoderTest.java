package ru.ashemchuk;

import ru.ashemchuk.generation.CertificateGenerator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import ru.ashemchuk.network.ResponseEncoder;
import ru.ashemchuk.generation.CryptoIdentity;

import static org.junit.jupiter.api.Assertions.*;

class ResponseEncoderTest {
    private static KeyPair keys;
    private static X509Certificate certificate;

    @BeforeAll
    static void createIdentity() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        keys = rsa.generateKeyPair();

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair issuer = ec.generateKeyPair();
        certificate = new CertificateGenerator().generate(
                "Anna", keys.getPublic(), issuer.getPrivate(), "Lab CA");
    }

    @Test
    void encodesKeyAndCertificateAsLengthPrefixedParts() throws Exception {
        ByteBuffer response = new ResponseEncoder().encode(new CryptoIdentity(keys.getPrivate(), certificate));
        byte[] bytes = new byte[response.remaining()];
        response.get(bytes);

        // read the response as a client would
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int keyLength = input.readInt();
            assertEquals(keys.getPrivate().getEncoded().length, keyLength);
            byte[] keyBytes = input.readNBytes(keyLength);
            assertEquals(keyLength, keyBytes.length);
            PrivateKey decodedKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            assertArrayEquals(keys.getPrivate().getEncoded(), decodedKey.getEncoded());

            int certLength = input.readInt();
            assertEquals(certificate.getEncoded().length, certLength);
            byte[] certBytes = input.readNBytes(certLength);
            assertEquals(certLength, certBytes.length);
            X509Certificate decodedCertificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certBytes));
            assertArrayEquals(certificate.getEncoded(), decodedCertificate.getEncoded());
            assertEquals(-1, input.read(), "No extra bytes expected");
        }
    }

    @Test
    void givesEachClientAnIndependentResponse() throws Exception {
        ResponseEncoder encoder = new ResponseEncoder();
        ByteBuffer first = encoder.encode(new CryptoIdentity(keys.getPrivate(), certificate));
        ByteBuffer second = encoder.encode(new CryptoIdentity(keys.getPrivate(), certificate));
        byte[] expected = new byte[second.remaining()];
        second.duplicate().get(expected);

        // one client consumes data while the other waits
        while (first.hasRemaining()) {
            first.get();
        }

        byte[] actual = new byte[second.remaining()];
        second.get(actual);
        assertArrayEquals(expected, actual);
    }
}
