package ru.ashemchuk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 8080)) {
            System.out.println("YOHOO");
            OutputStream output = socket.getOutputStream();

            output.write("Anna".getBytes(StandardCharsets.US_ASCII));
            output.write(0);
            output.flush();
        }
    }
}
