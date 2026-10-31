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
                    ByteArrayOutputStream request = new ByteArrayOutputStream();
                    int value;
                    while ((value = socket.getInputStream().read()) != 0) {
                        if (value == -1) throw new EOFException("Incomplete client request");
                        request.write(value);
                    }
                    socket.getOutputStream().write(response);
                    return request.toString(java.nio.charset.StandardCharsets.US_ASCII);
                }
            });

            Path output = temporaryDirectory.resolve("identity");
            Path log = temporaryDirectory.resolve("client.log");
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            // include child-process coverage when running under jacoco
            ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .filter(arg -> arg.startsWith("-javaagent:") && arg.toLowerCase(Locale.ROOT).contains("jacoco"))
                    .forEach(command::add);
            command.addAll(List.of("-cp", Path.of(Client.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).toString(),
                    Client.class.getName(), "--name", "Anna", "--host", "127.0.0.1",
                    "--port", String.valueOf(listener.getLocalPort()), "--output-dir", output.toString()));
            client = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();

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
            if (client != null && client.isAlive()) {
                client.destroyForcibly();
                client.waitFor(3, TimeUnit.SECONDS);
            }
            thread.shutdownNow();
            assertTrue(thread.awaitTermination(3, TimeUnit.SECONDS), "Test server must stop");
        }
    }

}
