package ru.ashemchuk;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record GenerationResult(String name, KeyPair keys, X509Certificate cert, Exception error) {
}
