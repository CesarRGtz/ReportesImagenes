package com.teosa.app.prototipo.network;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class LocalReportServer implements AutoCloseable {
    private final HttpServer server;
    private final ServerStorage storage;

    public LocalReportServer(int port, ServerStorage storage) throws IOException {
        this.storage = storage;
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() { server.start(); }
    public int getPort() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath().substring("/api".length());
            String[] parts = path.split("/");
            if (path.equals("/health") && method.equals("GET")) {
                JsonObject health = new JsonObject();
                health.addProperty("ok", true);
                health.addProperty("computer", UserIdentity.computer());
                send(exchange, 200, health);
            } else if (path.equals("/reports") && method.equals("GET")) {
                send(exchange, 200, storage.listReports(query(exchange.getRequestURI(), "q")));
            } else if (path.equals("/reports") && method.equals("POST")) {
                ReportTransfer transfer = read(exchange, ReportTransfer.class);
                send(exchange, 200, storage.saveReport(transfer));
            } else if (parts.length == 4 && parts[1].equals("reports")
                    && parts[3].equals("versions") && method.equals("GET")) {
                send(exchange, 200, storage.listVersions(decode(parts[2])));
            } else if (parts.length == 5 && parts[1].equals("reports")
                    && parts[3].equals("versions")) {
                String id = decode(parts[2]);
                int version = Integer.parseInt(parts[4]);
                if (method.equals("GET")) send(exchange, 200, storage.loadReport(id, version));
                else if (method.equals("DELETE")) { storage.deleteVersion(id, version); sendOk(exchange); }
                else sendError(exchange, 405, "Método no permitido");
            } else if (parts.length == 3 && parts[1].equals("reports") && method.equals("DELETE")) {
                storage.deleteReport(decode(parts[2]));
                sendOk(exchange);
            } else if (path.equals("/templates") && method.equals("GET")) {
                send(exchange, 200, storage.listTemplates());
            } else if (path.equals("/templates") && method.equals("POST")) {
                storage.saveTemplate(read(exchange, TemplateDefinition.class));
                sendOk(exchange);
            } else if (parts.length == 3 && parts[1].equals("templates") && method.equals("DELETE")) {
                storage.deleteTemplate(decode(parts[2]));
                sendOk(exchange);
            } else {
                sendError(exchange, 404, "Ruta no encontrada");
            }
        } catch (Exception ex) {
            sendError(exchange, 500, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } finally {
            exchange.close();
        }
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return JsonSupport.GSON.fromJson(json, type);
    }

    private void sendOk(HttpExchange exchange) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        send(exchange, 200, result);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("message", message);
        send(exchange, status, result);
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = JsonSupport.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
    }

    private String query(URI uri, String name) {
        if (uri.getRawQuery() == null) return "";
        for (String entry : uri.getRawQuery().split("&")) {
            String[] pair = entry.split("=", 2);
            if (decode(pair[0]).equals(name)) return pair.length > 1 ? decode(pair[1]) : "";
        }
        return "";
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override public void close() { server.stop(1); }
}
