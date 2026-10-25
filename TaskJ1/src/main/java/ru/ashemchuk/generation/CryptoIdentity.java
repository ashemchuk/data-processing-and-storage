package ru.ashemchuk.generation;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record CryptoIdentity(PrivateKey key, X509Certificate cert) {
}
