package ru.ashemchuk;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.ashemchuk.generation.CryptoGenerator;
import ru.ashemchuk.generation.CryptoIdentity;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class CryptoGeneratorTest {
    private static CryptoIdentity identity;

    @BeforeAll
    static void setUp() throws Exception { identity = TestIdentities.create(); }

    @Test
    void sharesPendingAndCompletedGeneration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (CryptoGenerator generator = new CryptoGenerator(2, name -> {
            calls.incrementAndGet();
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
            return identity;
        })) {
            CompletableFuture<CryptoIdentity> first = generator.getOrGenerate("Anna");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<CryptoIdentity> second = generator.getOrGenerate("Anna");
            assertFalse(first.isDone());
            release.countDown();
            assertSame(identity, first.get(2, TimeUnit.SECONDS));
            assertSame(identity, second.get(2, TimeUnit.SECONDS));
            assertSame(identity, generator.getOrGenerate("Anna").get(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void retriesFailedGeneration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (CryptoGenerator generator = new CryptoGenerator(1, name -> {
            if (calls.incrementAndGet() == 1) throw new IOException("Generation failed");
            return identity;
        })) {
            CompletableFuture<CryptoIdentity> failed = generator.getOrGenerate("Anna");
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> failed.get(2, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, error.getCause());
            assertSame(identity, generator.getOrGenerate("Anna").get(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }
}
