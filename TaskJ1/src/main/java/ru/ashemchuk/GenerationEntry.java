package ru.ashemchuk;

import java.nio.channels.SelectionKey;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class GenerationEntry {
    private KeyState state;
    private KeyPair keys;
    private X509Certificate cert;
    private final List<SelectionKey> waitingUsers;

    public X509Certificate getCert() {
        return cert;
    }

    public void setCert(X509Certificate cert) {
        this.cert = cert;
    }

    public KeyState getState() {
        return state;
    }

    public KeyPair getKeys() {
        return keys;
    }

    public GenerationEntry(KeyState state, KeyPair keys) {
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
