package ru.ashemchuk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        HashMap<String, Key> generations = new HashMap<>();
        Queue<GenerationResult> results = new ConcurrentLinkedQueue<>();
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.bind(new InetSocketAddress(8080));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Waiting for connections on port 8080...");

            while (true) {
                selector.select();
                // process generated results
                GenerationResult result;

                while ((result = results.poll()) != null) {
                    if (result.error() != null) {
                        System.err.println("Key generation failed: " + result.error().getMessage());
                        generations.remove(result.name());
                        continue;
                    }

                    Key entry = generations.get(result.name());
                    entry.setKeys(result.keys());
                    entry.setState(KeyState.DONE);
                }

                // process network
                Set<SelectionKey> readyKeys = selector.selectedKeys();

                for (SelectionKey selectionKey : readyKeys) {

                    if (selectionKey.isAcceptable()) {
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
                    } else if (selectionKey.isReadable()) {
                        String name = readName(selectionKey);
                        if (name != null) {
                            if (generations.containsKey(name) && generations.get(name).getState() == KeyState.DONE) {
                                // prepare the response
                            } else if (generations.containsKey(name) && generations.get(name).getState() == KeyState.IN_PROGRESS) {
                                // waiting for results & add another waiting user
                                generations.get(name).getWaitingUsers().add(selectionKey);
                            } else { // computing keys
                                Key generatingKey = new Key(KeyState.IN_PROGRESS, null);
                                // add 1st waiting user
                                generatingKey.getWaitingUsers().add(selectionKey);
                                generations.put(name, generatingKey);
                                workers.submit(() -> {
                                    try {
                                        KeyGenerator generator = new KeyGenerator();
                                        KeyPair keys = generator.generate();
                                        System.out.println("Key generated for " + name);
                                        results.add(new GenerationResult(name, keys, null));
                                    } catch (NoSuchAlgorithmException e) {
                                        System.err.println("Key generation failed: "+ e.getMessage());
                                        results.add(new GenerationResult(name, null, e));
                                    } finally {
                                        selector.wakeup();
                                    }
                                });
                            }
                        }
                    }
                }
                readyKeys.clear();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            workers.shutdown();
        }
    }
    static String readName(SelectionKey key){
        StringBuilder name = (StringBuilder) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(256);
            int read = client.read(buffer);
            if (read == -1) {
                System.out.println("Client is disconnected before end of name");
                client.close();
                return null;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                byte b = buffer.get();
                if (b == 0) { // null byte ends the name
                    System.out.println("Name is: " + name);
                    // remove OP_READ, name is full
                    key.interestOps(0);
                    return name.toString();
                }
                name.append((char)b);
            }
        } catch (IOException e) {
            System.out.println("SocketChannel: "+ e.getMessage());
        }

        return null;
    }
}
