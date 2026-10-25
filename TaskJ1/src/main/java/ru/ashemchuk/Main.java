package ru.ashemchuk;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Arrays;
import ru.ashemchuk.generation.CryptoGenerator;
import ru.ashemchuk.generation.IssuerKeyLoader;

public final class Main {
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--help")) {
            System.out.println("Server: --port 8080 --threads 2 --issuer-name Name --issuer-key path");
            return;
        }
        try {
            ServerConfig config = ServerConfig.fromArgs(args);
            PrivateKey issuerKey = new IssuerKeyLoader().load(Path.of(config.issuerPrivateKeyLocation()));
            try (CryptoGenerator generator = new CryptoGenerator(config.threadCnt(), issuerKey, config.issuerName());
                 Server server = new Server(config.port(), generator)) {
                Thread shutdown = new Thread(server::close, "server-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    server.start();
                } finally {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdown);
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
