package ru.ashemchuk;

import java.nio.channels.SelectionKey;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

public class Key {
    private KeyState state;
    private KeyPair keys;
    private final List<SelectionKey> waitingUsers;

    public KeyState getState() {
        return state;
    }

    public KeyPair getKeys() {
        return keys;
    }

    public Key(KeyState state, KeyPair keys) {
        this.state = state;
        this.keys = keys;
        this.waitingUsers = new ArrayList<>();
    }

    public void setState(KeyState state) {
        this.state = state;
    }

    public void setKeys(KeyPair keys) {
        this.keys = keys;
    }

    public List<SelectionKey> getWaitingUsers() {
        return waitingUsers;
    }
}
