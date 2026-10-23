package ru.ashemchuk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        try (Socket socket = new Socket("localhost", 8080)) {
            System.out.println("Connected to server");
            OutputStream output = socket.getOutputStream();

            output.write("An".getBytes(StandardCharsets.US_ASCII));
            output.flush();

            Thread.sleep(10_000);

            output.write("na".getBytes(StandardCharsets.US_ASCII));
            output.write(0);
            output.flush();
        }
    }
}
