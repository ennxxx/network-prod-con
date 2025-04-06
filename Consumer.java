import java.io.*;
import java.net.*;

public class Consumer {

    public static void main(String[] args) {
        int port = 12345;  

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Consumer is waiting for incoming connection...");

            Socket socket = serverSocket.accept();
            System.out.println("Producer connected!");

            DataInputStream input = new DataInputStream(socket.getInputStream());

            int producerThreads = input.readInt();
            int consumerThreads = input.readInt();
            int queueSize = input.readInt();

            System.out.println("Received producer threads: " + producerThreads);
            System.out.println("Received consumer threads: " + consumerThreads);
            System.out.println("Received queue size: " + queueSize);

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
