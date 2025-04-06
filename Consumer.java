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

            // Start consumer threads
            ExecutorService executor = Executors.newFixedThreadPool(c);
            for (int i = 0; i < c; i++) {
                executor.submit(() -> {
                    while (true) {
                        try {
                            FileData data = queue.take(); // Blocks thread until data is available
                            
                             // Signals to stop processing
                            if (data == FileData.POISON_PILL) {
                                System.out.println("Consumer exiting...");
                                break;
                            }

                            String timestamp = getCurrentTimestamp();
                            VideoServer.uploadTimestamps.put(data.fileName, timestamp);

                            // Process the file data by writing it to the output directory
                            File outputFile = new File(OUTPUT_DIR + "/" + data.fileName);
                            FileOutputStream fos = new FileOutputStream(outputFile);
                            fos.write(data.bytes);
                            fos.close();

                            System.out.println("Written: " + data.fileName + " at " + getCurrentTimestamp());

                            arrivalOrder.add(data.fileName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            new Thread(() -> {
                try {
                    while (true) {
                        File[] currentFiles = outputDir.listFiles();
                        if (currentFiles != null && currentFiles.length > 0) {
                            // Notify clients of new files
                            VideoServer.notifyNewFiles(currentFiles);
                        }
                        Thread.sleep(1000); // Check every second
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Read incoming files from Producer
            while (true) {
                String fileName = in.readUTF();
                if (fileName.equals("END")) break;

                long fileSize = in.readLong();
                byte[] fileData = new byte[(int) fileSize];

                int totalRead = 0;
                while (totalRead < fileSize) {
                    int read = in.read(fileData, totalRead, (int) fileSize - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                // This inserts file data into the queue
                // If the queue is full, the file will be dropped
                // and a message will be printed to the console
                FileData fd = new FileData(fileName, fileData);
                if (!queue.offer(fd)) {
                    System.out.println("Dropped: " + fileName + " (Queue full)");
                } else {
                    System.out.println("Received: " + fileName + " at " + getCurrentTimestamp());
                }
            }

            // Stop consumers
            for (int i = 0; i < c; i++) {
                queue.put(FileData.POISON_PILL);
                System.out.println("Sent poison pill to consumer " + i);
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
}