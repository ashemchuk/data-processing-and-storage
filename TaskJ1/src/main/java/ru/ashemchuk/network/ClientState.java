package ru.ashemchuk.network;

import java.nio.ByteBuffer;

public final class ClientState {
    private final RequestDecoder requestDecoder = new RequestDecoder();
    private final ByteBuffer input = ByteBuffer.allocate(1024);
    private ByteBuffer response;

    public RequestDecoder getRequestDecoder() { return requestDecoder; }
    public ByteBuffer getInput() { return input; }
    public ByteBuffer getResponse() { return response; }
    public void setResponse(ByteBuffer response) { this.response = response; }
}
