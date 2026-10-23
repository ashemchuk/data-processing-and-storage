package ru.ashemchuk;

import java.security.KeyPair;

public record GenerationResult(String name, KeyPair keys, Exception error) {
}
