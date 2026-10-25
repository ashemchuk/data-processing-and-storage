package ru.ashemchuk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import ru.ashemchuk.generation.CryptoGenerator;
import ru.ashemchuk.network.ClientState;
import ru.ashemchuk.network.GenerationResponse;
import ru.ashemchuk.network.ResponseEncoder;

public final class Server implements AutoCloseable {
    private final CryptoGenerator generator;
    private final Selector selector;
    private final ServerSocketChannel listener;
    private final Queue<GenerationResponse> responses = new ConcurrentLinkedQueue<>();
    private final ResponseEncoder encoder = new ResponseEncoder();
    private volatile boolean running = true;
    private boolean started;

    public Server(int port, CryptoGenerator generator) throws IOException {
        this.generator = generator;
        selector = Selector.open();
        ServerSocketChannel channel = null;
        try {
            channel = ServerSocketChannel.open();
            channel.bind(new InetSocketAddress(port));
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_ACCEPT);
            listener = channel;
        } catch (IOException | RuntimeException e) {
            if (channel != null) channel.close();
            selector.close();
            throw e;
        }
    }

    public int getPort() throws IOException {
        return ((InetSocketAddress) listener.getLocalAddress()).getPort();
    }

    public void start() throws IOException {
        synchronized (this) {
            if (!running) return;
            if (started) throw new IllegalStateException("Server is already started");
            started = true;
        }
        try {
            System.out.println("Waiting for connections on port " + getPort());
            while (running) {
                selector.select();
                if (!running) break;
                processResponses();
                for (SelectionKey key : selector.selectedKeys()) {
                    if (!key.isValid()) continue;
                    try {
                        if (key.isAcceptable()) acceptClient();
                        else if (key.isReadable()) readRequest(key);
                        else if (key.isWritable()) writeResponse(key);
                    } catch (IOException | IllegalArgumentException | CancelledKeyException e) {
                        if (key.channel() == listener) throw new IOException("Accept failed", e);
                        System.err.println("Client request failed: " + e.getMessage());
                        closeClient(key);
                    }
                }
                selector.selectedKeys().clear();
            }
        } finally {
            running = false;
            for (SelectionKey key : new ArrayList<>(selector.keys())) closeClient(key);
            selector.close();
        }
    }

    private void acceptClient() throws IOException {
        SocketChannel client = listener.accept();
        if (client == null) return;
        try {
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ, new ClientState());
        } catch (IOException | RuntimeException e) {
            try {
                client.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            System.err.println("Client setup failed: " + e.getMessage());
        }
    }

    private void readRequest(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        ByteBuffer input = state.getInput();
        input.clear();
        int count = client.read(input);
        if (count == -1) {
            closeClient(key);
            return;
        }
        if (count == 0) return;
        input.flip();
        state.getRequestDecoder().decode(input).ifPresent(request -> {
            key.interestOps(0);
            generator.getOrGenerate(request.name()).whenComplete((identity, error) -> {
                responses.add(new GenerationResponse(key, identity, error));
                selector.wakeup();
            });
        });
    }

    private void processResponses() {
        GenerationResponse response;
        while ((response = responses.poll()) != null) {
            SelectionKey key = response.client();
            if (!key.isValid()) continue;
            if (response.error() != null) {
                System.err.println("Generation failed: " + response.error().getMessage());
                closeClient(key);
                continue;
            }
            try {
                ClientState state = (ClientState) key.attachment();
                state.setResponse(encoder.encode(response.identity()));
                key.interestOps(SelectionKey.OP_WRITE);
            } catch (java.security.cert.CertificateEncodingException | CancelledKeyException e) {
                System.err.println("Response encoding failed: " + e.getMessage());
                closeClient(key);
            }
        }
    }

    private void writeResponse(SelectionKey key) throws IOException {
        ClientState state = (ClientState) key.attachment();
        ((SocketChannel) key.channel()).write(state.getResponse());
        if (!state.getResponse().hasRemaining()) closeClient(key);
    }

    private void closeClient(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException e) {
            System.err.println("Client close failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        selector.wakeup();
        if (!started) {
            try {
                listener.close();
            } catch (IOException e) {
                System.err.println("Listener close failed: " + e.getMessage());
            } finally {
                try {
                    selector.close();
                } catch (IOException e) {
                    System.err.println("Selector close failed: " + e.getMessage());
                }
            }
        }
    }
}
