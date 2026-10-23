package ru.ashemchuk;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

class KeyGeneratorTest {
    @Test
    void generatesRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPair pair = new KeyGenerator().generate();

        RSAPublicKey publicKey = assertInstanceOf(RSAPublicKey.class, pair.getPublic());
        RSAPrivateKey privateKey = assertInstanceOf(RSAPrivateKey.class, pair.getPrivate());

        assertEquals("RSA", publicKey.getAlgorithm());
        assertEquals("RSA", privateKey.getAlgorithm());
        assertEquals(8192, publicKey.getModulus().bitLength(), "RSA modulus must have 8192 bits");
        assertEquals(publicKey.getModulus(), privateKey.getModulus(),
                "Both keys must have the same modulus");
    }
}
