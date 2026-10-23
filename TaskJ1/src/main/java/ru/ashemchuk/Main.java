package ru.ashemchuk;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(8080)){
            System.out.println("Waiting...");
            try(Socket connection = server.accept()) {
                System.out.println("Connected");
                InputStream input = connection.getInputStream();
                StringBuilder str = new StringBuilder();
                while (true) {
                    int i = input.read();
                    if (i == -1) {
                        System.out.println("AAA");
                        return;
                    }
                    if (i == 0) {
                        System.out.println("End of name");
                        break;
                    }
                    char c = (char)i;
                    str.append(c);
                }
                System.out.println(str);
            }
        }
    }
}