package com.campuscare;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

public class CampusCareServer {
    private static ComplaintRepository repository;
    private static final Path PUBLIC_DIR = Path.of("public").toAbsolutePath().normalize();
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "Submitted", "Under Review", "Assigned", "In Progress", "Resolved", "Rejected"
    );

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }

        repository = new ComplaintRepository(Path.of("data", "complaints.tsv"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/complaints", CampusCareServer::handleComplaintsRoute);
        server.createContext("/api/stats", CampusCareServer::handleStatsRoute);
        server.createContext("/api/export", CampusCareServer::handleExportRoute);

        // Static Web Portal Route
        server.createContext("/", CampusCareServer::handleStaticFiles);

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("=============================================================");
        System.out.println("   CampusCare — Campus Complaint Priority System (Java 21)");
        System.out.println("=============================================================");
        System.out.println(" Backend Server running at: http://localhost:" + port);
        System.out.println(" Web Portal accessible at : http://localhost:" + port);
        System.out.println(" Data Storage Path        : " + Path.of("data", "complaints.tsv").toAbsolutePath());
        System.out.println(" Ready to accept student and administrator requests.");
        System.out.println("=============================================================");
    }

    private static void handleComplaintsRoute(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            // 1. GET /api/complaints
            if (method.equals("GET") && path.equals("/api/complaints")) {
                Map<String, String> q = JsonUtil.parseQuery(exchange.getRequestURI().getRawQuery());
                String search = q.getOrDefault("search", "").toLowerCase().trim();
                String status = q.getOrDefault("status", "All");
                String category = q.getOrDefault("category", "All");
                String urgencyStr = q.getOrDefault("urgency", "All");
                String sortBy = q.getOrDefault("sortBy", "priority"); // priority, newest, upvotes

                List<Complaint> list = new ArrayList<>(repository.findAll().stream()
                        .filter(c -> status.equalsIgnoreCase("All") || c.getStatus().equalsIgnoreCase(status))
                        .filter(c -> category.equalsIgnoreCase("All") || c.getCategory().equalsIgnoreCase(category))
                        .filter(c -> {
                            if (urgencyStr.equalsIgnoreCase("All")) return true;
                            try { return c.getUrgency() == Integer.parseInt(urgencyStr); } catch (Exception e) { return true; }
                        })
                        .filter(c -> {
                            if (search.isBlank()) return true;
                            String combined = (c.getTitle() + " " + c.getLocation() + " " + c.getStudentName() + " " + c.getRollNumber() + " " + c.getDepartment()).toLowerCase();
                            return combined.contains(search);
                        })
                        .toList());

                // Sorting
                if ("newest".equalsIgnoreCase(sortBy)) {
                    list.sort(Comparator.comparing(Complaint::getCreatedAt).reversed());
                } else if ("upvotes".equalsIgnoreCase(sortBy)) {
                    list.sort(Comparator.comparingInt(Complaint::getUpvotes).reversed());
                } else {
                    list.sort(Comparator.comparingInt(Complaint::getPriorityScore).reversed()
                            .thenComparing(Complaint::getCreatedAt));
                }

                sendJson(exchange, 200, serializeComplaints(list));
                return;
            }

            // 2. POST /api/complaints (New Complaint)
            if (method.equals("POST") && path.equals("/api/complaints")) {
                String bodyStr = readBody(exchange);
                Map<String, String> body = JsonUtil.parseFlatJson(bodyStr);

                String[] required = {"studentName", "rollNumber", "title", "description", "category", "location", "urgency"};
                for (String key : required) {
                    if (body.getOrDefault(key, "").isBlank()) {
                        sendJson(exchange, 400, "{\"error\":\"Please fill in all required fields.\"}");
                        return;
                    }
                }

                int urgency;
                try {
                    urgency = Math.max(1, Math.min(4, Integer.parseInt(body.get("urgency"))));
                } catch (NumberFormatException e) {
                    urgency = 2;
                }

                Complaint created = repository.add(
                        body.get("studentName"),
                        body.get("rollNumber"),
                        body.get("title"),
                        body.get("description"),
                        body.get("category"),
                        body.get("location"),
                        urgency
                );

                sendJson(exchange, 201, serializeComplaint(created));
                return;
            }

            // 3. POST /api/complaints/{id}/upvote
            if (method.equals("POST") && path.matches("/api/complaints/\\d+/upvote")) {
                int id = Integer.parseInt(path.split("/")[3]);
                Optional<Complaint> updated = repository.upvote(id);
                if (updated.isEmpty()) {
                    sendJson(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                } else {
                    sendJson(exchange, 200, serializeComplaint(updated.get()));
                }
                return;
            }

            // 4. PATCH /api/complaints/{id}/status
            if (method.equals("PATCH") && path.matches("/api/complaints/\\d+/status")) {
                int id = Integer.parseInt(path.split("/")[3]);
                Map<String, String> body = JsonUtil.parseFlatJson(readBody(exchange));
                String status = body.getOrDefault("status", "").trim();
                String remark = body.getOrDefault("adminRemark", "").trim();
                String department = body.getOrDefault("department", "").trim();

                if (!ALLOWED_STATUSES.contains(status)) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid status value provided.\"}");
                    return;
                }

                Optional<Complaint> updated = repository.updateStatus(id, status, remark, department);
                if (updated.isEmpty()) {
                    sendJson(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                } else {
                    sendJson(exchange, 200, serializeComplaint(updated.get()));
                }
                return;
            }

            // 5. DELETE /api/complaints/{id}
            if (method.equals("DELETE") && path.matches("/api/complaints/\\d+")) {
                int id = Integer.parseInt(path.split("/")[3]);
                boolean deleted = repository.delete(id);
                if (deleted) {
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"Complaint deleted.\"}");
                } else {
                    sendJson(exchange, 404, "{\"error\":\"Complaint not found.\"}");
                }
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
        } catch (Exception err) {
            err.printStackTrace();
            sendJson(exchange, 500, "{\"error\":\"Internal server error: " + JsonUtil.quote(err.getMessage()) + "\"}");
        }
    }

    private static void handleStatsRoute(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
            return;
        }

        List<Complaint> all = repository.findAll();
        long total = all.size();
        long open = all.stream().filter(c -> !c.getStatus().equalsIgnoreCase("Resolved") && !c.getStatus().equalsIgnoreCase("Rejected")).count();
        long critical = all.stream().filter(c -> c.getPriorityScore() >= 35 && !c.getStatus().equalsIgnoreCase("Resolved")).count();
        long high = all.stream().filter(c -> c.getPriorityScore() >= 25 && c.getPriorityScore() < 35 && !c.getStatus().equalsIgnoreCase("Resolved")).count();
        long resolved = all.stream().filter(c -> c.getStatus().equalsIgnoreCase("Resolved")).count();
        double rate = total == 0 ? 0 : (resolved * 100.0 / total);

        // Category frequency
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (Complaint c : all) {
            categoryCounts.put(c.getCategory(), categoryCounts.getOrDefault(c.getCategory(), 0) + 1);
        }

        StringBuilder catJson = new StringBuilder("{");
        int idx = 0;
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            if (idx++ > 0) catJson.append(",");
            catJson.append(JsonUtil.quote(entry.getKey())).append(":").append(entry.getValue());
        }
        catJson.append("}");

        String response = String.format(
                "{\"total\":%d,\"open\":%d,\"critical\":%d,\"high\":%d,\"resolved\":%d,\"resolutionRate\":%d,\"categoryCounts\":%s}",
                total, open, critical, high, resolved, Math.round(rate), catJson.toString()
        );

        sendJson(exchange, 200, response);
    }

    private static void handleExportRoute(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
            return;
        }
        byte[] csvBytes = repository.exportCsv().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"Campus_Complaints_Report.csv\"");
        exchange.sendResponseHeaders(200, csvBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(csvBytes);
        }
    }

    private static void handleStaticFiles(HttpExchange exchange) throws IOException {
        String reqPath = exchange.getRequestURI().getPath();
        if (reqPath.equals("/")) reqPath = "/index.html";

        Path file = PUBLIC_DIR.resolve(reqPath.startsWith("/") ? reqPath.substring(1) : reqPath).normalize();

        if (!file.startsWith(PUBLIC_DIR) || !Files.isRegularFile(file)) {
            byte[] notFound = "<h1>404 Not Found - CampusCare</h1>".getBytes(StandardCharsets.UTF_8);
            send(exchange, 404, "text/html; charset=utf-8", notFound);
            return;
        }

        String mime = Files.probeContentType(file);
        if (file.toString().endsWith(".js")) mime = "text/javascript; charset=utf-8";
        else if (file.toString().endsWith(".css")) mime = "text/css; charset=utf-8";
        else if (file.toString().endsWith(".html")) mime = "text/html; charset=utf-8";
        else if (file.toString().endsWith(".svg")) mime = "image/svg+xml";

        send(exchange, 200, mime != null ? mime : "application/octet-stream", Files.readAllBytes(file));
    }

    private static String serializeComplaints(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeComplaint(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String serializeComplaint(Complaint c) {
        long waitingDays = c.getWaitingDays();
        return "{" +
                "\"id\":" + c.getId() + "," +
                "\"formattedCode\":" + JsonUtil.quote(c.formattedCode()) + "," +
                "\"studentName\":" + JsonUtil.quote(c.getStudentName()) + "," +
                "\"rollNumber\":" + JsonUtil.quote(c.getRollNumber()) + "," +
                "\"title\":" + JsonUtil.quote(c.getTitle()) + "," +
                "\"description\":" + JsonUtil.quote(c.getDescription()) + "," +
                "\"category\":" + JsonUtil.quote(c.getCategory()) + "," +
                "\"location\":" + JsonUtil.quote(c.getLocation()) + "," +
                "\"urgency\":" + c.getUrgency() + "," +
                "\"priorityScore\":" + c.getPriorityScore() + "," +
                "\"priority\":" + JsonUtil.quote(c.priorityLabel()) + "," +
                "\"sla\":" + JsonUtil.quote(c.expectedSla()) + "," +
                "\"status\":" + JsonUtil.quote(c.getStatus()) + "," +
                "\"department\":" + JsonUtil.quote(c.getDepartment()) + "," +
                "\"adminRemark\":" + JsonUtil.quote(c.getAdminRemark()) + "," +
                "\"upvotes\":" + c.getUpvotes() + "," +
                "\"waitingDays\":" + waitingDays + "," +
                "\"createdAt\":" + JsonUtil.quote(c.getCreatedAt().toString()) + "," +
                "\"updatedAt\":" + JsonUtil.quote(c.getUpdatedAt().toString()) +
                "}";
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
