package ru.ashemchuk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class MainTest {
    private Selector selector;
    private ServerSocketChannel server;
    private final List<SocketChannel> connections = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        selector = Selector.open();
        server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("localhost", 0)); // use a free port
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            for (SocketChannel connection : connections) {
                connection.close();
            }
        } finally {
            try {
                if (server != null) server.close();
            } finally {
                if (selector != null) selector.close();
            }
        }
    }

    @Test
    void waitsForCompleteName() throws IOException {
        try (Socket client = connectClient()) {
            send(client, "An");
            assertTrue(readReadyNames().isEmpty(), "A partial name must not be returned");

            send(client, "na\0");
            assertEquals(List.of("Anna"), awaitNames());
        }
    }

    @Test
    void keepsClientNamesSeparate() throws IOException {
        try (Socket first = connectClient();
             Socket second = connectClient()) {
            send(first, "An");
            assertTrue(readReadyNames().isEmpty(), "The first name is not complete");

            // the second client finishes before the first
            send(second, "Bob\0");
            assertEquals(List.of("Bob"), awaitNames());

            send(first, "na\0");
            assertEquals(List.of("Anna"), awaitNames());
        }
    }

    private Socket connectClient() throws IOException {
        int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
        Socket client = new Socket("localhost", port);
        try {
            SocketChannel connection = server.accept();
            connections.add(connection);
            connection.configureBlocking(false);
            connection.register(selector, SelectionKey.OP_READ, new StringBuilder());
            return client;
        } catch (IOException | RuntimeException e) {
            client.close();
            throw e;
        }
    }

    private void send(Socket client, String text) throws IOException {
        client.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private List<String> readReadyNames() throws IOException {
        assertTrue(selector.select(1000) > 0, "Timed out waiting for data");
        List<String> names = new ArrayList<>();
        for (SelectionKey key : selector.selectedKeys()) {
            if (key.isReadable()) {
                String name = Main.readName(key);
                if (name != null) names.add(name);
            }
        }
        selector.selectedKeys().clear();
        return names;
    }

    private List<String> awaitNames() throws IOException {
        List<String> names;
        do {
            names = readReadyNames();
        } while (names.isEmpty());
        return names;
    }
}
