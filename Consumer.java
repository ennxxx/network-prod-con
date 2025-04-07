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

        // Launch web server immediately
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
                java.awt.Desktop.getDesktop().browse(new URI("http://localhost:8000"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            Socket socket = serverSocket.accept();
            System.out.println("\nConnected to Producer!");

            DataInputStream in = new DataInputStream(socket.getInputStream());

            int p = in.readInt(); // Number of producers
            int c = in.readInt(); // Number of consumers
            int q = in.readInt(); // Queue size

            List<Integer> producerPorts = new ArrayList<>();
            for (int i = 0; i < p; i++) {
                producerPorts.add(in.readInt());
            }

            // Ensure output directory exists and is empty
            File outputDir = new File(OUTPUT_DIR);
            if (outputDir.exists()) {
                for (File file : outputDir.listFiles()) {
                    if (file.isFile()) file.delete();
                }
            } else {
                outputDir.mkdir();
            }

            BlockingQueue<FileData> queue = new ArrayBlockingQueue<>(q);
            List<String> arrivalOrder = Collections.synchronizedList(new ArrayList<>());

            // Start consumer threads
            ExecutorService consumerExecutor = Executors.newFixedThreadPool(c);
            for (int i = 0; i < c; i++) {
                consumerExecutor.submit(() -> {
                    while (true) {
                        try {
                            FileData data = queue.take();
                            if (data == FileData.POISON_PILL) {
                                System.out.println("Consumer exiting...");
                                break;
                            }

                            String timestamp = getCurrentTimestamp();
                            VideoServer.uploadTimestamps.put(data.fileName, timestamp);

                            File outputFile = new File(OUTPUT_DIR + "/" + data.fileName);
                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                fos.write(data.bytes);
                            }

                            System.out.println("Written: " + data.fileName + " at " + getCurrentTimestamp());
                            arrivalOrder.add(data.fileName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            // Notify clients of new files
            new Thread(() -> {
                try {
                    while (true) {
                        File[] currentFiles = outputDir.listFiles();
                        if (currentFiles != null && currentFiles.length > 0) {
                            VideoServer.notifyNewFiles(currentFiles);
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Start producer threads
            ExecutorService producerExecutor = Executors.newFixedThreadPool(producerPorts.size());

            for (int portNum : producerPorts) {
                producerExecutor.submit(() -> {
                    try (ServerSocket producerSocket = new ServerSocket(portNum)) {
                        System.out.println("Listening for producers on port: " + portNum);
                        Boolean isRunning = true;

                        while (isRunning) {
                            try (Socket producerConn = producerSocket.accept();
                                 DataInputStream producerIn = new DataInputStream(producerConn.getInputStream())) {

                                System.out.println("Producer connected on port: " + portNum);

                                while (true) {
                                    String fileName = producerIn.readUTF();
                                    if (fileName.equals("END")) {
                                        System.out.println("Producer disconnected from port " + portNum);
                                        isRunning = false;
                                        break;
                                    };

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
                                        System.out.println("Received from port " + portNum + ": " + fileName + " at " + getCurrentTimestamp());
                                    }
                                }
                            } catch (IOException e) {
                                System.out.println("Connection error on port " + portNum + ": " + e.getMessage());
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Failed to open server socket on port " + portNum + ": " + e.getMessage());
                    } 
                });
            }

            // Send poison pills to stop consumers
            for (int i = 0; i < c; i++) {
                System.out.println("Sent poison pill to consumer " + i);
                queue.put(FileData.POISON_PILL);
            }

            consumerExecutor.shutdown();
            consumerExecutor.awaitTermination(10, TimeUnit.SECONDS);
            producerExecutor.shutdown();

            System.out.println("\n=== Upload Complete ===");
            System.out.println("Files in order of arrival:");
            for (String name : arrivalOrder) {
                System.out.println(name);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

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
                if (file.isDirectory()) deleteDirectory(file);
                else file.delete();
            }
        }
        directory.delete();
    }
}
