package ru.ashemchuk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test
    @Timeout(5)
    void waitsForCompleteName() throws Exception {
        // capture console output
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();

        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open();
             PrintStream capturedOutput = new PrintStream(printed, true, StandardCharsets.UTF_8)) {
            server.bind(new InetSocketAddress("localhost", 0)); // use a free port
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            try (Socket client = new Socket("localhost", port);
                 SocketChannel connection = server.accept()) {
                connection.configureBlocking(false);
                StringBuilder name = new StringBuilder();
                SelectionKey key = connection.register(selector, SelectionKey.OP_READ, name);
                System.setOut(capturedOutput);

                // send a part of name
                client.getOutputStream().write("An".getBytes(StandardCharsets.US_ASCII));
                while (name.length() < 2) {
                    assertTrue(selector.select(1000) > 0, "Timed out waiting for the first part");
                    selector.selectedKeys().clear();
                    Main.readName(key);
                    assertTrue(connection.isOpen(), "A partial name must not close the channel");
                    assertEquals("", printed.toString(StandardCharsets.UTF_8),
                            "A partial name must not be printed");
                }
                assertEquals("An", name.toString());

                // send the rest and a null byte
                client.getOutputStream().write(new byte[] {'n', 'a', 0});
                while (connection.isOpen()) {
                    assertTrue(selector.select(1000) > 0, "Timed out waiting for the rest of the name");
                    selector.selectedKeys().clear();
                    Main.readName(key);
                }
                assertEquals("Name is: Anna" + System.lineSeparator(),
                        printed.toString(StandardCharsets.UTF_8));
            }
        } finally {
            System.setOut(originalOutput);
        }
    }
}
