package ru.ashemchuk;

import java.nio.ByteBuffer;

public class ClientState {
    private final StringBuilder name;
    private ByteBuffer response;

    public ClientState() {
        this.name = new StringBuilder();
    }

    public StringBuilder getName() {
        return name;
    }

    public ByteBuffer getResponse() {
        return response;
    }

    public void setResponse(ByteBuffer response) {
        this.response = response;
    }
}
