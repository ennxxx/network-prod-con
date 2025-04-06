import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Producer {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter number of producer threads: ");
        int producerThreads = scanner.nextInt();

        System.out.print("Enter number of consumer threads: ");
        int consumerThreads = scanner.nextInt();

        System.out.print("Enter max queue size: ");
        int queueSize = scanner.nextInt();

        // Consumer's IP address and port to connect to
        String serverIp = "172.16.146.131";  // Consumer's IP address
        int port = 12345;

        try (Socket socket = new Socket(serverIp, port)) {
            System.out.println("Connected to Consumer!");

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            // Send the data (producer threads, consumer threads, queue size)
            output.writeInt(producerThreads);
            output.writeInt(consumerThreads);
            output.writeInt(queueSize);

            System.out.println("Data sent to Consumer:");
            System.out.println("Producer threads: " + producerThreads);
            System.out.println("Consumer threads: " + consumerThreads);
            System.out.println("Queue size: " + queueSize);

        } catch (IOException e) {
            e.printStackTrace();
        }

        scanner.close();
    }
}
