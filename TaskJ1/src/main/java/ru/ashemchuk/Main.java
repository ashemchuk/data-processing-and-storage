package ru.ashemchuk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.bind(new InetSocketAddress(8080));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Waiting for connections on port 8080...");

            while (true) {
                selector.select();
                Set<SelectionKey> readyKeys = selector.selectedKeys();

                for (SelectionKey key : readyKeys) {

                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        if (client == null) {
                            continue;
                        }

                        try {
                            client.configureBlocking(false);
                            client.register(selector, SelectionKey.OP_READ, new StringBuilder());
                            System.out.println("Client connected");
                        } catch (IOException e) {
                            client.close();
                            System.err.println("Client error: " + e.getMessage());
                        }
                    } else if (key.isReadable()) {
                        readName(key);
                    }
                }
                readyKeys.clear();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    static void readName(SelectionKey key){
        StringBuilder name = (StringBuilder) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(256);
            int read = client.read(buffer);
            if (read == -1) {
                System.out.println("Client is disconnected before end of name");
                client.close();
                return;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                byte b = buffer.get();
                if (b == 0) { // null byte ends the name
                    System.out.println("Name is: " + name);
                    client.close();
                    return;
                }
                name.append((char)b);
            }
        } catch (IOException e) {
            System.out.println("SocketChannel: "+ e.getMessage());
        }

    }
}
