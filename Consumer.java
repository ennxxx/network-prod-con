import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class Consumer {
    private static final String OUTPUT_DIR = "cons_output";

    public static void main(String[] args) {
        int port = 12345;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Waiting for Producer...");

            Socket socket = serverSocket.accept();
            System.out.println("Connected to Producer!");

            DataInputStream in = new DataInputStream(socket.getInputStream());

            int p = in.readInt();
            int c = in.readInt();
            int q = in.readInt();

            BlockingQueue<FileData> queue = new ArrayBlockingQueue<>(q);
            List<String> arrivalOrder = Collections.synchronizedList(new ArrayList<>());

            // Start consumer threads
            ExecutorService executor = Executors.newFixedThreadPool(c);
            for (int i = 0; i < c; i++) {
                executor.submit(() -> {
                    while (true) {
                        try {
                            FileData data = queue.take();
                            if (data == FileData.POISON_PILL) break;

                            File outputFile = new File(OUTPUT_DIR + "/" + data.fileName);
                            FileOutputStream fos = new FileOutputStream(outputFile);
                            fos.write(data.bytes);
                            fos.close();

                            arrivalOrder.add(data.fileName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

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

                FileData fd = new FileData(fileName, fileData);
                if (!queue.offer(fd)) {
                    System.out.println("Dropped: " + fileName + " (Queue full)");
                } else {
                    System.out.println("Received: " + fileName);
                }
            }

            // Stop consumers
            for (int i = 0; i < c; i++) {
                queue.offer(FileData.POISON_PILL);
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
