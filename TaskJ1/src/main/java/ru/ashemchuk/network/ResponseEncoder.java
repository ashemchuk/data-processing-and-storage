package ru.ashemchuk.network;

import java.nio.ByteBuffer;
import java.security.cert.CertificateEncodingException;
import ru.ashemchuk.generation.CryptoIdentity;

public final class ResponseEncoder {
    public ByteBuffer encode(CryptoIdentity identity) throws CertificateEncodingException {
        byte[] key = identity.key().getEncoded();
        byte[] cert = identity.cert().getEncoded();
        return ByteBuffer.allocate(Integer.BYTES + key.length + Integer.BYTES + cert.length)
                .putInt(key.length).put(key).putInt(cert.length).put(cert).flip();
    }
}
