import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class VideoServer {
    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        File videoDir = new File("output");

        // Serve static frontend HTML
        server.createContext("/", exchange -> {
            byte[] html = Files.readAllBytes(new File("index.html").toPath());
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length);
            exchange.getResponseBody().write(html);
            exchange.close();
        });

        // Serve video files
        server.createContext("/videos", exchange -> {
            String path = exchange.getRequestURI().getPath().replace("/videos/", "");
            File file = new File(videoDir, path);
            if (file.exists()) {
                exchange.getResponseHeaders().add("Content-Type", "video/mp4");
                exchange.sendResponseHeaders(200, file.length());
                Files.copy(file.toPath(), exchange.getResponseBody());
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });

        // Serve video list as JSON
        server.createContext("/list", exchange -> {
            StringBuilder json = new StringBuilder("[");
            File[] files = videoDir.listFiles((dir, name) -> name.endsWith(".mp4"));
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    json.append("\"").append(files[i].getName()).append("\"");
                    if (i < files.length - 1) json.append(",");
                }
            }
            json.append("]");
            byte[] response = json.toString().getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        System.out.println("Server started at http://localhost:" + port);
        server.start();
    }
}
