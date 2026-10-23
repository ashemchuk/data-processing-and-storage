package ru.ashemchuk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bouncycastle.operator.OperatorCreationException;

public class Main {
    public static void main(String[] args) {
        PrivateKey issuerPrivateKey;
        try {
            // fixme hardcoded path
             issuerPrivateKey = loadKey("config/SUPER_SECRET_ISSUER.key");
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            System.err.println("Issuer private key loading failed: " + e.getMessage());
            return;
        }
        ExecutorService workers = Executors.newFixedThreadPool(2);
        HashMap<String, GenerationEntry> generations = new HashMap<>();
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

                    GenerationEntry entry = generations.get(result.name());
                    entry.setKeys(result.keys());
                    entry.setCert(result.cert());
                    entry.setState(KeyState.DONE);

                    for (SelectionKey clientKey : entry.getWaitingUsers()) {
                        var name = ((ClientState)clientKey.attachment()).getName();
                        if (clientKey.isValid()) {
                            try {
                                prepareResponse(clientKey, entry);
                            } catch (CertificateEncodingException e) {
                                System.err.println("Encoding failed for " + name + ": " + e.getMessage());
                                closeClient(clientKey);
                            }
                        }
                    }

                    entry.getWaitingUsers().clear();
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
                            client.register(selector, SelectionKey.OP_READ, new ClientState());
                            System.out.println("Client connected");
                        } catch (IOException e) {
                            client.close();
                            System.err.println("Client error: " + e.getMessage());
                        }
                    } else if (selectionKey.isReadable()) {
                        String name = readName(selectionKey);
                        if (name != null) {
                            GenerationEntry entry = generations.get(name);
                            if (generations.containsKey(name) && generations.get(name).getState() == KeyState.DONE) {
                                //fixme exceptions
                                try {
                                    prepareResponse(selectionKey, entry);
                                } catch (CertificateEncodingException e) {
                                    System.err.println("Encoding failed for " + name + ": " + e.getMessage());
                                    closeClient(selectionKey);
                                }
                            } else if (generations.containsKey(name) && generations.get(name).getState() == KeyState.IN_PROGRESS) {
                                // waiting for results & add another waiting user
                                generations.get(name).getWaitingUsers().add(selectionKey);
                            } else { // computing keys
                                GenerationEntry generatingGenerationEntry = new GenerationEntry(KeyState.IN_PROGRESS, null);
                                // add 1st waiting user
                                generatingGenerationEntry.getWaitingUsers().add(selectionKey);
                                generations.put(name, generatingGenerationEntry);
                                workers.submit(() -> {
                                    try {
                                        KeyGenerator generator = new KeyGenerator();
                                        KeyPair keys = generator.generate();
                                        System.out.println("Key generated for " + name);
                                        X509Certificate cert = new CertificateGenerator().generate(name, keys.getPublic(), issuerPrivateKey, "Nerlikh67CoolCerts");
                                        results.add(new GenerationResult(name, keys, cert, null));
                                    } catch (GeneralSecurityException | OperatorCreationException e) {
                                        System.err.println("Key generation failed: "+ e.getMessage());
                                        results.add(new GenerationResult(name, null, null, e));
                                    } finally {
                                        selector.wakeup();
                                    }
                                });
                            }
                        }
                    } else if (selectionKey.isWritable()) {
                        SocketChannel client = (SocketChannel) selectionKey.channel();
                        ClientState state = (ClientState) selectionKey.attachment();
                        ByteBuffer response = state.getResponse();
                        try {
                            client.write(response);

                            if (!response.hasRemaining()) {
                                closeClient(selectionKey);
                            }
                        } catch (IOException e) {
                            System.err.println("Response write failed: " + e.getMessage());
                            closeClient(selectionKey);
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
        ClientState state = (ClientState) key.attachment();
        StringBuilder name = state.getName();
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
    public static PrivateKey loadKey(String location) throws IOException, GeneralSecurityException {
        String pem = Files.readString(Paths.get(location));
        String encoded = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "");
        // MIME - allows spaces
        byte[] bytes = Base64.getMimeDecoder().decode(encoded);
        KeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(keySpec);
    }

    private static void prepareResponse(SelectionKey selectionKey, GenerationEntry entry)
        throws CertificateEncodingException {
        ByteBuffer response = new ResponseEncoder().encode(entry.getKeys(), entry.getCert());
        ClientState state = (ClientState) selectionKey.attachment();
        state.setResponse(response);
        selectionKey.interestOps(SelectionKey.OP_WRITE);
    }
    private static void closeClient(SelectionKey key) {
        key.cancel();

        try {
            key.channel().close();
        } catch (IOException e) {
            System.err.println("Client close failed: " + e.getMessage());
        }
    }
}

