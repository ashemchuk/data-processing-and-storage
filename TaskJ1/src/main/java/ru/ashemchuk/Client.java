package ru.ashemchuk;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import ru.ashemchuk.generation.CryptoIdentity;
import ru.ashemchuk.network.ResponseDecoder;

public final class Client {
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--help")) {
            System.out.println("Client: --name Name --host localhost --port 8080 --delay 0 --disconnect --output-dir output");
            return;
        }
        try {
            Map<String, String> options = CommandLine.parse(args,
                    Set.of("--name", "--host", "--port", "--delay", "--output-dir"), Set.of("--disconnect"));
            String name = options.get("--name");
            if (name == null) throw new IllegalArgumentException("--name is required");
            if (name.chars().anyMatch(c -> c == 0 || c > 127)) {
                throw new IllegalArgumentException("Name must contain ASCII characters without a null byte");
            }
            int port = Integer.parseInt(options.getOrDefault("--port", "8080"));
            long delay = Long.parseLong(options.getOrDefault("--delay", "0"));
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
            if (delay < 0) throw new IllegalArgumentException("Delay must not be negative");
            long delayMillis = Math.multiplyExact(delay, 1000);
            try (Socket socket = new Socket(options.getOrDefault("--host", "localhost"), port)) {
                socket.getOutputStream().write(name.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(0);
                socket.getOutputStream().flush();
                if (options.containsKey("--disconnect")) {
                    System.out.println("Disconnected without reading the response");
                    return;
                }
                Thread.sleep(delayMillis);
                CryptoIdentity identity = new ResponseDecoder().decode(socket.getInputStream());
                Path output = Path.of(options.getOrDefault("--output-dir", "output"));
                Files.createDirectories(output);
                writePem(output.resolve("identity.key"), "PRIVATE KEY", identity.key().getEncoded());
                writePem(output.resolve("identity.crt"), "CERTIFICATE", identity.cert().getEncoded());
                System.out.println("Identity saved to " + output.toAbsolutePath());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Client interrupted");
        } catch (Exception e) {
            System.err.println("Client failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void writePem(Path path, String type, byte[] bytes) throws java.io.IOException {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }
}
