package ru.ashemchuk;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class ResponseEncoder {
    // format:
    // 4 bytes: private key len
    // N bytes: private key
    // 4 bytes: cert len
    // M bytes: cert
    public ByteBuffer encode(KeyPair keyPair, X509Certificate certificate)
        throws CertificateEncodingException {
        byte[] privateKey = keyPair.getPrivate().getEncoded();
        byte[] cert = certificate.getEncoded();
        return ByteBuffer
            .allocate(Integer.BYTES + privateKey.length + Integer.BYTES + cert.length)
            .putInt(privateKey.length)
            .put(privateKey)
            .putInt(cert.length)
            .put(cert)
            .flip();
    }
}
