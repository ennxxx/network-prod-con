import java.io.*;
import java.net.*;
import java.util.*;

public class Producer {
    private static final int MAX_PRODUCER_FOLDERS = 5;
    private static final String INPUT_DIR = "input";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int producerThreads = 0, consumerThreads = 0, queueSize = 0;

        System.out.println("Starting Producer...");

        // Input validation for producer threads
        while (true) {
            System.out.print("Enter number of producer threads: ");
            producerThreads = scanner.nextInt();
            
            // Check if the number of producer threads is valid and within the folder limit
            if (producerThreads > 0 && producerThreads <= MAX_PRODUCER_FOLDERS) {
                break; // Valid input
            } else if (producerThreads <= 0) {
                System.out.println("Error: The number of producer threads must be positive.");
            } else {
                System.out.println("Error: Only " + MAX_PRODUCER_FOLDERS + " input folders are available.");
            }
        }

        // Input validation for consumer threads
        while (true) {
            System.out.print("Enter number of consumer threads: ");
            consumerThreads = scanner.nextInt();
            if (consumerThreads > 0) {
                break; // Valid input
            } else {
                System.out.println("Error: The number of consumer threads must be positive.");
            }
        }

        // Input validation for queue size
        while (true) {
            System.out.print("Enter max queue size (between 1 and 100): ");
            queueSize = scanner.nextInt();
            if (queueSize >= 1 && queueSize <= 100) {
                break; // Valid input
            } else {
                System.out.println("Error: Queue size must be between 1 and 100.");
            }
        }

        // Connecting to the consumer
        String serverIp = "172.16.146.131"; // Replace with VM IP address
        int port = 12345;

        try (Socket socket = new Socket(serverIp, port)) {
            System.out.println("\n=== Connected to Consumer ===");

            // Send user inputs to the consumer
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(producerThreads);
            out.writeInt(consumerThreads);
            out.writeInt(queueSize);

            // Create an array of threads and assign each thread to a folder
            Thread[] threads = new Thread[producerThreads];
            for (int i = 0; i < producerThreads; i++) {
                final int index = i + 1;
                threads[i] = new Thread(() -> {
                    File folder = new File(INPUT_DIR + "/prod" + index);
                    if (!folder.exists() || !folder.isDirectory()) {
                        System.out.println("Error: Folder " + folder.getPath() + " does not exist or is not a directory.");
                        continue; // Skip this producer thread if the folder doesn't exist
                    }
                    File[] files = folder.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            try {
                                // This makes sure that multiple threads don't write to the output stream at the same time
                                synchronized (out) {
                                    out.writeUTF(file.getName()); // File name
                                    out.writeLong(file.length()); // File size
                                    
                                    FileInputStream fis = new FileInputStream(file);
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    while ((bytesRead = fis.read(buffer)) != -1) {
                                        out.write(buffer, 0, bytesRead);
                                    }
                                    fis.close();
                                    System.out.println("Sent: " + file.getName());
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                threads[i].start();
            }
            // Wait for all producer threads to finish
            for (Thread t : threads) {
                t.join();
            }

            // Send this signal to the consumer to indicate that all files have been sent
            out.writeUTF("END");
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        scanner.close();
    }
}
