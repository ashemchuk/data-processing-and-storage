package ru.ashemchuk;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import ru.ashemchuk.network.ResponseEncoder;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void savesMatchingPrivateKeyAndCertificate() throws Exception {
        var identity = TestIdentities.create();
        var encoded = new ResponseEncoder().encode(identity);
        byte[] response = new byte[encoded.remaining()];
        encoded.get(response);
        var thread = Executors.newSingleThreadExecutor();
        Process client = null;

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            var exchange = thread.submit(() -> {
                try (var socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    String request = readRequest(socket);
                    socket.getOutputStream().write(response);
                    return request;
                }
            });

            Path output = temporaryDirectory.resolve("identity");
            Path log = temporaryDirectory.resolve("client.log");
            client = startClient(listener.getLocalPort(), output);

            assertEquals("Anna", exchange.get(10, TimeUnit.SECONDS));
            assertTrue(client.waitFor(10, TimeUnit.SECONDS), "Client must finish");
            assertEquals(0, client.exitValue(), Files.readString(log));

            String pem = Files.readString(output.resolve("identity.key"));
            assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"));
            byte[] keyBytes = Base64.getDecoder().decode(pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            X509Certificate certificate;
            try (var input = Files.newInputStream(output.resolve("identity.crt"))) {
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
            assertArrayEquals(identity.key().getEncoded(), key.getEncoded());
            assertArrayEquals(identity.cert().getEncoded(), certificate.getEncoded());

            byte[] message = {1, 2, 3, 4};
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(message);
            byte[] signed = signature.sign();
            signature.initVerify(certificate.getPublicKey());
            signature.update(message);
            assertTrue(signature.verify(signed), "Saved private and public keys must match");
        } finally {
            stopClient(client);
            thread.shutdownNow();
            assertTrue(thread.awaitTermination(3, TimeUnit.SECONDS), "Test server must stop");
        }
    }

    @Test
    @Timeout(20)
    void delaysResponseProcessingAfterSendingCompleteRequest() throws Exception {
        var identity = TestIdentities.create();
        var encoded = new ResponseEncoder().encode(identity);
        byte[] response = new byte[encoded.remaining()];
        encoded.get(response);
        Path output = temporaryDirectory.resolve("delayed");
        Process client = null;

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            client = startClient(listener.getLocalPort(), output, "--delay", "2");
            try (var socket = listener.accept()) {
                socket.setSoTimeout(1000);
                assertEquals("Anna", readRequest(socket));
                socket.getOutputStream().write(response);
                socket.getOutputStream().flush();

                assertFalse(client.waitFor(500, TimeUnit.MILLISECONDS),
                        "Client must wait even when the response is ready");
                assertFalse(Files.exists(output.resolve("identity.key")));
                assertFalse(Files.exists(output.resolve("identity.crt")));

                assertTrue(client.waitFor(10, TimeUnit.SECONDS), "Client must finish after the delay");
                assertEquals(0, client.exitValue(), Files.readString(temporaryDirectory.resolve("client.log")));
                assertTrue(Files.isRegularFile(output.resolve("identity.key")));
                assertTrue(Files.isRegularFile(output.resolve("identity.crt")));
            }
        } finally {
            stopClient(client);
        }
    }

    @Test
    @Timeout(15)
    void disconnectsAfterCompleteRequestWithoutWaitingForResponse() throws Exception {
        Path output = temporaryDirectory.resolve("disconnected");
        Process client = null;

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            client = startClient(listener.getLocalPort(), output, "--disconnect", "--delay", "60");
            try (var socket = listener.accept()) {
                socket.setSoTimeout(5000);
                assertEquals("Anna", readRequest(socket));

                // keep the server side open without sending a response
                assertTrue(client.waitFor(5, TimeUnit.SECONDS), "Disconnect must skip reading and delay");
                assertEquals(0, client.exitValue(), Files.readString(temporaryDirectory.resolve("client.log")));
                assertEquals(-1, socket.getInputStream().read(), "Client must close its connection");
                assertFalse(Files.exists(output), "Disconnect must not create output files");
            }
        } finally {
            stopClient(client);
        }
    }

    private String readRequest(java.net.Socket socket) throws java.io.IOException {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        int value;
        while ((value = socket.getInputStream().read()) != 0) {
            if (value == -1) throw new EOFException("Incomplete client request");
            request.write(value);
        }
        return request.toString(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private Process startClient(int port, Path output, String... options) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        // include child-process coverage when running under jacoco
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:") && arg.toLowerCase(Locale.ROOT).contains("jacoco"))
                .forEach(command::add);
        command.addAll(List.of("-cp", Path.of(Client.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Client.class.getName(), "--name", "Anna", "--host", "127.0.0.1",
                "--port", String.valueOf(port), "--output-dir", output.toString()));
        command.addAll(List.of(options));
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(temporaryDirectory.resolve("client.log").toFile()).start();
    }

    private void stopClient(Process client) throws InterruptedException {
        if (client != null && client.isAlive()) {
            client.destroyForcibly();
            assertTrue(client.waitFor(3, TimeUnit.SECONDS), "Client process must stop");
        }
    }

}
