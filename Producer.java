import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Producer {
    private static final int MAX_PRODUCER_FOLDERS = 5;
    private static final String INPUT_DIR = "input";

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        int producerThreads = 0;
        int consumerThreads = 0;
        int queueSize = 0;

        System.out.println("Preparing Producer...\n");

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
            if (consumerThreads >= 1 && consumerThreads <= 100) {
                break; // Valid input
            } else {
                System.out.println("Error: Number of consumer threads must be between 1 and 100.");
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

        // Create a list of port numbers for the producer threads
        List<Integer> producerPorts = new ArrayList<>();
        for (int i = 0; i < producerThreads; i++) {
            producerPorts.add(port + i + 1);
        }

        try (Socket socket = new Socket(serverIp, port)) {
            System.out.println("\n=== Producer Logs ===");

            // Send user inputs to the consumer
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(producerThreads);
            out.writeInt(consumerThreads);
            out.writeInt(queueSize);
            for (int producerPort : producerPorts) {
                out.writeInt(producerPort);
            }

            // Create an array of threads and assign each thread to a folder
            Thread[] threads = new Thread[producerThreads];
            for (int i = 0; i < producerThreads; i++) {
                final int index = i + 1;
                threads[i] = new Thread(() -> {
                    File folder = new File(INPUT_DIR + "/prod" + index);
                    File[] files = folder.listFiles();
                    
                    try(Socket producerSocket = new Socket(serverIp, producerPorts.get(index - 1))) {
                        DataOutputStream threadOut = new DataOutputStream(producerSocket.getOutputStream());
                        System.out.println("\033[32m[CONNECTED] Thread " + index + " to Consumer at port " + producerPorts.get(index - 1) + "\033[0m");
                        if (files != null) {
                            for (File file : files) {
                                try {
                                    // This makes sure that multiple threads don't write to the output stream at the same time
                                    synchronized (threadOut) {
                                        threadOut.writeUTF(file.getName()); // File name
                                        threadOut.writeLong(file.length()); // File size
                                        
                                        print("Video File: " + file.getName() + "found with size " + file.length() + " bytes");
                                        
                                        FileInputStream fis = new FileInputStream(file);
                                        byte[] buffer = new byte[4096];
                                        int bytesRead;
                                        while ((bytesRead = fis.read(buffer)) != -1) {
                                            threadOut.write(buffer, 0, bytesRead);
                                        }
                                        fis.close();
                                        System.out.println("Sent: " + file.getName() + " at " + getCurrentTimestamp());
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        threadOut.writeUTF("END");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                threads[i].start();
            }
            
            // Wait for all producer threads to finish
            for (Thread t : threads) {
                t.join();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        scanner.close();
    }
}