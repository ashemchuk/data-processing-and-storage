package ru.ashemchuk;

import java.util.Map;
import java.util.Set;

public record ServerConfig(int port, int threadCnt, String issuerName, String issuerPrivateKeyLocation) {
    public ServerConfig {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        if (threadCnt < 1) throw new IllegalArgumentException("Thread count must be positive");
        if (issuerName == null || issuerName.isBlank()) throw new IllegalArgumentException("Issuer name is required");
    }

    public static ServerConfig fromArgs(String[] args) {
        Map<String, String> options = CommandLine.parse(args,
                Set.of("--port", "--threads", "--issuer-name", "--issuer-key"), Set.of());
        return new ServerConfig(
                Integer.parseInt(options.getOrDefault("--port", "8080")),
                Integer.parseInt(options.getOrDefault("--threads", "" + Runtime.getRuntime().availableProcessors())),
                options.getOrDefault("--issuer-name", "Nerlikh67CoolCerts"),
                options.getOrDefault("--issuer-key", "config/SUPER_SECRET_ISSUER.key"));
    }
}
