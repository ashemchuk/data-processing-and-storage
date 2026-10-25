package ru.ashemchuk.network;

import java.nio.channels.SelectionKey;
import ru.ashemchuk.generation.CryptoIdentity;

public record GenerationResponse(SelectionKey client, CryptoIdentity identity, Throwable error) {}
