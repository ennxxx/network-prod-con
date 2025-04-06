import java.io.*;
import java.net.*;
import java.util.*;

public class Producer {
    private static final int MAX_PRODUCER_FOLDERS = 5;
    private static final String INPUT_DIR = "input";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter number of producer threads: ");
        int producerThreads = scanner.nextInt();

        System.out.print("Enter number of consumer threads: ");
        int consumerThreads = scanner.nextInt();

        System.out.print("Enter max queue size: ");
        int queueSize = scanner.nextInt();

        if (producerThreads > MAX_PRODUCER_FOLDERS) {
            System.out.println("Error: Only " + MAX_PRODUCER_FOLDERS + " input folders are available.");
            scanner.close();
            return;
        }

        String serverIp = "172.16.146.131"; // VM IP
        int port = 12345;

        try (Socket socket = new Socket(serverIp, port)) {
            System.out.println("Connected to Consumer!");

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(producerThreads);
            out.writeInt(consumerThreads);
            out.writeInt(queueSize);

            // Start producer threads
            Thread[] threads = new Thread[producerThreads];
            for (int i = 0; i < producerThreads; i++) {
                final int index = i + 1;
                threads[i] = new Thread(() -> {
                    File folder = new File(INPUT_DIR + "/prod" + index);
                    File[] files = folder.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            try {
                                synchronized (out) {
                                    out.writeUTF(file.getName());
                                    out.writeLong(file.length());

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

            for (Thread t : threads) {
                t.join();
            }

            // Signal end of transmission
            out.writeUTF("END");

        } catch (Exception e) {
            e.printStackTrace();
        }

        scanner.close();
    }
}
