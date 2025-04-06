import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class VideoServer {
    // Use ConcurrentHashMap instead of CopyOnWriteArraySet for better performance
    private static final Map<HttpExchange, Boolean> clients = new ConcurrentHashMap<>();
    public static final Map<String, String> uploadTimestamps = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        File videoDir = new File("output");

        // Serve static frontend HTML
        server.createContext("/", exchange -> {
            try {
                byte[] html = Files.readAllBytes(new File("index.html").toPath());
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
            } finally {
                exchange.close();
            }
        });

        // Serve video files
        server.createContext("/videos", exchange -> {
            String path = exchange.getRequestURI().getPath().replace("/videos/", "");
            File file = new File(videoDir, path);
            if (file.exists()) {
                exchange.getResponseHeaders().add("Content-Type", "video/mp4");
                exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
                exchange.getResponseHeaders().add("Content-Length", String.valueOf(file.length()));
                exchange.sendResponseHeaders(200, file.length());
                Files.copy(file.toPath(), exchange.getResponseBody());
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });

        // Serve video list as JSON
        server.createContext("/list", exchange -> {
            try {
                StringBuilder json = new StringBuilder("[");
                File[] files = videoDir.listFiles((dir, name) -> name.endsWith(".mp4"));
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        String filename = files[i].getName();
                        String timestamp = uploadTimestamps.getOrDefault(filename,
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(files[i].lastModified())));
                        
                        json.append("{\"name\":\"").append(filename)
                           .append("\",\"time\":\"").append(timestamp)
                           .append("\"}");
                        if (i < files.length - 1) json.append(",");
                    }
                }
                json.append("]");
                byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });

        // Server-Sent Events endpoint
        server.createContext("/updates", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            clients.put(exchange, true);
            
            // Clean up when connection is closed
            exchange.getRequestBody().close();
        });

        System.out.println("Server started at http://localhost:" + port);
        server.start();
    }

    public static void notifyNewFiles(File[] files) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < files.length; i++) {
            String filename = files[i].getName();
            String timestamp = uploadTimestamps.getOrDefault(filename,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(files[i].lastModified())));
            
            json.append("{\"name\":\"").append(filename)
               .append("\",\"time\":\"").append(timestamp)
               .append("\"}");
            if (i < files.length - 1) json.append(",");
        }
        json.append("]");
        
        String message = "data: " + json.toString() + "\n\n";
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        
        // Use ConcurrentHashMap's keySet for safe iteration
        clients.keySet().removeIf(client -> {
            try {
                client.getResponseBody().write(data);
                client.getResponseBody().flush();
                return false;
            } catch (IOException e) {
                return true; // Remove disconnected clients
            }
        });
    }
}