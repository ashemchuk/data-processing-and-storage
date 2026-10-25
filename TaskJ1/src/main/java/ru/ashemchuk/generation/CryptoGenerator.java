package ru.ashemchuk.generation;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CryptoGenerator implements AutoCloseable {
    @FunctionalInterface
    public interface IdentityFactory {
        CryptoIdentity generate(String name) throws Exception;
    }

    private final Map<String, CompletableFuture<CryptoIdentity>> cache = new HashMap<>();
    private final ExecutorService workers;
    private final IdentityFactory factory;

    public CryptoGenerator(int threadCount, PrivateKey issuerKey, String issuerName) {
        this(threadCount, name -> {
            KeyPair keys = new KeyGenerator().generate();
            return new CryptoIdentity(keys.getPrivate(), new CertificateGenerator().generate(
                    name, keys.getPublic(), issuerKey, issuerName));
        });
    }

    public CryptoGenerator(int threadCount, IdentityFactory factory) {
        if (threadCount < 1) throw new IllegalArgumentException("Thread count must be positive");
        this.workers = Executors.newFixedThreadPool(threadCount);
        this.factory = java.util.Objects.requireNonNull(factory);
    }

    // called only by the server thread
    public CompletableFuture<CryptoIdentity> getOrGenerate(String name) {
        CompletableFuture<CryptoIdentity> existing = cache.get(name);
        if (existing != null && !existing.isCompletedExceptionally()) return existing;

        CompletableFuture<CryptoIdentity> future = new CompletableFuture<>();
        cache.put(name, future);
        try {
            workers.execute(() -> {
                try {
                    future.complete(factory.generate(name));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
