import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TodoServer {

    static class Task {
        int id;
        String title;
        String listName;
        String dateTime;
        boolean completed;

        Task(int id, String title, String listName, String dateTime) {
            this.id = id;
            this.title = title;
            this.listName = listName;
            this.dateTime = dateTime;
            this.completed = false;
        }
    }

    private static final List<Task> tasks = new ArrayList<>();
    private static final AtomicInteger idCounter = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        // Sample starter tasks so the app looks polished on first load
        tasks.add(new Task(idCounter.getAndIncrement(), "Complete Task 04 To-Do Web App", "Work", "2026-11-14T18:00"));
        tasks.add(new Task(idCounter.getAndIncrement(), "Buy groceries for the week", "Personal", "2026-11-15T10:30"));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Static files
        server.createContext("/", new FileHandler("index.html", "text/html"));
        server.createContext("/style.css", new FileHandler("style.css", "text/css"));

        // API Endpoints
        server.createContext("/api/tasks", exchange -> sendTasksJson(exchange));
        server.createContext("/api/add", exchange -> handleAdd(exchange));
        server.createContext("/api/edit", exchange -> handleEdit(exchange));
        server.createContext("/api/toggle", exchange -> handleToggle(exchange));
        server.createContext("/api/delete", exchange -> handleDelete(exchange));

        server.setExecutor(null);
        server.start();
        System.out.println("To-Do Server running at http://localhost:8080");
    }

    private static void handleAdd(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String title = params.getOrDefault("title", "").trim();
        String listName = params.getOrDefault("list", "Personal").trim();
        String dateTime = params.getOrDefault("dateTime", "").trim();

        if (!title.isEmpty()) {
            tasks.add(new Task(idCounter.getAndIncrement(), title, listName, dateTime));
        }
        sendTasksJson(exchange);
    }

    private static void handleEdit(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        int id = Integer.parseInt(params.getOrDefault("id", "-1"));
        String title = params.getOrDefault("title", "").trim();
        String listName = params.getOrDefault("list", "Personal").trim();
        String dateTime = params.getOrDefault("dateTime", "").trim();

        for (Task t : tasks) {
            if (t.id == id && !title.isEmpty()) {
                t.title = title;
                t.listName = listName;
                t.dateTime = dateTime;
                break;
            }
        }
        sendTasksJson(exchange);
    }

    private static void handleToggle(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        int id = Integer.parseInt(params.getOrDefault("id", "-1"));

        for (Task t : tasks) {
            if (t.id == id) {
                t.completed = !t.completed;
                break;
            }
        }
        sendTasksJson(exchange);
    }

    private static void handleDelete(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        int id = Integer.parseInt(params.getOrDefault("id", "-1"));
        tasks.removeIf(t -> t.id == id);
        sendTasksJson(exchange);
    }

    private static void sendTasksJson(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            json.append(String.format(
                "{\"id\":%d,\"title\":\"%s\",\"list\":\"%s\",\"dateTime\":\"%s\",\"completed\":%b}",
                t.id, escapeJson(t.title), escapeJson(t.listName), escapeJson(t.dateTime), t.completed
            ));
            if (i < tasks.size() - 1) json.append(",");
        }
        json.append("]");

        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, val);
        }
        return map;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    static class FileHandler implements HttpHandler {
        private final String filePath;
        private final String contentType;

        public FileHandler(String filePath, String contentType) {
            this.filePath = filePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}