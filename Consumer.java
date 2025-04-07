import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;

public class Consumer {
    private static final String OUTPUT_DIR = "output";

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }

    public static void main(String[] args) {
        int port = 12345;

        // Launch web server immediately in a separate thread
        new Thread(() -> {
            try {
                VideoServer.main(new String[]{});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(new File(OUTPUT_DIR));
                System.out.println("\nOutput directory deleted on shutdown...");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Waiting for Producer...");

        Thread.sleep(1000);

        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:8000"));
        } catch (Exception e) {
            e.printStackTrace();
        }

            Socket socket = serverSocket.accept();
            System.out.println("\nConnected to Producer!");

            DataInputStream in = new DataInputStream(socket.getInputStream());

            int p = in.readInt();
            int c = in.readInt();
            int q = in.readInt();
            
            // Add code for the new inputs
            List<Integer> producerPorts = new ArrayList<>();
            for (int i = 0; i < p; i++) {
                producerPorts.add(in.readInt());
            }

            // Ensure output directory exists and is empty
            File outputDir = new File(OUTPUT_DIR);
            if (outputDir.exists()) {
                // Delete all files in the output directory
                for (File file : outputDir.listFiles()) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            } else {
                // Create the output directory if it doesn't exist
                outputDir.mkdir();
            }
            
            // This is to hold the file data that consumer threads will process
            // Consumer will block when the queue is empty (waiting for new files)
            // Queue will not exceed the size specified by the user
            BlockingQueue<FileData> queue = new ArrayBlockingQueue<>(q);
            List<String> arrivalOrder = Collections.synchronizedList(new ArrayList<>());

            ExecutorService producerExecutor = Executors.newFixedThreadPool(producerPorts.size());

            for (int producerPort : producerPorts) {
                producerExecutor.submit(() -> {
                    try (ServerSocket producerSocket = new ServerSocket(producerPort)) {
                        System.out.println("Listening for producers on port: " + producerPort);

                        while (true) {
                            try (Socket producerClientSocket = producerSocket.accept();
                                DataInputStream producerIn = new DataInputStream(producerClientSocket.getInputStream())) {
                                
                                System.out.println("Producer connected on port: " + producerPort);
                                
                                // Read files from the producer
                                while (true) {
                                    String fileName = producerIn.readUTF();
                                    if (fileName.equals("END")) break;

                                    long fileSize = producerIn.readLong();
                                    byte[] fileData = new byte[(int) fileSize];

                                    int totalRead = 0;
                                    while (totalRead < fileSize) {
                                        int read = producerIn.read(fileData, totalRead, (int) fileSize - totalRead);
                                        if (read == -1) break;
                                        totalRead += read;
                                    }

                                    FileData fd = new FileData(fileName, fileData);
                                    if (!queue.offer(fd)) {
                                        System.out.println("Dropped: " + fileName + " (Queue full)");
                                    } else {
                                        System.out.println("Received from port " + producerPort + ": " + fileName + " at " + getCurrentTimestamp());
                                    }
                                }

                                System.out.println("Producer disconnected from port " + producerPort);
                                break;

                            } catch (IOException e) {
                                System.out.println("Connection error on port " + producerPort + ": " + e.getMessage());
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Failed to open server socket on port " + producerPort + ": " + e.getMessage());
                    }
                });
            }

            // Stop consumers
            for (int i = 0; i < c; i++) {
                System.out.println("Sent poison pill to consumer " + i);
                queue.put(FileData.POISON_PILL);
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            System.out.println("\n=== Upload Complete ===");
            System.out.println("Files in order of arrival:");
            for (String name : arrivalOrder) {
                System.out.println(name);
            }
            

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Class to hold file data
    static class FileData {
        String fileName;
        byte[] bytes;

        static final FileData POISON_PILL = new FileData("POISON", new byte[0]);

        FileData(String fileName, byte[] bytes) {
            this.fileName = fileName;
            this.bytes = bytes;
        }
    }

    private static void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}